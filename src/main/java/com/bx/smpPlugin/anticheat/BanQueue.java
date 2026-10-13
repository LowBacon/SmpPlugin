package com.bx.smpPlugin.anticheat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Players who reached a ban threshold, waiting for a staff decision (QUEUE mode) or for the next ban wave
 * (AUTO mode). Saved to {@code anticheat/ban-queue.yml} after every change so a restart loses nothing.
 * Main thread only.
 */
final class BanQueue {

    /** One queued ban. */
    record Entry(UUID playerId, String playerName, CheckType check, String evidenceId, String reason, long queuedAtMillis) {
    }

    private final File file;
    private final Logger logger;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    BanQueue(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        load();
    }

    /** Adds a player unless already queued. Returns true when newly queued. */
    boolean add(Entry entry) {
        if (entries.containsKey(entry.playerId())) {
            return false;
        }
        entries.put(entry.playerId(), entry);
        save();
        return true;
    }

    Optional<Entry> remove(UUID playerId) {
        Entry removed = entries.remove(playerId);
        if (removed != null) {
            save();
        }
        return Optional.ofNullable(removed);
    }

    Optional<Entry> findByName(String name) {
        return entries.values().stream().filter(entry -> entry.playerName().equalsIgnoreCase(name)).findFirst();
    }

    boolean contains(UUID playerId) {
        return entries.containsKey(playerId);
    }

    Collection<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** Removes and returns every entry. */
    List<Entry> drain() {
        List<Entry> drained = new ArrayList<>(entries.values());
        entries.clear();
        save();
        return drained;
    }

    int size() {
        return entries.size();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("queue");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                CheckType check = CheckType.fromInput(row.getString("check", "")).orElse(CheckType.FLY);
                entries.put(id, new Entry(id, row.getString("name", key), check, row.getString("evidence", ""),
                        row.getString("reason", ""), row.getLong("queued-at")));
            } catch (IllegalArgumentException exception) {
                logger.warning("[AntiCheat] Skipping invalid ban-queue entry " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Entry entry : entries.values()) {
            String base = "queue." + entry.playerId();
            yaml.set(base + ".name", entry.playerName());
            yaml.set(base + ".check", entry.check().name());
            yaml.set(base + ".evidence", entry.evidenceId());
            yaml.set(base + ".reason", entry.reason());
            yaml.set(base + ".queued-at", entry.queuedAtMillis());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            yaml.save(file);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "[AntiCheat] Could not save the ban queue", exception);
        }
    }
}
