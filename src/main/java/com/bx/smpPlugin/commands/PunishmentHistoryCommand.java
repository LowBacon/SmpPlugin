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

        if (args.length == 0) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.USAGE",
                    "&cᴜѕᴀɢᴇ: /" + label + " <player>"
            )));
            return true;
        }

        UUID targetUuid = plugin.getPunishmentManager().resolveTargetUuid(args[0], true).orElse(null);
        if (targetUuid == null) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "PUNISHMENTS.NOT-FOUND",
                    "&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."
            )));
            return true;
        }

        new PunishmentHistoryMenu(plugin, targetUuid, false).open(player);
        return true;
    }
}
