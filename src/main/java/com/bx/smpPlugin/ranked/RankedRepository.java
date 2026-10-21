package com.bx.smpPlugin.ranked;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reads and writes the ranked tables ({@code ranked_ratings}, {@code ranked_matches}, created by
 * database migration 3). Every method blocks, so callers run them on the database thread.
 */
public final class RankedRepository {

    /** One player's ranked record. */
    public record Profile(UUID player, String name, int rating, int peak, int wins, int losses, int draws,
                          int games, long updatedAt) {

        public static Profile fresh(UUID player, String name, int startRating) {
            return new Profile(player, name, startRating, startRating, 0, 0, 0, 0, 0L);
        }

        public Profile afterMatch(String newName, int newRating, Elo.Outcome outcomeForThisPlayer, long now) {
            int w = wins + (outcomeForThisPlayer == Elo.Outcome.PLAYER_ONE_WINS ? 1 : 0);
            int l = losses + (outcomeForThisPlayer == Elo.Outcome.PLAYER_TWO_WINS ? 1 : 0);
            int d = draws + (outcomeForThisPlayer == Elo.Outcome.DRAW ? 1 : 0);
            return new Profile(player, newName == null ? name : newName, newRating, Math.max(peak, newRating),
                    w, l, d, games + 1, now);
        }

        public Profile withRating(int newRating, long now) {
            return new Profile(player, name, newRating, Math.max(peak, newRating), wins, losses, draws, games, now);
        }
    }

    /** One finished ranked match, newest first in history. */
    public record MatchRow(long duelMatchId, UUID playerOne, String playerOneName, UUID playerTwo, String playerTwoName,
                           UUID winner, String endReason, boolean rated, String unratedReason,
                           int playerOneBefore, int playerOneAfter, int playerTwoBefore, int playerTwoAfter,
                           long durationSeconds, long endedAt) {
    }

    private final Supplier<Connection> connection;

    public RankedRepository(Supplier<Connection> connection) {
        this.connection = connection;
    }

    public Optional<Profile> load(UUID player) {
        try (PreparedStatement statement = connection.get().prepareStatement(
                "SELECT player_uuid, player_name, rating, peak_rating, wins, losses, draws, games, updated_at"
                        + " FROM ranked_ratings WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load the ranked rating of " + player, exception);
        }
    }

    public void save(Profile profile) {
        try (PreparedStatement statement = connection.get().prepareStatement(
                "REPLACE INTO ranked_ratings (player_uuid, player_name, rating, peak_rating, wins, losses, draws, games,"
                        + " updated_at) VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, profile.player().toString());
            statement.setString(2, profile.name());
            statement.setInt(3, profile.rating());
            statement.setInt(4, profile.peak());
            statement.setInt(5, profile.wins());
            statement.setInt(6, profile.losses());
            statement.setInt(7, profile.draws());
            statement.setInt(8, profile.games());
            statement.setLong(9, profile.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save the ranked rating of " + profile.player(), exception);
        }
    }

    public void delete(UUID player) {
        try (PreparedStatement statement = connection.get().prepareStatement(
                "DELETE FROM ranked_ratings WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not reset the ranked rating of " + player, exception);
        }
    }

    /** The best players who have played at least one game, highest rating first. */
    public List<Profile> top(int limit) {
        List<Profile> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.get().prepareStatement(
                "SELECT player_uuid, player_name, rating, peak_rating, wins, losses, draws, games, updated_at"
                        + " FROM ranked_ratings WHERE games > 0 ORDER BY rating DESC, games DESC, player_name ASC LIMIT ?")) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(read(result));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read the ranked leaderboard", exception);
        }
        return rows;
    }

    public void insertMatch(MatchRow row) {
        try (PreparedStatement statement = connection.get().prepareStatement(
                "INSERT INTO ranked_matches (duel_match_id, player_one_uuid, player_one_name, player_two_uuid,"
                        + " player_two_name, winner_uuid, end_reason, rated, unrated_reason, player_one_before,"
                        + " player_one_after, player_two_before, player_two_after, duration_seconds, ended_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setLong(1, row.duelMatchId());
            statement.setString(2, row.playerOne().toString());
            statement.setString(3, row.playerOneName());
            statement.setString(4, row.playerTwo().toString());
            statement.setString(5, row.playerTwoName());
            statement.setString(6, row.winner() == null ? null : row.winner().toString());
            statement.setString(7, row.endReason());
            statement.setInt(8, row.rated() ? 1 : 0);
            statement.setString(9, row.unratedReason());
            statement.setInt(10, row.playerOneBefore());
            statement.setInt(11, row.playerOneAfter());
            statement.setInt(12, row.playerTwoBefore());
            statement.setInt(13, row.playerTwoAfter());
            statement.setLong(14, row.durationSeconds());
            statement.setLong(15, row.endedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not record ranked match " + row.duelMatchId(), exception);
        }
    }

    public List<MatchRow> history(UUID player, int limit) {
        List<MatchRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.get().prepareStatement(
                "SELECT duel_match_id, player_one_uuid, player_one_name, player_two_uuid, player_two_name, winner_uuid,"
                        + " end_reason, rated, unrated_reason, player_one_before, player_one_after, player_two_before,"
                        + " player_two_after, duration_seconds, ended_at FROM ranked_matches"
                        + " WHERE player_one_uuid = ? OR player_two_uuid = ? ORDER BY ended_at DESC, ranked_match_id DESC LIMIT ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new MatchRow(result.getLong(1), UUID.fromString(result.getString(2)), result.getString(3),
                            UUID.fromString(result.getString(4)), result.getString(5), uuidOrNull(result.getString(6)),
                            result.getString(7), result.getInt(8) == 1, result.getString(9), result.getInt(10),
                            result.getInt(11), result.getInt(12), result.getInt(13), result.getLong(14), result.getLong(15)));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read the ranked history of " + player, exception);
        }
        return rows;
    }

    /** Two players who met in a ranked match, and when it ended. */
    public record PlayedPair(UUID first, UUID second, long endedAt) {
    }

    /** Every pair that played since {@code sinceMillis}, oldest first, to rebuild the anti-boost window. */
    public List<PlayedPair> pairsSince(long sinceMillis) {
        List<PlayedPair> pairs = new ArrayList<>();
        try (PreparedStatement statement = connection.get().prepareStatement(
                "SELECT player_one_uuid, player_two_uuid, ended_at FROM ranked_matches WHERE ended_at >= ?"
                        + " ORDER BY ended_at ASC")) {
            statement.setLong(1, sinceMillis);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    pairs.add(new PlayedPair(UUID.fromString(result.getString(1)), UUID.fromString(result.getString(2)),
                            result.getLong(3)));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read recent ranked matches", exception);
        }
        return pairs;
    }

    private static Profile read(ResultSet result) throws SQLException {
        return new Profile(UUID.fromString(result.getString(1)), result.getString(2), result.getInt(3), result.getInt(4),
                result.getInt(5), result.getInt(6), result.getInt(7), result.getInt(8), result.getLong(9));
    }

    private static UUID uuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
