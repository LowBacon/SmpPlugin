package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public BalanceCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("history")) {
            showHistory(sender, label, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            if (args.length == 0) {
                sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " <player>"));
                return true;
            }
            // The console can look anyone up; the name is resolved off the main thread.
            com.bx.smpPlugin.core.Lookup.account(plugin, sender, args[0], account -> {
                if (account == null) {
                    sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.ADMIN.PLAYER-NOT-FOUND")));
                    return;
                }
                double balance = plugin.getEconomyManager().getBalance(account.uuid());
                sender.sendMessage(ColorUtils.toComponent("&7" + account.displayName() + ": &a" + fullMoney(balance)));
            });
            return true;
        }

        if (args.length == 0) {
            double balance = plugin.getEconomyManager().getBalance(player);
            String msg = plugin.getConfigManager().getMessage("BALANCE.YOUR-BALANCE",
                    "{amount}", compactAmount(balance),
                    "{amount_full}", fullAmount(balance),
                    "{money}", plugin.getCurrencyManager().formatMoneyCompact(balance),
                    "{money_full}", fullMoney(balance));
            player.sendMessage(ColorUtils.toComponent(msg));
        } else {
            // Resolved off the main thread: an unknown name could otherwise make the server ask Mojang.
            com.bx.smpPlugin.core.Lookup.account(plugin, player, args[0], account -> {
                if (account == null) {
                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.ADMIN.PLAYER-NOT-FOUND")));
                    return;
                }
                double balance = plugin.getEconomyManager().getBalance(account.uuid());
                String msg = plugin.getConfigManager().getMessage("BALANCE.OTHER-BALANCE",
                        "{player}", account.displayName(),
                        "{amount}", compactAmount(balance),
                        "{amount_full}", fullAmount(balance),
                        "{money}", plugin.getCurrencyManager().formatMoneyCompact(balance),
                        "{money_full}", fullMoney(balance));
                player.sendMessage(ColorUtils.toComponent(msg));
            });
        }
        return true;
    }

    /** /balance history [page]: the player's own money and shard changes from the economy ledger. */
    private void showHistory(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cUse /economy history <player> from the console."));
            return;
        }
        if (!com.bx.smpPlugin.utils.PermissionUtils.has(player, "smpplugin.balance.history")) {
            player.sendMessage(ColorUtils.toComponent("&cYou do not have permission to see your transaction history."));
            return;
        }
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException exception) {
                player.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " history [page]"));
                return;
            }
        }
        com.bx.smpPlugin.economy.EconomyLedger ledger = plugin.getV4Module() == null ? null : plugin.getV4Module().ledger();
        com.bx.smpPlugin.economy.EconomyCommand.showHistory(plugin, ledger, player, player.getUniqueId(), player.getName(), page);
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
