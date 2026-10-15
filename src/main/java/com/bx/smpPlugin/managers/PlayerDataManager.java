package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.listeners.JoinPreloadListener;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.PlayerSettingDefaults;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final SmpPlugin plugin;
    /** Read from leaderboards and placeholders off the main thread, so it has to be concurrent. */
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    /**
     * Players whose quit save is still queued on the database thread. A quick rejoin takes this
     * copy back instead of reading a row the save has not reached yet.
     */
    private final Map<UUID, PlayerData> savingAfterQuit = new ConcurrentHashMap<>();

    public PlayerDataManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerData loadOrCreate(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = cache.get(uuid);
        if (data == null) {
            data = savingAfterQuit.get(uuid);
        }
        if (data == null) {
            JoinPreloadListener preloader = plugin.getJoinPreloadListener();
            if (preloader != null && preloader.hasPreloaded(uuid)) {
                data = preloader.takePlayerData(uuid);
            } else {
                data = plugin.getDatabaseManager().loadPlayer(uuid);
            }
        }
        if (data == null) {
            data = new PlayerData(uuid, player.getName());
            PlayerSettingDefaults.applyDefaults(plugin, data);
            double startMoney = plugin.getConfigManager().getConfig()
                    .getDouble("SETTINGS.MONEY-PER-DEFAULT", 1000.0);
            data.setMoney(startMoney);
            plugin.getDatabaseManager().savePlayerAsync(data);
        } else {
            data.setUsername(player.getName());
        }
        PlayerSettingDefaults.applyDisabledOptions(plugin, data);
        data.setSessionStartMillis(System.currentTimeMillis());
        cache.put(uuid, data);
        return data;
    }

    /** Commits the session and queues the save; the row is written on the database thread. */
    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data == null) {
            return;
        }
        data.commitSession();
        savingAfterQuit.put(uuid, data);
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().savePlayer(data);
            savingAfterQuit.remove(uuid, data);
        });
    }

    /**
     * Drops one player's cached copy without saving it. A restore has just rewritten their row, so
     * writing the in-memory totals back would undo it.
     */
    public void discardWithoutSaving(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
            savingAfterQuit.remove(uuid);
        }
    }

    public boolean isSaving(UUID uuid) {
        return uuid != null && savingAfterQuit.containsKey(uuid);
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            data.commitSession();
            plugin.getDatabaseManager().savePlayer(data);
        }
    }

    public PlayerData get(UUID uuid) {
        return uuid == null ? null : cache.get(uuid);
    }

    public PlayerData get(Player player) {
        return cache.get(player.getUniqueId());
    }

    public Collection<PlayerData> getAll() {
        return cache.values();
    }

    public void discardAllForServerWipe() {
        cache.clear();
        savingAfterQuit.clear();
    }

    public boolean isLoaded(UUID uuid) {
        return uuid != null && cache.containsKey(uuid);
    }

    /** Re-pins options removed with ENABLED: false after menus.yml was reloaded. */
    public void refreshRemovedSettingOptions() {
        for (PlayerData data : cache.values()) {
            PlayerSettingDefaults.applyDisabledOptions(plugin, data);
        }
    }

    /** Periodic dirty-save without committing sessions */
    public void autoSaveDirty() {
        if (plugin.getStatsWipeManager() != null && plugin.getStatsWipeManager().isWipeInProgress()) {
            return;
        }
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                plugin.getDatabaseManager().savePlayer(data);
            }
        }
    }
}
