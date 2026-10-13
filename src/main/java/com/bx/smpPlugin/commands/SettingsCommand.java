package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.PlayerSettingsMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SettingsCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public SettingsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }
        if (!plugin.getConfigManager().isCommandEnabled("SETTINGS")) {
            player.sendMessage(ColorUtils.toComponent("&cѕᴇᴛᴛɪɴɢѕ ᴄᴏᴍᴍᴀɴᴅ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }
        // Paper and Purpur show the settings as a dialog screen; Spigot, or a disabled screen, gets the chest menu.
        if (plugin.getDialogManager() != null && plugin.getDialogManager().openSettings(player)) {
            return true;
        }
        new PlayerSettingsMenu(plugin).open(player);
        return true;
    }
}
