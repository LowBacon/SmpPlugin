package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.StatsMenu;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /stats [player]: the stats menu for players, a short summary for the console. */
public class StatsCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public StatsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " [player]"));
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /" + label + " <player>");
                return true;
            }
            new StatsMenu(plugin, player.getUniqueId(), plugin.getHideManager().publicName(player)).open(player);
            return true;
        }

        // Disguised players are found by their alias, never by the real name behind it.
        Player online = plugin.getHideManager().findOnlinePlayer(sender, args[0]);
        if (online != null) {
            show(sender, online.getUniqueId(), plugin.getHideManager().publicName(online));
            return true;
        }

        String input = args[0];
        plugin.getDatabaseManager().executeAsync(() -> {
            UUID uuid = plugin.getHideManager().findKnownPlayerUuid(sender, input);
            String known = uuid == null ? null : plugin.getDatabaseManager().getLastKnownUsername(uuid);
            Runnable reply = () -> {
                if (uuid == null) {
                    sender.sendMessage(ColorUtils.toComponent("&cNo player named &f" + input + " &chas joined this server."));
                    return;
                }
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                String fallback = known != null && !known.isBlank() ? known
                        : offline.getName() != null ? offline.getName() : input;
                show(sender, uuid, plugin.getHideManager().publicName(uuid, fallback));
            };
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
        return true;
    }

    private void show(CommandSender sender, UUID uuid, String name) {
        if (sender instanceof Player player) {
            new StatsMenu(plugin, uuid, name).open(player);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null) {
            data = plugin.getDatabaseManager().loadPlayer(uuid);
        }
        if (data == null) {
            sender.sendMessage("No stats recorded for " + name + ".");
            return;
        }
        sender.sendMessage(ColorUtils.toComponent("&7&m------------------"));
        sender.sendMessage(ColorUtils.toComponent("&b" + name + "&7's stats"));
        sender.sendMessage(ColorUtils.toComponent("&7Money: &f" + plugin.getCurrencyManager().formatMoneyCompact(data.getMoney())));
        sender.sendMessage(ColorUtils.toComponent("&7Shards: &f" + plugin.getCurrencyManager().formatShardsCompact(data.getShards())));
        sender.sendMessage(ColorUtils.toComponent("&7Kills: &f" + data.getKills() + " &7Deaths: &f" + data.getDeaths()));
        sender.sendMessage(ColorUtils.toComponent("&7Playtime: &f" + NumberUtils.formatTimeLong(data.getTotalPlaytimeSeconds())));
        sender.sendMessage(ColorUtils.toComponent("&7&m------------------"));
    }
}
