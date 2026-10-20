package com.bx.smpPlugin.testsupport;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.managers.ConfigManager;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.DatabaseManager;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.managers.PlayerDataManager;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A plugin with a real DatabaseManager (in-memory SQLite behind the plugin's own thread-safe connection wrapper),
 * a real PlayerDataManager and a real EconomyManager, without a Minecraft server. Players are "online" when they
 * are in {@link #online}; their data is in the PlayerDataManager cache exactly as after a join.
 */
public final class EconomyHarness implements AutoCloseable {

    public final SmpPlugin plugin;
    public final DatabaseManager database;
    public final PlayerDataManager players;
    public final EconomyManager economy;
    public final YamlConfiguration config = new YamlConfiguration();
    public final Map<UUID, Player> online = new ConcurrentHashMap<>();
    /** The plugin's data folder, a fresh temporary directory. */
    public final File dataFolder;
    private final Connection raw;

    public EconomyHarness() throws Exception {
        plugin = newPlugin();
        set(JavaPlugin.class, plugin, "logger", Logger.getLogger("EconomyHarness"));
        ConfigManager configManager = new ConfigManager(plugin);
        set(ConfigManager.class, configManager, "config", config);
        set(ConfigManager.class, configManager, "database", new YamlConfiguration());
        set(ConfigManager.class, configManager, "menus", new YamlConfiguration());
        set(SmpPlugin.class, plugin, "configManager", configManager);
        set(SmpPlugin.class, plugin, "currencyManager", new CurrencyManager(plugin));
        dataFolder = Files.createTempDirectory("smpplugin-harness").toFile();
        set(JavaPlugin.class, plugin, "dataFolder", dataFolder);
        installServer();

        raw = DriverManager.getConnection("jdbc:sqlite::memory:");
        database = new DatabaseManager(plugin);
        Method wrap = DatabaseManager.class.getDeclaredMethod("wrapConnection", Connection.class);
        wrap.setAccessible(true);
        Connection wrapped = (Connection) wrap.invoke(database, raw);
        set(DatabaseManager.class, database, "rawConnection", raw);
        set(DatabaseManager.class, database, "connection", wrapped);
        invoke(database, "createTables");
        invoke(database, "ensurePlayerColumns");
        EconomyStore.createSchema(wrapped, database::executeSchema);
        set(DatabaseManager.class, database, "ready", true);
        set(SmpPlugin.class, plugin, "databaseManager", database);

        players = new PlayerDataManager(plugin);
        set(SmpPlugin.class, plugin, "playerDataManager", players);
        economy = new EconomyManager(plugin);
        set(SmpPlugin.class, plugin, "economyManager", economy);
    }

    /** A player who is online with {@code money} in memory and the same balance stored. */
    @SuppressWarnings("unchecked")
    public PlayerData addOnline(UUID uuid, String name, double money) throws Exception {
        PlayerData data = new PlayerData(uuid, name);
        data.setMoney(money);
        database.savePlayer(data);
        Field cacheField = PlayerDataManager.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        ((Map<UUID, PlayerData>) cacheField.get(players)).put(uuid, data);
        online.put(uuid, player(uuid, name));
        return data;
    }

    /** A player who is offline with a stored row. */
    public void addOffline(UUID uuid, String name, double money) {
        PlayerData data = new PlayerData(uuid, name);
        data.setMoney(money);
        database.savePlayer(data);
    }

    /** Waits until everything queued on the database writer so far has run. */
    public void drainWrites() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        database.executeAsync(latch::countDown);
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("database writer did not drain");
        }
    }

    public Connection connection() {
        return database.getConnection();
    }

    public double storedMoney(UUID uuid) throws SQLException {
        return queryDouble("SELECT money FROM players WHERE uuid = ?", uuid);
    }

    public long storedVersion(UUID uuid) throws SQLException {
        return (long) queryDouble("SELECT money_version FROM players WHERE uuid = ?", uuid);
    }

    public int ledgerRows(UUID uuid) throws SQLException {
        return (int) queryDouble("SELECT COUNT(*) FROM economy_ledger WHERE player_uuid = ?", uuid);
    }

    public long ledgerSum(UUID uuid) throws SQLException {
        return (long) queryDouble("SELECT COALESCE(SUM(amount_cents), 0) FROM economy_ledger WHERE player_uuid = ?", uuid);
    }

    private double queryDouble(String sql, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : Double.NaN;
            }
        }
    }

    @Override
    public void close() throws Exception {
        database.close();
        raw.close();
    }

    public static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static SmpPlugin newPlugin() throws Exception {
        java.lang.reflect.Constructor<Object> objectConstructor = Object.class.getConstructor();
        sun.reflect.ReflectionFactory factory = sun.reflect.ReflectionFactory.getReflectionFactory();
        java.lang.reflect.Constructor<?> constructor =
                factory.newConstructorForSerialization(SmpPlugin.class, objectConstructor);
        return (SmpPlugin) constructor.newInstance();
    }

    private void installServer() throws Exception {
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> args != null && args.length == 1 && args[0] instanceof UUID id ? online.get(id) : null;
                    case "getOfflinePlayer" -> args != null && args.length == 1 && args[0] instanceof UUID id
                            ? offlinePlayer(id) : null;
                    case "getLogger" -> Logger.getLogger("EconomyHarnessServer");
                    case "isPrimaryThread" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "EconomyHarnessServer";
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
    }

    private OfflinePlayer offlinePlayer(UUID uuid) {
        Player live = online.get(uuid);
        return (OfflinePlayer) Proxy.newProxyInstance(EconomyHarness.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> live == null ? null : live.getName();
                    case "isOnline" -> live != null;
                    case "getPlayer" -> live;
                    case "hasPlayedBefore" -> true;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }

    /** A stand-in online player that only knows its id and name. */
    public static Player player(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(EconomyHarness.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "isOnline" -> true;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player[" + name + "]";
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }
}
