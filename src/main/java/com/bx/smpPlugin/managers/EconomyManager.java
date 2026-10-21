package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.economy.EconomyLedger;
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

import java.util.UUID;

public class EconomyManager {

    public record AccountReference(UUID uuid, String displayName, Player onlinePlayer) {
        public boolean isOnline() {
            return onlinePlayer != null;
        }
    }

    private record LoadedAccount(AccountReference reference, PlayerData data) {
    }

    private final SmpPlugin plugin;

    public EconomyManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public AccountReference resolveAccount(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim();
        Player onlinePlayer = Bukkit.getPlayerExact(normalized);
        if (onlinePlayer != null) {
            return new AccountReference(
                    onlinePlayer.getUniqueId(),
                    onlinePlayer.getName(),
                    onlinePlayer
            );
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

        if (plugin.getPlayerDataManager().isLoaded(uuid) || plugin.getDatabaseManager().loadPlayer(uuid) != null) {
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

    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) {
            return false;
        }

        LoadedAccount existing = loadAccount(player.getUniqueId(), player.getName(), false);
        if (existing != null) {
            return true;
        }

        return loadAccount(player.getUniqueId(), player.getName(), true) != null;
    }

    public double getBalance(Player player) {
        return player == null ? 0D : getBalance(player.getUniqueId());
    }

    public double getBalance(OfflinePlayer player) {
        return player == null ? 0D : getBalance(player.getUniqueId());
    }

    public double getBalance(UUID uuid) {
        LoadedAccount loadedAccount = loadAccount(uuid, null, false);
        return loadedAccount == null ? 0D : Money.toDouble(Money.balanceCents(loadedAccount.data().getMoney()));
    }

    public boolean has(Player player, double amount) {
        return player != null && has(player.getUniqueId(), amount);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return player != null && has(player.getUniqueId(), amount);
    }

    public boolean has(UUID uuid, double amount) {
        java.util.OptionalLong cents = Money.toCents(amount);
        if (cents.isEmpty()) {
            return false;
        }
        if (cents.getAsLong() == 0L) {
            return true;
        }
        LoadedAccount loadedAccount = loadAccount(uuid, null, false);
        return loadedAccount != null && Money.balanceCents(loadedAccount.data().getMoney()) >= cents.getAsLong();
    }

    public EconomyTransactionResult deposit(Player player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return depositInternal(player.getUniqueId(), player.getName(), amount, reason, true);
    }

    public EconomyTransactionResult deposit(OfflinePlayer player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return depositInternal(player.getUniqueId(), player.getName(), amount, reason, true);
    }

    public EconomyTransactionResult deposit(AccountReference account, double amount, EconomyReason reason) {
        if (account == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return depositInternal(account.uuid(), account.displayName(), amount, reason, false);
    }

    public EconomyTransactionResult withdraw(Player player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return withdrawInternal(player.getUniqueId(), player.getName(), amount, reason);
    }

    public EconomyTransactionResult withdraw(OfflinePlayer player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return withdrawInternal(player.getUniqueId(), player.getName(), amount, reason);
    }

    public EconomyTransactionResult withdraw(AccountReference account, double amount, EconomyReason reason) {
        if (account == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return withdrawInternal(account.uuid(), account.displayName(), amount, reason);
    }

    public EconomyTransactionResult setBalance(AccountReference account, double amount, EconomyReason reason) {
        if (account == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return setBalanceInternal(account.uuid(), account.displayName(), amount, reason);
    }

    public EconomyTransactionResult setBalance(Player player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return setBalanceInternal(player.getUniqueId(), player.getName(), amount, reason);
    }

    public EconomyTransactionResult setBalance(OfflinePlayer player, double amount, EconomyReason reason) {
        if (player == null) {
            return failure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, null, null, amount, 0D, 0D);
        }
        return setBalanceInternal(player.getUniqueId(), player.getName(), amount, reason);
    }

    public EconomyTransferResult transfer(Player sender, Player recipient, double amount, EconomyReason reason) {
        if (sender == null || recipient == null) {
            return transferFailure(EconomyFailureReason.PLAYER_NOT_FOUND, reason, sender, recipient, amount);
        }

        return transfer(
                sender.getUniqueId(),
                sender.getName(),
                recipient.getUniqueId(),
                recipient.getName(),
                amount,
                reason
        );
    }

    public EconomyTransferResult transfer(
            UUID senderUuid,
            String senderName,
            UUID recipientUuid,
            String recipientName,
            double amount,
            EconomyReason reason
    ) {
        java.util.OptionalLong parsedAmount = positiveCents(amount);
        if (parsedAmount.isEmpty()) {
            return transferFailure(EconomyFailureReason.INVALID_AMOUNT, reason,
                    senderUuid, senderName, recipientUuid, recipientName, amount);
        }
        if (senderUuid == null || recipientUuid == null) {
            return transferFailure(EconomyFailureReason.PLAYER_NOT_FOUND, reason,
                    senderUuid, senderName, recipientUuid, recipientName, amount);
        }
        if (senderUuid.equals(recipientUuid)) {
            return transferFailure(EconomyFailureReason.SAME_ACCOUNT, reason,
                    senderUuid, senderName, recipientUuid, recipientName, amount);
        }

        LoadedAccount sender = loadAccount(senderUuid, senderName, false);
        LoadedAccount recipient = loadAccount(recipientUuid, recipientName, false);
        if (sender == null || recipient == null) {
            return transferFailure(EconomyFailureReason.NO_PLAYER_DATA, reason,
                    senderUuid, senderName, recipientUuid, recipientName, amount);
        }

        long amountCents = parsedAmount.getAsLong();
        long senderBefore = Money.balanceCents(sender.data().getMoney());
        long recipientBefore = Money.balanceCents(recipient.data().getMoney());
        java.util.OptionalLong senderAfter = Money.subtract(senderBefore, amountCents);
        if (senderAfter.isEmpty()) {
            return transferFailure(EconomyFailureReason.INSUFFICIENT_FUNDS, reason,
                    sender.reference().uuid(), sender.reference().displayName(),
                    recipient.reference().uuid(), recipient.reference().displayName(), Money.toDouble(amountCents));
        }
        java.util.OptionalLong recipientAfter = Money.add(recipientBefore, amountCents, maxBalanceCents());
        if (recipientAfter.isEmpty()) {
            return transferFailure(EconomyFailureReason.BALANCE_LIMIT, reason,
                    sender.reference().uuid(), sender.reference().displayName(),
                    recipient.reference().uuid(), recipient.reference().displayName(), Money.toDouble(amountCents));
        }

        // Both balances change together, in the same tick, before anything else can read them.
        sender.data().setMoney(Money.toDouble(senderAfter.getAsLong()));
        recipient.data().setMoney(Money.toDouble(recipientAfter.getAsLong()));
        saveLoadedAccount(sender);
        saveLoadedAccount(recipient);

        String sName = senderName != null ? senderName : sender.reference().displayName();
        String rName = recipientName != null ? recipientName : recipient.reference().displayName();
        String transactionId = EconomyLedger.newTransactionId();
        EconomyLedger ledger = EconomyLedger.active();
        if (ledger != null) {
            ledger.record(transactionId, EconomyLedger.Currency.MONEY, EconomyLedger.Kind.TRANSFER_OUT, reason,
                    senderUuid, sName, recipientUuid, rName, amountCents, senderBefore, senderAfter.getAsLong());
            ledger.record(transactionId, EconomyLedger.Currency.MONEY, EconomyLedger.Kind.TRANSFER_IN, reason,
                    recipientUuid, rName, senderUuid, sName, amountCents, recipientBefore, recipientAfter.getAsLong());
        }
        double normalizedAmount = Money.toDouble(amountCents);
        plugin.getPlayerLogsManager().log(
                senderUuid,
                sName,
                "Economy",
                "ECONOMY_TRANSFER",
                "Paid " + rName + " " + plugin.getCurrencyManager().formatMoney(normalizedAmount)
        );
        plugin.getPlayerLogsManager().log(
                recipientUuid,
                rName,
                "Economy",
                "ECONOMY_TRANSFER",
                "Received " + plugin.getCurrencyManager().formatMoney(normalizedAmount) + " from " + sName
        );

        return new EconomyTransferResult(
                true,
                EconomyFailureReason.NONE,
                reason,
                sender.reference().uuid(),
                sender.reference().displayName(),
                recipient.reference().uuid(),
                recipient.reference().displayName(),
                normalizedAmount,
                Money.toDouble(senderBefore),
                Money.toDouble(senderAfter.getAsLong()),
                Money.toDouble(recipientBefore),
                Money.toDouble(recipientAfter.getAsLong())
        );
    }

    private EconomyTransactionResult depositInternal(
            UUID uuid,
            String displayNameHint,
            double amount,
            EconomyReason reason,
            boolean createIfMissing
    ) {
        java.util.OptionalLong parsedAmount = positiveCents(amount);
        if (parsedAmount.isEmpty()) {
            return failure(EconomyFailureReason.INVALID_AMOUNT, reason, uuid, displayNameHint, amount, 0D, 0D);
        }

        LoadedAccount loadedAccount = loadAccount(uuid, displayNameHint, createIfMissing);
        if (loadedAccount == null) {
            return failure(EconomyFailureReason.NO_PLAYER_DATA, reason, uuid, displayNameHint, amount, 0D, 0D);
        }

        long amountCents = parsedAmount.getAsLong();
        long beforeCents = Money.balanceCents(loadedAccount.data().getMoney());
        java.util.OptionalLong afterCents = Money.add(beforeCents, amountCents, maxBalanceCents());
        if (afterCents.isEmpty()) {
            return failure(EconomyFailureReason.BALANCE_LIMIT, reason, loadedAccount.reference().uuid(),
                    loadedAccount.reference().displayName(), Money.toDouble(amountCents),
                    Money.toDouble(beforeCents), Money.toDouble(beforeCents));
        }
        loadedAccount.data().setMoney(Money.toDouble(afterCents.getAsLong()));
        saveLoadedAccount(loadedAccount);
        recordLedger(EconomyLedger.Kind.DEPOSIT, reason, loadedAccount, amountCents, beforeCents, afterCents.getAsLong());

        double normalizedAmount = Money.toDouble(amountCents);
        if (reason == EconomyReason.ADMIN_ADD) {
            plugin.getPlayerLogsManager().log(
                    uuid,
                    loadedAccount.reference().displayName(),
                    "Economy",
                    "ECONOMY_ADD",
                    "Admin added " + plugin.getCurrencyManager().formatMoney(normalizedAmount) + " to balance (New Balance: "
                            + plugin.getCurrencyManager().formatMoney(Money.toDouble(afterCents.getAsLong())) + ")"
            );
        }

        return new EconomyTransactionResult(
                true,
                EconomyFailureReason.NONE,
                reason,
                loadedAccount.reference().uuid(),
                loadedAccount.reference().displayName(),
                normalizedAmount,
                Money.toDouble(beforeCents),
                Money.toDouble(afterCents.getAsLong())
        );
    }

    private EconomyTransactionResult withdrawInternal(
            UUID uuid,
            String displayNameHint,
            double amount,
            EconomyReason reason
    ) {
        java.util.OptionalLong parsedAmount = positiveCents(amount);
        if (parsedAmount.isEmpty()) {
            return failure(EconomyFailureReason.INVALID_AMOUNT, reason, uuid, displayNameHint, amount, 0D, 0D);
        }

        LoadedAccount loadedAccount = loadAccount(uuid, displayNameHint, false);
        if (loadedAccount == null) {
            return failure(EconomyFailureReason.NO_PLAYER_DATA, reason, uuid, displayNameHint, amount, 0D, 0D);
        }

        long amountCents = parsedAmount.getAsLong();
        long beforeCents = Money.balanceCents(loadedAccount.data().getMoney());
        java.util.OptionalLong afterCents = Money.subtract(beforeCents, amountCents);
        if (afterCents.isEmpty()) {
            return failure(
                    EconomyFailureReason.INSUFFICIENT_FUNDS,
                    reason,
                    loadedAccount.reference().uuid(),
                    loadedAccount.reference().displayName(),
                    Money.toDouble(amountCents),
                    Money.toDouble(beforeCents),
                    Money.toDouble(beforeCents)
            );
        }

        loadedAccount.data().setMoney(Money.toDouble(afterCents.getAsLong()));
        saveLoadedAccount(loadedAccount);
        recordLedger(EconomyLedger.Kind.WITHDRAW, reason, loadedAccount, amountCents, beforeCents, afterCents.getAsLong());

        double normalizedAmount = Money.toDouble(amountCents);
        if (reason == EconomyReason.ADMIN_REMOVE) {
            plugin.getPlayerLogsManager().log(
                    uuid,
                    loadedAccount.reference().displayName(),
                    "Economy",
                    "ECONOMY_REMOVE",
                    "Admin removed " + plugin.getCurrencyManager().formatMoney(normalizedAmount) + " from balance (New Balance: "
                            + plugin.getCurrencyManager().formatMoney(Money.toDouble(afterCents.getAsLong())) + ")"
            );
        }

        return new EconomyTransactionResult(
                true,
                EconomyFailureReason.NONE,
                reason,
                loadedAccount.reference().uuid(),
                loadedAccount.reference().displayName(),
                normalizedAmount,
                Money.toDouble(beforeCents),
                Money.toDouble(afterCents.getAsLong())
        );
    }

    private EconomyTransactionResult setBalanceInternal(
            UUID uuid,
            String displayNameHint,
            double amount,
            EconomyReason reason
    ) {
        java.util.OptionalLong parsedAmount = Money.toCents(amount);
        if (parsedAmount.isEmpty()) {
            return failure(EconomyFailureReason.INVALID_AMOUNT, reason, uuid, displayNameHint, amount, 0D, 0D);
        }
        if (parsedAmount.getAsLong() > Money.effectiveLimit(maxBalanceCents())) {
            return failure(EconomyFailureReason.BALANCE_LIMIT, reason, uuid, displayNameHint, amount, 0D, 0D);
        }

        LoadedAccount loadedAccount = loadAccount(uuid, displayNameHint, false);
        if (loadedAccount == null) {
            return failure(EconomyFailureReason.NO_PLAYER_DATA, reason, uuid, displayNameHint, amount, 0D, 0D);
        }

        long newCents = parsedAmount.getAsLong();
        long beforeCents = Money.balanceCents(loadedAccount.data().getMoney());
        loadedAccount.data().setMoney(Money.toDouble(newCents));
        saveLoadedAccount(loadedAccount);
        recordLedger(EconomyLedger.Kind.SET, reason, loadedAccount, Math.abs(newCents - beforeCents), beforeCents, newCents);

        double normalizedAmount = Money.toDouble(newCents);
        if (reason == EconomyReason.ADMIN_SET) {
            plugin.getPlayerLogsManager().log(
                    uuid,
                    loadedAccount.reference().displayName(),
                    "Economy",
                    "ECONOMY_SET",
                    "Admin set balance to " + plugin.getCurrencyManager().formatMoney(normalizedAmount) + " (Previous Balance: "
                            + plugin.getCurrencyManager().formatMoney(Money.toDouble(beforeCents)) + ")"
            );
        }

        return new EconomyTransactionResult(
                true,
                EconomyFailureReason.NONE,
                reason,
                loadedAccount.reference().uuid(),
                loadedAccount.reference().displayName(),
                normalizedAmount,
                Money.toDouble(beforeCents),
                normalizedAmount
        );
    }

    private LoadedAccount loadAccount(UUID uuid, String displayNameHint, boolean createIfMissing) {
        if (uuid == null) {
            return null;
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            PlayerData data = plugin.getPlayerDataManager().get(onlinePlayer);
            if (data == null) {
                data = plugin.getPlayerDataManager().loadOrCreate(onlinePlayer);
            }

            return new LoadedAccount(
                    new AccountReference(uuid, onlinePlayer.getName(), onlinePlayer),
                    data
            );
        }

        // The one shared copy of an offline player's row: a second change right after the first
        // builds on it instead of on a row the first change has not been written to yet.
        PlayerData offlineData = plugin.getPlayerDataManager().getOrLoadOffline(uuid);
        if (offlineData != null) {
            return new LoadedAccount(
                    new AccountReference(uuid, resolveDisplayName(uuid, displayNameHint), null),
                    offlineData
            );
        }

        if (!createIfMissing) {
            return null;
        }

        String username = resolveDisplayName(uuid, displayNameHint);
        PlayerData created = new PlayerData(uuid, username);
        PlayerSettingDefaults.applyDefaults(plugin, created);
        double startMoney = plugin.getConfigManager().getConfig()
                .getDouble("SETTINGS.MONEY-PER-DEFAULT", 1000.0D);
        created.setMoney(Money.toDouble(Money.toCents(startMoney).orElse(0L)));
        plugin.getPlayerDataManager().saveOffline(created, null);

        return new LoadedAccount(
                new AccountReference(uuid, username, null),
                created
        );
    }

    private void saveLoadedAccount(LoadedAccount loadedAccount) {
        if (loadedAccount.reference().isOnline()) {
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().invalidateLive(LeaderboardManager.LeaderboardType.MONEY);
            }
        } else {
            // Invalidate after the row is written, or the next read would cache the old balance.
            plugin.getPlayerDataManager().saveOffline(loadedAccount.data(), () -> {
                if (plugin.getLeaderboardManager() != null) {
                    plugin.getLeaderboardManager().invalidate(LeaderboardManager.LeaderboardType.MONEY);
                }
            });
        }
    }

    private void recordLedger(EconomyLedger.Kind kind, EconomyReason reason, LoadedAccount account,
                              long amountCents, long beforeCents, long afterCents) {
        EconomyLedger ledger = EconomyLedger.active();
        if (ledger != null) {
            ledger.record(null, EconomyLedger.Currency.MONEY, kind, reason, account.reference().uuid(),
                    account.reference().displayName(), null, null, amountCents, beforeCents, afterCents);
        }
    }

    /** ECONOMY.MAX-BALANCE in cents; 0 means the 90 trillion precision limit. */
    public long maxBalanceCents() {
        double configured = plugin.getConfigManager().getConfig().getDouble("ECONOMY.MAX-BALANCE", 0D);
        if (!Double.isFinite(configured) || configured <= 0D) {
            return Money.PRECISION_LIMIT_CENTS;
        }
        return Money.toCents(configured).orElse(Money.PRECISION_LIMIT_CENTS);
    }

    /** A positive amount in whole cents; empty for NaN, infinity, zero and amounts under half a cent. */
    private static java.util.OptionalLong positiveCents(double amount) {
        java.util.OptionalLong cents = Money.toCents(amount);
        return cents.isPresent() && cents.getAsLong() > 0L ? cents : java.util.OptionalLong.empty();
    }

    // ── Shards ────────────────────────────────────────────────────────────────

    /** The result of a shard change: whether it happened, and the balance before and after. */
    public record ShardResult(boolean success, EconomyFailureReason failure, long before, long after) {
    }

    /**
     * Adds shards to a player, online or offline, through the same audited path as money. Every
     * shard reward, refund and admin change calls this.
     */
    public ShardResult depositShards(UUID uuid, String nameHint, long amount, EconomyReason reason) {
        return changeShards(uuid, nameHint, amount, reason, EconomyLedger.Kind.DEPOSIT);
    }

    /** Takes shards away; fails without changing anything when the player has too few. */
    public ShardResult withdrawShards(UUID uuid, String nameHint, long amount, EconomyReason reason) {
        return changeShards(uuid, nameHint, amount, reason, EconomyLedger.Kind.WITHDRAW);
    }

    /** Sets a shard balance (admin use). */
    public ShardResult setShards(UUID uuid, String nameHint, long amount, EconomyReason reason) {
        return changeShards(uuid, nameHint, amount, reason, EconomyLedger.Kind.SET);
    }

    /** Moves shards between two players at once. */
    public ShardResult transferShards(UUID from, String fromName, UUID to, String toName, long amount, EconomyReason reason) {
        if (amount <= 0L || from == null || to == null) {
            return new ShardResult(false, EconomyFailureReason.INVALID_AMOUNT, 0L, 0L);
        }
        if (from.equals(to)) {
            return new ShardResult(false, EconomyFailureReason.SAME_ACCOUNT, 0L, 0L);
        }
        LoadedAccount sender = loadAccount(from, fromName, false);
        LoadedAccount recipient = loadAccount(to, toName, false);
        if (sender == null || recipient == null) {
            return new ShardResult(false, EconomyFailureReason.NO_PLAYER_DATA, 0L, 0L);
        }
        long senderBefore = sender.data().getShards();
        long recipientBefore = recipient.data().getShards();
        if (senderBefore < amount) {
            return new ShardResult(false, EconomyFailureReason.INSUFFICIENT_FUNDS, senderBefore, senderBefore);
        }
        long recipientAfter;
        try {
            recipientAfter = Math.addExact(recipientBefore, amount);
        } catch (ArithmeticException overflow) {
            return new ShardResult(false, EconomyFailureReason.BALANCE_LIMIT, senderBefore, senderBefore);
        }
        sender.data().setShards(senderBefore - amount);
        recipient.data().setShards(recipientAfter);
        saveShardAccount(sender);
        saveShardAccount(recipient);
        EconomyLedger ledger = EconomyLedger.active();
        if (ledger != null) {
            String transactionId = EconomyLedger.newTransactionId();
            ledger.record(transactionId, EconomyLedger.Currency.SHARDS, EconomyLedger.Kind.TRANSFER_OUT, reason,
                    from, sender.reference().displayName(), to, recipient.reference().displayName(),
                    amount, senderBefore, senderBefore - amount);
            ledger.record(transactionId, EconomyLedger.Currency.SHARDS, EconomyLedger.Kind.TRANSFER_IN, reason,
                    to, recipient.reference().displayName(), from, sender.reference().displayName(),
                    amount, recipientBefore, recipientAfter);
        }
        return new ShardResult(true, EconomyFailureReason.NONE, senderBefore, senderBefore - amount);
    }

    private ShardResult changeShards(UUID uuid, String nameHint, long amount, EconomyReason reason, EconomyLedger.Kind kind) {
        if (uuid == null) {
            return new ShardResult(false, EconomyFailureReason.PLAYER_NOT_FOUND, 0L, 0L);
        }
        if (amount < 0L || (amount == 0L && kind != EconomyLedger.Kind.SET)) {
            return new ShardResult(false, EconomyFailureReason.INVALID_AMOUNT, 0L, 0L);
        }
        LoadedAccount account = loadAccount(uuid, nameHint, false);
        if (account == null) {
            return new ShardResult(false, EconomyFailureReason.NO_PLAYER_DATA, 0L, 0L);
        }
        long before = account.data().getShards();
        long after;
        switch (kind) {
            case DEPOSIT -> {
                try {
                    after = Math.addExact(before, amount);
                } catch (ArithmeticException overflow) {
                    return new ShardResult(false, EconomyFailureReason.BALANCE_LIMIT, before, before);
                }
            }
            case WITHDRAW -> {
                if (before < amount) {
                    return new ShardResult(false, EconomyFailureReason.INSUFFICIENT_FUNDS, before, before);
                }
                after = before - amount;
            }
            default -> after = amount;
        }
        account.data().setShards(after);
        saveShardAccount(account);
        EconomyLedger ledger = EconomyLedger.active();
        if (ledger != null) {
            ledger.record(null, EconomyLedger.Currency.SHARDS, kind, reason, uuid, account.reference().displayName(),
                    null, null, kind == EconomyLedger.Kind.SET ? Math.abs(after - before) : amount, before, after);
        }
        return new ShardResult(true, EconomyFailureReason.NONE, before, after);
    }

    private void saveShardAccount(LoadedAccount account) {
        if (account.reference().isOnline()) {
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().invalidateLive(LeaderboardManager.LeaderboardType.SHARDS);
            }
            return;
        }
        plugin.getPlayerDataManager().saveOffline(account.data(), () -> {
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().invalidate(LeaderboardManager.LeaderboardType.SHARDS);
            }
        });
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

    private double roundCurrency(double amount) {
        return Money.toDouble(Money.toCents(Math.max(0D, amount)).orElse(0L));
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
                roundCurrency(Math.max(0D, Double.isFinite(amount) ? amount : 0D)),
                roundCurrency(Math.max(0D, beforeBalance)),
                roundCurrency(Math.max(0D, afterBalance))
        );
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
                roundCurrency(Math.max(0D, Double.isFinite(amount) ? amount : 0D)),
                0D,
                0D,
                0D,
                0D
        );
    }
}
