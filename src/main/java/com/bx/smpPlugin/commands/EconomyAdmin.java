// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.economy.Money;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.models.EconomyFailureReason;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class EconomyAdmin {

    enum Action { GIVE, TAKE, SET, RESET }

    private final SmpPlugin plugin;

    EconomyAdmin(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    void run(CommandSender sender, Action action, String target, String amountText, String note) {
        double amount;
        if (action == Action.RESET) {
            amount = plugin.getConfigManager().getConfig().getDouble("SETTINGS.MONEY-PER-DEFAULT", 1000.0D);
        } else {
            try {
                amount = NumberUtils.parse(amountText);
            } catch (NumberFormatException e) {
                message(sender, "BALANCE.ADMIN.INVALID-AMOUNT");
                return;
            }
        }
        if (!Double.isFinite(amount) || (action == Action.SET || action == Action.RESET ? amount < 0 : amount <= 0)) {
            message(sender, action == Action.SET || action == Action.RESET
                    ? "BALANCE.ADMIN.MUST-BE-NON-NEGATIVE" : "BALANCE.ADMIN.MUST-BE-POSITIVE");
            return;
        }

        EconomyManager economy = plugin.getEconomyManager();
        EconomyManager.AccountReference account = economy.resolveAccount(target);
        if (account == null) {
            message(sender, "BALANCE.ADMIN.PLAYER-NOT-FOUND");
            return;
        }

        EconomyContext context = EconomyContext.of(sender.getName(), note);
        EconomyTransactionResult result = switch (action) {
            case GIVE -> economy.deposit(account, amount, EconomyReason.ADMIN_ADD, context);
            case TAKE -> economy.withdraw(account, amount, EconomyReason.ADMIN_REMOVE, context);
            case SET -> economy.setBalance(account, amount, EconomyReason.ADMIN_SET, context);
            case RESET -> economy.setBalance(account, amount, EconomyReason.ADMIN_RESET, context);
        };
        if (!result.success()) {
            failure(sender, result);
            return;
        }

        String successKey = switch (action) {
            case GIVE -> "BALANCE.ADMIN.ADD-MONEY-SUCCESS";
            case TAKE -> "BALANCE.ADMIN.REMOVE-MONEY-SUCCESS";
            case SET, RESET -> "BALANCE.ADMIN.SET-MONEY-SUCCESS";
        };
        String receivedKey = switch (action) {
            case GIVE -> "BALANCE.ADMIN.ADD-MONEY-RECEIVED";
            case TAKE -> "BALANCE.ADMIN.REMOVE-MONEY-RECEIVED";
            case SET, RESET -> "BALANCE.ADMIN.SET-MONEY-RECEIVED";
        };
        // SET reports the new balance as {amount}, like /setmoney always did.
        double shown = action == Action.SET || action == Action.RESET ? result.afterBalance() : result.amount();
        sender.sendMessage(ColorUtils.toComponent(fill(successKey, result, shown, sender.getName())));
        Player targetPlayer = result.targetUuid() == null ? null : Bukkit.getPlayer(result.targetUuid());
        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent(fill(receivedKey, result, shown, sender.getName())));
        }
    }

    private void failure(CommandSender sender, EconomyTransactionResult result) {
        EconomyFailureReason reason = result.failureReason();
        switch (reason) {
            case INSUFFICIENT_FUNDS -> sender.sendMessage(ColorUtils.toComponent(fill(
                    "BALANCE.ADMIN.TARGET-NOT-ENOUGH-MONEY", result, result.amount(), sender.getName())));
            case BALANCE_LIMIT -> Text.send(sender, "ECONOMY.ADMIN-LIMIT",
                    "&cThat would take {player} above the balance limit of &f{limit}&c.",
                    "{player}", result.displayName(),
                    "{limit}", plugin.getCurrencyManager().formatMoney(
                            Money.toUnits(plugin.getEconomyManager().maxBalanceCents())));
            case INVALID_AMOUNT -> message(sender, "BALANCE.ADMIN.INVALID-AMOUNT");
            case STORAGE_UNAVAILABLE, STORAGE_ERROR -> Text.send(sender, "ECONOMY.STORAGE-FAILED",
                    "&cThe database did not accept the change, so nothing was changed. See the console.");
            default -> message(sender, "BALANCE.ADMIN.PLAYER-NOT-FOUND");
        }
    }

    private void message(CommandSender sender, String key) {
        sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(key)));
    }

    private String fill(String key, EconomyTransactionResult result, double amount, String admin) {
        CurrencyManager currency = plugin.getCurrencyManager();
        return plugin.getConfigManager().getMessage(key,
                "{player}", result.displayName(),
                "{admin}", admin,
                "{amount}", currency.formatCompactAmount(CurrencyManager.CurrencyType.MONEY, amount),
                "{amount_full}", currency.formatAmount(CurrencyManager.CurrencyType.MONEY, amount),
                "{money}", currency.formatMoneyCompact(amount),
                "{money_full}", currency.format(CurrencyManager.CurrencyType.MONEY, amount, false),
                "{balance}", currency.formatCompactAmount(CurrencyManager.CurrencyType.MONEY, result.afterBalance()),
                "{balance_full}", currency.formatAmount(CurrencyManager.CurrencyType.MONEY, result.afterBalance()),
                "{balance_money}", currency.formatMoneyCompact(result.afterBalance()),
                "{balance_money_full}", currency.format(CurrencyManager.CurrencyType.MONEY, result.afterBalance(), false),
                "{previous_balance}", currency.formatCompactAmount(CurrencyManager.CurrencyType.MONEY, result.beforeBalance()),
                "{previous_balance_full}", currency.formatAmount(CurrencyManager.CurrencyType.MONEY, result.beforeBalance()),
                "{previous_balance_money}", currency.formatMoneyCompact(result.beforeBalance()),
                "{previous_balance_money_full}", currency.format(CurrencyManager.CurrencyType.MONEY,
                        result.beforeBalance(), false));
    }
}
