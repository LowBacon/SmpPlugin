package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.commands.FeatureCommandExecutor;
import com.bx.smpPlugin.halloween.HalloweenCommand;
import com.bx.smpPlugin.halloween.HalloweenListener;
import com.bx.smpPlugin.halloween.HalloweenManager;
import com.bx.smpPlugin.halloween.TrickOrTreatCommand;
import com.bx.smpPlugin.engagement.Announcer;
import com.bx.smpPlugin.engagement.ChatGames;
import com.bx.smpPlugin.engagement.Challenges;
import com.bx.smpPlugin.engagement.DailyRewards;
import com.bx.smpPlugin.engagement.Giveaways;
import com.bx.smpPlugin.engagement.Mail;
import com.bx.smpPlugin.engagement.PendingItems;
import com.bx.smpPlugin.engagement.Polls;
import com.bx.smpPlugin.extras.ExtrasListener;
import com.bx.smpPlugin.extras.FunCommands;
import com.bx.smpPlugin.extras.UtilityCommands;
import com.bx.smpPlugin.managers.FeatureManager.Feature;
import com.bx.smpPlugin.moderation.AdminCommands;
import com.bx.smpPlugin.moderation.PermsCommand;
import com.bx.smpPlugin.moderation.Spy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Everything added in 3.0, started and stopped as one unit: the Halloween calendar, daily
 * rewards, challenges, events, mail, fun, utility and staff commands.
 *
 * <p>Each part is created in its own try block, so one broken part (a bad config, a missing
 * server API) is logged and skipped while the rest of the plugin keeps working.
 */
public final class V3Module implements Listener {

    private final SmpPlugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private PlayerStore store;
    private HalloweenManager halloween;
    private DailyRewards daily;
    private Challenges challenges;
    private com.bx.smpPlugin.world.BorderManager borders;

    /** Commands added in 3.0, for the help menu and the docs. */
    public static final List<String> COMMANDS = List.of(
            "halloween", "trickortreat", "daily", "challenges", "poll", "giveaway", "chatgame", "announcer", "mail",
            "slap", "highfive", "poke", "wave", "cheer", "dice", "coinflip", "rps", "rate", "firework", "sit",
            "craft", "anvil", "grindstone", "stonecutter", "loom", "smithing", "cartography", "back", "near",
            "seen", "coords", "suicide", "ptime", "pweather", "repair", "skull", "lore", "glow", "online", "uptime", "calc",
            "perms", "broadcast", "day", "night", "sun", "rain", "thunder", "smite", "butcher", "spawnmob", "more",
            "countdown", "performance", "speed", "whois", "spook", "socialspy", "commandspy",
            "guide", "sidebar", "border");

    public V3Module(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** The spawn and lobby borders, or null when that part could not start. */
    public com.bx.smpPlugin.world.BorderManager borders() {
        return borders;
    }

    /** Takes down the spawn's or the lobby's border, for when that spawn or lobby is deleted. */
    public void turnOffBorder(String zone) {
        if (borders != null && borders.zone(zone) != null && borders.zone(zone).enabled()) {
            borders.update(borders.zone(zone).withEnabled(false));
        }
    }

    public PlayerStore store() {
        return store;
    }

    public HalloweenManager halloween() {
        return halloween;
    }

    /**
     * PlaceholderAPI values for the 3.0 features (null when the name is not one of them):
     * daily_streak, daily_ready, halloween_opened, halloween_ready, halloween_countdown,
     * challenges_done, challenges_total.
     */
    public String placeholder(org.bukkit.OfflinePlayer offline, String params) {
        if (params == null || !(params.startsWith("daily_") || params.startsWith("halloween_") || params.startsWith("challenges_"))) {
            return null;
        }
        Player player = offline == null ? null : offline.getPlayer();
        java.util.UUID uuid = offline == null ? null : offline.getUniqueId();
        switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "daily_streak":
                return daily == null || uuid == null ? "0" : Integer.toString(daily.currentStreak(uuid));
            case "daily_ready":
                return daily != null && uuid != null && daily.isReady(uuid) ? "yes" : "no";
            case "halloween_opened":
                return halloween == null || uuid == null ? "0" : Integer.toString(halloween.openedCount(uuid));
            case "halloween_ready":
                return halloween == null || uuid == null ? "0" : Integer.toString(halloween.claimableCount(uuid));
            case "halloween_countdown":
                if (halloween == null) {
                    return "";
                }
                long left = halloween.rules().secondsUntilLastDay(halloween.now());
                return left > 0 ? Text.duration(left) : "Happy Halloween!";
            case "challenges_done":
                return challenges == null || player == null ? "0" : Integer.toString(challenges.doneToday(player));
            case "challenges_total":
                return challenges == null ? "0" : Integer.toString(challenges.perDay());
            default:
                return null;
        }
    }

    public void enable() {
        store = new PlayerStore(plugin);
        store.initialize();
        listen(this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            store.loadAsync(online.getUniqueId());
        }

        part("Halloween", () -> {
            halloween = new HalloweenManager(plugin, store);
            HalloweenCommand command = new HalloweenCommand(plugin, halloween);
            register("halloween", command, command, Feature.HALLOWEEN);
            register("trickortreat", new TrickOrTreatCommand(plugin, halloween, store), null, Feature.HALLOWEEN);
            listen(new HalloweenListener(plugin, halloween, store));
        });

        part("Daily rewards", () -> {
            daily = new DailyRewards(plugin, store);
            register("daily", daily, null, Feature.DAILY_REWARDS);
            listen(daily);
        });

        part("Challenges", () -> {
            challenges = new Challenges(plugin, store);
            register("challenges", challenges, null, Feature.CHALLENGES);
            task(plugin.getSpigotScheduler().runGlobalTimer(challenges::checkAll, 600L, 600L));
        });

        part("Events", () -> {
            Polls polls = new Polls(plugin);
            register("poll", polls, polls, Feature.EVENTS);
            onShutdown(polls::shutdown);
            Giveaways giveaways = new Giveaways(plugin);
            register("giveaway", giveaways, giveaways, Feature.EVENTS);
            onShutdown(giveaways::shutdown);
            ChatGames chatGames = new ChatGames(plugin);
            register("chatgame", chatGames, chatGames, Feature.EVENTS);
            listen(chatGames);
            task(plugin.getSpigotScheduler().runGlobalTimer(chatGames::autoTick, 1200L, 1200L));
            onShutdown(chatGames::stop);
        });

        part("Announcer", () -> {
            Announcer announcer = new Announcer(plugin);
            register("announcer", announcer, announcer, Feature.ANNOUNCER);
            task(plugin.getSpigotScheduler().runGlobalTimer(announcer::tick, 400L, 400L));
        });

        part("Mail", () -> {
            Mail mail = new Mail(plugin, store);
            register("mail", mail, mail, Feature.MAIL);
            listen(mail);
        });

        part("Fun commands", () -> {
            FunCommands fun = new FunCommands(plugin);
            for (String name : List.of("slap", "highfive", "poke", "wave", "cheer", "rps", "rate", "firework", "sit")) {
                register(name, fun, fun, Feature.FUN);
            }
            task(plugin.getSpigotScheduler().runGlobalTimer(fun::cleanSeats, 20L, 20L));
            onShutdown(fun::removeSeats);
        });

        part("Gambling", () -> {
            com.bx.smpPlugin.gambling.Gambling gambling = new com.bx.smpPlugin.gambling.Gambling(plugin);
            register("dice", gambling.dice(), gambling.dice(), Feature.GAMBLING);
            listen(gambling.dice());
            register("coinflip", gambling.coinFlip(), gambling.coinFlip(), Feature.COINFLIP);
            onShutdown(gambling::shutdown);
        });

        part("Guide and sidebar", () -> {
            com.bx.smpPlugin.display.GuideCommand guide = new com.bx.smpPlugin.display.GuideCommand(plugin);
            register("guide", guide, guide, Feature.GUIDE);
            com.bx.smpPlugin.display.SidebarCommand sidebar = new com.bx.smpPlugin.display.SidebarCommand(plugin);
            register("sidebar", sidebar, sidebar, Feature.SCOREBOARD);
        });

        part("Spawn and lobby borders", () -> {
            borders = new com.bx.smpPlugin.world.BorderManager(plugin);
            borders.load();
            listen(borders);
            com.bx.smpPlugin.world.BorderCommand command = new com.bx.smpPlugin.world.BorderCommand(plugin, borders);
            register("border", command, command, Feature.BORDERS);
            onShutdown(borders::shutdown);
        });

        part("Utility commands", () -> {
            ExtrasListener tracker = new ExtrasListener(plugin);
            listen(tracker);
            UtilityCommands utilities = new UtilityCommands(plugin, tracker);
            for (String name : List.of("craft", "anvil", "grindstone", "stonecutter", "loom", "smithing", "cartography",
                    "back", "near", "seen", "coords", "suicide", "ptime", "pweather", "repair", "skull", "lore",
                    "glow", "online", "uptime", "calc")) {
                register(name, utilities, utilities, Feature.UTILITIES);
            }
        });

        part("Staff tools", () -> {
            AdminCommands admin = new AdminCommands(plugin);
            for (String name : List.of("broadcast", "day", "night", "sun", "rain", "thunder", "smite", "butcher",
                    "spawnmob", "more", "performance", "speed", "whois", "spook")) {
                register(name, admin, admin, Feature.STAFF_EXTRAS);
            }
            register("countdown", admin, admin, Feature.EVENTS);
            onShutdown(admin::shutdown);
            Spy spy = new Spy(plugin, store);
            register("socialspy", spy, null, Feature.STAFF_EXTRAS);
            register("commandspy", spy, null, Feature.STAFF_EXTRAS);
            listen(spy);
            PermsCommand perms = new PermsCommand(plugin);
            register("perms", perms, perms, Feature.STAFF_EXTRAS);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline() && store.isLoaded(player.getUniqueId())) {
                PendingItems.deliver(plugin, store, player);
            }
        }, 40L);
    }

    /** Called by /smpplugin reload after the config files were read again. */
    public void reload() {
        if (borders != null) {
            borders.load();
        }
    }

    public void disable() {
        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "A 3.0 feature did not shut down cleanly", exception);
            }
        }
        shutdownHooks.clear();
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    // ------------------------------------------------------------------ wiring helpers

    /** Runs one part's setup, logging instead of failing the whole plugin. */
    void part(String name, Runnable setup) {
        try {
            setup.run();
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "3.0 feature '" + name + "' could not start and is turned off.", error);
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

    // ------------------------------------------------------------------ player data lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            store.loadBlocking(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Later than other quit handlers, so they can still read the values while saving.
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getSpigotScheduler().runGlobalLater(() -> {
            if (Bukkit.getPlayer(uuid) == null) {
                store.unload(uuid);
            }
        }, 20L);
    }
}
