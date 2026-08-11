package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.menus.ShopMenu;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public ShopCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return reload(sender);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize(text(
                    "SHOP-GUI.MESSAGES.PLAYER-ONLY",
                    "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ."
            )));
            return true;
        }

        if (command.getName().equalsIgnoreCase("shardshop")) {
            new ShopMenu(plugin, "SHARD-MENU", 0).open(player);
            return true;
        }

        if (args.length == 0) {
            new ShopMenu(plugin).open(player);
            return true;
        }

        player.sendMessage(ColorUtils.toComponent(text(
                "SHOP-GUI.MESSAGES.USAGE",
                "&cᴜѕᴀɢᴇ: /shop [ʀᴇʟᴏᴀᴅ]"
        )));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!PermissionUtils.has(sender, "finnishsmp.admin.shop")) {
            sender.sendMessage(ColorUtils.colorize(text(
                    "SHOP-GUI.MESSAGES.NO-RELOAD-PERMISSION",
                    "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ʀᴇʟᴏᴀᴅ ѕʜᴏᴘ ѕᴇᴛᴛɪɴɢѕ."
            )));
            return true;
        }

        plugin.getConfigManager().reloadShop();
        plugin.getConfigManager().reloadMenus();
        plugin.getConfigManager().reloadSounds();
        plugin.getShopManager().reload();
        sender.sendMessage(ColorUtils.colorize(text(
                "SHOP-GUI.MESSAGES.RELOADED",
                "&aѕʜᴏᴘ ᴄᴏɴꜰɪɢᴜʀᴀᴛɪᴏɴ ʀᴇʟᴏᴀᴅᴇᴅ."
        )));
        return true;
    }

    private String text(String path, String fallback) {
        return plugin.getConfigManager().getShop().getString(path, fallback);
    }
}
