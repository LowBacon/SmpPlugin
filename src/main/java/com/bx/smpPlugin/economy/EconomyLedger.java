package com.bx.smpPlugin.economy;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.DatabaseManager;
import com.bx.smpPlugin.models.EconomyReason;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The audit trail of money and shard changes: table {@code economy_ledger}.
 *
 * <p>Every change made through the economy writes one row (a payment writes two, one per player,
 * with the same transaction id) holding who, how much, why, who caused it, and the balance before
 * and after. Rows are written in order on the database thread, so recording never slows the game.
 * Admin commands name themselves as the actor with {@link #asActor}.
 */
public final class EconomyLedger {

    public enum Currency {
        MONEY, SHARDS
    }

    public enum Kind {
        DEPOSIT, WITHDRAW, SET, TRANSFER_OUT, TRANSFER_IN
    }

    public record Entry(
            String transactionId,
            long createdAt,
            Currency currency,
            Kind kind,
            String reason,
            UUID player,
            String playerName,
            UUID counterparty,
            String counterpartyName,
            UUID actor,
            String actorName,
            long amountMinor,
            long beforeMinor,
            long afterMinor,
            String note
    ) {
    }

    private record Actor(UUID uuid, String name, String note) {
    }

    private static final ThreadLocal<Actor> CURRENT_ACTOR = new ThreadLocal<>();
    private static volatile EconomyLedger active;

    private final SmpPlugin plugin;
    private volatile boolean ready;

    public EconomyLedger(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** The running ledger, or null before 4.0 started (or when it is turned off). */
    public static EconomyLedger active() {
        return active;
    }

    public void start() {
        ready = true;
        active = this;
        int days = plugin.getConfigManager().getConfig().getInt("ECONOMY.LEDGER.RETENTION-DAYS", 180);
        if (days > 0) {
            long cutoff = System.currentTimeMillis() - days * 86_400_000L;
            plugin.getDatabaseManager().executeAsync(() -> prune(cutoff));
        }
    }

    public void stop() {
        ready = false;
        if (active == this) {
            active = null;
        }
    }

    public boolean isEnabled() {
        return ready && plugin.getConfigManager().getConfig().getBoolean("ECONOMY.LEDGER.ENABLED", true);
    }

    /**
     * Runs an economy change with the sender recorded as the one who made it, for admin commands.
     * The note (a reason the admin gave) is stored with the row.
     */
    public static <T> T asActor(CommandSender sender, String note, Supplier<T> change) {
        Actor previous = CURRENT_ACTOR.get();
        UUID uuid = sender instanceof Player player ? player.getUniqueId() : null;
        String name = sender == null ? "unknown" : sender instanceof Player ? sender.getName() : "CONSOLE";
        CURRENT_ACTOR.set(new Actor(uuid, name, note));
        try {
            return change.get();
        } finally {
            if (previous == null) {
                CURRENT_ACTOR.remove();
            } else {
                CURRENT_ACTOR.set(previous);
            }
        }
    }

    /** A new id for one transaction (shared by both rows of a payment). */
    public static String newTransactionId() {
        return UUID.randomUUID().toString();
    }

    /** Queues one row. Does nothing while the ledger is off. */
    public void record(String transactionId, Currency currency, Kind kind, EconomyReason reason,
                       UUID player, String playerName, UUID counterparty, String counterpartyName,
                       long amountMinor, long beforeMinor, long afterMinor) {
        if (!isEnabled() || player == null) {
            return;
        }
        if (currency == Currency.SHARDS && reason == EconomyReason.SHARD_PASSIVE
                && !plugin.getConfigManager().getConfig().getBoolean("ECONOMY.LEDGER.RECORD-PASSIVE-SHARDS", false)) {
            return;
        }
        Actor actor = CURRENT_ACTOR.get();
        Entry entry = new Entry(transactionId == null ? newTransactionId() : transactionId, System.currentTimeMillis(),
                currency, kind, reason == null ? null : reason.name(), player, playerName, counterparty,
                counterpartyName, actor == null ? null : actor.uuid(), actor == null ? null : actor.name(),
                amountMinor, beforeMinor, afterMinor, actor == null ? null : actor.note());
        plugin.getDatabaseManager().executeAsync(() -> insert(entry));
    }

    private void insert(Entry entry) {
        Connection connection = connection();
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO economy_ledger"
                + " (tx_uuid, created_at, currency, kind, reason, player_uuid, player_name, counterparty_uuid,"
                + " counterparty_name, actor_uuid, actor_name, amount_minor, balance_before_minor,"
                + " balance_after_minor, note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            write(statement, entry);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not write economy ledger row " + entry.transactionId(), exception);
        }
    }

    static void write(PreparedStatement statement, Entry entry) throws SQLException {
        statement.setString(1, entry.transactionId());
        statement.setLong(2, entry.createdAt());
        statement.setString(3, entry.currency().name());
        statement.setString(4, entry.kind().name());
        statement.setString(5, entry.reason());
        statement.setString(6, entry.player().toString());
        statement.setString(7, entry.playerName());
        statement.setString(8, entry.counterparty() == null ? null : entry.counterparty().toString());
        statement.setString(9, entry.counterpartyName());
        statement.setString(10, entry.actor() == null ? null : entry.actor().toString());
        statement.setString(11, entry.actorName());
        statement.setLong(12, entry.amountMinor());
        statement.setLong(13, entry.beforeMinor());
        statement.setLong(14, entry.afterMinor());
        statement.setString(15, entry.note());
    }

    /** One page of a player's rows, newest first, read on the database thread. */
    public CompletableFuture<List<Entry>> history(UUID player, int page, int pageSize) {
        int size = Math.max(1, Math.min(50, pageSize));
        int offset = Math.max(0, page - 1) * size;
        return plugin.getDatabaseManager().supplyAsync(() -> readHistory(connection(), player, size, offset));
    }

    static List<Entry> readHistory(Connection connection, UUID player, int limit, int offset) {
        List<Entry> rows = new ArrayList<>();
        if (connection == null || player == null) {
            return rows;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT tx_uuid, created_at, currency, kind,"
                + " reason, player_uuid, player_name, counterparty_uuid, counterparty_name, actor_uuid, actor_name,"
                + " amount_minor, balance_before_minor, balance_after_minor, note FROM economy_ledger"
                + " WHERE player_uuid = ? ORDER BY created_at DESC, ledger_id DESC LIMIT ? OFFSET ?")) {
            statement.setString(1, player.toString());
            statement.setInt(2, limit);
            statement.setInt(3, offset);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new Entry(
                            result.getString(1),
                            result.getLong(2),
                            parseCurrency(result.getString(3)),
                            parseKind(result.getString(4)),
                            result.getString(5),
                            uuid(result.getString(6)),
                            result.getString(7),
                            uuid(result.getString(8)),
                            result.getString(9),
                            uuid(result.getString(10)),
                            result.getString(11),
                            result.getLong(12),
                            result.getLong(13),
                            result.getLong(14),
                            result.getString(15)));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read the economy ledger", exception);
        }
        return rows;
    }

    private void prune(long cutoff) {
        Connection connection = connection();
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM economy_ledger WHERE created_at < ?")) {
            statement.setLong(1, cutoff);
            int removed = statement.executeUpdate();
            if (removed > 0) {
                plugin.getLogger().info("Removed " + removed + " economy ledger rows older than the retention period.");
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not prune the economy ledger", exception);
        }
    }

    private Connection connection() {
        DatabaseManager database = plugin.getDatabaseManager();
        return database == null ? null : database.getConnection();
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Currency parseCurrency(String raw) {
        return "SHARDS".equalsIgnoreCase(raw) ? Currency.SHARDS : Currency.MONEY;
    }

    private static Kind parseKind(String raw) {
        try {
            return Kind.valueOf(raw);
        } catch (RuntimeException exception) {
            return Kind.SET;
        }
    }
}
