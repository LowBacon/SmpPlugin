package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.SellAllConfirmMenu;
import com.bx.smpPlugin.menus.SellHistoryMenu;
import com.bx.smpPlugin.menus.SellMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public SellCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }

        switch (label.toLowerCase()) {
            case "sell"        -> new SellMenu(plugin).open(player);
            case "sellhand"    -> {
                double total = plugin.getShopManager().sellInventory(player, true);
                if (total <= 0) player.sendMessage(ColorUtils.toComponent(
                        plugin.getConfigManager().getMessage("WORTH.NO-SELLABLE")));
            }
            case "sellall"     -> new SellAllConfirmMenu(plugin).open(player);
            case "sellhistory" -> new SellHistoryMenu(plugin).open(player);
        }
        return true;
    }
}
