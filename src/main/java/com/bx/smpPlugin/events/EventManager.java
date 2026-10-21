package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Starts and stops the seasonal events.
 *
 * <p>Each event has a mode: AUTO runs it inside its START and END dates from events.yml, ON runs it
 * now, OFF keeps it stopped. Staff change the mode with /event enable|disable|auto; the choice is
 * kept in {@code data/events-state.yml}, so events.yml itself is never rewritten. Once a minute
 * the manager checks every event and starts or stops it to match.
 */
public final class EventManager {

    public enum Mode {
        AUTO, ON, OFF;

        static Mode parse(String raw) {
            if (raw == null) {
                return AUTO;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return AUTO;
            }
        }
    }

    private static final String STATE_FILE = "data/events-state.yml";

    private final SmpPlugin plugin;
    private final Map<String, SeasonalEvent> events = new LinkedHashMap<>();
    private final Map<String, Mode> modes = new ConcurrentHashMap<>();
    private BukkitTask ticker;
    private boolean started;

    public EventManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(SeasonalEvent event) {
        events.put(event.id(), event);
    }

    public Collection<SeasonalEvent> all() {
        return Collections.unmodifiableCollection(events.values());
    }

    public Optional<SeasonalEvent> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(events.get(id.toLowerCase(Locale.ROOT)));
    }

    public FileConfiguration config() {
        FileConfiguration config = plugin.getConfigManager().getEvents();
        return config == null ? new YamlConfiguration() : config;
    }

    /** The current time in events.yml's TIME-ZONE (the server's zone when empty). */
    public LocalDateTime now() {
        String raw = config().getString("TIME-ZONE", "");
        ZoneId zone = ZoneId.systemDefault();
        if (raw != null && !raw.isBlank()) {
            try {
                zone = ZoneId.of(raw.trim());
            } catch (DateTimeException exception) {
                zone = ZoneId.systemDefault();
            }
        }
        return LocalDateTime.now(zone);
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        loadState();
        tick();
        started = true;
        ticker = plugin.getSpigotScheduler().runGlobalTimer(this::tick, 1200L, 1200L);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        started = false;
        events.values().forEach(SeasonalEvent::stop);
    }

    /** After events.yml was read again: every running event restarts with the new settings. */
    public void reload() {
        for (SeasonalEvent event : events.values()) {
            reload(event);
        }
        tick();
    }

    public void reload(SeasonalEvent event) {
        boolean wasRunning = event.isRunning();
        event.stop();
        event.onReload();
        if (wasRunning && shouldRun(event)) {
            event.start();
        }
    }

    // ------------------------------------------------------------------ modes

    public Mode mode(SeasonalEvent event) {
        return modes.getOrDefault(event.id(), Mode.AUTO);
    }

    /** Changes an event's mode, saves it, and starts or stops the event right away. */
    public void setMode(SeasonalEvent event, Mode mode) {
        if (mode == Mode.AUTO) {
            modes.remove(event.id());
        } else {
            modes.put(event.id(), mode);
        }
        saveState();
        tick();
    }

    public boolean shouldRun(SeasonalEvent event) {
        if (!event.isConfigured()) {
            return false;
        }
        return switch (mode(event)) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> event.schedule().map(window -> window.contains(now())).orElse(false);
        };
    }

    /** Starts and stops events to match their mode and dates. */
    public void tick() {
        for (SeasonalEvent event : events.values()) {
            if (!event.isManaged()) {
                continue;
            }
            boolean should = shouldRun(event);
            if (should && !event.isRunning()) {
                event.start();
                if (started && event.isRunning()) {
                    announce(event, "START-MESSAGE", "&a&l{event} &7has started! &8(/event)");
                }
            } else if (!should && event.isRunning()) {
                event.stop();
                if (started) {
                    announce(event, "END-MESSAGE", "&7{event} has ended. Thanks for taking part!");
                }
            }
        }
    }

    private void announce(SeasonalEvent event, String key, String fallback) {
        String message = event.config().getString(key, fallback);
        if (message != null && !message.isBlank()) {
            Text.broadcast(Text.prefix() + message.replace("{event}", event.displayName()));
        }
    }

    /** "running, ends in 3d 4h", "starts in 2d", "off (switched off by staff)"... for /event. */
    public String statusLine(SeasonalEvent event) {
        if (!event.isConfigured()) {
            return "&8disabled in events.yml";
        }
        if (!event.isManaged()) {
            return event.isRunning() ? "&arunning" : "&7not running";
        }
        Mode mode = mode(event);
        LocalDateTime now = now();
        if (event.isRunning()) {
            if (mode == Mode.ON) {
                return "&arunning &8(switched on by staff)";
            }
            long left = event.schedule().map(window -> window.secondsUntilEnd(now)).orElse(-1L);
            return left > 0 ? "&arunning&7, ends in &f" + Text.duration(left) : "&arunning";
        }
        if (mode == Mode.OFF) {
            return "&coff &8(switched off by staff)";
        }
        Optional<EventSchedule> schedule = event.schedule();
        if (schedule.isEmpty()) {
            return "&7no dates set &8(start it with /event enable " + event.id() + ")";
        }
        long wait = schedule.get().secondsUntilStart(now);
        return wait < 0 ? "&7over" : "&7starts in &f" + Text.duration(wait);
    }

    // ------------------------------------------------------------------ persistence

    private File stateFile() {
        return new File(plugin.getDataFolder(), STATE_FILE);
    }

    private void loadState() {
        modes.clear();
        File file = stateFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration state = new YamlConfiguration();
        try {
            state.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + STATE_FILE + "; every event is on AUTO.", exception);
            return;
        }
        ConfigurationSection section = state.getConfigurationSection("MODES");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Mode mode = Mode.parse(section.getString(key));
            if (mode != Mode.AUTO) {
                modes.put(key.toLowerCase(Locale.ROOT), mode);
            }
        }
    }

    private void saveState() {
        YamlConfiguration state = new YamlConfiguration();
        modes.forEach((id, mode) -> state.set("MODES." + id, mode.name()));
        String text = "# Event modes set with /event enable|disable|auto. AUTO events are not listed.\n" + state.saveToString();
        File file = stateFile();
        plugin.getSpigotScheduler().runAsync(() -> {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + STATE_FILE, exception);
            }
        });
    }
}
