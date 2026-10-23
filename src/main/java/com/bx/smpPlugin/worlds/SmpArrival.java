// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.LobbyManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmpArrival {

    private static final long FIRST_DELAY = 30L;
    private static final long SPACING = 20L;

    private static final Map<UUID, Map<String, Runnable>> WAITING = new ConcurrentHashMap<>();

    private SmpArrival() {
    }

    public static void run(SmpPlugin plugin, Player player, String kind, Runnable action) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!inLobby(plugin, player)) {
            action.run();
            return;
        }
        WAITING.computeIfAbsent(player.getUniqueId(), uuid -> new LinkedHashMap<>()).put(kind, action);
    }

    public static void arrived(SmpPlugin plugin, Player player) {
        Map<String, Runnable> waiting = WAITING.remove(player.getUniqueId());
        if (waiting == null || waiting.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Runnable>> entries = new ArrayList<>(waiting.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Runnable> entry = entries.get(i);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                // Went straight back to the lobby: keep it for the next arrival instead.
                run(plugin, player, entry.getKey(), entry.getValue());
            }, FIRST_DELAY + SPACING * i);
        }
    }

    public static int waiting(UUID uuid) {
        Map<String, Runnable> waiting = WAITING.get(uuid);
        return waiting == null ? 0 : waiting.size();
    }

    public static void forget(UUID uuid) {
        WAITING.remove(uuid);
    }

    static void clearAll() {
        WAITING.clear();
    }

    public static boolean inLobby(SmpPlugin plugin, Player player) {
        LobbyManager lobby = plugin.getLobbyManager();
        if (lobby != null && lobby.isInLobby(player)) {
            return true;
        }
        WorldSystem worlds = plugin.getWorldSystem();
        return worlds != null && worlds.isWorld(player.getWorld(), "lobby");
    }
}
