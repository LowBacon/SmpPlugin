package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ClearLagCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public ClearLagCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.clearlag")) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }
        int cleared = plugin.getClearLagManager().clearEntities();
        plugin.getClearLagManager().broadcastSuccess(cleared);
        return true;
    }
}
