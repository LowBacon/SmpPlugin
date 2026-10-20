package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.economy.LedgerEntry;
import com.bx.smpPlugin.economy.Money;
import com.bx.smpPlugin.models.EconomyFailureReason;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.models.EconomyTransferResult;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.PlayerSettingDefaults;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The one service that changes money balances. Every change is computed in whole cents, checked against the
 * balance limit, and written to the {@code economy_ledger} together with the new balance:
 *
 * <ul>
 *   <li><b>Online players</b> (and players whose quit save is still queued): the balance changes in memory at once
 *       and the new balance plus its ledger row are written by the database writer in one transaction, normally
 *       within milliseconds. Writes carry a balance version, so they can arrive in any order without an older
 *       balance overwriting a newer one.</li>
 *   <li><b>Offline players</b>: the change is one database transaction (read, compare-and-set on the balance
 *       version, ledger row), retried when another server changed the same row first. Nothing is held in memory,
 *       so two changes can never overwrite each other.</li>
 * </ul>
 *
 * <p>When the database is not ready no money moves at all ({@link EconomyFailureReason#STORAGE_UNAVAILABLE}).</p>
 */
public class EconomyManager {

    private static final int OFFLINE_ATTEMPTS = 5;
    private static final int WRITE_ATTEMPTS = 3;
    /** Default ECONOMY.MAX-BALANCE: 10 trillion. */
    public static final double DEFAULT_MAX_BALANCE = 10_000_000_000_000D;

    public record AccountReference(UUID uuid, String displayName, Player onlinePlayer) {
        public boolean isOnline() {
            return onlinePlayer != null;
        }
    }

    /** One page of a player's ledger, newest first. */
    public record HistoryPage(UUID player, int page, int pageSize, int total, List<LedgerEntry> entries) {
        public int pages() {
            return Math.max(1, (total + pageSize - 1) / pageSize);
        }
    }

    /** Money moved per reason since a point in time, and the money supply now. */
    public record EconomyStats(long sinceMillis, List<EconomyStore.ReasonTotal> totals,
                               EconomyStore.MoneySupply supply) {
    }

    /** The /pay rules from ECONOMY.PAY. {@code maxCents} 0 means no limit. */
    public record PayRules(long minCents, long maxCents, double taxPercent, long cooldownMillis) {
        /** Tax on a payment, always leaving the recipient at least one cent. */
        public long taxFor(long cents) {
            return Math.min(Money.percentOf(cents, taxPercent), Math.max(0L, cents - 1L));
        }
    }

    private enum Op { DEPOSIT, WITHDRAW, SET }

    /** One balance change inside a transaction. */
    private record Leg(UUID uuid, String nameHint, Op op, long cents, boolean createIfMissing, UUID counterparty) {
    }

    /** What a leg did. */
    private record LegResult(UUID uuid, String name, long beforeCents, long afterCents, long version, boolean online) {
    }

    private record Outcome(EconomyFailureReason failure, int failedLeg, List<LegResult> legs, long failedBefore) {
        static Outcome ok(List<LegResult> legs) {
            return new Outcome(EconomyFailureReason.NONE, -1, legs, 0L);
        }

        static Outcome fail(EconomyFailureReason failure, int leg, long before) {
            return new Outcome(failure, leg, List.of(), before);
        }

        boolean success() {
            return failure == EconomyFailureReason.NONE;
        }
    }

    private record Plan(long after, EconomyFailureReason failure) {
        boolean ok() {
            return failure == EconomyFailureReason.NONE;
        }
    }

    /** A live (in-memory) leg after planning: the data it changes and the balance before. */
    private record LiveLeg(int index, PlayerData data, long beforeCents, long afterCents) {
    }

    /** Signals that an offline row changed between reading and writing; the transaction is retried. */
    private static final class ConcurrentChange extends RuntimeException {
        ConcurrentChange() {
            super(null, null, false, false);
        }
    }

    private final SmpPlugin plugin;
    /** Highest balance version an offline change wrote per player, so a login that read the row earlier re-reads it. */
    private final Map<UUID, Long> offlineVersions = new ConcurrentHashMap<>();
    private final AtomicLong pendingWrites = new AtomicLong();
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong versionConflicts = new AtomicLong();
    private final Map<UUID, Long> lastPayment = new ConcurrentHashMap<>();

    public EconomyManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ /pay rules

    public PayRules payRules() {
        var config = plugin.getConfigManager().getConfig();
        long min = Math.max(1L, Money.toCents(config.getDouble("ECONOMY.PAY.MIN-AMOUNT", 0.01D)).orElse(1L));
        long max = Math.max(0L, Money.toCents(config.getDouble("ECONOMY.PAY.MAX-AMOUNT", 0D)).orElse(0L));
        double tax = Math.max(0D, Math.min(50D, config.getDouble("ECONOMY.PAY.TAX-PERCENT", 0D)));
        long cooldown = Math.max(0L, Math.min(3600L, config.getLong("ECONOMY.PAY.COOLDOWN-SECONDS", 0L))) * 1000L;
        return new PayRules(min, max, Double.isFinite(tax) ? tax : 0D, cooldown);
    }

    /** Milliseconds until this player may send another payment; 0 when they may now. */
    public long payCooldownRemaining(UUID player) {
        long cooldown = payRules().cooldownMillis();
        Long last = player == null ? null : lastPayment.get(player);
        if (cooldown <= 0L || last == null) {
            return 0L;
        }
        long remaining = last + cooldown - System.currentTimeMillis();
        if (remaining <= 0L) {
            lastPayment.remove(player, last);
            return 0L;
        }
        return remaining;
    }

    /** Starts the payment cooldown. Kept across relogs; expired entries are dropped as the map grows. */
    public void markPayment(UUID player) {
        long cooldown = payRules().cooldownMillis();
        if (player == null || cooldown <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastPayment.size() > 1000) {
            lastPayment.values().removeIf(last -> last + cooldown <= now);
        }
        lastPayment.put(player, now);
    }

    // ------------------------------------------------------------------ accounts

    public AccountReference resolveAccount(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim();
        Player onlinePlayer = Bukkit.getPlayerExact(normalized);
        if (onlinePlayer != null) {
            return new AccountReference(onlinePlayer.getUniqueId(), onlinePlayer.getName(), onlinePlayer);
        }

        UUID storedUuid = plugin.getDatabaseManager().findPlayerUuidByUsername(normalized);
        if (storedUuid != null) {
            return resolveAccount(storedUuid);
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(normalized);
        if (offlinePlayer.isOnline() || offlinePlayer.hasPlayedBefore()) {
            return new AccountReference(
                    offlinePlayer.getUniqueId(),
                    resolveDisplayName(offlinePlayer.getUniqueId(), offlinePlayer.getName()),
                    offlinePlayer.getPlayer()
            );
        }

        return null;
    }

    public AccountReference resolveAccount(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return new AccountReference(uuid, onlinePlayer.getName(), onlinePlayer);
        }

        if (plugin.getPlayerDataManager().isLoaded(uuid) || plugin.getPlayerDataManager().isSaving(uuid)
                || readStored(uuid) != null) {
            return new AccountReference(uuid, resolveDisplayName(uuid, null), null);
        }

        return null;
    }

    public String getDisplayName(UUID uuid) {
        return resolveDisplayName(uuid, null);
    }

    public boolean hasAccount(OfflinePlayer player) {
        return player != null && hasAccount(player.getUniqueId());
    }

    public boolean hasAccount(UUID uuid) {
        return resolveAccount(uuid) != null;
    }

    /** Creates a row with the starting balance for a player who has none. True when the account exists afterwards. */
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (liveData(uuid) != null || readStored(uuid) != null) {
            return true;
        }
        if (!isStorageReady()) {
            return false;
        }
        plugin.getDatabaseManager().savePlayer(newAccount(uuid, resolveDisplayName(uuid, player.getName())));
        return readStored(uuid) != null;
    }

    // ------------------------------------------------------------------ balances

    public double getBalance(Player player) {
        return player == null ? 0D : getBalance(player.getUniqueId());
    }

    public double getBalance(OfflinePlayer player) {
        return player == null ? 0D : getBalance(player.getUniqueId());
    }

    public double getBalance(UUID uuid) {
        return Money.toUnits(getBalanceCents(uuid));
    }

    /** The balance in whole cents; 0 for unknown players. Reads the database for offline players. */
    public long getBalanceCents(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        PlayerData live = liveData(uuid);
        if (live != null) {
            return Money.storedToCents(live.getMoney());
        }
        EconomyStore.StoredBalance stored = readStored(uuid);
        return stored == null ? 0L : stored.cents();
    }

    public boolean has(Player player, double amount) {
        return player != null && has(player.getUniqueId(), amount);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return player != null && has(player.getUniqueId(), amount);
    }

    public boolean has(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) {
            return false;
        }
        if (amount <= 0D) {
            return true;
        }
        OptionalLong cents = Money.toCents(amount);
        return cents.isPresent() && getBalanceCents(uuid) >= cents.getAsLong();
    }

    /** ECONOMY.MAX-BALANCE in cents: deposits may not take a balance above it. */
    public long maxBalanceCents() {
        double configured = plugin.getConfigManager().getConfig().getDouble("ECONOMY.MAX-BALANCE", DEFAULT_MAX_BALANCE);
        OptionalLong cents = Money.toCents(configured);
        if (cents.isEmpty() || cents.getAsLong() <= 0L) {
            return Money.MAX_EXACT_CENTS;
        }
        return Math.min(cents.getAsLong(), Money.MAX_EXACT_CENTS);
    }

    /** True when money may move: the database started and is still open. */
    public boolean isStorageReady() {
        DatabaseManager database = plugin.getDatabaseManager();
        return database != null && database.isReady();
    }

    // ------------------------------------------------------------------ single changes

    public EconomyTransactionResult deposit(Player player, double amount, EconomyReason reason) {
        return deposit(player, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult deposit(Player player, double amount, EconomyReason reason, EconomyContext context) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(player.getUniqueId(), player.getName(), Op.DEPOSIT, amount, reason, context, true);
    }

    public EconomyTransactionResult deposit(OfflinePlayer player, double amount, EconomyReason reason) {
        return deposit(player, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult deposit(OfflinePlayer player, double amount, EconomyReason reason,
                                            EconomyContext context) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(player.getUniqueId(), player.getName(), Op.DEPOSIT, amount, reason, context, true);
    }

    public EconomyTransactionResult deposit(AccountReference account, double amount, EconomyReason reason) {
        return deposit(account, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult deposit(AccountReference account, double amount, EconomyReason reason,
                                            EconomyContext context) {
        if (account == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(account.uuid(), account.displayName(), Op.DEPOSIT, amount, reason, context, false);
    }

    public EconomyTransactionResult withdraw(Player player, double amount, EconomyReason reason) {
        return withdraw(player, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult withdraw(Player player, double amount, EconomyReason reason, EconomyContext context) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(player.getUniqueId(), player.getName(), Op.WITHDRAW, amount, reason, context, false);
    }

    public EconomyTransactionResult withdraw(OfflinePlayer player, double amount, EconomyReason reason) {
        return withdraw(player, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult withdraw(OfflinePlayer player, double amount, EconomyReason reason,
                                             EconomyContext context) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(player.getUniqueId(), player.getName(), Op.WITHDRAW, amount, reason, context, false);
    }

    public EconomyTransactionResult withdraw(AccountReference account, double amount, EconomyReason reason) {
        return withdraw(account, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult withdraw(AccountReference account, double amount, EconomyReason reason,
                                             EconomyContext context) {
        if (account == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(account.uuid(), account.displayName(), Op.WITHDRAW, amount, reason, context, false);
    }

    public EconomyTransactionResult setBalance(AccountReference account, double amount, EconomyReason reason) {
        return setBalance(account, amount, reason, EconomyContext.NONE);
    }

    public EconomyTransactionResult setBalance(AccountReference account, double amount, EconomyReason reason,
                                               EconomyContext context) {
        if (account == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(account.uuid(), account.displayName(), Op.SET, amount, reason, context, false);
    }

    public EconomyTransactionResult setBalance(Player player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(player.getUniqueId(), player.getName(), Op.SET, amount, reason, EconomyContext.NONE, false);
    }

    public EconomyTransactionResult setBalance(OfflinePlayer player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return change(player.getUniqueId(), player.getName(), Op.SET, amount, reason, EconomyContext.NONE, false);
    }

    // ------------------------------------------------------------------ transfers

    public EconomyTransferResult transfer(Player sender, Player recipient, double amount, EconomyReason reason) {
        if (sender == null || recipient == null) {
            return transferFailure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, sender, recipient, amount);
        }
        return transfer(sender.getUniqueId(), sender.getName(), recipient.getUniqueId(), recipient.getName(),
                amount, reason);
    }

    public EconomyTransferResult transfer(
            UUID senderUuid,
            String senderName,
            UUID recipientUuid,
            String recipientName,
            double amount,
            EconomyReason reason
    ) {
        return transfer(senderUuid, senderName, recipientUuid, recipientName, amount, 0L, reason, EconomyContext.NONE);
    }

    /**
     * Moves {@code amount} from the sender; the recipient receives it minus {@code taxCents}, which leaves the
     * economy. Both balances change in one transaction or not at all.
     */
    public EconomyTransferResult transfer(
            UUID senderUuid,
            String senderName,
            UUID recipientUuid,
            String recipientName,
            double amount,
            long taxCents,
            EconomyReason reason,
            EconomyContext context
    ) {
        OptionalLong parsed = Money.toCents(amount);
        if (parsed.isEmpty() || parsed.getAsLong() <= 0L || taxCents < 0L || taxCents >= parsed.getAsLong()) {
            return transferFailure(EconomyFailureReason.INVALID_AMOUNT, reason, senderUuid, senderName,
                    recipientUuid, recipientName, amount);
        }
        if (senderUuid == null || recipientUuid == null) {
            return transferFailure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, senderUuid, senderName,
                    recipientUuid, recipientName, amount);
        }
        if (senderUuid.equals(recipientUuid)) {
            return transferFailure(EconomyFailureReason.SAME_ACCOUNT, reason, senderUuid, senderName,
                    recipientUuid, recipientName, amount);
        }

        long cents = parsed.getAsLong();
        EconomyContext ctx = context == null ? EconomyContext.NONE : context;
        if (taxCents > 0L) {
            String taxNote = "tax " + Money.toUnits(taxCents);
            ctx = new EconomyContext(ctx.actor(), ctx.note() == null ? taxNote : ctx.note() + "; " + taxNote, ctx.step());
        }
        Outcome outcome = execute(List.of(
                new Leg(senderUuid, senderName, Op.WITHDRAW, cents, false, recipientUuid),
                new Leg(recipientUuid, recipientName, Op.DEPOSIT, cents - taxCents, false, senderUuid)
        ), reason, ctx);

        if (!outcome.success()) {
            EconomyFailureReason failureReason = outcome.failure() == EconomyFailureReason.PLAYER_NOT_FOUND
                    ? EconomyFailureReason.NO_PLAYER_DATA : outcome.failure();
            return transferFailure(failureReason, reason, senderUuid, senderName, recipientUuid, recipientName,
                    Money.toUnits(cents));
        }

        LegResult sender = outcome.legs().get(0);
        LegResult recipient = outcome.legs().get(1);
        String sName = senderName != null ? senderName : sender.name();
        String rName = recipientName != null ? recipientName : recipient.name();
        log(senderUuid, sName, "ECONOMY_TRANSFER", () -> "Paid " + rName + " " + money(cents)
                + (taxCents > 0L ? " (tax " + money(taxCents) + ")" : ""));
        log(recipientUuid, rName, "ECONOMY_TRANSFER", () -> "Received " + money(cents - taxCents) + " from " + sName);

        return new EconomyTransferResult(
                true,
                EconomyFailureReason.NONE,
                reason,
                sender.uuid(),
                sender.name(),
                recipient.uuid(),
                recipient.name(),
                Money.toUnits(cents),
                Money.toUnits(sender.beforeCents()),
                Money.toUnits(sender.afterCents()),
                Money.toUnits(recipient.beforeCents()),
                Money.toUnits(recipient.afterCents())
        );
    }

    // ------------------------------------------------------------------ history and statistics

    /** Reads one page of a player's ledger on the database thread and answers on the main thread. */
    public void history(UUID player, int page, int pageSize, Consumer<HistoryPage> callback) {
        int size = Math.max(1, Math.min(45, pageSize));
        int safePage = Math.max(1, page);
        if (player == null || !isStorageReady()) {
            callback.accept(new HistoryPage(player, safePage, size, 0, List.of()));
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            HistoryPage result;
            try {
                Connection connection = connection();
                int total = EconomyStore.countHistory(connection, player);
                List<LedgerEntry> entries = EconomyStore.history(connection, player, size, (safePage - 1) * size);
                result = new HistoryPage(player, safePage, size, total, entries);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read the economy ledger of " + player, exception);
                result = new HistoryPage(player, safePage, size, 0, List.of());
            }
            HistoryPage answer = result;
            plugin.getSpigotScheduler().runGlobal(() -> callback.accept(answer));
        });
    }

    /** Totals per reason since {@code sinceMillis} and the money supply, answered on the main thread. */
    public void stats(long sinceMillis, Consumer<EconomyStats> callback) {
        if (!isStorageReady()) {
            callback.accept(new EconomyStats(sinceMillis, List.of(), new EconomyStore.MoneySupply(0L, 0D)));
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            EconomyStats result;
            try {
                Connection connection = connection();
                result = new EconomyStats(sinceMillis, EconomyStore.totalsSince(connection, sinceMillis),
                        EconomyStore.moneySupply(connection));
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read economy statistics", exception);
                result = new EconomyStats(sinceMillis, List.of(), new EconomyStore.MoneySupply(0L, 0D));
            }
            EconomyStats answer = result;
            plugin.getSpigotScheduler().runGlobal(() -> callback.accept(answer));
        });
    }

    /** Deletes ledger rows older than ECONOMY.LEDGER.RETENTION-DAYS (0 keeps everything). Runs in the background. */
    public void pruneLedger() {
        int days = plugin.getConfigManager().getConfig().getInt("ECONOMY.LEDGER.RETENTION-DAYS", 0);
        if (days <= 0 || !isStorageReady()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                int removed = EconomyStore.pruneLedger(connection(), cutoff);
                if (removed > 0) {
                    plugin.getLogger().info("Removed " + removed + " economy ledger rows older than " + days + " days.");
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not prune the economy ledger", exception);
            }
        });
    }

    /** Balance writes queued on the database thread right now. */
    public long pendingWrites() {
        return pendingWrites.get();
    }

    /** Balance writes that failed after retries since start-up (the balances stay in memory and save on quit). */
    public long failedWrites() {
        return failedWrites.get();
    }

    /** Times a stored balance was found newer than the live one (another server or tool wrote it). */
    public long versionConflicts() {
        return versionConflicts.get();
    }

    /** The newest balance version an offline change wrote for this player since start-up, or 0. */
    public long offlineVersion(UUID uuid) {
        Long version = uuid == null ? null : offlineVersions.get(uuid);
        return version == null ? 0L : version;
    }

    /** Called once a joining player's row has been read after their last offline change. */
    public void forgetOfflineVersion(UUID uuid) {
        if (uuid != null) {
            offlineVersions.remove(uuid);
        }
    }

    // ------------------------------------------------------------------ engine

    private EconomyTransactionResult change(UUID uuid, String nameHint, Op op, double amount, EconomyReason reason,
                                            EconomyContext context, boolean createIfMissing) {
        OptionalLong parsed = Money.toCents(amount);
        boolean valid = parsed.isPresent() && (op == Op.SET ? parsed.getAsLong() >= 0L : parsed.getAsLong() > 0L);
        if (!valid) {
            return failure(EconomyFailureReason.INVALID_AMOUNT, reason, uuid, nameHint, amount, 0D, 0D);
        }
        if (uuid == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, nameHint, amount, 0D, 0D);
        }

        long cents = parsed.getAsLong();
        Outcome outcome = execute(List.of(new Leg(uuid, nameHint, op, cents, createIfMissing, null)), reason,
                context == null ? EconomyContext.NONE : context);
        if (!outcome.success()) {
            double before = Money.toUnits(outcome.failedBefore());
            return failure(outcome.failure(), reason, uuid, nameHint, Money.toUnits(cents), before, before);
        }

        LegResult leg = outcome.legs().get(0);
        logAdminChange(reason, leg, cents);
        return new EconomyTransactionResult(
                true,
                EconomyFailureReason.NONE,
                reason,
                leg.uuid(),
                leg.name(),
                Money.toUnits(op == Op.SET ? leg.afterCents() : cents),
                Money.toUnits(leg.beforeCents()),
                Money.toUnits(leg.afterCents())
        );
    }

    private Outcome execute(List<Leg> legs, EconomyReason reason, EconomyContext context) {
        if (!isStorageReady()) {
            return Outcome.fail(EconomyFailureReason.STORAGE_UNAVAILABLE, 0, 0L);
        }
        long max = maxBalanceCents();
        List<PlayerData> live = new ArrayList<>(legs.size());
        boolean allLive = true;
        for (Leg leg : legs) {
            PlayerData data = liveData(leg.uuid());
            live.add(data);
            allLive &= data != null;
        }
        Outcome outcome = allLive
                ? executeLive(legs, live, reason, context, max)
                : executeWithDatabase(legs, live, reason, context, max);
        if (outcome.success()) {
            invalidateLeaderboards(outcome.legs());
        }
        return outcome;
    }

    /** Every account is in memory: change them under their locks, then queue one write with the ledger rows. */
    private Outcome executeLive(List<Leg> legs, List<PlayerData> live, EconomyReason reason, EconomyContext context,
                                long max) {
        List<Integer> order = lockOrder(legs);
        return lockAll(order, live, 0, () -> {
            List<LiveLeg> planned = new ArrayList<>(legs.size());
            for (int i = 0; i < legs.size(); i++) {
                PlayerData data = live.get(i);
                long before = Money.storedToCents(data.getMoney());
                Plan plan = plan(legs.get(i).op(), before, legs.get(i).cents(), max);
                if (!plan.ok()) {
                    return Outcome.fail(plan.failure(), i, before);
                }
                planned.add(new LiveLeg(i, data, before, plan.after()));
            }

            List<LegResult> results = new ArrayList<>(legs.size());
            List<PlayerData.MoneyState> states = new ArrayList<>(legs.size());
            for (LiveLeg leg : planned) {
                leg.data().setMoney(Money.toUnits(leg.afterCents()));
                PlayerData.MoneyState state = leg.data().moneyState();
                states.add(state);
                Leg source = legs.get(leg.index());
                results.add(new LegResult(source.uuid(), resolveDisplayName(source.uuid(), source.nameHint()),
                        leg.beforeCents(), leg.afterCents(), state.version(), true));
            }

            List<LedgerEntry> entries = ledgerEntries(legs, results, reason, context);
            List<PlayerData> datas = planned.stream().map(LiveLeg::data).toList();
            writeLiveInBackground(legs, datas, states, entries, context.step());
            return Outcome.ok(results);
        });
    }

    /**
     * At least one account is offline: one database transaction changes the offline rows (compare-and-set on
     * their version), writes the new balances of the in-memory accounts and the ledger rows. If it fails, the
     * in-memory balances are put back.
     */
    private Outcome executeWithDatabase(List<Leg> legs, List<PlayerData> live, EconomyReason reason,
                                        EconomyContext context, long max) {
        for (int attempt = 1; attempt <= OFFLINE_ATTEMPTS; attempt++) {
            List<LiveLeg> applied = new ArrayList<>();
            try {
                Outcome outcome = EconomyStore.inTransaction(connection(), connection -> {
                    // 1. Read and plan every leg without changing anything.
                    long[] before = new long[legs.size()];
                    long[] after = new long[legs.size()];
                    long[] versions = new long[legs.size()];
                    for (int i = 0; i < legs.size(); i++) {
                        Leg leg = legs.get(i);
                        PlayerData data = live.get(i);
                        if (data != null) {
                            before[i] = Money.storedToCents(data.getMoney());
                        } else {
                            EconomyStore.StoredBalance stored = EconomyStore.readBalance(connection, leg.uuid());
                            if (stored == null && leg.createIfMissing()) {
                                createAccountRow(connection, leg.uuid(), leg.nameHint());
                                stored = EconomyStore.readBalance(connection, leg.uuid());
                            }
                            if (stored == null) {
                                return Outcome.fail(EconomyFailureReason.NO_PLAYER_DATA, i, 0L);
                            }
                            before[i] = stored.cents();
                            versions[i] = stored.version();
                        }
                        Plan plan = plan(leg.op(), before[i], leg.cents(), max);
                        if (!plan.ok()) {
                            return Outcome.fail(plan.failure(), i, before[i]);
                        }
                        after[i] = plan.after();
                    }

                    // 2. Offline rows: compare-and-set; a row changed meanwhile restarts the transaction.
                    for (int i = 0; i < legs.size(); i++) {
                        if (live.get(i) == null) {
                            if (!EconomyStore.compareAndSet(connection, legs.get(i).uuid(), versions[i], after[i])) {
                                throw new ConcurrentChange();
                            }
                            versions[i] = versions[i] + 1L;
                        }
                    }

                    // 3. In-memory accounts: change them and write their new balance in this transaction.
                    for (int i = 0; i < legs.size(); i++) {
                        PlayerData data = live.get(i);
                        if (data == null) {
                            continue;
                        }
                        synchronized (data) {
                            long current = Money.storedToCents(data.getMoney());
                            Plan plan = plan(legs.get(i).op(), current, legs.get(i).cents(), max);
                            if (!plan.ok()) {
                                throw new IllegalStateException("balance changed during the transaction");
                            }
                            data.setMoney(Money.toUnits(plan.after()));
                            applied.add(new LiveLeg(i, data, current, plan.after()));
                            before[i] = current;
                            after[i] = plan.after();
                            PlayerData.MoneyState state = data.moneyState();
                            versions[i] = state.version();
                            writeLiveState(connection, legs.get(i).uuid(), data, state);
                        }
                    }

                    List<LegResult> results = new ArrayList<>(legs.size());
                    for (int i = 0; i < legs.size(); i++) {
                        Leg leg = legs.get(i);
                        results.add(new LegResult(leg.uuid(), resolveDisplayName(leg.uuid(), leg.nameHint()),
                                before[i], after[i], versions[i], live.get(i) != null));
                    }
                    for (LedgerEntry entry : ledgerEntries(legs, results, reason, context)) {
                        EconomyStore.insertLedger(connection, entry);
                    }
                    if (context.step() != null) {
                        context.step().run(connection);
                    }
                    return Outcome.ok(results);
                });
                if (outcome.success()) {
                    for (LegResult leg : outcome.legs()) {
                        if (!leg.online()) {
                            offlineVersions.merge(leg.uuid(), leg.version(), Math::max);
                        }
                    }
                }
                return outcome;
            } catch (ConcurrentChange retry) {
                revert(applied);
            } catch (SQLException exception) {
                revert(applied);
                if (EconomyStore.isRetryable(exception) && attempt < OFFLINE_ATTEMPTS) {
                    continue;
                }
                plugin.getLogger().log(Level.SEVERE, "An economy transaction (" + reason + ") failed and was rolled back;"
                        + " no balance changed.", exception);
                return Outcome.fail(EconomyFailureReason.STORAGE_ERROR, 0, 0L);
            } catch (RuntimeException exception) {
                revert(applied);
                plugin.getLogger().log(Level.SEVERE, "An economy transaction (" + reason + ") failed and was rolled back;"
                        + " no balance changed.", exception);
                return Outcome.fail(EconomyFailureReason.STORAGE_ERROR, 0, 0L);
            }
        }
        plugin.getLogger().warning("An economy transaction (" + reason + ") kept colliding with other changes and was"
                + " given up after " + OFFLINE_ATTEMPTS + " attempts; no balance changed.");
        return Outcome.fail(EconomyFailureReason.STORAGE_ERROR, 0, 0L);
    }

    private void revert(List<LiveLeg> applied) {
        for (LiveLeg leg : applied) {
            leg.data().setMoney(Money.toUnits(leg.beforeCents()));
        }
    }

    private static Plan plan(Op op, long before, long cents, long max) {
        return switch (op) {
            case DEPOSIT -> {
                OptionalLong sum = Money.add(before, cents);
                yield sum.isEmpty() || sum.getAsLong() > max
                        ? new Plan(before, EconomyFailureReason.BALANCE_LIMIT)
                        : new Plan(sum.getAsLong(), EconomyFailureReason.NONE);
            }
            case WITHDRAW -> before < cents
                    ? new Plan(before, EconomyFailureReason.INSUFFICIENT_FUNDS)
                    : new Plan(before - cents, EconomyFailureReason.NONE);
            case SET -> cents > max
                    ? new Plan(before, EconomyFailureReason.BALANCE_LIMIT)
                    : new Plan(cents, EconomyFailureReason.NONE);
        };
    }

    /** Leg indexes in a fixed order (by player id), so two transactions never lock the same players in reverse. */
    private static List<Integer> lockOrder(List<Leg> legs) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(i -> legs.get(i).uuid()));
        return order;
    }

    private interface Locked {
        Outcome run();
    }

    private static Outcome lockAll(List<Integer> order, List<PlayerData> live, int position, Locked body) {
        if (position == order.size()) {
            return body.run();
        }
        synchronized (live.get(order.get(position))) {
            return lockAll(order, live, position + 1, body);
        }
    }

    private List<LedgerEntry> ledgerEntries(List<Leg> legs, List<LegResult> results, EconomyReason reason,
                                            EconomyContext context) {
        long now = System.currentTimeMillis();
        String group = UUID.randomUUID().toString();
        List<LedgerEntry> entries = new ArrayList<>(legs.size());
        for (int i = 0; i < legs.size(); i++) {
            LegResult result = results.get(i);
            entries.add(new LedgerEntry(
                    legs.size() == 1 ? group : UUID.randomUUID().toString(),
                    group,
                    now,
                    result.uuid(),
                    legs.get(i).counterparty(),
                    reason == null ? "UNKNOWN" : reason.name(),
                    result.afterCents() - result.beforeCents(),
                    result.afterCents(),
                    result.version(),
                    context.actor(),
                    context.note()));
        }
        return entries;
    }

    /** Queues the new balances and their ledger rows on the database writer, in one transaction. */
    private void writeLiveInBackground(List<Leg> legs, List<PlayerData> datas, List<PlayerData.MoneyState> states,
                                       List<LedgerEntry> entries, EconomyContext.SqlStep step) {
        pendingWrites.incrementAndGet();
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                for (int attempt = 1; ; attempt++) {
                    try {
                        EconomyStore.inTransaction(connection(), connection -> {
                            for (int i = 0; i < datas.size(); i++) {
                                writeLiveState(connection, legs.get(i).uuid(), datas.get(i), states.get(i));
                            }
                            for (LedgerEntry entry : entries) {
                                EconomyStore.insertLedger(connection, entry);
                            }
                            if (step != null) {
                                step.run(connection);
                            }
                            return null;
                        });
                        return;
                    } catch (SQLException exception) {
                        if (attempt >= WRITE_ATTEMPTS) {
                            failedWrites.incrementAndGet();
                            plugin.getLogger().log(Level.SEVERE, "Could not save a balance change after " + attempt
                                    + " attempts. The balance is kept in memory and saved with the player; the ledger"
                                    + " row " + entries.get(0).groupId() + " is missing.", exception);
                            return;
                        }
                        try {
                            // Database thread: a short pause lets another connection finish its write.
                            Thread.sleep(25L * attempt);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } finally {
                pendingWrites.decrementAndGet();
            }
        });
    }

    /**
     * Writes an in-memory balance. If the stored balance turned out to be newer (written by another server or a
     * tool), the in-memory balance, which is what the player sees, is kept and written with a version above it.
     */
    private void writeLiveState(Connection connection, UUID uuid, PlayerData data, PlayerData.MoneyState state)
            throws SQLException {
        EconomyStore.BalanceSnapshot snapshot = snapshot(uuid, state);
        if (EconomyStore.writeSnapshot(connection, snapshot)) {
            return;
        }
        EconomyStore.StoredBalance stored = EconomyStore.readBalance(connection, uuid);
        if (stored == null || stored.version() <= snapshot.version()) {
            // No row yet (the first save is still queued and writes this state), or a save already wrote it.
            return;
        }
        versionConflicts.incrementAndGet();
        PlayerData.MoneyState rebased = data.ensureMoneyVersionAbove(stored.version());
        EconomyStore.writeSnapshot(connection, snapshot(uuid, rebased));
        plugin.getLogger().warning("The stored balance of " + uuid + " was changed outside this server (version "
                + stored.version() + " > " + snapshot.version() + "). The live balance was kept.");
    }

    private static EconomyStore.BalanceSnapshot snapshot(UUID uuid, PlayerData.MoneyState state) {
        return new EconomyStore.BalanceSnapshot(uuid, Money.storedToCents(state.money()), state.moneySpent(),
                state.moneyMade(), state.version());
    }

    private void createAccountRow(Connection connection, UUID uuid, String nameHint) throws SQLException {
        PlayerData created = newAccount(uuid, resolveDisplayName(uuid, nameHint));
        plugin.getDatabaseManager().savePlayer(created);
        if (EconomyStore.readBalance(connection, uuid) == null) {
            throw new SQLException("Could not create an economy account for " + uuid);
        }
    }

    private PlayerData newAccount(UUID uuid, String username) {
        PlayerData created = new PlayerData(uuid, username);
        PlayerSettingDefaults.applyDefaults(plugin, created);
        double startMoney = plugin.getConfigManager().getConfig().getDouble("SETTINGS.MONEY-PER-DEFAULT", 1000.0D);
        long startCents = Money.toCents(startMoney).orElse(0L);
        created.setMoney(Money.toUnits(Math.max(0L, Math.min(startCents, maxBalanceCents()))));
        return created;
    }

    /** The in-memory copy of a player's data, or null when they are offline and fully saved. */
    private PlayerData liveData(UUID uuid) {
        PlayerDataManager players = plugin.getPlayerDataManager();
        PlayerData data = players.get(uuid);
        if (data != null) {
            return data;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return players.loadOrCreate(online);
        }
        return players.getSaving(uuid);
    }

    private EconomyStore.StoredBalance readStored(UUID uuid) {
        if (uuid == null || !isStorageReady()) {
            return null;
        }
        try {
            return EconomyStore.readBalance(connection(), uuid);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read the balance of " + uuid, exception);
            return null;
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = plugin.getDatabaseManager().getConnection();
        if (connection == null) {
            throw new SQLException("The database is not connected");
        }
        return connection;
    }

    private void invalidateLeaderboards(List<LegResult> legs) {
        LeaderboardManager leaderboards = plugin.getLeaderboardManager();
        if (leaderboards == null) {
            return;
        }
        boolean anyOffline = false;
        for (LegResult leg : legs) {
            anyOffline |= !leg.online();
        }
        leaderboards.invalidateLive(LeaderboardManager.LeaderboardType.MONEY);
        if (anyOffline) {
            leaderboards.invalidate(LeaderboardManager.LeaderboardType.MONEY);
        }
    }

    private void logAdminChange(EconomyReason reason, LegResult leg, long cents) {
        if (reason == null) {
            return;
        }
        switch (reason) {
            case ADMIN_ADD -> log(leg.uuid(), leg.name(), "ECONOMY_ADD",
                    () -> "Admin added " + money(cents) + " to balance (New Balance: " + money(leg.afterCents()) + ")");
            case ADMIN_REMOVE -> log(leg.uuid(), leg.name(), "ECONOMY_REMOVE",
                    () -> "Admin removed " + money(cents) + " from balance (New Balance: " + money(leg.afterCents()) + ")");
            case ADMIN_SET -> log(leg.uuid(), leg.name(), "ECONOMY_SET",
                    () -> "Admin set balance to " + money(leg.afterCents()) + " (Previous Balance: "
                            + money(leg.beforeCents()) + ")");
            case ADMIN_RESET -> log(leg.uuid(), leg.name(), "ECONOMY_SET",
                    () -> "Admin reset balance to " + money(leg.afterCents()) + " (Previous Balance: "
                            + money(leg.beforeCents()) + ")");
            default -> {
            }
        }
    }

    private String money(long cents) {
        return plugin.getCurrencyManager().formatMoney(Money.toUnits(cents));
    }

    /** Writes a /logs line. The text is built inside the guard: a log line must never undo or block a money movement. */
    private void log(UUID uuid, String name, String type, Supplier<String> detail) {
        try {
            plugin.getPlayerLogsManager().log(uuid, name, "Economy", type, detail.get());
        } catch (RuntimeException ignored) {
            // Logging is best effort.
        }
    }

    private String resolveDisplayName(UUID uuid, String fallbackName) {
        if (uuid == null) {
            return fallbackName != null && !fallbackName.isBlank() ? fallbackName : "unknown";
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName;
        }

        String storedName = plugin.getDatabaseManager().getLastKnownUsername(uuid);
        if (storedName != null && !storedName.isBlank()) {
            return storedName;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()) {
            return offlinePlayer.getName();
        }

        return uuid.toString();
    }

    private EconomyTransactionResult failure(
            EconomyFailureReason failureReason,
            EconomyReason reason,
            UUID uuid,
            String displayName,
            double amount,
            double beforeBalance,
            double afterBalance
    ) {
        return new EconomyTransactionResult(
                false,
                failureReason,
                reason,
                uuid,
                displayName != null && !displayName.isBlank()
                        ? displayName
                        : (uuid != null ? resolveDisplayName(uuid, null) : "ᴜɴᴋɴᴏᴡɴ"),
                safeUnits(amount),
                safeUnits(beforeBalance),
                safeUnits(afterBalance)
        );
    }

    private static double safeUnits(double amount) {
        return Money.toUnits(Money.toCents(Math.max(0D, Double.isFinite(amount) ? amount : 0D)).orElse(0L));
    }

    private EconomyTransferResult transferFailure(
            EconomyFailureReason failureReason,
            EconomyReason reason,
            Player sender,
            Player recipient,
            double amount
    ) {
        return transferFailure(
                failureReason,
                reason,
                sender != null ? sender.getUniqueId() : null,
                sender != null ? sender.getName() : null,
                recipient != null ? recipient.getUniqueId() : null,
                recipient != null ? recipient.getName() : null,
                amount
        );
    }

    private EconomyTransferResult transferFailure(
            EconomyFailureReason failureReason,
            EconomyReason reason,
            UUID senderUuid,
            String senderName,
            UUID recipientUuid,
            String recipientName,
            double amount
    ) {
        return new EconomyTransferResult(
                false,
                failureReason,
                reason,
                senderUuid,
                senderName != null && !senderName.isBlank()
                        ? senderName
                        : (senderUuid != null ? resolveDisplayName(senderUuid, null) : "ᴜɴᴋɴᴏᴡɴ"),
                recipientUuid,
                recipientName != null && !recipientName.isBlank()
                        ? recipientName
                        : (recipientUuid != null ? resolveDisplayName(recipientUuid, null) : "ᴜɴᴋɴᴏᴡɴ"),
                safeUnits(amount),
                0D,
                0D,
                0D,
                0D
        );
    }
}
