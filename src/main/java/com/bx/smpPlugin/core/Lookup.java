package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Finds a player by name without stalling the server. An online player answers at once; anyone
 * else needs the database and possibly Mojang (for a name the server has never seen), so that part
 * runs off the main thread and the answer comes back on the sender's thread.
 */
public final class Lookup {

    private Lookup() {
    }

    /** Calls back with the account, or null when nobody by that name has joined. */
    public static void account(SmpPlugin plugin, CommandSender sender, String name,
                               Consumer<EconomyManager.AccountReference> callback) {
        Player online = name == null ? null : Bukkit.getPlayerExact(name);
        if (online != null) {
            callback.accept(new EconomyManager.AccountReference(online.getUniqueId(), online.getName(), online));
            return;
        }
        plugin.getSpigotScheduler().runAsync(() -> {
            EconomyManager.AccountReference account = plugin.getEconomyManager().resolveAccount(name);
            Runnable reply = () -> callback.accept(account);
            if (sender instanceof Player player) {
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (player.isOnline()) {
                        reply.run();
                    }
                });
            } else {
                plugin.getSpigotScheduler().runGlobal(reply);
            }
        });
    }
}
