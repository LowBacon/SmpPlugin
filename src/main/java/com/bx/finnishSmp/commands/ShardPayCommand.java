package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.menus.PayConfirmMenu;
import com.bx.finnishSmp.models.PlayerData;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.NumberUtils;
import com.bx.finnishSmp.utils.PaymentUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShardPayCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public ShardPayCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }
        if (args.length < 2) { player.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /shardpay <player> <amount>")); return true; }

        if (args[0].equalsIgnoreCase(player.getName())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.CANT-PAY-SELF")));
            return true;
        }
        long amount;
        try { amount = NumberUtils.parseLong(args[1]); }
        catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.INVALID-AMOUNT")));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.MUST-BE-POSITIVE")));
            return true;
        }
        PlayerData senderData = plugin.getPlayerDataManager().get(player);
        if (senderData != null && senderData.isPayConfirmMenuEnabled()) {
            new PayConfirmMenu(plugin, args[0], amount).open(player);
            return true;
        }

        PaymentUtils.transferShards(plugin, player, args[0], amount);
        return true;
    }
}
