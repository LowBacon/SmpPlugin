package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public VanishCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getStaffModeManager().getMessage("PLAYER-ONLY", "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ.")
            ));
            return true;
        }

        if (!plugin.getStaffModeManager().canUseVanish(player)) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getStaffModeManager().getMessage("NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ.")
            ));
            return true;
        }

        if (!plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴍᴜѕᴛ ʙᴇ ɪɴ ѕᴛᴀꜰꜰ ᴍᴏᴅᴇ ᴛᴏ ᴜѕᴇ /vanish."));
            return true;
        }

        plugin.getStaffModeManager().toggleVanish(player);
        return true;
    }
}
