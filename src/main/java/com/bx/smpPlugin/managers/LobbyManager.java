// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.AttributeUtils;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.LocationUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class LobbyManager {

    private final SmpPlugin plugin;
    private final File playerLocationsFile;

    private volatile Location lobbyLocation;

    // The lobby area. It is kept with a movement clamp; /border lobby can also show it as a real
    // Minecraft border (world.BorderManager), which sets this area to the same square.
    //
    // Internally always a rectangle (minX/maxX/minZ/maxZ), settable two ways:
    //  - /lobby border <radius>: a square centered on the lobby location
    //  - /lobby border pos1 + /lobby border pos2: an exact rectangle from two in-world corners
    private volatile boolean borderEnabled;
    private volatile double borderMinX;
    private volatile double borderMaxX;
    private volatile double borderMinZ;
    private volatile double borderMaxZ;

    private final Map<UUID, double[]> pendingBorderCorner1 = new ConcurrentHashMap<>();

    private final Map<UUID, PlayerLocation> lastSmpLocations = new ConcurrentHashMap<>();
    private final Map<UUID, SavedInventory> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> npcCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> borderWarnCooldowns = new ConcurrentHashMap<>();

    public LobbyManager(SmpPlugin plugin) {
        this.plugin = plugin;
        this.playerLocationsFile = new File(plugin.getDataFolder(), "lobby-playerlocations.yml");
        loadConfig();
        loadPlayerLocations();
    }

    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration lobby = plugin.getConfigManager().getLobby();
        lobbyLocation = LocationUtils.parse(lobby.getString("LOCATIONS.LOBBY-LOCATION", ""));
        if (lobbyLocation == null && plugin.getWorldSystem() != null) {
            // No /setlobby yet: the dedicated "lobby" world (worlds.yml) is the lobby.
            lobbyLocation = plugin.getWorldSystem().spawnLocation("lobby");
        }
        borderEnabled = lobby.getBoolean("BORDER.ENABLED", true);

        if (lobby.contains("BORDER.MIN-X")) {
            borderMinX = lobby.getDouble("BORDER.MIN-X");
            borderMaxX = lobby.getDouble("BORDER.MAX-X");
            borderMinZ = lobby.getDouble("BORDER.MIN-Z");
            borderMaxZ = lobby.getDouble("BORDER.MAX-Z");
        } else if (lobbyLocation != null) {
            // First load after upgrading from the old radius-only format (or a fresh install) -
            // derive a square from BORDER.RADIUS centered on the lobby location.
            double radius = Math.max(1.0, lobby.getDouble("BORDER.RADIUS", 100.0));
            applyRadius(radius);
        }
    }

    private FileConfiguration config() {
        return plugin.getConfigManager().getLobby();
    }

    // ------------------------------------------------------------------
    // Lobby location
    // ------------------------------------------------------------------

    public Location getLobbyLocation() {
        return lobbyLocation != null ? lobbyLocation.clone() : null;
    }

    public boolean hasLobbyLocation() {
        return lobbyLocation != null;
    }

    public boolean setLobbyLocation(Location location) {
        config().set("LOCATIONS.LOBBY-LOCATION", LocationUtils.serialize(location));
        boolean saved = plugin.getConfigManager().saveLobby();
        if (saved) {
            lobbyLocation = location.clone();
            if (!config().contains("BORDER.MIN-X")) {
                // First time the lobby is being set up - give the border a sensible default
                // (100-block radius, i.e. a 200x200 square) centered here, rather than leaving
                // it as an uninitialized zero-size rectangle that would trap everyone at (0,0).
                // A later /setlobby (moving an already-configured lobby) leaves an existing
                // border alone.
                applyRadius(100.0);
                persistBorder();
            }
        }
        return saved;
    }

    public boolean isLobbyWorld(org.bukkit.World world) {
        return world != null && lobbyLocation != null && world.equals(lobbyLocation.getWorld());
    }

    public boolean isLobbyActive() {
        return com.bx.smpPlugin.managers.FeatureManager.isEnabled(plugin.getConfigManager().getConfig(),
                FeatureManager.Feature.LOBBY);
    }

    // With the lobby switched off, anyone still holding a lobby snapshot (in the lobby when it was
    // turned off, or offline then) goes back to where they were with their items.
    public void releaseFromLobby(Player player) {
        if (player == null || !player.isOnline() || isLobbyActive()) {
            return;
        }
        if (hasSavedInventory(player.getUniqueId())) {
            sendToSurvival(player);
        }
    }

    public boolean isInLobby(Player player) {
        return isLocationInLobby(player.getLocation());
    }

    public boolean isLocationInLobby(Location location) {
        if (!isLobbyActive()) {
            return false;
        }
        if (lobbyLocation == null || location.getWorld() == null || !location.getWorld().equals(lobbyLocation.getWorld())) {
            return false;
        }
        if (plugin.getWorldSystem() != null && plugin.getWorldSystem().isWorld(location.getWorld(), "lobby")) {
            return true; // A dedicated lobby world is the lobby from edge to edge.
        }
        if (!borderEnabled) {
            return true; // No border configured - the whole world counts as "the lobby".
        }
        double x = location.getX();
        double z = location.getZ();
        return x >= borderMinX && x <= borderMaxX && z >= borderMinZ && z <= borderMaxZ;
    }

    // ------------------------------------------------------------------
    // Join / leave orchestration
    // ------------------------------------------------------------------

    public boolean sendToLobby(Player player, boolean applyJoinEffects) {
        if (!isLobbyActive()) {
            return false;
        }
        if (lobbyLocation == null) {
            plugin.getLogger().warning("Lobby location is not set - use /setlobby. "
                    + player.getName() + " was not teleported.");
            return false;
        }

        // Save location and inventory if player is leaving the SMP
        boolean wasOutsideLobby = !isInLobby(player);
        saveSmpLocationIfOutsideLobby(player);
        
        // Save and clear inventory when entering lobby from SMP
        if (wasOutsideLobby) {
            saveAndClearInventory(player);
        }

        plugin.getSpigotScheduler().teleport(player, lobbyLocation.clone()).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success)) {
                // Still in the SMP: give the inventory straight back instead of leaving them empty-handed.
                if (wasOutsideLobby) {
                    plugin.getSpigotScheduler().runEntity(player, () -> {
                        if (player.isOnline() && !isInLobby(player)) {
                            restoreInventory(player);
                        }
                    });
                }
                return;
            }
            if (applyJoinEffects) {
                plugin.getSpigotScheduler().runEntity(player, () -> applyJoinEffects(player));
            }
        });
        return true;
    }

    public void restoreAfterLeaving(Player player, boolean alwaysSetGameMode) {
        boolean hadSaved = hasSavedInventory(player.getUniqueId());
        restoreInventory(player);
        // The join greetings and reminders held back while in the lobby.
        com.bx.smpPlugin.worlds.SmpArrival.arrived(plugin, player);
        FileConfiguration lobby = config();
        if ((hadSaved || alwaysSetGameMode) && lobby.getBoolean("LEAVE-TO-SURVIVAL.SET-GAMEMODE", true)) {
            String modeName = lobby.getString("LEAVE-TO-SURVIVAL.GAMEMODE", "SURVIVAL");
            try {
                player.setGameMode(GameMode.valueOf(modeName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid LEAVE-TO-SURVIVAL.GAMEMODE '" + modeName + "' in lobby.yml - skipping.");
            }
        }
    }

    private void applyJoinEffects(Player player) {
        FileConfiguration lobby = config();
        // Health, XP and effects are only reset when the snapshot holds the real values to give
        // back on the way out; otherwise every join cost players their XP and healed them for free.
        boolean statusSaved = hasSavedInventory(player.getUniqueId());
        if (statusSaved && lobby.getBoolean("JOIN.HEAL", true)) {
            player.setHealth(AttributeUtils.getMaxHealth(player));
        }
        if (statusSaved && lobby.getBoolean("JOIN.FEED", true)) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
        if (statusSaved && lobby.getBoolean("JOIN.CLEAR-POTION-EFFECTS", true)) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        }
        if (statusSaved && lobby.getBoolean("JOIN.RESET-EXPERIENCE", true)) {
            player.setExp(0f);
            player.setLevel(0);
            player.setTotalExperience(0);
        }
        if (lobby.getBoolean("JOIN.SET-GAMEMODE", true)) {
            String modeName = lobby.getString("JOIN.GAMEMODE", "ADVENTURE");
            try {
                player.setGameMode(GameMode.valueOf(modeName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid JOIN.GAMEMODE '" + modeName + "' in lobby.yml - skipping.");
            }
        }

        Map<String, String> placeholders = Map.of("%player%", player.getName());
        String title = applyPlaceholders(lobby.getString("JOIN.WELCOME-TITLE", ""), placeholders);
        String subtitle = applyPlaceholders(lobby.getString("JOIN.WELCOME-SUBTITLE", ""), placeholders);
        int fadeIn = lobby.getInt("JOIN.WELCOME-TITLE-FADE-IN", 10);
        int stay = lobby.getInt("JOIN.WELCOME-TITLE-STAY", 40);
        int fadeOut = lobby.getInt("JOIN.WELCOME-TITLE-FADE-OUT", 10);
        if (!title.isBlank() || !subtitle.isBlank()) {
            player.sendTitle(ColorUtils.colorize(title, player), ColorUtils.colorize(subtitle, player), fadeIn, stay, fadeOut);
        }
        String actionBar = lobby.getString("JOIN.WELCOME-ACTIONBAR", "");
        if (!actionBar.isBlank()) {
            PlayerSettingUtils.sendActionBar(plugin, player, actionBar);
        }
        SoundUtils.play(player, lobby.getString("JOIN.WELCOME-SOUND", ""));
    }

    public void sendToSurvival(Player player) {
        FileConfiguration lobby = config();
        Location destination = getSavedSmpLocation(player.getUniqueId());
        boolean usedFallback = false;

        if (destination == null) {
            if (!lobby.getBoolean("LEAVE-TO-SURVIVAL.FALLBACK-TO-SERVER-SPAWN", true)) {
                player.sendMessage(ColorUtils.toComponent(
                        lobby.getString("LEAVE-TO-SURVIVAL.SPAWN-NOT-SET-MESSAGE", ""), player));
                return;
            }
            destination = plugin.getSpawnManager().hasSpawn() ? plugin.getSpawnManager().getSpawnLocation() : null;
            usedFallback = true;
            if (destination == null) {
                player.sendMessage(ColorUtils.toComponent(
                        lobby.getString("LEAVE-TO-SURVIVAL.SPAWN-NOT-SET-MESSAGE", ""), player));
                return;
            }
        }

        if (usedFallback) {
            player.sendMessage(ColorUtils.toComponent(
                    lobby.getString("LEAVE-TO-SURVIVAL.NO-SAVED-LOCATION-MESSAGE", ""), player));
        }

        Location finalDestination = destination;
        plugin.getSpigotScheduler().teleport(player, finalDestination).thenAccept(success -> {
            if (!Boolean.TRUE.equals(success)) {
                return;
            }
            plugin.getSpigotScheduler().runEntity(player, () -> {
                restoreAfterLeaving(player, true);
                for (String rawCommand : lobby.getStringList("LEAVE-TO-SURVIVAL.COMMANDS-ON-ENTER")) {
                    String parsed = rawCommand.replace("%player%", player.getName());
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), parsed);
                }
                player.sendMessage(ColorUtils.toComponent(
                        lobby.getString("LEAVE-TO-SURVIVAL.TELEPORTED-MESSAGE", ""), player));
            });
        });
    }

    public boolean isBlockedByCombat(Player player) {
        if (!config().getBoolean("COOLDOWNS.BLOCK-DURING-COMBAT", true)) {
            return false;
        }
        return plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId());
    }

    public String getCombatBlockedMessage() {
        return config().getString("COOLDOWNS.BLOCKED-BY-COMBAT-MESSAGE", "");
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Border: the lobby area (a movement clamp; /border lobby adds the visible Minecraft border).
    // ------------------------------------------------------------------

    public boolean isBorderEnabled() {
        return borderEnabled;
    }

    public double getBorderRadius() {
        return ((borderMaxX - borderMinX) + (borderMaxZ - borderMinZ)) / 4.0;
    }

    public String describeBorderSize() {
        return String.format(Locale.ROOT, "%.0f x %.0f", borderMaxX - borderMinX, borderMaxZ - borderMinZ);
    }

    public void setBorderRadius(double radius) {
        applyRadius(Math.max(1.0, radius));
        persistBorder();
    }

    public void setBorderSquare(double centerX, double centerZ, double halfWidth) {
        double radius = Math.max(1.0, halfWidth);
        borderMinX = centerX - radius;
        borderMaxX = centerX + radius;
        borderMinZ = centerZ - radius;
        borderMaxZ = centerZ + radius;
        persistBorder();
    }

    public double[] getBorderBounds() {
        return lobbyLocation == null ? null : new double[]{borderMinX, borderMaxX, borderMinZ, borderMaxZ};
    }

    private void applyRadius(double radius) {
        double centerX = lobbyLocation != null ? lobbyLocation.getX() : 0.0;
        double centerZ = lobbyLocation != null ? lobbyLocation.getZ() : 0.0;
        borderMinX = centerX - radius;
        borderMaxX = centerX + radius;
        borderMinZ = centerZ - radius;
        borderMaxZ = centerZ + radius;
    }

    public void setBorderCorner1(Player player, Location location) {
        pendingBorderCorner1.put(player.getUniqueId(), new double[]{location.getX(), location.getZ()});
    }

    public boolean setBorderCorner2(Player player, Location location) {
        double[] corner1 = pendingBorderCorner1.remove(player.getUniqueId());
        if (corner1 == null) {
            return false;
        }
        double x2 = location.getX();
        double z2 = location.getZ();
        borderMinX = Math.min(corner1[0], x2);
        borderMaxX = Math.max(corner1[0], x2);
        borderMinZ = Math.min(corner1[1], z2);
        borderMaxZ = Math.max(corner1[1], z2);
        persistBorder();
        return true;
    }

    public boolean hasPendingBorderCorner1(Player player) {
        return pendingBorderCorner1.containsKey(player.getUniqueId());
    }

    private void persistBorder() {
        FileConfiguration lobby = config();
        lobby.set("BORDER.MIN-X", borderMinX);
        lobby.set("BORDER.MAX-X", borderMaxX);
        lobby.set("BORDER.MIN-Z", borderMinZ);
        lobby.set("BORDER.MAX-Z", borderMaxZ);
        plugin.getConfigManager().saveLobby();
    }

    public Location clampIfOutside(Location location) {
        if (!borderEnabled || lobbyLocation == null || location.getWorld() == null
                || !location.getWorld().equals(lobbyLocation.getWorld())) {
            return null;
        }

        double x = location.getX();
        double z = location.getZ();
        boolean outsideX = x < borderMinX || x > borderMaxX;
        boolean outsideZ = z < borderMinZ || z > borderMaxZ;
        if (!outsideX && !outsideZ) {
            return null;
        }

        Location clamped = location.clone();
        if (outsideX) {
            clamped.setX(Math.max(borderMinX, Math.min(borderMaxX, x)));
        }
        if (outsideZ) {
            clamped.setZ(Math.max(borderMinZ, Math.min(borderMaxZ, z)));
        }
        return clamped;
    }

    public void warnIfNearEdge(Player player, Location location) {
        if (!borderEnabled || lobbyLocation == null || location.getWorld() == null
                || !location.getWorld().equals(lobbyLocation.getWorld())) {
            // Only in the lobby's own world. Elsewhere every spot is "outside" the rectangle, so
            // players in the SMP were told every few seconds that they had reached the lobby border.
            return;
        }
        boolean messageEnabled = config().getBoolean("BORDER.WARN-MESSAGE-ENABLED", true);
        boolean soundEnabled = config().getBoolean("BORDER.WARN-SOUND-ENABLED", true);
        if (!messageEnabled && !soundEnabled) {
            return;
        }

        double distanceFromEdge = Math.min(
                Math.min(location.getX() - borderMinX, borderMaxX - location.getX()),
                Math.min(location.getZ() - borderMinZ, borderMaxZ - location.getZ()));
        int warningDistance = config().getInt("BORDER.WARNING-DISTANCE", 5);
        if (distanceFromEdge > warningDistance) {
            return;
        }

        long cooldownMillis = config().getLong("BORDER.WARN-COOLDOWN-SECONDS", 3) * 1000L;
        long now = System.currentTimeMillis();
        long lastWarned = borderWarnCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastWarned < cooldownMillis) {
            return;
        }
        borderWarnCooldowns.put(player.getUniqueId(), now);

        if (messageEnabled) {
            player.sendMessage(ColorUtils.toComponent(config().getString("BORDER.WARNING-MESSAGE", ""), player));
        }
        if (soundEnabled) {
            SoundUtils.play(player, config().getString("BORDER.WARN-SOUND", ""));
        }
    }

    // ------------------------------------------------------------------
    // Protection toggles (settings.yml-equivalent section of lobby.yml)
    // ------------------------------------------------------------------

    public boolean isProtectionEnabled(String key) {
        return config().getBoolean("PROTECTION." + key, true);
    }

    public void sendProtectionDeniedMessage(Player player) {
        String message = config().getString("PROTECTION.DENIED-MESSAGE", "");
        if (message != null && !message.isBlank()) {
            player.sendMessage(ColorUtils.toComponent(message, player));
        }
    }

    // ------------------------------------------------------------------
    // Command cooldowns (/lobby, /smpplugin)
    // ------------------------------------------------------------------

    public long checkAndStartCommandCooldown(Player player) {
        long cooldownSeconds = config().getLong("COOLDOWNS.LOBBY-COMMAND-SECONDS", 5);
        if (cooldownSeconds <= 0 || player.hasPermission("smpplugin.bypass.lobby")) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long last = commandCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = cooldownSeconds * 1000L;
        long elapsed = now - last;
        if (elapsed < cooldownMillis) {
            return (cooldownMillis - elapsed + 999) / 1000;
        }
        commandCooldowns.put(player.getUniqueId(), now);
        return 0L;
    }

    public long checkAndStartNpcCooldown(Player player) {
        long cooldownSeconds = config().getLong("NPC.INTERACTION-COOLDOWN-SECONDS", 3);
        if (cooldownSeconds <= 0) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long last = npcCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = cooldownSeconds * 1000L;
        long elapsed = now - last;
        if (elapsed < cooldownMillis) {
            return (cooldownMillis - elapsed + 999) / 1000;
        }
        npcCooldowns.put(player.getUniqueId(), now);
        return 0L;
    }

    // ------------------------------------------------------------------
    // Per-player saved SMP location
    // ------------------------------------------------------------------

    public void saveSmpLocation(Player player, Location from) {
        if (from == null || from.getWorld() == null || isLocationInLobby(from)) {
            return;
        }
        lastSmpLocations.put(player.getUniqueId(), PlayerLocation.of(from));
        plugin.getSpigotScheduler().runAsync(this::flushPlayerLocationsToDisk);
    }

    public void saveSmpLocationIfOutsideLobby(Player player) {
        Location current = player.getLocation();
        if (isInLobby(player)) {
            return;
        }
        lastSmpLocations.put(player.getUniqueId(), PlayerLocation.of(current));
        plugin.getSpigotScheduler().runAsync(this::flushPlayerLocationsToDisk);
    }

    public boolean hasSavedSmpLocation(UUID uuid) {
        return lastSmpLocations.containsKey(uuid);
    }

    public Location getSavedSmpLocation(UUID uuid) {
        PlayerLocation stored = lastSmpLocations.get(uuid);
        return stored != null ? stored.toLocation() : null;
    }

    private void loadPlayerLocations() {
        if (!playerLocationsFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerLocationsFile);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String uuidString : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                String raw = section.getString(uuidString);
                Location location = LocationUtils.parse(raw);
                if (location != null) {
                    lastSmpLocations.put(uuid, PlayerLocation.of(location));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping malformed entry in lobby-playerlocations.yml: " + uuidString);
            }
        }
        plugin.getLogger().info("Loaded " + lastSmpLocations.size() + " saved lobby player location(s).");
    }

    private synchronized void flushPlayerLocationsToDisk() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerLocation> entry : lastSmpLocations.entrySet()) {
            yaml.set("players." + entry.getKey(), entry.getValue().serialized());
        }
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) {
                folder.mkdirs();
            }
            yaml.save(playerLocationsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save lobby-playerlocations.yml", e);
        }
    }

    // ------------------------------------------------------------------
    // Scoreboard integration (consumed by ScoreboardManager)
    // ------------------------------------------------------------------

    public String getScoreboardTitle() {
        return config().getString("SCOREBOARD.TITLE", "&6&lLOBBY");
    }

    public List<String> getScoreboardLines() {
        List<String> lines = config().getStringList("SCOREBOARD.LINES");
        String onlineCount = String.valueOf(org.bukkit.Bukkit.getOnlinePlayers().size());
        List<String> resolved = new ArrayList<>(lines.size());
        for (String line : lines) {
            resolved.add(line.replace("%server_online%", onlineCount));
        }
        return resolved;
    }

    // ------------------------------------------------------------------
    // Inventory Management
    // ------------------------------------------------------------------

    // Saves everything the lobby takes away (items, XP, health, hunger, effects) and clears the
    // inventory. An existing snapshot is never overwritten: what the player carries then goes to
    // the reward vault, so a failed teleport or an exit that skipped the restore loses nothing.
    public void saveAndClearInventory(Player player) {
        UUID uuid = player.getUniqueId();
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        if (savedInventories.containsKey(uuid)) {
            List<org.bukkit.inventory.ItemStack> carried = player.getGameMode() == GameMode.CREATIVE
                    ? List.of() : nonEmpty(inv.getContents());
            if (!carried.isEmpty()) {
                if (!com.bx.smpPlugin.engagement.PendingItems.keep(plugin, uuid, carried)) {
                    plugin.getLogger().warning(player.getName() + " already has a saved lobby inventory and the reward"
                            + " vault is unavailable; their current items are left in place.");
                    return;
                }
                plugin.getLogger().warning(player.getName() + " entered the lobby with a saved inventory already held; "
                        + carried.size() + " carried stack(s) went to their reward vault (/claimitems).");
            }
            clearInventory(inv);
            return;
        }
        SavedInventory saved = SavedInventory.of(player);
        savedInventories.put(uuid, saved);
        persistSavedInventory(uuid, saved);
        clearInventory(inv);
    }

    // Gives back the snapshot. Items gained in the lobby (a reward, a staff /give) are handed back
    // on top instead of being overwritten; what does not fit goes to the reward vault. Not in
    // creative, where items cost nothing.
    public void restoreInventory(Player player) {
        SavedInventory saved = savedInventories.remove(player.getUniqueId());
        if (saved == null) {
            return;
        }
        deleteSavedInventoryFile(player.getUniqueId());

        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        List<org.bukkit.inventory.ItemStack> gainedInLobby = player.getGameMode() == GameMode.CREATIVE
                ? List.of() : nonEmpty(inv.getContents());
        inv.setStorageContents(saved.storage());
        inv.setArmorContents(saved.armor());
        inv.setItemInOffHand(saved.offHand());
        saved.applyStatus(player);
        if (!gainedInLobby.isEmpty()) {
            plugin.getLogger().info("Gave " + player.getName() + " back " + gainedInLobby.size()
                    + " item stack(s) they got while in the lobby.");
            com.bx.smpPlugin.engagement.PendingItems.giveOrKeep(plugin, player, gainedInLobby);
        }
    }

    public boolean hasSavedInventory(UUID uuid) {
        return savedInventories.containsKey(uuid);
    }

    public void clearSavedInventory(UUID uuid) {
        savedInventories.remove(uuid);
        deleteSavedInventoryFile(uuid);
    }

    // Reads a saved snapshot from disk on the login thread. The in-memory copy, when there is one,
    // is newer than the file (its write may still be queued), so it always wins.
    public void preloadSavedInventory(UUID uuid) {
        File file = savedInventoryFile(uuid);
        if (uuid == null || savedInventories.containsKey(uuid) || !file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> storage = yaml.getList("STORAGE");
        List<?> armor = yaml.getList("ARMOR");
        if (storage == null || armor == null) {
            plugin.getLogger().warning("Could not read the saved lobby inventory " + file.getName()
                    + "; it is kept on disk for staff to look at.");
            return;
        }
        List<PotionEffect> effects = null;
        if (yaml.contains("EFFECTS")) {
            effects = new ArrayList<>();
            for (Object raw : yaml.getList("EFFECTS", List.of())) {
                if (raw instanceof PotionEffect effect) {
                    effects.add(effect);
                }
            }
        }
        savedInventories.putIfAbsent(uuid, new SavedInventory(toItems(storage), toItems(armor),
                yaml.getItemStack("OFF-HAND"),
                yaml.contains("LEVEL") ? yaml.getInt("LEVEL") : null,
                yaml.contains("EXP") ? (float) yaml.getDouble("EXP") : null,
                yaml.contains("TOTAL-EXP") ? yaml.getInt("TOTAL-EXP") : null,
                yaml.contains("HEALTH") ? yaml.getDouble("HEALTH") : null,
                yaml.contains("FOOD") ? yaml.getInt("FOOD") : null,
                yaml.contains("SATURATION") ? (float) yaml.getDouble("SATURATION") : null,
                effects));
    }

    private static List<org.bukkit.inventory.ItemStack> nonEmpty(org.bukkit.inventory.ItemStack[] contents) {
        List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    private static void clearInventory(org.bukkit.inventory.PlayerInventory inv) {
        inv.clear();
        inv.setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        inv.setItemInOffHand(null);
    }

    private static org.bukkit.inventory.ItemStack[] toItems(List<?> raw) {
        org.bukkit.inventory.ItemStack[] items = new org.bukkit.inventory.ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            items[i] = raw.get(i) instanceof org.bukkit.inventory.ItemStack stack ? stack : null;
        }
        return items;
    }

    private File savedInventoryFile(UUID uuid) {
        return new File(new File(plugin.getDataFolder(), "data/lobby-inventories"), uuid + ".yml");
    }

    // Serialised on the main thread, written in order on the database writer thread. The file is
    // written next to the real one and moved over it, so a crash never leaves half a file.
    private void persistSavedInventory(UUID uuid, SavedInventory saved) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("STORAGE", java.util.Arrays.asList(saved.storage()));
        yaml.set("ARMOR", java.util.Arrays.asList(saved.armor()));
        yaml.set("OFF-HAND", saved.offHand());
        yaml.set("LEVEL", saved.level());
        yaml.set("EXP", saved.exp() == null ? null : saved.exp().doubleValue());
        yaml.set("TOTAL-EXP", saved.totalExperience());
        yaml.set("HEALTH", saved.health());
        yaml.set("FOOD", saved.food());
        yaml.set("SATURATION", saved.saturation() == null ? null : saved.saturation().doubleValue());
        yaml.set("EFFECTS", saved.effects());
        yaml.set("SAVED-AT", System.currentTimeMillis());
        String text = yaml.saveToString();
        File file = savedInventoryFile(uuid);
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                java.nio.file.Files.createDirectories(file.getParentFile().toPath());
                java.nio.file.Path temp = file.toPath().resolveSibling(file.getName() + ".tmp");
                java.nio.file.Files.writeString(temp, text, java.nio.charset.StandardCharsets.UTF_8);
                try {
                    java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                    java.nio.file.Files.move(temp, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save the lobby inventory of " + uuid
                        + "; it is still held in memory until they leave the lobby.", exception);
            }
        });
    }

    private void deleteSavedInventoryFile(UUID uuid) {
        File file = savedInventoryFile(uuid);
        plugin.getDatabaseManager().executeAsync(() -> {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("Could not delete " + file.getPath() + " after restoring it.");
            }
        });
    }

    private static final class PlayerLocation {
        private final String serialized;

        private PlayerLocation(String serialized) {
            this.serialized = serialized;
        }

        static PlayerLocation of(Location location) {
            return new PlayerLocation(LocationUtils.serialize(location));
        }

        String serialized() {
            return serialized;
        }

        Location toLocation() {
            return LocationUtils.parse(serialized);
        }
    }

    // Status fields are null in snapshots written before 4.2; those are then left as they are.
    private record SavedInventory(org.bukkit.inventory.ItemStack[] storage, org.bukkit.inventory.ItemStack[] armor,
                                  org.bukkit.inventory.ItemStack offHand, Integer level, Float exp,
                                  Integer totalExperience, Double health, Integer food, Float saturation,
                                  List<PotionEffect> effects) {

        static SavedInventory of(Player player) {
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            org.bukkit.inventory.ItemStack offHand = inv.getItemInOffHand();
            return new SavedInventory(inv.getStorageContents().clone(), inv.getArmorContents().clone(),
                    offHand.getType().isAir() ? null : offHand.clone(),
                    player.getLevel(), player.getExp(), player.getTotalExperience(),
                    player.getHealth(), player.getFoodLevel(), player.getSaturation(),
                    new ArrayList<>(player.getActivePotionEffects()));
        }

        void applyStatus(Player player) {
            if (level != null && exp != null) {
                if (totalExperience != null) {
                    player.setTotalExperience(totalExperience);
                }
                player.setLevel(level);
                player.setExp(Math.max(0F, Math.min(0.9999F, exp)));
            }
            if (health != null && !player.isDead()) {
                player.setHealth(Math.max(1D, Math.min(health, AttributeUtils.getMaxHealth(player))));
            }
            if (food != null) {
                player.setFoodLevel(Math.max(0, Math.min(20, food)));
            }
            if (saturation != null) {
                player.setSaturation(Math.max(0F, saturation));
            }
            if (effects != null) {
                for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) {
                    player.removePotionEffect(active.getType());
                }
                player.addPotionEffects(effects);
            }
        }
    }
}
