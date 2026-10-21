package com.bx.smpPlugin.ranked;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.ConfigManager;
import com.bx.smpPlugin.managers.DatabaseManager;
import com.bx.smpPlugin.managers.DuelManager;
import com.bx.smpPlugin.models.DuelMatch;
import com.bx.smpPlugin.storage.SchemaMigrations;
import com.bx.smpPlugin.utils.SpigotScheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A ranked duel's result, from the duel system's callback to the ratings stored in the database.
 * The database is a real in-memory SQLite one; the scheduler runs everything at once.
 */
class RankedManagerTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private Connection connection;
    private MockedStatic<Bukkit> bukkit;
    private RankedManager ranked;
    private RankedRepository repository;
    private Consumer<DuelManager.FinishedMatch> finish;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE players (uuid TEXT PRIMARY KEY)");
        }
        SchemaMigrations.migrate(connection, null);
        repository = new RankedRepository(() -> connection);

        Player playerA = player(alice, "Alice");
        Player playerB = player(bob, "Bob");
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());
        bukkit.when(() -> Bukkit.getPlayer(alice)).thenReturn(playerA);
        bukkit.when(() -> Bukkit.getPlayer(bob)).thenReturn(playerB);

        SmpPlugin plugin = mock(SmpPlugin.class);
        DatabaseManager database = mock(DatabaseManager.class);
        when(database.getConnection()).thenReturn(connection);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(database).executeAsync(any());
        when(database.supplyAsync(any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(((Supplier<Object>) invocation.getArgument(0)).get()));
        SpigotScheduler scheduler = mock(SpigotScheduler.class);
        when(scheduler.runGlobal(any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        when(scheduler.runGlobalTimer(any(), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRanked()).thenReturn(bundledRanked());
        DuelManager duels = mock(DuelManager.class);
        when(duels.isEnabled()).thenReturn(true);

        when(plugin.getDatabaseManager()).thenReturn(database);
        when(plugin.getSpigotScheduler()).thenReturn(scheduler);
        when(plugin.getConfigManager()).thenReturn(config);
        when(plugin.getDuelManager()).thenReturn(duels);

        ranked = new RankedManager(plugin);
        ranked.start();
        ArgumentCaptor<Consumer<DuelManager.FinishedMatch>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(duels).setMatchFinishListener(captor.capture());
        finish = captor.getValue();

        for (Player player : List.of(playerA, playerB)) {
            PlayerJoinEvent join = mock(PlayerJoinEvent.class);
            when(join.getPlayer()).thenReturn(player);
            ranked.onJoin(join);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        bukkit.close();
        connection.close();
    }

    private static Player player(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static YamlConfiguration bundledRanked() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                RankedManagerTest.class.getClassLoader().getResourceAsStream("ranked.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private void play(long id, UUID winner, long seconds, boolean cancelled) {
        UUID loser = winner == null ? null : winner.equals(alice) ? bob : alice;
        finish.accept(new DuelManager.FinishedMatch(id, DuelMatch.MatchType.RANKED, alice, "Alice", bob, "Bob",
                winner, loser, winner == null ? "TIMEOUT" : "DEATH", cancelled, seconds));
    }

    @Test
    void aWinChangesBothRatingsAndIsSaved() {
        assertEquals(1000, ranked.profile(alice).rating(), "loaded on join");
        play(1L, alice, 60L, false);
        // Both are new (provisional K 40): 1000 vs 1000, so +20 and -20.
        assertEquals(1020, ranked.profile(alice).rating());
        assertEquals(980, ranked.profile(bob).rating());
        assertEquals(1020, repository.load(alice).orElseThrow().rating());
        assertEquals(1, repository.load(alice).orElseThrow().wins());
        assertEquals(1, repository.load(bob).orElseThrow().losses());
        List<RankedRepository.MatchRow> history = repository.history(alice, 10);
        assertEquals(1, history.size());
        assertTrue(history.get(0).rated());
        assertEquals("1020", ranked.placeholder(alice, "elo"));
    }

    @Test
    void quickMatchesAndRepeatedPairsDoNotCount() {
        play(1L, alice, 5L, false);
        assertEquals(1000, ranked.profile(alice).rating(), "shorter than MIN-MATCH-SECONDS");
        assertFalse(repository.history(alice, 10).get(0).rated());

        // MAX-GAMES-PER-PAIR is 3; the quick match above already counts as one game together.
        play(2L, alice, 60L, false);
        play(3L, bob, 60L, false);
        int before = ranked.profile(alice).rating();
        play(4L, alice, 60L, false);
        assertEquals(before, ranked.profile(alice).rating(), "the fourth game in the window is unrated");
        RankedRepository.MatchRow last = repository.history(alice, 10).get(0);
        assertFalse(last.rated());
        assertEquals("too many games against each other", last.unratedReason());
    }

    @Test
    void drawsAndCancelledMatches() {
        play(1L, null, 120L, false);
        assertEquals(1000, ranked.profile(alice).rating(), "a draw between equals changes nothing");
        assertEquals(1, repository.load(alice).orElseThrow().draws());
        play(2L, alice, 120L, true);
        RankedRepository.MatchRow cancelled = repository.history(alice, 10).get(0);
        assertFalse(cancelled.rated());
        assertEquals("cancelled", cancelled.unratedReason());
        assertEquals(1, repository.load(alice).orElseThrow().games(), "a cancelled match is not a game");
    }

    @Test
    void casualDuelsAreIgnored() {
        finish.accept(new DuelManager.FinishedMatch(9L, DuelMatch.MatchType.QUEUE, alice, "Alice", bob, "Bob",
                alice, bob, "DEATH", false, 60L));
        assertEquals(1000, ranked.profile(alice).rating());
        assertTrue(repository.history(alice, 10).isEmpty());
    }
}
