package com.bx.finnishSmp.managers;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.AttributeUtils;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.LocationUtils;
import com.bx.finnishSmp.utils.PlayerSettingUtils;
import com.bx.finnishSmp.utils.SoundUtils;
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

/**
 * Owns the protected lobby: the lobby location, its square border, its protection toggles, and
 * every player's last-known SMP location (so the SMP-entrance NPC / /lobby survival can restore
 * them exactly). Config lives in lobby.yml via {@link ConfigManager#getLobby()}.
 *
 * Player SMP locations are intentionally kept in their own small flat file
 * (lobby-playerlocations.yml, in the plugin's data folder) rather than the main player database -
 * this is a self-contained, easily-removable feature that doesn't touch the existing
 * PlayerData/DatabaseManager schema.
 */
public final class LobbyManager {

    private final FinnishSmp plugin;
    private final File playerLocationsFile;

    private volatile Location lobbyLocation;

    // Border (see class Javadoc for why this is a movement clamp, not a vanilla WorldBorder:
    // this project deliberately compiles against spigot-api, not paper-api, to avoid linking to
    // server-specific additions, and Player#setWorldBorder is a Paper-only API).
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

    public LobbyManager(FinnishSmp plugin) {
        this.plugin = plugin;
        this.playerLocationsFile = new File(plugin.getDataFolder(), "lobby-playerlocations.yml");
        loadConfig();
        loadPlayerLocations();
    }

    /** Re-reads lobby.yml. Does not touch the saved-player-location cache. */
    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration lobby = plugin.getConfigManager().getLobby();
        lobbyLocation = LocationUtils.parse(lobby.getString("LOCATIONS.LOBBY-LOCATION", ""));
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

    /** True while the player is standing in the lobby world. Called frequently (chat, scoreboard) - kept cheap. */
    /**
     * True while the player is actually within the lobby (world + border bounds, if a border is
     * enabled). Called frequently (chat, scoreboard, protection) - kept cheap.
     *
     * Checking border bounds (not just world) matters whenever the lobby and the SMP share the
     * same world rather than being separate dedicated worlds - without this, leaving the lobby
     * area would never actually register as "leaving the lobby", so the scoreboard/chat/
     * protection would incorrectly keep treating the whole world as the lobby forever.
     */
    public boolean isInLobby(Player player) {
        return isLocationInLobby(player.getLocation());
    }

    /** Same check as {@link #isInLobby(Player)} but for events that only give us a Location (explosions, redstone, mob-griefing) rather than a Player. */
    public boolean isLocationInLobby(Location location) {
        if (lobbyLocation == null || location.getWorld() == null || !location.getWorld().equals(lobbyLocation.getWorld())) {
            return false;
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

    /**
     * Teleports the player into the lobby (Folia-safe, via SpigotScheduler). If they weren't
     * already in the lobby, their current position is saved first as their "last SMP location"
     * so the NPC / /lobby survival can restore it later. Optionally applies the configured
     * join effects (heal/feed/clear-effects/reset-xp/gamemode/title/actionbar/sound) - callers
     * pass false for applyJoinEffects when this is a plain "/lobby" request rather than an
     * actual server join.
     */
    public boolean sendToLobby(Player player, boolean applyJoinEffects) {
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
            if (Boolean.TRUE.equals(success) && applyJoinEffects) {
                plugin.getSpigotScheduler().runEntity(player, () -> applyJoinEffects(player));
            }
        });
        return true;
    }

    private void applyJoinEffects(Player player) {
        FileConfiguration lobby = config();
        if (lobby.getBoolean("JOIN.HEAL", true)) {
            player.setHealth(AttributeUtils.getMaxHealth(player));
        }
        if (lobby.getBoolean("JOIN.FEED", true)) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
        if (lobby.getBoolean("JOIN.CLEAR-POTION-EFFECTS", true)) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        }
        if (lobby.getBoolean("JOIN.RESET-EXPERIENCE", true)) {
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

    /**
     * Sends the player from the lobby into the SMP: restores their exact saved location, or
     * falls back to the main server spawn (/setspawn) if they've never been in the SMP before.
     * Used by both /lobby survival (and /northsmp survival) and the FancyNpcs NPC.
     */
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
                // Restore inventory before applying other effects
                restoreInventory(player);
                
                if (lobby.getBoolean("LEAVE-TO-SURVIVAL.SET-GAMEMODE", true)) {
                    String modeName = lobby.getString("LEAVE-TO-SURVIVAL.GAMEMODE", "SURVIVAL");
                    try {
                        player.setGameMode(GameMode.valueOf(modeName.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Invalid LEAVE-TO-SURVIVAL.GAMEMODE '" + modeName + "' in lobby.yml - skipping.");
                    }
                }
                for (String rawCommand : lobby.getStringList("LEAVE-TO-SURVIVAL.COMMANDS-ON-ENTER")) {
                    String parsed = rawCommand.replace("%player%", player.getName());
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), parsed);
                }
                player.sendMessage(ColorUtils.toComponent(
                        lobby.getString("LEAVE-TO-SURVIVAL.TELEPORTED-MESSAGE", ""), player));
            });
        });
    }

    /** True if the player should currently be blocked from entering survival (in combat, if configured). */
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
    // Border - see class Javadoc for why this is a movement clamp, not a vanilla WorldBorder.
    // ------------------------------------------------------------------

    public boolean isBorderEnabled() {
        return borderEnabled;
    }

    /** Half-width for display purposes (e.g. /lobby border with no args) - reflects the average of the current rectangle's half-widths, even if it isn't a perfect square. */
    public double getBorderRadius() {
        return ((borderMaxX - borderMinX) + (borderMaxZ - borderMinZ)) / 4.0;
    }

    /** A human-readable "X x Z" description of the current border size, for command output. */
    public String describeBorderSize() {
        return String.format(Locale.ROOT, "%.0f x %.0f", borderMaxX - borderMinX, borderMaxZ - borderMinZ);
    }

    /** Sets a square border of the given half-width, centered on the lobby location. */
    public void setBorderRadius(double radius) {
        applyRadius(Math.max(1.0, radius));
        persistBorder();
    }

    private void applyRadius(double radius) {
        double centerX = lobbyLocation != null ? lobbyLocation.getX() : 0.0;
        double centerZ = lobbyLocation != null ? lobbyLocation.getZ() : 0.0;
        borderMinX = centerX - radius;
        borderMaxX = centerX + radius;
        borderMinZ = centerZ - radius;
        borderMaxZ = centerZ + radius;
    }

    /**
     * First step of setting an exact rectangular border: remembers this corner for the player.
     * Nothing is persisted until {@link #setBorderCorner2(Player, Location)} is also called.
     */
    public void setBorderCorner1(Player player, Location location) {
        pendingBorderCorner1.put(player.getUniqueId(), new double[]{location.getX(), location.getZ()});
    }

    /**
     * Completes a two-corner border definition started with {@link #setBorderCorner1}. Returns
     * false if the player hasn't set a first corner yet this session.
     */
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

    /**
     * If the location is outside the rectangular lobby border, returns a corrected location
     * clamped to just inside the edge. Returns null if already inside (or the border/lobby isn't
     * set up) so callers can treat null as "no change needed".
     */
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

    /** Cosmetic message/sound feedback when a player presses against the border, cooldown-throttled. */
    public void warnIfNearEdge(Player player, Location location) {
        if (!borderEnabled || lobbyLocation == null) {
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
    // Command cooldowns (/lobby, /northsmp)
    // ------------------------------------------------------------------

    /** Returns remaining cooldown seconds (0 if not on cooldown), and starts a fresh cooldown as a side effect if not on cooldown. */
    public long checkAndStartCommandCooldown(Player player) {
        long cooldownSeconds = config().getLong("COOLDOWNS.LOBBY-COMMAND-SECONDS", 5);
        if (cooldownSeconds <= 0 || player.hasPermission("finnishsmp.bypass.lobby")) {
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

    /** Captures the player's current position as their "last SMP location", if they're not currently in the lobby. */
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

    /** Safe to call from any thread - only touches the filesystem. */
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

    /**
     * Saves the player's current inventory and clears it. This should be called when
     * the player is about to enter the lobby from the SMP.
     */
    public void saveAndClearInventory(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        
        // Save current inventory
        SavedInventory saved = new SavedInventory(
            inv.getStorageContents().clone(),
            inv.getArmorContents().clone(),
            inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null
        );
        
        savedInventories.put(player.getUniqueId(), saved);
        
        // Clear inventory
        inv.clear();
        inv.setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        inv.setItemInOffHand(null);
    }

    /**
     * Restores a player's saved inventory. This should be called when the player
     * leaves the lobby and returns to the SMP.
     */
    public void restoreInventory(Player player) {
        SavedInventory saved = savedInventories.remove(player.getUniqueId());
        if (saved == null) {
            return; // No saved inventory to restore
        }
        
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        inv.setStorageContents(saved.storage);
        inv.setArmorContents(saved.armor);
        if (saved.offHand != null) {
            inv.setItemInOffHand(saved.offHand);
        }
    }

    /**
     * Checks if a player has a saved inventory.
     */
    public boolean hasSavedInventory(UUID uuid) {
        return savedInventories.containsKey(uuid);
    }

    /**
     * Clears a player's saved inventory without restoring it.
     * Useful for cleanup on logout.
     */
    public void clearSavedInventory(UUID uuid) {
        savedInventories.remove(uuid);
    }

    /** Immutable snapshot of a location, serializable via the project's "world,x,y,z,yaw,pitch" convention. */
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

    /** Immutable snapshot of a player's inventory for restoration when leaving the lobby. */
    private static final class SavedInventory {
        private final org.bukkit.inventory.ItemStack[] storage;
        private final org.bukkit.inventory.ItemStack[] armor;
        private final org.bukkit.inventory.ItemStack offHand;

        SavedInventory(org.bukkit.inventory.ItemStack[] storage, org.bukkit.inventory.ItemStack[] armor, org.bukkit.inventory.ItemStack offHand) {
            this.storage = storage;
            this.armor = armor;
            this.offHand = offHand;
        }
    }
}
