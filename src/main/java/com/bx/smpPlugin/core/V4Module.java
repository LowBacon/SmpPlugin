package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.commands.FeatureCommandExecutor;
import com.bx.smpPlugin.economy.EconomyCommand;
import com.bx.smpPlugin.economy.EconomyLedger;
import com.bx.smpPlugin.managers.DatabaseManager;
import com.bx.smpPlugin.managers.FeatureManager.Feature;
import com.bx.smpPlugin.storage.SchemaMigrations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Everything added in 4.0, started and stopped as one unit: database migrations, the economy
 * ledger and /economy, ranked PvP, seasonal events, survival rules and diagnostics.
 *
 * <p>Like {@link V3Module}, each part starts in its own try block and records whether it came up,
 * so /smp status can show a part that failed while the rest of the plugin keeps running. Every
 * listener and task a part creates is registered here and removed on disable.
 */
public final class V4Module {

    private final SmpPlugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    /** Part name to "running" or the reason it did not start, in start order. */
    private final Map<String, String> partStatus = new LinkedHashMap<>();
    private EconomyLedger ledger;
    private int schemaVersion;
    private com.bx.smpPlugin.ranked.RankedManager ranked;
    private boolean rankedRunning;
    private com.bx.smpPlugin.events.SeasonalEvents events;
    private boolean eventsRunning;
    private com.bx.smpPlugin.survival.SurvivalRules survivalRules;
    private boolean rulesRunning;

    public V4Module(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public EconomyLedger ledger() {
        return ledger;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * Each 4.0 part and whether it is running, for /smp status: "running", the reason it did not
     * start, or "switched off in /smp modules" for a part that started but whose module is off now.
     */
    public Map<String, String> partStatus() {
        Map<String, String> status = new LinkedHashMap<>(partStatus);
        markSwitchedOff(status, "Ranked PvP", Feature.RANKED);
        markSwitchedOff(status, "Seasonal events", Feature.SEASONAL_EVENTS);
        markSwitchedOff(status, "Survival rules", Feature.SURVIVAL_RULES);
        return Collections.unmodifiableMap(status);
    }

    private void markSwitchedOff(Map<String, String> status, String part, Feature feature) {
        if ("running".equals(status.get(part)) && !isRunning(feature)) {
            status.put(part, "switched off in /smp modules");
        }
    }

    public com.bx.smpPlugin.ranked.RankedManager ranked() {
        return ranked;
    }

    public com.bx.smpPlugin.events.SeasonalEvents events() {
        return events;
    }

    public com.bx.smpPlugin.survival.SurvivalRules survivalRules() {
        return survivalRules;
    }

    /** PlaceholderAPI and internal values for the 4.0 features (null when the name is not one of them). */
    public String placeholder(OfflinePlayer player, String key) {
        if ((key.equals("elo") || key.startsWith("ranked_")) && ranked != null) {
            return ranked.placeholder(player == null ? null : player.getUniqueId(), key);
        }
        if (key.startsWith("event") && events != null) {
            return events.placeholder(player, key);
        }
        return null;
    }

    public void enable() {
        boolean migrated = part("Database migrations", this::migrate);

        part("Economy ledger", () -> {
            if (!migrated) {
                throw new IllegalStateException("the economy_ledger table could not be created");
            }
            ledger = new EconomyLedger(plugin);
            ledger.start();
            onShutdown(ledger::stop);
            EconomyCommand command = new EconomyCommand(plugin, ledger);
            register("economy", command, command);
        });

        part("Ranked PvP", () -> {
            if (plugin.getDuelManager() == null) {
                throw new IllegalStateException("duels are not available");
            }
            ranked = new com.bx.smpPlugin.ranked.RankedManager(plugin);
            com.bx.smpPlugin.ranked.RankedCommand command = new com.bx.smpPlugin.ranked.RankedCommand(plugin, ranked);
            register("ranked", command, command, Feature.DUELS, Feature.RANKED);
            applyFeature(Feature.RANKED, plugin.getFeatureManager().isEnabled(Feature.RANKED));
            onShutdown(() -> applyFeature(Feature.RANKED, false));
        });

        part("Seasonal events", () -> {
            com.bx.smpPlugin.core.PlayerStore store = plugin.getV3Module() == null ? null : plugin.getV3Module().store();
            if (store == null) {
                throw new IllegalStateException("the player store did not start");
            }
            events = new com.bx.smpPlugin.events.SeasonalEvents(plugin, store, plugin.getV3Module().halloween());
            com.bx.smpPlugin.events.EventCommand eventCommand = events.eventCommand();
            register("event", eventCommand, eventCommand, Feature.SEASONAL_EVENTS);
            com.bx.smpPlugin.halloween.HalloweenCommand advent = events.adventCommand();
            register("advent", advent, advent, Feature.SEASONAL_EVENTS);
            com.bx.smpPlugin.events.EasterCommand easter = events.easterCommand();
            register("easter", easter, easter, Feature.SEASONAL_EVENTS);
            applyFeature(Feature.SEASONAL_EVENTS, plugin.getFeatureManager().isEnabled(Feature.SEASONAL_EVENTS));
            onShutdown(() -> applyFeature(Feature.SEASONAL_EVENTS, false));
        });

        part("Survival rules", () -> {
            survivalRules = new com.bx.smpPlugin.survival.SurvivalRules(plugin);
            applyFeature(Feature.SURVIVAL_RULES, plugin.getFeatureManager().isEnabled(Feature.SURVIVAL_RULES));
            onShutdown(() -> applyFeature(Feature.SURVIVAL_RULES, false));
        });

        part("Diagnostics", () -> {
            Diagnostics.gauge("cache.player-data", () -> plugin.getPlayerDataManager().getAll().size());
            Diagnostics.gauge("cache.offline-player-copies", () -> plugin.getPlayerDataManager().offlineCopyCount());
            Diagnostics.gauge("cache.leaderboard-rows", () -> plugin.getLeaderboardManager().cachedRowCount());
            Diagnostics.gauge("database.pending-writes", () -> plugin.getDatabaseManager().getPendingWriteCount());
            Diagnostics.gauge("scheduler.plugin-tasks", this::countPluginTasks);
            if (ranked != null) {
                Diagnostics.gauge("ranked.queue", () -> ranked.queueSize());
            }
            if (plugin.getV3Module() != null && plugin.getV3Module().store() != null) {
                Diagnostics.gauge("cache.v3-player-store", () -> plugin.getV3Module().store().loadedCount());
            }
            // A cheap health check of the database every minute: how long the queue and a query take.
            task(plugin.getSpigotScheduler().runGlobalTimer(this::probeDatabase, 100L, 1200L));
            if (plugin.getLeaderboardManager() != null) {
                plugin.getLeaderboardManager().refreshStoredRowsAsync();
            }
        });
    }

    /** Called by /smp reload after the config files were read again. */
    public void reload() {
        if (ranked != null) {
            ranked.reload();
        }
        if (events != null && eventsRunning) {
            events.reload();
        }
        if (survivalRules != null) {
            survivalRules.reload();
        }
    }

    /**
     * Starts or stops a 4.0 part when its feature is switched in /smp modules. Stopping removes the
     * part's listeners and tasks for real; player data stays where it is.
     */
    public void applyFeature(Feature feature, boolean enabled) {
        switch (feature) {
            case RANKED -> {
                if (ranked == null || enabled == rankedRunning) {
                    return;
                }
                if (enabled) {
                    ranked.start();
                    Bukkit.getPluginManager().registerEvents(ranked, plugin);
                } else {
                    HandlerList.unregisterAll(ranked);
                    ranked.stop();
                }
                rankedRunning = enabled;
            }
            case SEASONAL_EVENTS -> {
                if (events == null || enabled == eventsRunning) {
                    return;
                }
                if (enabled) {
                    events.start();
                } else {
                    events.stop();
                }
                eventsRunning = enabled;
            }
            case SURVIVAL_RULES -> {
                if (survivalRules == null || enabled == rulesRunning) {
                    return;
                }
                if (enabled) {
                    survivalRules.reload();
                    Bukkit.getPluginManager().registerEvents(survivalRules, plugin);
                } else {
                    HandlerList.unregisterAll(survivalRules);
                }
                rulesRunning = enabled;
            }
            default -> {
            }
        }
    }

    /** Whether a 4.0 part is running right now, for /smp status. */
    public boolean isRunning(Feature feature) {
        return switch (feature) {
            case RANKED -> rankedRunning;
            case SEASONAL_EVENTS -> eventsRunning;
            case SURVIVAL_RULES -> rulesRunning;
            default -> false;
        };
    }

    public void disable() {
        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "A 4.0 feature did not shut down cleanly", exception);
            }
        }
        shutdownHooks.clear();
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    // ------------------------------------------------------------------ parts

    private void migrate() {
        DatabaseManager database = plugin.getDatabaseManager();
        Connection connection = database == null ? null : database.getConnection();
        if (connection == null) {
            throw new IllegalStateException("there is no database connection");
        }
        try {
            List<Integer> ran = SchemaMigrations.migrate(connection, database::executeSchema);
            schemaVersion = SchemaMigrations.currentVersion(connection);
            if (!ran.isEmpty()) {
                plugin.getLogger().info("Database migrated to schema version " + schemaVersion + " (ran " + ran + ").");
            }
            List<String> missing = SchemaMigrations.missingTables(connection);
            if (!missing.isEmpty()) {
                plugin.getLogger().warning("Database schema check: these tables are missing: " + missing);
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private void probeDatabase() {
        DatabaseManager database = plugin.getDatabaseManager();
        if (database == null) {
            return;
        }
        long queued = System.nanoTime();
        database.executeAsync(() -> {
            long started = System.nanoTime();
            String error = null;
            try (Statement statement = database.getConnection().createStatement();
                 ResultSet ignored = statement.executeQuery("SELECT 1")) {
                ignored.next();
            } catch (Exception exception) {
                error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            long finished = System.nanoTime();
            Diagnostics.databaseProbe((started - queued) / 1_000_000D, (finished - started) / 1_000_000D, error);
        });
    }

    private int countPluginTasks() {
        int count = 0;
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            if (task.getOwner() == plugin) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ wiring helpers

    /** Runs one part's setup; returns false (and records why) instead of failing the plugin. */
    boolean part(String name, Runnable setup) {
        try {
            setup.run();
            partStatus.put(name, "running");
            return true;
        } catch (RuntimeException | LinkageError error) {
            partStatus.put(name, "off: " + error.getMessage());
            plugin.getLogger().log(Level.WARNING, "4.0 feature '" + name + "' could not start and is turned off.", error);
            return false;
        }
    }

    void listen(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    void task(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    void onShutdown(Runnable hook) {
        shutdownHooks.add(hook);
    }

    /** Binds a plugin.yml command, wrapped so a feature turned off in config answers politely. */
    void register(String name, CommandExecutor executor, TabCompleter completer, Feature... features) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        FeatureCommandExecutor wrapped = new FeatureCommandExecutor(plugin, executor, features);
        command.setExecutor(wrapped);
        if (completer != null) {
            command.setTabCompleter((sender, cmd, alias, args) -> {
                for (Feature feature : features) {
                    if (!plugin.getFeatureManager().isEnabled(feature)) {
                        return Collections.emptyList();
                    }
                }
                List<String> result = completer.onTabComplete(sender, cmd, alias, args);
                return result == null ? Collections.emptyList() : result;
            });
        }
    }
}
