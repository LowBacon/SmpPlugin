package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.RTPMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RTPCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public RTPCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        if (!plugin.getConfigManager().isCommandEnabled("RTP")) {
            player.sendMessage(ColorUtils.toComponent("&cʀᴛᴘ ᴄᴏᴍᴍᴀɴᴅ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (!plugin.getRtpManager().isEnabled()) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getRtp().getString("MESSAGES.DISABLED", "&cʀᴛᴘ ɪѕ ᴅɪѕᴀʙʟᴇᴅ.")));
            return true;
        }

        if (args.length == 0) {
            new RTPMenu(plugin).open(player);
            return true;
        }

        plugin.getRtpManager().queueCommandTeleport(player, args[0]);
        return true;
    }
}
