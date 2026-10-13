package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HelpopCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.helpop";

    private final SmpPlugin plugin;

    public HelpopCommand(SmpPlugin plugin) {
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
