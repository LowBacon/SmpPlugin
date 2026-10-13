package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DrawCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public DrawCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        plugin.getDuelManager().requestDraw(player);
        return true;
    }
}
