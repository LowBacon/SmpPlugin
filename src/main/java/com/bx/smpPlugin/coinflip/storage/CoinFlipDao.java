package com.bx.smpPlugin.coinflip.storage;

import com.bx.smpPlugin.coinflip.Achievement;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipStats;
import com.bx.smpPlugin.coinflip.CoinFlipTransaction;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.coinflip.EconomyGateway;
import com.bx.smpPlugin.coinflip.MatchState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every SQL statement CoinFlip runs. All statements are prepared, every query is bounded, and every state change
 * is a compare-and-set that throws {@link CoinFlipStorageException.Reason#CONFLICT} when the row was not in the
 * expected state. Used only on the storage worker thread, inside the transaction the repository opened.
 */
public final class CoinFlipDao {

    static final int SCHEMA_VERSION = 1;

    /** One leaderboard row. */
    public record RankRow(UUID playerId, String playerName, long value, long played) {
    }

    /** History filter for paginated match history. */
    public enum HistoryFilter {
        ALL, WINS, LOSSES, CANCELLED
    }

    /** Leaderboard metrics. Win rate requires a minimum number of matches. */
    public enum Metric {
        WINS("wins"), PLAYED("played"), WIN_RATE("wins"), GROSS_PAYOUT("gross_payout_cents"),
        NET_PROFIT("net_cents"), LARGEST_PAYOUT("largest_payout_cents"), BEST_STREAK("best_streak"),
        WEEKLY_PLAYED("played"), MONTHLY_PLAYED("played"), WEEKLY_NET("net_cents"), MONTHLY_NET("net_cents");

        private final String column;

        Metric(String column) {
            this.column = column;
        }

        public boolean periodic() {
            return name().startsWith("WEEKLY") || name().startsWith("MONTHLY");
        }

        public boolean weekly() {
            return name().startsWith("WEEKLY");
        }
    }

    private final Connection connection;
    private final boolean mySql;
    private final int queryTimeoutSeconds;

    CoinFlipDao(Connection connection, boolean mySql, int queryTimeoutSeconds) {
        this.connection = connection;
        this.mySql = mySql;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    // ── Schema ───────────────────────────────────────────────────────────────────

    void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_meta (meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(255))");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_matches ("
                    + "id VARCHAR(16) PRIMARY KEY, state VARCHAR(24) NOT NULL, "
                    + "creator_uuid VARCHAR(36) NOT NULL, creator_name VARCHAR(32) NOT NULL, creator_side VARCHAR(8) NOT NULL, "
                    + "private_match INTEGER NOT NULL, invitee_uuid VARCHAR(36), invitee_name VARCHAR(32), "
                    + "opponent_uuid VARCHAR(36), opponent_name VARCHAR(32), "
                    + "stake_cents BIGINT NOT NULL, fee_bps INTEGER NOT NULL, "
                    + "outcome VARCHAR(8), outcome_id VARCHAR(32), winner_uuid VARCHAR(36), "
                    + "created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, joined_at BIGINT NOT NULL DEFAULT 0, "
                    + "ended_at BIGINT NOT NULL DEFAULT 0, end_reason VARCHAR(255))");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_transactions ("
                    + "id VARCHAR(36) PRIMARY KEY, match_id VARCHAR(16) NOT NULL, player_uuid VARCHAR(36) NOT NULL, "
                    + "player_name VARCHAR(32), kind VARCHAR(12) NOT NULL, amount_cents BIGINT NOT NULL, "
                    + "state VARCHAR(16) NOT NULL, reason VARCHAR(64), created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, "
                    + "note VARCHAR(255), UNIQUE (match_id, player_uuid, kind))");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_stats ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(32), played BIGINT NOT NULL, wins BIGINT NOT NULL, "
                    + "losses BIGINT NOT NULL, staked_cents BIGINT NOT NULL, gross_payout_cents BIGINT NOT NULL, "
                    + "net_cents BIGINT NOT NULL, largest_payout_cents BIGINT NOT NULL, largest_net_win_cents BIGINT NOT NULL, "
                    + "current_streak BIGINT NOT NULL, best_streak BIGINT NOT NULL, heads_picks BIGINT NOT NULL, "
                    + "tails_picks BIGINT NOT NULL, public_matches BIGINT NOT NULL, private_matches BIGINT NOT NULL, "
                    + "joined_matches BIGINT NOT NULL, practice_flips BIGINT NOT NULL, total_duration_ms BIGINT NOT NULL, "
                    + "first_match_at BIGINT NOT NULL, last_match_at BIGINT NOT NULL, active_days BIGINT NOT NULL, "
                    + "last_day VARCHAR(10), hidden INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_periods ("
                    + "player_uuid VARCHAR(36) NOT NULL, period VARCHAR(12) NOT NULL, player_name VARCHAR(32), "
                    + "played BIGINT NOT NULL, wins BIGINT NOT NULL, net_cents BIGINT NOT NULL, staked_cents BIGINT NOT NULL, "
                    + "PRIMARY KEY (player_uuid, period))");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_achievements ("
                    + "player_uuid VARCHAR(36) NOT NULL, achievement VARCHAR(32) NOT NULL, unlocked_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (player_uuid, achievement))");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_preferences ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY, data VARCHAR(512) NOT NULL, updated_at BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS coinflip_audit ("
                    + "id VARCHAR(36) PRIMARY KEY, at BIGINT NOT NULL, actor VARCHAR(64) NOT NULL, action VARCHAR(32) NOT NULL, "
                    + "match_id VARCHAR(16), detail VARCHAR(1000))");
        }
        index("idx_cf_matches_state", "coinflip_matches", "state");
        index("idx_cf_matches_creator", "coinflip_matches", "creator_uuid");
        index("idx_cf_matches_opponent", "coinflip_matches", "opponent_uuid");
        index("idx_cf_matches_ended", "coinflip_matches", "ended_at");
        index("idx_cf_tx_state", "coinflip_transactions", "state");
        index("idx_cf_tx_player", "coinflip_transactions", "player_uuid, state");
        index("idx_cf_periods_period", "coinflip_periods", "period");
        index("idx_cf_audit_at", "coinflip_audit", "at");
        index("idx_cf_audit_match", "coinflip_audit", "match_id");
    }

    private void index(String name, String table, String columns) throws SQLException {
        String sql = mySql
                ? "CREATE INDEX " + name + " ON " + table + " (" + columns + ")"
                : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + columns + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
            if (!(mySql && (message.contains("duplicate") || message.contains("exists")))) {
                throw exception;
            }
        }
    }

    int schemaVersion() throws SQLException {
        try (PreparedStatement statement = prepare("SELECT meta_value FROM coinflip_meta WHERE meta_key = 'schema_version'");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? Integer.parseInt(result.getString(1)) : 0;
        }
    }

    void setSchemaVersion(int version) throws SQLException {
        try (PreparedStatement update = prepare("UPDATE coinflip_meta SET meta_value = ? WHERE meta_key = 'schema_version'")) {
            update.setString(1, Integer.toString(version));
            if (update.executeUpdate() == 1) {
                return;
            }
        }
        try (PreparedStatement insert = prepare("INSERT INTO coinflip_meta (meta_key, meta_value) VALUES ('schema_version', ?)")) {
            insert.setString(1, Integer.toString(version));
            insert.executeUpdate();
        }
    }

    // ── Matches ──────────────────────────────────────────────────────────────────

    public void insertMatch(CoinFlipMatch match) throws SQLException {
        try (PreparedStatement statement = prepare("INSERT INTO coinflip_matches (id, state, creator_uuid, creator_name, "
                + "creator_side, private_match, invitee_uuid, invitee_name, stake_cents, fee_bps, created_at, expires_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, match.id());
            statement.setString(2, match.state().durable().name());
            statement.setString(3, match.creatorId().toString());
            statement.setString(4, match.creatorName());
            statement.setString(5, match.creatorSide().name());
            statement.setInt(6, match.isPrivate() ? 1 : 0);
            setUuid(statement, 7, match.inviteeId());
            statement.setString(8, match.inviteeName());
            statement.setLong(9, match.stakeCents());
            statement.setInt(10, match.feeBasisPoints());
            statement.setLong(11, match.createdAtMillis());
            statement.setLong(12, match.expiresAtMillis());
            statement.executeUpdate();
        }
    }

    /** Moves a match between durable states; throws CONFLICT when it is no longer in {@code from}. */
    public void moveMatch(String id, MatchState from, MatchState to, long endedAt, String reason) throws SQLException {
        try (PreparedStatement statement = prepare("UPDATE coinflip_matches SET state = ?, ended_at = CASE WHEN ? > 0 "
                + "THEN ? ELSE ended_at END, end_reason = CASE WHEN ? IS NULL THEN end_reason ELSE ? END "
                + "WHERE id = ? AND state = ?")) {
            statement.setString(1, to.name());
            statement.setLong(2, endedAt);
            statement.setLong(3, endedAt);
            statement.setString(4, reason);
            statement.setString(5, reason);
            statement.setString(6, id);
            statement.setString(7, from.name());
            requireOne(statement.executeUpdate(), "match " + id + " is not " + from);
        }
    }

    public void reserveOpponent(String id, UUID opponent, String opponentName) throws SQLException {
        try (PreparedStatement statement = prepare("UPDATE coinflip_matches SET state = 'JOINING', opponent_uuid = ?, "
                + "opponent_name = ? WHERE id = ? AND state = 'WAITING'")) {
            statement.setString(1, opponent.toString());
            statement.setString(2, opponentName);
            statement.setString(3, id);
            requireOne(statement.executeUpdate(), "match " + id + " is not waiting");
        }
    }

    public void releaseOpponent(String id) throws SQLException {
        try (PreparedStatement statement = prepare("UPDATE coinflip_matches SET state = 'WAITING', opponent_uuid = NULL, "
                + "opponent_name = NULL WHERE id = ? AND state = 'JOINING'")) {
            statement.setString(1, id);
            requireOne(statement.executeUpdate(), "match " + id + " is not joining");
        }
    }

    /** Stores the one and only outcome. The {@code outcome IS NULL} guard makes a second write impossible. */
    public void lockOutcome(String id, CoinSide outcome, String outcomeId, UUID winner, long joinedAt) throws SQLException {
        try (PreparedStatement statement = prepare("UPDATE coinflip_matches SET state = 'LOCKED', outcome = ?, outcome_id = ?, "
                + "winner_uuid = ?, joined_at = ? WHERE id = ? AND state = 'JOINING' AND outcome IS NULL")) {
            statement.setString(1, outcome.name());
            statement.setString(2, outcomeId);
            statement.setString(3, winner.toString());
            statement.setLong(4, joinedAt);
            statement.setString(5, id);
            requireOne(statement.executeUpdate(), "match " + id + " already has an outcome or is not joining");
        }
    }

    public Optional<CoinFlipMatch> loadMatch(String id) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_matches WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readMatch(result)) : Optional.empty();
            }
        }
    }

    public List<CoinFlipMatch> loadUnfinishedMatches() throws SQLException {
        List<CoinFlipMatch> matches = new ArrayList<>();
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_matches WHERE state NOT IN "
                + "('COMPLETED','CANCELLED','EXPIRED') ORDER BY created_at, id")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    matches.add(readMatch(result));
                }
            }
        }
        return matches;
    }

    public List<CoinFlipMatch> loadHistory(UUID player, HistoryFilter filter, int limit, int offset) throws SQLException {
        String condition = switch (filter) {
            case ALL -> "state IN ('COMPLETED','CANCELLED','EXPIRED')";
            case WINS -> "state = 'COMPLETED' AND winner_uuid = ?";
            case LOSSES -> "state = 'COMPLETED' AND winner_uuid <> ?";
            case CANCELLED -> "state IN ('CANCELLED','EXPIRED')";
        };
        String sql = "SELECT * FROM coinflip_matches WHERE (creator_uuid = ? OR opponent_uuid = ?) AND " + condition
                + " ORDER BY ended_at DESC, id DESC LIMIT ? OFFSET ?";
        List<CoinFlipMatch> matches = new ArrayList<>();
        try (PreparedStatement statement = prepare(sql)) {
            int index = 1;
            statement.setString(index++, player.toString());
            statement.setString(index++, player.toString());
            if (filter == HistoryFilter.WINS || filter == HistoryFilter.LOSSES) {
                statement.setString(index++, player.toString());
            }
            statement.setInt(index++, Math.max(1, Math.min(100, limit)));
            statement.setInt(index, Math.max(0, offset));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    matches.add(readMatch(result));
                }
            }
        }
        return matches;
    }

    /** Completed matches the player took part in that ended at or after {@code since}. */
    public List<CoinFlipMatch> completedSince(UUID player, long since) throws SQLException {
        List<CoinFlipMatch> matches = new ArrayList<>();
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_matches WHERE state = 'COMPLETED' AND ended_at >= ? "
                + "AND (creator_uuid = ? OR opponent_uuid = ?) LIMIT 5000")) {
            statement.setLong(1, since);
            statement.setString(2, player.toString());
            statement.setString(3, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    matches.add(readMatch(result));
                }
            }
        }
        return matches;
    }

    public int countCreatedSince(UUID player, long since) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT COUNT(*) FROM coinflip_matches WHERE creator_uuid = ? AND created_at >= ?")) {
            statement.setString(1, player.toString());
            statement.setLong(2, since);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Stakes applied minus refunds applied since {@code since}. */
    public long stakedSince(UUID player, long since) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT kind, SUM(amount_cents) FROM coinflip_transactions WHERE player_uuid = ? "
                + "AND state IN ('APPLIED','PENDING_CLAIM') AND kind IN ('STAKE','REFUND') AND created_at >= ? GROUP BY kind")) {
            statement.setString(1, player.toString());
            statement.setLong(2, since);
            long total = 0L;
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long amount = result.getLong(2);
                    total += "STAKE".equals(result.getString(1)) ? amount : -amount;
                }
            }
            return Math.max(0L, total);
        }
    }

    public Map<MatchState, Integer> countByState() throws SQLException {
        Map<MatchState, Integer> counts = new EnumMap<>(MatchState.class);
        try (PreparedStatement statement = prepare("SELECT state, COUNT(*) FROM coinflip_matches GROUP BY state");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                try {
                    counts.put(MatchState.valueOf(result.getString(1)), result.getInt(2));
                } catch (IllegalArgumentException ignored) {
                    // unknown state from a newer version; reported as corrupt elsewhere
                }
            }
        }
        return counts;
    }

    private CoinFlipMatch readMatch(ResultSet result) throws SQLException {
        String id = result.getString("id");
        try {
            String outcome = result.getString("outcome");
            return CoinFlipMatch.restore(
                    id,
                    UUID.fromString(result.getString("creator_uuid")),
                    result.getString("creator_name"),
                    CoinSide.valueOf(result.getString("creator_side")),
                    result.getInt("private_match") == 1,
                    uuid(result.getString("invitee_uuid")),
                    result.getString("invitee_name"),
                    result.getLong("stake_cents"),
                    result.getInt("fee_bps"),
                    result.getLong("created_at"),
                    result.getLong("expires_at"),
                    MatchState.valueOf(result.getString("state")),
                    uuid(result.getString("opponent_uuid")),
                    result.getString("opponent_name"),
                    outcome == null ? null : CoinSide.valueOf(outcome),
                    result.getString("outcome_id"),
                    result.getLong("joined_at"),
                    result.getLong("ended_at"),
                    result.getString("end_reason"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SQLException("Corrupt coinflip_matches row " + id + ": " + exception.getMessage(), exception);
        }
    }

    // ── Transactions (journal) ───────────────────────────────────────────────────

    public void insertTransaction(CoinFlipTransaction tx) throws SQLException {
        try (PreparedStatement statement = prepare("INSERT INTO coinflip_transactions (id, match_id, player_uuid, player_name, kind, "
                + "amount_cents, state, reason, created_at, updated_at, note) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, tx.id());
            statement.setString(2, tx.matchId());
            statement.setString(3, tx.playerId().toString());
            statement.setString(4, tx.playerName());
            statement.setString(5, tx.kind().name());
            statement.setLong(6, tx.amountCents());
            statement.setString(7, tx.state().name());
            statement.setString(8, tx.reason());
            statement.setLong(9, tx.createdAtMillis());
            statement.setLong(10, tx.updatedAtMillis());
            statement.setString(11, truncate(tx.note(), 255));
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (isUniqueViolation(exception)) {
                throw new CoinFlipStorageException(CoinFlipStorageException.Reason.CONFLICT,
                        "duplicate " + tx.kind() + " for match " + tx.matchId(), exception);
            }
            throw exception;
        }
    }

    public void moveTransaction(String id, CoinFlipTransaction.State from, CoinFlipTransaction.State to, long at, String note)
            throws SQLException {
        try (PreparedStatement statement = prepare("UPDATE coinflip_transactions SET state = ?, updated_at = ?, "
                + "note = CASE WHEN ? IS NULL THEN note ELSE ? END WHERE id = ? AND state = ?")) {
            statement.setString(1, to.name());
            statement.setLong(2, at);
            String trimmed = truncate(note, 255);
            statement.setString(3, trimmed);
            statement.setString(4, trimmed);
            statement.setString(5, id);
            statement.setString(6, from.name());
            requireOne(statement.executeUpdate(), "transaction " + id + " is not " + from);
        }
    }

    public List<CoinFlipTransaction> transactionsForMatch(String matchId) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_transactions WHERE match_id = ? ORDER BY created_at, id")) {
            statement.setString(1, matchId);
            return readTransactions(statement);
        }
    }

    public List<CoinFlipTransaction> transactionsInState(CoinFlipTransaction.State state, int limit) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_transactions WHERE state = ? ORDER BY created_at, id LIMIT ?")) {
            statement.setString(1, state.name());
            statement.setInt(2, limit);
            return readTransactions(statement);
        }
    }

    public List<CoinFlipTransaction> claimsFor(UUID player) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_transactions WHERE player_uuid = ? AND state = 'PENDING_CLAIM' "
                + "ORDER BY created_at, id LIMIT 100")) {
            statement.setString(1, player.toString());
            return readTransactions(statement);
        }
    }

    public Optional<CoinFlipTransaction> loadTransaction(String id) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_transactions WHERE id = ?")) {
            statement.setString(1, id);
            List<CoinFlipTransaction> found = readTransactions(statement);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
        }
    }

    private List<CoinFlipTransaction> readTransactions(PreparedStatement statement) throws SQLException {
        List<CoinFlipTransaction> transactions = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String id = result.getString("id");
                try {
                    transactions.add(new CoinFlipTransaction(
                            id,
                            result.getString("match_id"),
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("player_name"),
                            CoinFlipTransaction.Kind.valueOf(result.getString("kind")),
                            result.getLong("amount_cents"),
                            CoinFlipTransaction.State.valueOf(result.getString("state")),
                            result.getString("reason"),
                            result.getLong("created_at"),
                            result.getLong("updated_at"),
                            result.getString("note")));
                } catch (IllegalArgumentException | NullPointerException exception) {
                    throw new SQLException("Corrupt coinflip_transactions row " + id, exception);
                }
            }
        }
        return transactions;
    }

    // ── Balances ─────────────────────────────────────────────────────────────────

    /**
     * Writes the player's balance as it stands in memory right after a CoinFlip change, in the same database
     * transaction as the journal row, so the change and its record become durable together.
     */
    public void writeBalance(UUID player, EconomyGateway.StoredBalance balance) throws SQLException {
        // Only a newer version is written, so this can never put back an older balance that another writer
        // (the economy's own database writer, a quit save) has already replaced.
        try (PreparedStatement statement = prepare(
                "UPDATE players SET money = ?, money_version = ? WHERE uuid = ? AND money_version < ?")) {
            statement.setDouble(1, balance.money());
            statement.setLong(2, balance.version());
            statement.setString(3, player.toString());
            statement.setLong(4, balance.version());
            if (statement.executeUpdate() > 0) {
                return;
            }
        }
        // Nothing written: the account is missing, or this balance version (or a newer one, which includes this
        // change) is already stored. Only the first is a failure.
        try (PreparedStatement statement = prepare("SELECT money_version FROM players WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("no stored account for " + player);
                }
                if (result.getLong(1) < balance.version()) {
                    throw new SQLException("balance of " + player + " was not written");
                }
            }
        }
    }

    // ── Statistics ───────────────────────────────────────────────────────────────

    public CoinFlipStats loadStats(UUID player, String fallbackName) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT * FROM coinflip_stats WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return CoinFlipStats.empty(player, fallbackName);
                }
                return new CoinFlipStats(player, result.getString("player_name"), result.getLong("played"),
                        result.getLong("wins"), result.getLong("losses"), result.getLong("staked_cents"),
                        result.getLong("gross_payout_cents"), result.getLong("net_cents"),
                        result.getLong("largest_payout_cents"), result.getLong("largest_net_win_cents"),
                        result.getLong("current_streak"), result.getLong("best_streak"), result.getLong("heads_picks"),
                        result.getLong("tails_picks"), result.getLong("public_matches"), result.getLong("private_matches"),
                        result.getLong("joined_matches"), result.getLong("practice_flips"),
                        result.getLong("total_duration_ms"), result.getLong("first_match_at"),
                        result.getLong("last_match_at"), result.getLong("active_days"),
                        result.getString("last_day") == null ? "" : result.getString("last_day"),
                        result.getInt("hidden") == 1);
            }
        }
    }

    public void saveStats(CoinFlipStats stats) throws SQLException {
        String update = "UPDATE coinflip_stats SET player_name=?, played=?, wins=?, losses=?, staked_cents=?, gross_payout_cents=?, "
                + "net_cents=?, largest_payout_cents=?, largest_net_win_cents=?, current_streak=?, best_streak=?, heads_picks=?, "
                + "tails_picks=?, public_matches=?, private_matches=?, joined_matches=?, practice_flips=?, total_duration_ms=?, "
                + "first_match_at=?, last_match_at=?, active_days=?, last_day=?, hidden=? WHERE player_uuid=?";
        try (PreparedStatement statement = prepare(update)) {
            bindStats(statement, stats);
            statement.setString(24, stats.playerId().toString());
            if (statement.executeUpdate() == 1) {
                return;
            }
        }
        String insert = "INSERT INTO coinflip_stats (player_name, played, wins, losses, staked_cents, gross_payout_cents, net_cents, "
                + "largest_payout_cents, largest_net_win_cents, current_streak, best_streak, heads_picks, tails_picks, "
                + "public_matches, private_matches, joined_matches, practice_flips, total_duration_ms, first_match_at, "
                + "last_match_at, active_days, last_day, hidden, player_uuid) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = prepare(insert)) {
            bindStats(statement, stats);
            statement.setString(24, stats.playerId().toString());
            statement.executeUpdate();
        }
    }

    private static void bindStats(PreparedStatement statement, CoinFlipStats stats) throws SQLException {
        statement.setString(1, stats.playerName());
        statement.setLong(2, stats.played());
        statement.setLong(3, stats.wins());
        statement.setLong(4, stats.losses());
        statement.setLong(5, stats.stakedCents());
        statement.setLong(6, stats.grossPayoutCents());
        statement.setLong(7, stats.netCents());
        statement.setLong(8, stats.largestPayoutCents());
        statement.setLong(9, stats.largestNetWinCents());
        statement.setLong(10, stats.currentStreak());
        statement.setLong(11, stats.bestStreak());
        statement.setLong(12, stats.headsPicks());
        statement.setLong(13, stats.tailsPicks());
        statement.setLong(14, stats.publicMatches());
        statement.setLong(15, stats.privateMatches());
        statement.setLong(16, stats.joinedMatches());
        statement.setLong(17, stats.practiceFlips());
        statement.setLong(18, stats.totalDurationMillis());
        statement.setLong(19, stats.firstMatchMillis());
        statement.setLong(20, stats.lastMatchMillis());
        statement.setLong(21, stats.activeDays());
        statement.setString(22, stats.lastActiveDay());
        statement.setInt(23, stats.hidden() ? 1 : 0);
    }

    public void addToPeriod(UUID player, String name, String period, long played, long wins, long net, long staked)
            throws SQLException {
        try (PreparedStatement update = prepare("UPDATE coinflip_periods SET player_name = ?, played = played + ?, wins = wins + ?, "
                + "net_cents = net_cents + ?, staked_cents = staked_cents + ? WHERE player_uuid = ? AND period = ?")) {
            update.setString(1, name);
            update.setLong(2, played);
            update.setLong(3, wins);
            update.setLong(4, net);
            update.setLong(5, staked);
            update.setString(6, player.toString());
            update.setString(7, period);
            if (update.executeUpdate() == 1) {
                return;
            }
        }
        try (PreparedStatement insert = prepare("INSERT INTO coinflip_periods (player_uuid, period, player_name, played, wins, "
                + "net_cents, staked_cents) VALUES (?,?,?,?,?,?,?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, period);
            insert.setString(3, name);
            insert.setLong(4, played);
            insert.setLong(5, wins);
            insert.setLong(6, net);
            insert.setLong(7, staked);
            insert.executeUpdate();
        }
    }

    /** A player who has CoinFlip statistics, found by name (case-insensitive). */
    public Optional<RankRow> findPlayerByName(String name) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT player_uuid, player_name, played, played FROM coinflip_stats "
                + "WHERE LOWER(player_name) = LOWER(?) ORDER BY last_match_at DESC LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new RankRow(UUID.fromString(result.getString(1)), result.getString(2),
                        result.getLong(3), result.getLong(4))) : Optional.empty();
            }
        }
    }

    /** This player's matches played and net result in one period, e.g. "W2026-41". */
    public long[] periodTotals(UUID player, String period) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT played, net_cents FROM coinflip_periods WHERE player_uuid = ? AND period = ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, period);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new long[]{result.getLong(1), result.getLong(2)} : new long[]{0L, 0L};
            }
        }
    }

    public void resetStats(UUID player) throws SQLException {
        for (String table : new String[]{"coinflip_stats", "coinflip_periods", "coinflip_achievements"}) {
            try (PreparedStatement statement = prepare("DELETE FROM " + table + " WHERE player_uuid = ?")) {
                statement.setString(1, player.toString());
                statement.executeUpdate();
            }
        }
    }

    public void setHidden(UUID player, boolean hidden) throws SQLException {
        try (PreparedStatement statement = prepare("UPDATE coinflip_stats SET hidden = ? WHERE player_uuid = ?")) {
            statement.setInt(1, hidden ? 1 : 0);
            statement.setString(2, player.toString());
            statement.executeUpdate();
        }
    }

    /**
     * Top players for a metric. Ties are broken by fewer matches played (for totals) and then by UUID, so the
     * order is stable between refreshes. Hidden profiles are excluded.
     */
    public List<RankRow> leaderboard(Metric metric, String period, int limit, int minMatchesForRate) throws SQLException {
        String sql;
        if (metric.periodic()) {
            sql = "SELECT p.player_uuid, p.player_name, p." + metric.column + " AS metric_value, p.played FROM coinflip_periods p "
                    + "LEFT JOIN coinflip_stats s ON s.player_uuid = p.player_uuid WHERE p.period = ? AND p.played > 0 "
                    + "AND (s.hidden IS NULL OR s.hidden = 0) ORDER BY metric_value DESC, p.played ASC, p.player_uuid ASC LIMIT ?";
        } else if (metric == Metric.WIN_RATE) {
            sql = "SELECT player_uuid, player_name, (wins * 10000) / played AS metric_value, played FROM coinflip_stats "
                    + "WHERE hidden = 0 AND played >= ? ORDER BY metric_value DESC, played DESC, player_uuid ASC LIMIT ?";
        } else {
            sql = "SELECT player_uuid, player_name, " + metric.column + " AS metric_value, played FROM coinflip_stats "
                    + "WHERE hidden = 0 AND played > 0 ORDER BY metric_value DESC, played ASC, player_uuid ASC LIMIT ?";
        }
        List<RankRow> rows = new ArrayList<>();
        try (PreparedStatement statement = prepare(sql)) {
            int index = 1;
            if (metric.periodic()) {
                statement.setString(index++, period);
            } else if (metric == Metric.WIN_RATE) {
                statement.setInt(index++, minMatchesForRate);
            }
            statement.setInt(index, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new RankRow(UUID.fromString(result.getString(1)), result.getString(2), result.getLong(3),
                            result.getLong(4)));
                }
            }
        }
        return rows;
    }

    // ── Achievements ─────────────────────────────────────────────────────────────

    /** Inserts the achievement unless the player already has it. Returns true only for a new unlock. */
    public boolean unlock(UUID player, Achievement achievement, long at) throws SQLException {
        try (PreparedStatement check = prepare("SELECT 1 FROM coinflip_achievements WHERE player_uuid = ? AND achievement = ?")) {
            check.setString(1, player.toString());
            check.setString(2, achievement.name());
            try (ResultSet result = check.executeQuery()) {
                if (result.next()) {
                    return false;
                }
            }
        }
        try (PreparedStatement insert = prepare("INSERT INTO coinflip_achievements (player_uuid, achievement, unlocked_at) VALUES (?,?,?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, achievement.name());
            insert.setLong(3, at);
            insert.executeUpdate();
            return true;
        }
    }

    public Map<Achievement, Long> achievements(UUID player) throws SQLException {
        Map<Achievement, Long> unlocked = new LinkedHashMap<>();
        try (PreparedStatement statement = prepare("SELECT achievement, unlocked_at FROM coinflip_achievements WHERE player_uuid = ? "
                + "ORDER BY unlocked_at")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    try {
                        unlocked.put(Achievement.valueOf(result.getString(1)), result.getLong(2));
                    } catch (IllegalArgumentException ignored) {
                        // achievement removed in a later version
                    }
                }
            }
        }
        return unlocked;
    }

    // ── Preferences ──────────────────────────────────────────────────────────────

    public Optional<String> loadPreferences(UUID player) throws SQLException {
        try (PreparedStatement statement = prepare("SELECT data FROM coinflip_preferences WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.ofNullable(result.getString(1)) : Optional.empty();
            }
        }
    }

    public void savePreferences(UUID player, String data, long at) throws SQLException {
        try (PreparedStatement update = prepare("UPDATE coinflip_preferences SET data = ?, updated_at = ? WHERE player_uuid = ?")) {
            update.setString(1, data);
            update.setLong(2, at);
            update.setString(3, player.toString());
            if (update.executeUpdate() == 1) {
                return;
            }
        }
        try (PreparedStatement insert = prepare("INSERT INTO coinflip_preferences (player_uuid, data, updated_at) VALUES (?,?,?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, data);
            insert.setLong(3, at);
            insert.executeUpdate();
        }
    }

    // ── Audit ────────────────────────────────────────────────────────────────────

    /** One row in the append-only audit trail. */
    public record AuditRow(String id, long at, String actor, String action, String matchId, String detail) {
    }

    public void audit(String actor, String action, String matchId, String detail, long at) throws SQLException {
        try (PreparedStatement statement = prepare("INSERT INTO coinflip_audit (id, at, actor, action, match_id, detail) VALUES (?,?,?,?,?,?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setLong(2, at);
            statement.setString(3, truncate(actor, 64));
            statement.setString(4, truncate(action, 32));
            statement.setString(5, matchId);
            statement.setString(6, truncate(detail, 1000));
            statement.executeUpdate();
        }
    }

    public List<AuditRow> auditLog(String matchId, int limit) throws SQLException {
        String sql = matchId == null
                ? "SELECT * FROM coinflip_audit ORDER BY at DESC, id DESC LIMIT ?"
                : "SELECT * FROM coinflip_audit WHERE match_id = ? ORDER BY at DESC, id DESC LIMIT ?";
        List<AuditRow> rows = new ArrayList<>();
        try (PreparedStatement statement = prepare(sql)) {
            int index = 1;
            if (matchId != null) {
                statement.setString(index++, matchId);
            }
            statement.setInt(index, Math.max(1, Math.min(200, limit)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new AuditRow(result.getString("id"), result.getLong("at"), result.getString("actor"),
                            result.getString("action"), result.getString("match_id"), result.getString("detail")));
                }
            }
        }
        return rows;
    }

    // ── Retention ────────────────────────────────────────────────────────────────

    /**
     * Deletes finished matches older than {@code cutoff} whose every transaction is settled (applied or void).
     * Matches with a pending claim or an unknown transaction are never deleted.
     */
    public int pruneHistory(long cutoff) throws SQLException {
        String settled = "SELECT m.id FROM coinflip_matches m WHERE m.state IN ('COMPLETED','CANCELLED','EXPIRED') AND m.ended_at > 0 "
                + "AND m.ended_at < ? AND NOT EXISTS (SELECT 1 FROM coinflip_transactions t WHERE t.match_id = m.id "
                + "AND t.state NOT IN ('APPLIED','VOID')) LIMIT 500";
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = prepare(settled)) {
            statement.setLong(1, cutoff);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getString(1));
                }
            }
        }
        for (String id : ids) {
            try (PreparedStatement deleteTx = prepare("DELETE FROM coinflip_transactions WHERE match_id = ?");
                 PreparedStatement deleteMatch = prepare("DELETE FROM coinflip_matches WHERE id = ?")) {
                deleteTx.setString(1, id);
                deleteTx.executeUpdate();
                deleteMatch.setString(1, id);
                deleteMatch.executeUpdate();
            }
        }
        return ids.size();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private PreparedStatement prepare(String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(queryTimeoutSeconds);
        return statement;
    }

    private static void requireOne(int updated, String conflict) {
        if (updated != 1) {
            throw new CoinFlipStorageException(CoinFlipStorageException.Reason.CONFLICT, conflict);
        }
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private static UUID uuid(String raw) {
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean isUniqueViolation(SQLException exception) {
        String state = exception.getSQLState();
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        return "23000".equals(state) || "23505".equals(state) || message.contains("unique") || message.contains("duplicate");
    }
}
