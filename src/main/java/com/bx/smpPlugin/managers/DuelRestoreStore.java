// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

// The inventory a duel loser keeps, owed until they respawn or rejoin; on disk so a restart or a
// quit on the death screen no longer loses it. One file per player in data/duel-restores.
final class DuelRestoreStore {

    record Owed(ItemStack[] storage, ItemStack[] armor, ItemStack offHand, ItemStack cursor, Location returnLocation) {
    }

    private final SmpPlugin plugin;
    private final File folder;

    DuelRestoreStore(SmpPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data/duel-restores");
    }

    void save(UUID uuid, Owed owed) {
        if (uuid == null || owed == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("STORAGE", Arrays.asList(owed.storage()));
        yaml.set("ARMOR", Arrays.asList(owed.armor()));
        yaml.set("OFF-HAND", owed.offHand());
        yaml.set("CURSOR", owed.cursor());
        yaml.set("RETURN-LOCATION", owed.returnLocation());
        yaml.set("SAVED-AT", System.currentTimeMillis());
        String text = yaml.saveToString();
        File file = new File(folder, uuid + ".yml");
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                Files.createDirectories(folder.toPath());
                Path temp = file.toPath().resolveSibling(file.getName() + ".tmp");
                Files.writeString(temp, text, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the duel items owed to " + uuid
                        + "; they are still held in memory.", exception);
            }
        });
    }

    void delete(UUID uuid) {
        if (uuid == null) {
            return;
        }
        File file = new File(folder, uuid + ".yml");
        plugin.getDatabaseManager().executeAsync(() -> {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Could not delete " + file.getPath() + " after giving it back.");
            }
        });
    }

    Map<UUID, Owed> loadAll() {
        Map<UUID, Owed> owed = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return owed;
        }
        for (File file : files) {
            try {
                UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                Location returnLocation;
                try {
                    returnLocation = yaml.getLocation("RETURN-LOCATION");
                } catch (IllegalArgumentException exception) {
                    returnLocation = null;
                }
                owed.put(uuid, new Owed(items(yaml.getList("STORAGE", List.of())), items(yaml.getList("ARMOR", List.of())),
                        yaml.getItemStack("OFF-HAND"), yaml.getItemStack("CURSOR"), returnLocation));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read the duel items owed in " + file.getName()
                        + "; it is kept on disk for staff to look at.", exception);
            }
        }
        if (!owed.isEmpty()) {
            plugin.getLogger().info("Loaded duel items owed to " + owed.size() + " player(s) from before the restart.");
        }
        return owed;
    }

    private static ItemStack[] items(List<?> raw) {
        ItemStack[] items = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            items[i] = raw.get(i) instanceof ItemStack stack ? stack : null;
        }
        return items;
    }
}
