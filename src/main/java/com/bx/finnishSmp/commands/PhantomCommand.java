package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.models.PlayerData;
import com.bx.finnishSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PhantomCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public PhantomCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return true;
        data.setPhantomEnabled(!data.isPhantomEnabled());
        String msg = data.isPhantomEnabled()
                ? plugin.getConfigManager().getMessage("PHANTOM.DISABLED")
                : plugin.getConfigManager().getMessage("PHANTOM.ENABLED");
        player.sendMessage(ColorUtils.toComponent(msg));
        return true;
    }
}
