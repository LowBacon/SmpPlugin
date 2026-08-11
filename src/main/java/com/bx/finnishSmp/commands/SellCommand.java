package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.menus.SellAllConfirmMenu;
import com.bx.finnishSmp.menus.SellHistoryMenu;
import com.bx.finnishSmp.menus.SellMenu;
import com.bx.finnishSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public SellCommand(FinnishSmp plugin) {
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
