package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.utils.PermissionUtils;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.menus.BillfordMenu;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.SoundUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BillfordCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public BillfordCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        String permission = plugin.getConfigManager().getBillford()
                .getString("ACCESS.PERMISSION", "");
        if (!permission.isBlank() && !PermissionUtils.has(player, permission)) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessage(
                            "BILLFORD.NO-PERMISSION",
                            "{permission}",
                            permission
                    )
            ));
            return true;
        }

        SoundUtils.play(player, plugin.getConfigManager().getSound("BILLFORD.OPEN"));
        new BillfordMenu(plugin).open(player);
        return true;
    }
}
