// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.FfaPlayerSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

// FFA gear owed back to a player who is offline or dead, kept on disk until it is given back, so a
// restart in between no longer loses it. One file per player in data/ffa-restores.
final class FfaRestoreStore {

    record Owed(FfaPlayerSnapshot snapshot, Location respawnLocation) {
    }

    private final SmpPlugin plugin;
    private final File folder;

    FfaRestoreStore(SmpPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data/ffa-restores");
    }

    void save(UUID uuid, FfaPlayerSnapshot snapshot, Location respawnLocation) {
        if (uuid == null || snapshot == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("STORAGE", Arrays.asList(snapshot.getStorageContents()));
        yaml.set("ARMOR", Arrays.asList(snapshot.getArmorContents()));
        yaml.set("OFF-HAND", snapshot.getOffHand());
        yaml.set("HEALTH", snapshot.getHealth());
        yaml.set("ABSORPTION", snapshot.getAbsorptionAmount());
        yaml.set("FOOD", snapshot.getFoodLevel());
        yaml.set("SATURATION", (double) snapshot.getSaturation());
        yaml.set("EXHAUSTION", (double) snapshot.getExhaustion());
        yaml.set("LEVEL", snapshot.getLevel());
        yaml.set("EXP", (double) snapshot.getExperienceProgress());
        yaml.set("TOTAL-EXP", snapshot.getTotalExperience());
        yaml.set("EFFECTS", new ArrayList<>(snapshot.getPotionEffects()));
        yaml.set("RETURN-LOCATION", snapshot.getReturnLocation());
        yaml.set("GAMEMODE", snapshot.getGameMode().name());
        yaml.set("ALLOW-FLIGHT", snapshot.isAllowFlight());
        yaml.set("FLYING", snapshot.isFlying());
        yaml.set("FIRE-TICKS", snapshot.getFireTicks());
        yaml.set("RESPAWN-LOCATION", respawnLocation);
        yaml.set("SAVED-AT", System.currentTimeMillis());
        String text = yaml.saveToString();
        File file = file(uuid);
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
                plugin.getLogger().log(Level.SEVERE, "Could not save the FFA gear owed to " + uuid
                        + "; it is still held in memory.", exception);
            }
        });
    }

    void delete(UUID uuid) {
        if (uuid == null) {
            return;
        }
        File file = file(uuid);
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
            UUID uuid;
            try {
                uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            try {
                owed.put(uuid, read(YamlConfiguration.loadConfiguration(file)));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read the FFA gear owed in " + file.getName()
                        + "; it is kept on disk for staff to look at.", exception);
            }
        }
        if (!owed.isEmpty()) {
            plugin.getLogger().info("Loaded FFA gear owed to " + owed.size() + " player(s) from before the restart.");
        }
        return owed;
    }

    private static Owed read(YamlConfiguration yaml) {
        List<PotionEffect> effects = new ArrayList<>();
        for (Object raw : yaml.getList("EFFECTS", List.of())) {
            if (raw instanceof PotionEffect effect) {
                effects.add(effect);
            }
        }
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(yaml.getString("GAMEMODE", "SURVIVAL"));
        } catch (IllegalArgumentException exception) {
            gameMode = GameMode.SURVIVAL;
        }
        FfaPlayerSnapshot snapshot = new FfaPlayerSnapshot(
                items(yaml.getList("STORAGE", List.of())),
                items(yaml.getList("ARMOR", List.of())),
                yaml.getItemStack("OFF-HAND"),
                yaml.getDouble("HEALTH", 20D),
                yaml.getDouble("ABSORPTION", 0D),
                yaml.getInt("FOOD", 20),
                (float) yaml.getDouble("SATURATION", 5D),
                (float) yaml.getDouble("EXHAUSTION", 0D),
                yaml.getInt("LEVEL", 0),
                (float) yaml.getDouble("EXP", 0D),
                yaml.getInt("TOTAL-EXP", 0),
                effects,
                location(yaml, "RETURN-LOCATION"),
                gameMode,
                yaml.getBoolean("ALLOW-FLIGHT", false),
                yaml.getBoolean("FLYING", false),
                yaml.getInt("FIRE-TICKS", 0));
        return new Owed(snapshot, location(yaml, "RESPAWN-LOCATION"));
    }

    private static Location location(YamlConfiguration yaml, String path) {
        try {
            Location location = yaml.getLocation(path);
            return location == null || location.getWorld() == null ? null : location;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ItemStack[] items(List<?> raw) {
        ItemStack[] items = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            items[i] = raw.get(i) instanceof ItemStack stack ? stack : null;
        }
        return items;
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }
}
