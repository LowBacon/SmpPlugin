package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.EconomyReason;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gives back money still held by the CoinFlip that 3.2 replaced. That CoinFlip kept its matches in
 * the {@code coinflip_*} tables; when it was removed, a match could still be open (its stakes taken)
 * or a winner could still be owed a payout they were offline for.
 *
 * <p>Runs once at start-up. Every open match is cancelled and each stake taken for it is refunded;
 * every payout or refund still waiting for its player is paid. Each item is marked as settled in the
 * database <em>before</em> the money moves, so a crash can never pay twice (at worst one payment is
 * missing and named in the log). Money movements whose outcome the old CoinFlip itself did not know
 * ({@code UNKNOWN} or {@code INTENT}) are listed for staff and left alone. The tables are kept.
 */
final class LegacyCoinFlipCleanup {

    private static final String DONE_KEY = "removed-in-3.2-cleanup";

    private record Owed(String matchId, UUID player, String name, long cents, String txId, boolean stake) {
    }

    private LegacyCoinFlipCleanup() {
    }

    static void run(SmpPlugin plugin, Wallet wallet) {
        if (plugin.getDatabaseManager() == null) {
            return;
        }
        Connection connection = plugin.getDatabaseManager().getConnection();
        if (connection == null || !tableExists(connection) || alreadyDone(connection)) {
            return;
        }
        List<Owed> owed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        try {
            // Stakes of matches that never finished.
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT t.id, t.match_id, t.player_uuid, t.player_name, t.amount_cents FROM coinflip_transactions t"
                            + " JOIN coinflip_matches m ON m.id = t.match_id"
                            + " WHERE t.kind = 'STAKE' AND t.state = 'APPLIED'"
                            + " AND m.state NOT IN ('COMPLETED', 'CANCELLED', 'EXPIRED')"
                            + " AND NOT EXISTS (SELECT 1 FROM coinflip_transactions c WHERE c.match_id = t.match_id"
                            + " AND c.player_uuid = t.player_uuid AND c.kind IN ('REFUND', 'PAYOUT'))");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    owed.add(new Owed(rs.getString(2), UUID.fromString(rs.getString(3)), rs.getString(4), rs.getLong(5),
                            rs.getString(1), true));
                }
            }
            // Payouts and refunds still waiting for an offline player.
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT id, match_id, player_uuid, player_name, amount_cents FROM coinflip_transactions"
                            + " WHERE state = 'PENDING_CLAIM' AND kind IN ('PAYOUT', 'REFUND')");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    owed.add(new Owed(rs.getString(2), UUID.fromString(rs.getString(3)), rs.getString(4), rs.getLong(5),
                            rs.getString(1), false));
                }
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT match_id, player_name, kind, amount_cents, state FROM coinflip_transactions"
                            + " WHERE state IN ('UNKNOWN', 'INTENT')");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    unknown.add("match " + rs.getString(1) + ": " + rs.getString(3) + " of " + rs.getLong(4) / 100D
                            + " for " + rs.getString(2) + " (" + rs.getString(5) + ")");
                }
            }
        } catch (SQLException | IllegalArgumentException exception) {
            plugin.getLogger().log(Level.SEVERE, "[CoinFlip] Could not read the old CoinFlip tables; nothing was"
                    + " changed. Old matches stay as they are.", exception);
            return;
        }

        long now = System.currentTimeMillis();
        int paid = 0;
        for (Owed item : owed) {
            try {
                if (item.stake()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO coinflip_transactions (id, match_id, player_uuid, player_name, kind, amount_cents,"
                                    + " state, reason, created_at, updated_at, note) VALUES (?, ?, ?, ?, 'REFUND', ?, 'APPLIED',"
                                    + " 'coinflip removed', ?, ?, 'stake refunded when the old CoinFlip was removed in 3.2')")) {
                        insert.setString(1, UUID.randomUUID().toString());
                        insert.setString(2, item.matchId());
                        insert.setString(3, item.player().toString());
                        insert.setString(4, item.name());
                        insert.setLong(5, item.cents());
                        insert.setLong(6, now);
                        insert.setLong(7, now);
                        insert.executeUpdate();
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE coinflip_matches SET state = 'CANCELLED', ended_at = ?,"
                                    + " end_reason = 'CoinFlip replaced in 3.2; stakes refunded' WHERE id = ?")) {
                        update.setLong(1, now);
                        update.setString(2, item.matchId());
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE coinflip_transactions SET state = 'APPLIED', updated_at = ?,"
                                    + " note = 'paid when the old CoinFlip was removed in 3.2' WHERE id = ? AND state = 'PENDING_CLAIM'")) {
                        update.setLong(1, now);
                        update.setString(2, item.txId());
                        if (update.executeUpdate() != 1) {
                            continue;
                        }
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "[CoinFlip] Could not mark " + wallet.money(item.cents()) + " owed to "
                        + item.name() + " (match " + item.matchId() + ") as paid; it was not paid and stays for the next start.",
                        exception);
                continue;
            }
            if (wallet.pay(item.player(), item.name(), item.cents(), EconomyReason.COINFLIP_REFUND,
                    item.stake() ? "Old CoinFlip stake returned:" : "Old CoinFlip payout:")) {
                paid++;
            } else {
                plugin.getLogger().severe("[CoinFlip] Marked but could not pay " + wallet.money(item.cents()) + " to "
                        + item.name() + " (" + item.player() + ", match " + item.matchId() + "). Give it with /addmoney.");
            }
        }
        for (String line : unknown) {
            plugin.getLogger().warning("[CoinFlip] Unresolved money movement from the old CoinFlip, check by hand: " + line);
        }
        markDone(connection, plugin);
        if (!owed.isEmpty() || !unknown.isEmpty()) {
            plugin.getLogger().info("[CoinFlip] The old CoinFlip was removed: " + paid + " stake(s) and payout(s) were given"
                    + " back to their players" + (unknown.isEmpty() ? "." : "; " + unknown.size() + " need a staff check (see above)."));
        }
    }

    private static boolean tableExists(Connection connection) {
        try (PreparedStatement select = connection.prepareStatement("SELECT COUNT(*) FROM coinflip_transactions");
             ResultSet rs = select.executeQuery()) {
            return rs.next();
        } catch (SQLException missing) {
            return false;
        }
    }

    private static boolean alreadyDone(Connection connection) {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT meta_value FROM coinflip_meta WHERE meta_key = ?")) {
            select.setString(1, DONE_KEY);
            try (ResultSet rs = select.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException missing) {
            return false;
        }
    }

    private static void markDone(Connection connection, SmpPlugin plugin) {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO coinflip_meta (meta_key, meta_value) VALUES (?, ?)")) {
            insert.setString(1, DONE_KEY);
            insert.setString(2, Long.toString(System.currentTimeMillis()));
            insert.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.FINE, "Could not record the old CoinFlip cleanup", exception);
        }
    }
}
