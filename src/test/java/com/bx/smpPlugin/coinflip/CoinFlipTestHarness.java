package com.bx.smpPlugin.coinflip;

import com.bx.smpPlugin.coinflip.storage.CoinFlipRepository;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A CoinFlip service wired to a real SQLite file, a fake in-memory economy and a single-thread "server thread".
 * Tests call {@link #main(Callable)} to run code on that thread, exactly like the plugin does.
 */
final class CoinFlipTestHarness implements AutoCloseable {

    final Path databaseFile;
    final ExecutorService serverThread = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "test-main"));
    final AtomicLong clock = new AtomicLong(1_760_000_000_000L);
    final FakeEconomy economy = new FakeEconomy();
    final RecordingNotifier notifier = new RecordingNotifier();
    final ScriptedRandom random = new ScriptedRandom();
    final CoinFlipMetrics metrics = new CoinFlipMetrics();
    volatile CoinFlipConfig config;
    CoinFlipRepository repository;
    CoinFlipService service;

    CoinFlipTestHarness(Path folder, Consumer<YamlConfiguration> configure) throws Exception {
        this.databaseFile = folder.resolve("coinflip-test.db");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ECONOMY.MIN-STAKE", 1);
        yaml.set("ECONOMY.MAX-STAKE", 1_000_000);
        yaml.set("LIMITS.COOLDOWN-SECONDS", 0);
        configure.accept(yaml);
        this.config = CoinFlipConfig.from(yaml);
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, username TEXT, money REAL DEFAULT 0,"
                    + " money_cents BIGINT, money_version BIGINT DEFAULT 0)");
        }
        start();
    }

    /** Starts (or restarts, after {@link #crash()}) the repository and service on the same database file. */
    void start() throws Exception {
        if (repository != null) {
            repository.shutdown(5);
        }
        repository = new CoinFlipRepository(this::open, false, 10, 500, Logger.getLogger("CoinFlipTest"));
        repository.initialize().get(10, TimeUnit.SECONDS);
        service = new CoinFlipService(repository, economy, notifier, random, serverThread,
                (ticks, task) -> serverThread.execute(task), clock::get, ZoneId.of("UTC"), () -> config,
                player -> 10_000L, Logger.getLogger("CoinFlipTest"), metrics);
        await(main(() -> service.recoverAtStartup()));
    }

    /** Simulates a crash: the service and its memory vanish; only the database file remains. */
    void crash() {
        repository.shutdown(5);
    }

    Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=10000");
        }
        return connection;
    }

    UUID player(String name, long cents) throws Exception {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        economy.balances.put(id, cents);
        economy.online.add(id);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO players (uuid, username, money) VALUES (?,?,?)")) {
            statement.setString(1, id.toString());
            statement.setString(2, name);
            statement.setDouble(3, cents / 100D);
            statement.executeUpdate();
        }
        // Preferences load asynchronously on join, as in the plugin.
        await(main(() -> service.loadPreferences(id)));
        return id;
    }

    double storedMoney(UUID player) throws SQLException {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("SELECT money FROM players WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getDouble(1) : Double.NaN;
            }
        }
    }

    List<String> rows(String sql) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(i > 1 ? "|" : "").append(result.getString(i));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    void execute(String sql) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    <T> T main(Callable<T> work) throws Exception {
        return serverThread.submit(work).get(10, TimeUnit.SECONDS);
    }

    static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    /** Waits until every queued server-thread task and storage task has run. */
    void settleQueues() throws Exception {
        for (int i = 0; i < 20; i++) {
            main(() -> null);
            Thread.sleep(15);
            if (repository.queued() == 0) {
                main(() -> null);
            }
        }
    }

    @Override
    public void close() {
        repository.shutdown(5);
        serverThread.shutdownNow();
    }

    /** In-memory balances for online players, like the server economy. */
    static final class FakeEconomy implements EconomyGateway {
        final Map<UUID, Long> balances = new HashMap<>();
        /** Grows with every change, like PlayerData's balance version. */
        final Map<UUID, Long> versions = new HashMap<>();
        final Set<UUID> online = new HashSet<>();
        volatile boolean failWithdrawals;

        @Override
        public boolean isOnline(UUID player) {
            return online.contains(player);
        }

        @Override
        public long balanceCents(UUID player) {
            return balances.getOrDefault(player, 0L);
        }

        @Override
        public boolean withdraw(UUID player, long cents) {
            long balance = balanceCents(player);
            if (failWithdrawals || !online.contains(player) || balance < cents) {
                return false;
            }
            balances.put(player, balance - cents);
            versions.merge(player, 1L, Long::sum);
            return true;
        }

        @Override
        public boolean deposit(UUID player, long cents) {
            if (!online.contains(player)) {
                return false;
            }
            balances.merge(player, cents, Long::sum);
            versions.merge(player, 1L, Long::sum);
            return true;
        }

        @Override
        public Optional<StoredBalance> storedBalance(UUID player) {
            return balances.containsKey(player)
                    ? Optional.of(new StoredBalance(balances.get(player), versions.getOrDefault(player, 0L)))
                    : Optional.empty();
        }
    }

    /** Records what players and staff would have been told. */
    static final class RecordingNotifier implements CoinFlipNotifier {
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        final List<String> staff = Collections.synchronizedList(new ArrayList<>());
        final List<CoinFlipMatch> completed = Collections.synchronizedList(new ArrayList<>());
        final List<Achievement> achievements = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void send(UUID player, String key, Map<String, String> placeholders) {
            messages.add(player + ":" + key);
        }

        @Override
        public void challengeReceived(CoinFlipMatch match) {
            messages.add(match.inviteeId() + ":challenge-received");
        }

        @Override
        public long beginReveal(CoinFlipMatch match) {
            return 0L;
        }

        @Override
        public void matchCompleted(CoinFlipMatch match, boolean winnerPaidNow) {
            completed.add(match);
        }

        @Override
        public void achievementsUnlocked(UUID player, List<Achievement> unlocked) {
            achievements.addAll(unlocked);
        }

        @Override
        public void staffAlert(String message) {
            staff.add(message);
        }

        @Override
        public void matchesChanged() {
        }
    }

    /** Returns queued results first, then alternates; outcome IDs are unique counters. */
    static final class ScriptedRandom implements RandomSource {
        final Deque<CoinSide> next = new ArrayDeque<>();
        private long counter;
        private boolean toggle;

        @Override
        public synchronized CoinSide flip() {
            if (!next.isEmpty()) {
                return next.removeFirst();
            }
            toggle = !toggle;
            return toggle ? CoinSide.HEADS : CoinSide.TAILS;
        }

        @Override
        public synchronized String newOutcomeId() {
            counter++;
            return String.format("%024x", counter * 0x9E3779B97F4A7C15L & Long.MAX_VALUE);
        }
    }
}
