package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetMoneyCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.admin.setmoney";

    private final SmpPlugin plugin;

    public SetMoneyCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /setmoney <player> <amount>"));
            return true;
        }

        double amount;
        try {
            amount = NumberUtils.parse(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.ADMIN.INVALID-AMOUNT")));
            return true;
        }

        if (amount < 0D) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.ADMIN.MUST-BE-NON-NEGATIVE")));
            return true;
        }

        var account = plugin.getEconomyManager().resolveAccount(args[0]);
        if (account == null) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.ADMIN.PLAYER-NOT-FOUND")));
            return true;
        }

        var result = plugin.getEconomyManager().setBalance(account, amount, EconomyReason.ADMIN_SET);
        if (!result.success()) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.ADMIN.PLAYER-NOT-FOUND")));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                "BALANCE.ADMIN.SET-MONEY-SUCCESS",
                "{player}", result.displayName(),
                "{amount}", compactAmount(result.afterBalance()),
                "{amount_full}", fullAmount(result.afterBalance()),
                "{money}", plugin.getCurrencyManager().formatMoneyCompact(result.afterBalance()),
                "{money_full}", fullMoney(result.afterBalance()),
                "{previous_balance}", compactAmount(result.beforeBalance()),
                "{previous_balance_full}", fullAmount(result.beforeBalance()),
                "{previous_balance_money}", plugin.getCurrencyManager().formatMoneyCompact(result.beforeBalance()),
                "{previous_balance_money_full}", fullMoney(result.beforeBalance())
        )));

        Player targetPlayer = Bukkit.getPlayer(result.targetUuid());
        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                    "BALANCE.ADMIN.SET-MONEY-RECEIVED",
                    "{admin}", sender.getName(),
                    "{amount}", compactAmount(result.afterBalance()),
                    "{amount_full}", fullAmount(result.afterBalance()),
                    "{money}", plugin.getCurrencyManager().formatMoneyCompact(result.afterBalance()),
                    "{money_full}", fullMoney(result.afterBalance()),
                    "{previous_balance}", compactAmount(result.beforeBalance()),
                    "{previous_balance_full}", fullAmount(result.beforeBalance()),
                    "{previous_balance_money}", plugin.getCurrencyManager().formatMoneyCompact(result.beforeBalance()),
                    "{previous_balance_money_full}", fullMoney(result.beforeBalance())
            )));
        }

        return true;
    }

    private String compactAmount(double amount) {
        return plugin.getCurrencyManager().formatCompactAmount(CurrencyManager.CurrencyType.MONEY, amount);
    }

    private String fullAmount(double amount) {
        return plugin.getCurrencyManager().formatAmount(CurrencyManager.CurrencyType.MONEY, amount);
    }

    private String fullMoney(double amount) {
        return plugin.getCurrencyManager().format(CurrencyManager.CurrencyType.MONEY, amount, false);
    }
}
