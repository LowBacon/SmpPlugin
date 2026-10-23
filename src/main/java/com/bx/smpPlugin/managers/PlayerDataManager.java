// SmpPlugin by LowBacon
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
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
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
                EconomyManager economy = plugin.getEconomyManager();
                long offlineVersion = economy == null ? 0L : economy.offlineVersion(uuid);
                if (offlineVersion > 0L && (data == null || data.getMoneyVersion() < offlineVersion)) {
                    // The balance changed (e.g. an auction sale) after the login read the row; read it again.
                    data = plugin.getDatabaseManager().loadPlayer(uuid);
                }
            } else {
                data = plugin.getDatabaseManager().loadPlayer(uuid);
            }
            if (plugin.getEconomyManager() != null) {
                plugin.getEconomyManager().forgetOfflineVersion(uuid);
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

    public void discardWithoutSaving(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
            savingAfterQuit.remove(uuid);
        }
    }

    public boolean isSaving(UUID uuid) {
        return uuid != null && savingAfterQuit.containsKey(uuid);
    }

    public PlayerData getSaving(UUID uuid) {
        return uuid == null ? null : savingAfterQuit.get(uuid);
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

    public void refreshRemovedSettingOptions() {
        for (PlayerData data : cache.values()) {
            PlayerSettingDefaults.applyDisabledOptions(plugin, data);
        }
    }

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
