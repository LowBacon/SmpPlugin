package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.BillfordMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BillfordCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public BillfordCommand(SmpPlugin plugin) {
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
