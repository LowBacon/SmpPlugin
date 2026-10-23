// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.MainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MenuCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public MenuCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        new MainMenu(plugin, player).open(player);
        return true;
    }
}
