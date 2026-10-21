package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.PayConfirmMenu;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PaymentUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public PayCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }
        if (args.length < 2) { player.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /pay <player> <amount>")); return true; }

        if (args[0].equalsIgnoreCase(player.getName())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.CANT-PAY-SELF")));
            return true;
        }

        // Strict: digits with an optional k/m/b suffix. NaN, "Infinity", exponents and amounts under
        // a cent are refused here instead of failing later as a vague transaction error.
        java.util.OptionalLong cents = com.bx.smpPlugin.economy.Money.parsePositive(args[1]);
        if (cents.isEmpty()) {
            String key = args[1].trim().startsWith("-") || args[1].trim().equals("0")
                    ? "BALANCE.PAY.MUST-BE-POSITIVE" : "BALANCE.PAY.INVALID-AMOUNT";
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(key)));
            return true;
        }
        double amount = com.bx.smpPlugin.economy.Money.toDouble(cents.getAsLong());

        PlayerData senderData = plugin.getPlayerDataManager().get(player);
        if (senderData != null && senderData.isPayConfirmMenuEnabled()) {
            new PayConfirmMenu(plugin, args[0], amount).open(player);
            return true;
        }

        PaymentUtils.transferMoney(plugin, player, args[0], amount);
        return true;
    }
}
