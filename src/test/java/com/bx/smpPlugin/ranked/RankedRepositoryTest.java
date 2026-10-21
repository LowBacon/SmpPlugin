package com.bx.smpPlugin.ranked;

import com.bx.smpPlugin.storage.SchemaMigrations;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedRepositoryTest {

    @Test
    void ratingsAndMatchesSurviveARoundTrip() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY)");
            }
            SchemaMigrations.migrate(connection, null);
            RankedRepository repository = new RankedRepository(() -> connection);
            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();
            UUID newcomer = UUID.randomUUID();

            assertTrue(repository.load(alice).isEmpty());
            RankedRepository.Profile aliceAfter = RankedRepository.Profile.fresh(alice, "Alice", 1000)
                    .afterMatch("Alice", 1020, Elo.Outcome.PLAYER_ONE_WINS, 5L);
            RankedRepository.Profile bobAfter = RankedRepository.Profile.fresh(bob, "Bob", 1000)
                    .afterMatch("Bob", 980, Elo.Outcome.PLAYER_TWO_WINS, 5L);
            repository.save(aliceAfter);
            repository.save(bobAfter);
            repository.save(RankedRepository.Profile.fresh(newcomer, "New", 1000));

            RankedRepository.Profile loaded = repository.load(alice).orElseThrow();
            assertEquals(1020, loaded.rating());
            assertEquals(1020, loaded.peak());
            assertEquals(1, loaded.wins());
            assertEquals(1, loaded.games());

            List<RankedRepository.Profile> top = repository.top(10);
            assertEquals(List.of("Alice", "Bob"), top.stream().map(RankedRepository.Profile::name).toList(),
                    "players without a game are not on the leaderboard");

            repository.insertMatch(new RankedRepository.MatchRow(7L, alice, "Alice", bob, "Bob", alice, "DEATH", true,
                    null, 1000, 1020, 1000, 980, 42L, 10L));
            repository.insertMatch(new RankedRepository.MatchRow(8L, bob, "Bob", alice, "Alice", null, "TIMEOUT", false,
                    "too many games against each other", 980, 980, 1020, 1020, 300L, 20L));
            List<RankedRepository.MatchRow> history = repository.history(alice, 10);
            assertEquals(2, history.size());
            assertEquals(8L, history.get(0).duelMatchId(), "newest first");
            assertFalse(history.get(0).rated());
            assertEquals(2, repository.pairsSince(0L).size());
            assertEquals(1, repository.pairsSince(15L).size());

            repository.delete(alice);
            assertTrue(repository.load(alice).isEmpty());
        }
    }

    @Test
    void peakRatingNeverGoesDown() {
        RankedRepository.Profile profile = RankedRepository.Profile.fresh(UUID.randomUUID(), "A", 1000)
                .afterMatch("A", 1100, Elo.Outcome.PLAYER_ONE_WINS, 1L)
                .afterMatch("A", 1050, Elo.Outcome.PLAYER_TWO_WINS, 2L);
        assertEquals(1050, profile.rating());
        assertEquals(1100, profile.peak());
        assertEquals(1, profile.wins());
        assertEquals(1, profile.losses());
        assertEquals(2, profile.games());
        assertEquals(1100, profile.withRating(900, 3L).peak());
    }
}
