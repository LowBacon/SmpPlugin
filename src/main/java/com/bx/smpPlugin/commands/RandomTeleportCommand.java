package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RandomTeleportCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public RandomTeleportCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ."));
            return true;
        }

        if (!plugin.getStaffModeManager().canUseRandomTeleport(player)) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getStaffModeManager().getMessage("NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ.")
            ));
            return true;
        }

        if (plugin.getStaffModeManager().teleportToRandomPlayer(player) == null) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault("RANDOMTP.NO_PLAYERS", "&cɴᴏ ᴏᴛʜᴇʀ ᴘʟᴀʏᴇʀѕ ᴀᴠᴀɪʟᴀʙʟᴇ ꜰᴏʀ ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ")
            ));
        }
        return true;
    }
}
