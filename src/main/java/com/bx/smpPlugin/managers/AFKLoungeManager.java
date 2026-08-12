package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.LocationUtils;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AFKLoungeManager {

    private final SmpPlugin plugin;
    private final File configFile;

    private volatile Location afkLoungeLocation;
    private volatile boolean borderEnabled;
    private volatile double borderMinX;
    private volatile double borderMaxX;
    private volatile double borderMinZ;
    private volatile double borderMaxZ;
    private volatile String borderWorld;

    private final Map<UUID, Long> lastShardReward = new ConcurrentHashMap<>();
    private final Set<UUID> playersInLounge = new HashSet<>();
    private final Map<UUID, Location> pendingBorderCorner1 = new ConcurrentHashMap<>();

    public AFKLoungeManager(SmpPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "afk-lounge.yml");
        loadConfig();
    }

    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        afkLoungeLocation = LocationUtils.parse(config.getString("LOCATIONS.AFK-LOUNGE-LOCATION", ""));
        borderEnabled = config.getBoolean("BORDER.ENABLED", true);
        borderMinX = config.getDouble("BORDER.MIN-X", 0);
        borderMaxX = config.getDouble("BORDER.MAX-X", 0);
        borderMinZ = config.getDouble("BORDER.MIN-Z", 0);
        borderMaxZ = config.getDouble("BORDER.MAX-Z", 0);
        borderWorld = config.getString("BORDER.WORLD", "world");
    }

    public boolean isEnabled() {
        return plugin.getFeatureManager().isEnabled(FeatureManager.Feature.AFK)
                && getConfig().getBoolean("SETTINGS.ENABLED", true);
    }

    public FileConfiguration getConfig() {
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                var inputStream = plugin.getResource("afk-lounge.yml");
                if (inputStream != null) {
                    var defaultConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8));
                    defaultConfig.save(configFile);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create afk-lounge.yml: " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public void save() {
        try {
            getConfig().save(configFile);
            loadConfig();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save afk-lounge.yml: " + e.getMessage());
        }
    }

    public Location getAFKLoungeLocation() {
        return afkLoungeLocation;
    }

    public void setAFKLoungeLocation(Location location) {
        if (location != null) {
            afkLoungeLocation = location;
            FileConfiguration config = getConfig();
            config.set("LOCATIONS.AFK-LOUNGE-LOCATION", LocationUtils.serialize(location));
            save();
        }
    }

    public boolean isBorderEnabled() {
        return borderEnabled && afkLoungeLocation != null;
    }

    public boolean isPlayerInLounge(Player player) {
        if (!isBorderEnabled()) {
            return false;
        }
        Location loc = player.getLocation();
        return loc.getWorld().getName().equals(borderWorld)
                && loc.getX() >= borderMinX && loc.getX() <= borderMaxX
                && loc.getZ() >= borderMinZ && loc.getZ() <= borderMaxZ;
    }

    public void setBorderRadius(double radius) {
        if (afkLoungeLocation == null) {
            return;
        }
        double centerX = afkLoungeLocation.getX();
        double centerZ = afkLoungeLocation.getZ();
        borderMinX = centerX - radius;
        borderMaxX = centerX + radius;
        borderMinZ = centerZ - radius;
        borderMaxZ = centerZ + radius;
        borderWorld = afkLoungeLocation.getWorld().getName();
        saveBorder();
    }

    public void setBorderCorners(double x1, double z1, double x2, double z2) {
        if (afkLoungeLocation == null) {
            return;
        }
        borderMinX = Math.min(x1, x2);
        borderMaxX = Math.max(x1, x2);
        borderMinZ = Math.min(z1, z2);
        borderMaxZ = Math.max(z1, z2);
        borderWorld = afkLoungeLocation.getWorld().getName();
        saveBorder();
    }

    private void saveBorder() {
        FileConfiguration config = getConfig();
        config.set("BORDER.MIN-X", borderMinX);
        config.set("BORDER.MAX-X", borderMaxX);
        config.set("BORDER.MIN-Z", borderMinZ);
        config.set("BORDER.MAX-Z", borderMaxZ);
        config.set("BORDER.WORLD", borderWorld);
        save();
    }

    public int getShardRewardIntervalSeconds() {
        return getConfig().getInt("SETTINGS.SHARD-REWARD-INTERVAL-SECONDS", 30);
    }

    public int getShardRewardAmount() {
        return getConfig().getInt("SETTINGS.SHARD-REWARD-AMOUNT", 1);
    }

    public void recordShardReward(UUID uuid) {
        lastShardReward.put(uuid, System.currentTimeMillis());
    }

    public boolean canReceiveShard(UUID uuid) {
        Long lastReward = lastShardReward.get(uuid);
        if (lastReward == null) {
            return true;
        }
        return (System.currentTimeMillis() - lastReward) >= (getShardRewardIntervalSeconds() * 1000L);
    }

    public void playerEnteredLounge(UUID uuid) {
        playersInLounge.add(uuid);
    }

    public void playerLeftLounge(UUID uuid) {
        playersInLounge.remove(uuid);
        lastShardReward.remove(uuid);
    }

    public Set<UUID> getPlayersInLounge() {
        return new HashSet<>(playersInLounge);
    }

    public List<String> getScoreboardLines() {
        return getConfig().getStringList("SCOREBOARD.LINES");
    }

    public String getScoreboardTitle() {
        return getConfig().getString("SCOREBOARD.TITLE", "&6&lAFK LOUNGE");
    }

    public String getShardRewardMessage() {
        return getConfig().getString("MESSAGES.SHARD-REWARD", "&a+1 Shard earned! (AFK Lounge)");
    }

    public String getLeftLoungeMessage() {
        return getConfig().getString("MESSAGES.LEFT-LOUNGE", "&cYou must be in the AFK Lounge to earn shards.");
    }

    public String getEnteredBorderMessage() {
        return getConfig().getString("BORDER.ENTERED-BORDER-MESSAGE", "&aYou entered the AFK Lounge!");
    }

    public String getLeftBorderMessage() {
        return getConfig().getString("BORDER.LEFT-BORDER-MESSAGE", "&cYou left the AFK Lounge!");
    }

    public void removePlayer(UUID uuid) {
        lastShardReward.remove(uuid);
        playersInLounge.remove(uuid);
        pendingBorderCorner1.remove(uuid);
    }

    public void setPendingBorderCorner1(UUID uuid, Location location) {
        if (location != null) {
            pendingBorderCorner1.put(uuid, location);
        }
    }

    public Location getPendingBorderCorner1(UUID uuid) {
        return pendingBorderCorner1.get(uuid);
    }

    public void clearPendingBorderCorner1(UUID uuid) {
        pendingBorderCorner1.remove(uuid);
    }

    public boolean hasPendingBorderCorner1(UUID uuid) {
        return pendingBorderCorner1.containsKey(uuid);
    }

    public boolean isCooldownActionBarEnabled() {
        return getConfig().getBoolean("SETTINGS.SHOW-COOLDOWN-ACTIONBAR", true);
    }

    public long getRemainingShardCooldownSeconds(UUID uuid) {
        int interval = getShardRewardIntervalSeconds();
        Long lastReward = lastShardReward.get(uuid);
        if (lastReward == null) {
            return 0;
        }
        long elapsedSeconds = (System.currentTimeMillis() - lastReward) / 1000L;
        long remaining = interval - elapsedSeconds;
        return Math.max(0, remaining);
    }

    public String getNextShardTimerMessage(long remainingSeconds) {
        String template = getConfig().getString("MESSAGES.NEXT-SHARD-IN", "&eNext shard in &f{seconds}&es");
        return template.replace("{seconds}", String.valueOf(remainingSeconds));
    }
}
