// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.PunishmentHistoryMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PunishmentHistoryCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public PunishmentHistoryCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.PLAYER-ONLY",
                    "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ."
            ));
            return true;
        }

        if (!plugin.getPunishmentManager().canView(player)) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.NO-PERMISSION",
                    "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴠɪᴇᴡ ᴘᴜɴɪѕʜᴍᴇɴᴛ ʜɪѕᴛᴏʀʏ."
            )));
            return true;
        }

        // No name: the server-wide list of every punishment, newest first.
        if (args.length == 0) {
            new PunishmentHistoryMenu(plugin, null, null, false).open(player);
            return true;
        }

        // Name lookups can fall back to the database, so they run off the main thread.
        String input = args[0];
        plugin.getDatabaseManager().executeAsync(() -> {
            UUID targetUuid = plugin.getPunishmentManager().resolveTargetUuid(input, true).orElse(null);
            String targetName = targetUuid == null ? null : plugin.getPunishmentManager().resolveTargetName(targetUuid, input);
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (targetUuid == null) {
                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                            "PUNISHMENTS.NOT-FOUND",
                            "&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."
                    )));
                    return;
                }
                new PunishmentHistoryMenu(plugin, targetUuid, targetName, false).open(player);
            });
        });
        return true;
    }
}
