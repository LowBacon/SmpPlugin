package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

/**
 * One seasonal event (Advent, Christmas, Easter, Summer, a custom one).
 *
 * <p>The {@link EventManager} starts an event when its time comes (or staff switch it on) and stops
 * it afterwards. Everything an event does while it runs, its listeners and repeating tasks, is
 * registered through {@link #listen} and {@link #task}, so stopping it removes all of it. Player
 * progress lives in the shared player store and is never touched by stopping, so switching an
 * event off and on again neither loses progress nor hands out a reward twice.
 */
public abstract class SeasonalEvent {

    protected final SmpPlugin plugin;
    protected final EventManager manager;
    private final String id;
    private final String configPath;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean running;

    protected SeasonalEvent(SmpPlugin plugin, EventManager manager, String id) {
        this(plugin, manager, id, "EVENTS." + id.toUpperCase(Locale.ROOT));
    }

    /** An event whose settings are at {@code configPath} in events.yml (custom events). */
    protected SeasonalEvent(SmpPlugin plugin, EventManager manager, String id, String configPath) {
        this.plugin = plugin;
        this.manager = manager;
        this.id = id.toLowerCase(Locale.ROOT);
        this.configPath = configPath;
    }

    public final String id() {
        return id;
    }

    /** This event's section of events.yml (an empty one when it is missing). */
    public ConfigurationSection config() {
        ConfigurationSection section = manager.config().getConfigurationSection(configPath);
        return section == null ? new MemoryConfiguration() : section;
    }

    public String displayName() {
        return config().getString("NAME", defaultName());
    }

    public String description() {
        return config().getString("DESCRIPTION", defaultDescription());
    }

    public Material icon() {
        String raw = config().getString("ICON");
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() ? defaultIcon() : material;
    }

    protected abstract String defaultName();

    protected abstract String defaultDescription();

    protected abstract Material defaultIcon();

    /** The window events.yml gives this event (START and END); empty when it has none. */
    public Optional<EventSchedule> schedule() {
        return EventSchedule.parse(config().getString("START"), config().getString("END"));
    }

    /** False when ENABLED: false in events.yml: the event never runs, whatever its dates. */
    public boolean isConfigured() {
        return config().getBoolean("ENABLED", true);
    }

    /** True for events the manager starts and stops; the Halloween event manages itself. */
    public boolean isManaged() {
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------------ lifecycle (manager only)

    final void start() {
        if (running) {
            return;
        }
        running = true;
        try {
            onStart();
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "Event '" + id + "' could not start", error);
            stop();
        }
    }

    final void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            onStop();
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Event '" + id + "' did not stop cleanly", error);
        }
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    /** Registers listeners and tasks. Runs when the event starts. */
    protected void onStart() {
    }

    /** Extra clean-up beyond listeners and tasks (which are removed automatically). */
    protected void onStop() {
    }

    /** Called after events.yml was read again; a running event is restarted by the manager. */
    protected void onReload() {
    }

    /** What happens when a player picks this event in /event: open its screen or explain it. */
    public void open(Player player) {
        player.sendMessage(com.bx.smpPlugin.utils.ColorUtils.toComponent("&7" + description()));
    }

    /** Lines about the player's own progress for /event info and the events menu. */
    public List<String> progress(Player player) {
        return List.of();
    }

    // ------------------------------------------------------------------ helpers for subclasses

    protected final void listen(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    protected final void task(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    protected final PlayerStore store() {
        return plugin.getV3Module() == null ? null : plugin.getV3Module().store();
    }

    protected final Reward reward(String path) {
        return Reward.read(config().getConfigurationSection(path));
    }

    /** A per-player store key for this event, e.g. {@code event.easter.2027}. */
    protected final String storeKey(String suffix) {
        return "event." + id + "." + suffix;
    }

    protected final String today() {
        return manager.now().toLocalDate().toString();
    }

    protected final int seasonYear() {
        return schedule().map(window -> window.seasonYear(manager.now())).orElse(manager.now().getYear());
    }
}
