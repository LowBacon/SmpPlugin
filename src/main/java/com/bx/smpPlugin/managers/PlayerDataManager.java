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
    /**
     * Offline players whose row was changed in the last few minutes (money or shards paid to them,
     * an admin edit). Every change to an offline player goes through this one copy, so two changes
     * close together cannot each read the old row and overwrite the other, and a player who joins
     * right after gets the copy instead of a row the save has not reached yet.
     */
    private final Map<UUID, OfflineCopy> offlineCopies = new ConcurrentHashMap<>();
    static final long OFFLINE_COPY_TTL_MS = 5L * 60_000L;
    static final int OFFLINE_COPY_LIMIT = 5_000;

    private record OfflineCopy(PlayerData data, long touchedAt) {
    }

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
            OfflineCopy recent = offlineCopies.remove(uuid);
            if (recent != null) {
                data = recent.data();
            }
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
            offlineCopies.remove(uuid);
        }
    }

    public boolean isSaving(UUID uuid) {
        return uuid != null && (savingAfterQuit.containsKey(uuid) || offlineCopies.containsKey(uuid));
    }

    /**
     * The one copy of a player's data to change, online or not: the live copy for an online
     * player, otherwise the copy still being saved after they quit, a recent offline copy, or the
     * stored row. Null when the player has no row. Reads the database for a player not seen
     * recently, so call it for single changes (a payment, an admin edit), not in loops.
     */
    public PlayerData getOrLoadOffline(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerData live = cache.get(uuid);
        if (live != null) {
            return live;
        }
        PlayerData saving = savingAfterQuit.get(uuid);
        if (saving != null) {
            return saving;
        }
        OfflineCopy recent = offlineCopies.get(uuid);
        if (recent != null) {
            return recent.data();
        }
        return plugin.getDatabaseManager().loadPlayer(uuid);
    }

    /**
     * Saves a change made through {@link #getOrLoadOffline}. An online player's copy is saved by the
     * normal auto-save; an offline one is remembered for a few minutes and written on the database
     * thread, after which {@code afterWrite} runs there (it may be null).
     */
    public void saveOffline(PlayerData data, Runnable afterWrite) {
        if (data == null) {
            return;
        }
        UUID uuid = data.getUuid();
        if (cache.get(uuid) == data) {
            if (afterWrite != null) {
                afterWrite.run();
            }
            return;
        }
        long now = System.currentTimeMillis();
        offlineCopies.put(uuid, new OfflineCopy(data, now));
        pruneOfflineCopies(now);
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().savePlayer(data);
            if (afterWrite != null) {
                afterWrite.run();
            }
        });
    }

    /** Offline copies kept right now, for /smp diagnostics. */
    public int offlineCopyCount() {
        return offlineCopies.size();
    }

    private void pruneOfflineCopies(long now) {
        offlineCopies.values().removeIf(copy -> now - copy.touchedAt() > OFFLINE_COPY_TTL_MS);
        if (offlineCopies.size() > OFFLINE_COPY_LIMIT) {
            // Oldest first; each was already queued for saving, so dropping one loses nothing.
            offlineCopies.entrySet().stream()
                    .sorted(java.util.Comparator.comparingLong(entry -> entry.getValue().touchedAt()))
                    .limit(offlineCopies.size() - OFFLINE_COPY_LIMIT)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(offlineCopies::remove);
        }
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
        offlineCopies.clear();
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
