package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TPAutoCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public TPAutoCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) return true;
        data.setTpauto(!data.isTpauto());
        if (data.isTpauto()) {
            plugin.getTPAManager().processQueuedAutoRequests(player.getUniqueId());
        }
        String msg = data.isTpauto()
                ? plugin.getConfigManager().getMessage("TPAUTO.ENABLED")
                : plugin.getConfigManager().getMessage("TPAUTO.DISABLED");
        player.sendMessage(ColorUtils.toComponent(msg));
        return true;
    }
}
