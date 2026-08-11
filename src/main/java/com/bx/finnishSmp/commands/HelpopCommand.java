package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.utils.PermissionUtils;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HelpopCommand implements CommandExecutor {

    private static final String PERMISSION = "finnishsmp.helpop";

    private final FinnishSmp plugin;

    public HelpopCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "HELPOP.PLAYER_ONLY",
                    "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ʜᴇʟᴘᴏᴘ."
            )));
            return true;
        }

        if (!PermissionUtils.has(player, PERMISSION)) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "HELPOP.NO_PERMISSION",
                    "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ʀᴇǫᴜᴇѕᴛ ѕᴛᴀꜰꜰ ᴀѕѕɪѕᴛᴀɴᴄᴇ."
            )));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "HELPOP.USAGE",
                    "&cᴜѕᴀɢᴇ: /helpop <message>"
            )));
            return true;
        }

        plugin.getNetworkStaffAlertManager().sendHelpop(player, String.join(" ", args));
        return true;
    }
}
