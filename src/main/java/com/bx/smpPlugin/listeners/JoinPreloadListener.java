package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.Home;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a joining player's stats and homes while they are still logging in. That event runs off
 * the main thread, so by the time the join event fires the rows are already in memory and the
 * join itself never waits on the database. Anything not picked up within a minute is dropped.
 */
public class JoinPreloadListener implements Listener {

    private static final long MAX_AGE_MS = 60_000L;

    private record Preloaded(long loadedAt, PlayerData data, List<Home> homes) {
    }

    private final SmpPlugin plugin;
    private final Map<UUID, Preloaded> preloaded = new ConcurrentHashMap<>();

    public JoinPreloadListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        prune();
        // Still online elsewhere, or their quit save has not landed yet: the live copy wins.
        if (plugin.getPlayerDataManager().isLoaded(uuid) || plugin.getPlayerDataManager().isSaving(uuid)) {
            return;
        }
        try {
            PlayerData data = plugin.getDatabaseManager().loadPlayer(uuid);
            List<Home> homes = plugin.getDatabaseManager().loadHomes(uuid);
            preloaded.put(uuid, new Preloaded(System.currentTimeMillis(), data, homes));
        } catch (RuntimeException exception) {
            // The join falls back to reading the rows itself.
            preloaded.remove(uuid);
        }
    }

    /** The stats row read at login, or null when there is none (new player or nothing preloaded). */
    public PlayerData takePlayerData(UUID uuid) {
        Preloaded entry = fresh(uuid);
        return entry == null ? null : entry.data();
    }

    /** True when a login read happened, so a null {@link #takePlayerData} means a brand-new player. */
    public boolean hasPreloaded(UUID uuid) {
        return fresh(uuid) != null;
    }

    public List<Home> takeHomes(UUID uuid) {
        Preloaded entry = fresh(uuid);
        return entry == null ? null : entry.homes();
    }

    /** Called once the join has used what it needs. */
    public void discard(UUID uuid) {
        preloaded.remove(uuid);
    }

    private Preloaded fresh(UUID uuid) {
        Preloaded entry = uuid == null ? null : preloaded.get(uuid);
        if (entry == null || System.currentTimeMillis() - entry.loadedAt() > MAX_AGE_MS) {
            return null;
        }
        return entry;
    }

    private void prune() {
        long now = System.currentTimeMillis();
        preloaded.values().removeIf(entry -> now - entry.loadedAt() > MAX_AGE_MS);
    }
}
