package com.bx.smpPlugin.seasonal;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The /events switches, kept in {@code data/events.yml} so they survive restarts. Only the mode and who set it are
 * stored here; the dates stay in each event's own config file and are never written.
 */
public final class EventControl {

    /** One event's switch and who last changed it. */
    public record Setting(EventMode mode, String changedBy, long changedAtMillis) {
        static final Setting DEFAULT = new Setting(EventMode.AUTO, null, 0L);
    }

    private final File file;
    private final Logger logger;
    private final Map<EventId, Setting> settings = new EnumMap<>(EventId.class);
    private boolean readable = true;

    public EventControl(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    public synchronized Setting setting(EventId id) {
        return settings.getOrDefault(id, Setting.DEFAULT);
    }

    public EventMode mode(EventId id) {
        return setting(id).mode();
    }

    /** Changes and saves one switch. Returns false (nothing changed) when the file could not be read at start. */
    public synchronized boolean set(EventId id, EventMode mode, String actor, long nowMillis) {
        if (!readable) {
            return false;
        }
        settings.put(id, new Setting(mode, actor, nowMillis));
        save();
        return true;
    }

    /** False when data/events.yml was damaged at start; switches then stay AUTO until the file is fixed. */
    public synchronized boolean isReadable() {
        return readable;
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            // Never overwrite a damaged file: staff may want what was in it.
            readable = false;
            logger.log(Level.SEVERE, "Could not read " + file.getPath() + "; every event follows its dates until the"
                    + " file is fixed or removed, and /events cannot change them meanwhile.", exception);
            return;
        }
        for (EventId id : EventId.values()) {
            String path = "EVENTS." + id.key();
            if (!yaml.isConfigurationSection(path)) {
                continue;
            }
            EventMode mode = EventMode.parse(yaml.getString(path + ".MODE")).orElse(EventMode.AUTO);
            settings.put(id, new Setting(mode, yaml.getString(path + ".CHANGED-BY"), yaml.getLong(path + ".CHANGED-AT", 0L)));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of(
                "The /events switches. AUTO follows the dates in each event's file, ON runs it now, OFF stops it.",
                "Change them with /events <event> on|off|auto; the dates themselves are never written here."));
        for (Map.Entry<EventId, Setting> entry : settings.entrySet()) {
            String path = "EVENTS." + entry.getKey().key();
            yaml.set(path + ".MODE", entry.getValue().mode().name());
            yaml.set(path + ".CHANGED-BY", entry.getValue().changedBy());
            yaml.set(path + ".CHANGED-AT", entry.getValue().changedAtMillis());
        }
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(temp.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Could not save " + file.getPath() + "; the change works until the next restart.",
                    exception);
        }
    }
}
