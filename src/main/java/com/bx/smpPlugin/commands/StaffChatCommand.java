package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffChatCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.staff.chat.use";

    private final SmpPlugin plugin;

    public StaffChatCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !PermissionUtils.has(player, PERMISSION)) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault("STAFFCHAT.NO_PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴜѕᴇ ѕᴛᴀꜰꜰ ᴄʜᴀᴛ.")
            ));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault("STAFFCHAT.USAGE", "&cᴜѕᴀɢᴇ: /staffchat <message>")
            ));
            return true;
        }

        plugin.getNetworkStaffChatManager().sendStaffChat(sender, String.join(" ", args));
        return true;
    }
}
