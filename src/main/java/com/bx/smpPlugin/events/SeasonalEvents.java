package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.halloween.HalloweenCommand;
import com.bx.smpPlugin.halloween.HalloweenManager;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The seasonal events as one unit for the 4.0 module: builds every event from events.yml, and
 * hands out the commands (/event, /advent, /easter) and placeholders.
 */
public final class SeasonalEvents {

    /** Event ids that are built in; a custom event may not use one of these. */
    private static final Set<String> BUILT_IN = Set.of("halloween", "advent", "christmas", "easter", "summer");

    private final SmpPlugin plugin;
    private final EventManager manager;
    private final AdventEvent advent;
    private final EasterEvent easter;

    public SeasonalEvents(SmpPlugin plugin, PlayerStore store, HalloweenManager halloween) {
        this.plugin = plugin;
        this.manager = new EventManager(plugin);
        if (halloween != null) {
            manager.register(new HalloweenEvent(plugin, manager, halloween));
        }
        this.advent = new AdventEvent(plugin, manager, store);
        manager.register(advent);
        manager.register(new ChristmasEvent(plugin, manager));
        this.easter = new EasterEvent(plugin, manager);
        manager.register(easter);
        manager.register(new PlaytimeEvent(plugin, manager, "summer", "EVENTS.SUMMER", "&eSummer Splash",
                "Rewards for playing during the summer, and lucky catches while fishing.", Material.TROPICAL_FISH));
        registerCustomEvents();
    }

    /** Every section under CUSTOM-EVENTS becomes a playtime-reward event with its own id. */
    private void registerCustomEvents() {
        ConfigurationSection custom = manager.config().getConfigurationSection("CUSTOM-EVENTS");
        if (custom == null) {
            return;
        }
        for (String key : custom.getKeys(false)) {
            String id = key.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_-]{1,32}") || BUILT_IN.contains(id)) {
                plugin.getLogger().warning("events.yml CUSTOM-EVENTS." + key + " was skipped: use a short name of"
                        + " letters, digits, - and _ that is not one of " + BUILT_IN + ".");
                continue;
            }
            manager.register(new PlaytimeEvent(plugin, manager, id, "CUSTOM-EVENTS." + key,
                    "&e" + key, "A custom event.", Material.FIREWORK_ROCKET));
        }
    }

    public EventManager manager() {
        return manager;
    }

    public void start() {
        manager.start();
    }

    public void stop() {
        manager.stop();
    }

    public void reload() {
        manager.reload();
    }

    public EventCommand eventCommand() {
        return new EventCommand(plugin, manager);
    }

    /** /advent: the same command as /halloween, for the Advent Calendar. */
    public HalloweenCommand adventCommand() {
        return new HalloweenCommand(plugin, advent.calendar());
    }

    public EasterCommand easterCommand() {
        return new EasterCommand(plugin, easter);
    }

    /**
     * %smp_events_active% (names of the running events), %smp_event_&lt;id&gt;% (running or not)
     * and the Advent values %smp_event_advent_opened% / %smp_event_advent_ready%.
     */
    public String placeholder(OfflinePlayer player, String key) {
        if (key.equals("events_active") || key.equals("events")) {
            String names = manager.all().stream().filter(SeasonalEvent::isRunning)
                    .map(SeasonalEvent::displayName).collect(Collectors.joining("&7, "));
            return names.isEmpty() ? "None" : names;
        }
        if (key.equals("event_advent_opened")) {
            return player == null ? "0" : Integer.toString(advent.calendar().openedCount(player.getUniqueId()));
        }
        if (key.equals("event_advent_ready")) {
            return player == null ? "0" : Integer.toString(advent.calendar().claimableCount(player.getUniqueId()));
        }
        if (key.equals("event_easter_found")) {
            return player == null ? "0" : Integer.toString(easter.found(player.getUniqueId()).size());
        }
        if (key.startsWith("event_")) {
            return manager.get(key.substring("event_".length()))
                    .map(event -> event.isRunning() ? "running" : "off").orElse(null);
        }
        return null;
    }
}
