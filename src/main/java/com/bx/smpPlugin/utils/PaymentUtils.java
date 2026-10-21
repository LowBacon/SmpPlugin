package com.bx.smpPlugin.utils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.economy.Money;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.models.EconomyFailureReason;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.entity.Player;

import java.util.OptionalLong;

public final class PaymentUtils {

    private PaymentUtils() {
    }

    public static boolean transferMoney(SmpPlugin plugin, Player sender, String targetName, double amount) {
        if (targetName.equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.CANT-PAY-SELF")));
            return false;
        }
        if (amount <= 0) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.MUST-BE-POSITIVE")));
            return false;
        }

        Player target = plugin.getHideManager().findOnlinePlayer(sender, targetName);
        if (target == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ᴏɴʟɪɴᴇ."));
            return false;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.CANT-PAY-SELF")));
            return false;
        }
        PlayerData senderData = plugin.getPlayerDataManager().get(sender);
        PlayerData targetData = plugin.getPlayerDataManager().get(target);
        if (targetData == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴛᴀʀɢᴇᴛ ᴘʀᴏꜰɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return false;
        }
        if (targetData.getPaymentsChoice() == com.bx.smpPlugin.models.ThreeChoice.OFF) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.TARGET-DISABLED-PAYMENTS")));
            return false;
        }
        if (targetData.getPaymentsChoice() == com.bx.smpPlugin.models.ThreeChoice.FRIENDS_FOLLOWED) {
            boolean isFriendOrFollowed = plugin.getFriendsManager() != null && plugin.getFriendsManager().isFollowing(target.getUniqueId(), sender.getUniqueId());
            if (!isFriendOrFollowed) {
                sender.sendMessage(ColorUtils.toComponent("&c" + target.getName() + " only accepts payments from friends/followed."));
                return false;
            }
        }
        if (plugin.getFriendsManager() != null && plugin.getFriendsManager().isPaymentBlocked(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage(ColorUtils.toComponent("&c" + target.getName() + " has disabled payments from you."));
            return false;
        }
        if (senderData == null) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.NOT-ENOUGH-MONEY")));
            return false;
        }
        EconomyManager economy = plugin.getEconomyManager();
        OptionalLong requested = Money.toCents(amount);
        if (requested.isEmpty() || requested.getAsLong() <= 0L) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.INVALID-AMOUNT")));
            return false;
        }
        long cents = requested.getAsLong();
        EconomyManager.PayRules rules = economy.payRules();
        if (cents < rules.minCents()) {
            Text.send(sender, "ECONOMY.PAY-MINIMUM", "&cThe smallest payment is &f{amount}&c.",
                    "{amount}", plugin.getCurrencyManager().formatMoney(Money.toUnits(rules.minCents())));
            return false;
        }
        if (rules.maxCents() > 0L && cents > rules.maxCents()) {
            Text.send(sender, "ECONOMY.PAY-MAXIMUM", "&cYou can send at most &f{amount}&c in one payment.",
                    "{amount}", plugin.getCurrencyManager().formatMoney(Money.toUnits(rules.maxCents())));
            return false;
        }
        long wait = economy.payCooldownRemaining(sender.getUniqueId());
        if (wait > 0L) {
            Text.send(sender, "ECONOMY.PAY-COOLDOWN", "&cWait &f{seconds}s &cbefore sending another payment.",
                    "{seconds}", Long.toString((wait + 999L) / 1000L));
            return false;
        }
        if (!economy.has(sender, amount)) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.NOT-ENOUGH-MONEY")));
            return false;
        }

        long tax = rules.taxFor(cents);
        var transferResult = economy.transfer(sender.getUniqueId(), sender.getName(), target.getUniqueId(),
                target.getName(), Money.toUnits(cents), tax, EconomyReason.PLAYER_PAY, EconomyContext.NONE);
        if (!transferResult.success()) {
            if (transferResult.failureReason() == EconomyFailureReason.BALANCE_LIMIT) {
                Text.send(sender, "ECONOMY.PAY-RECIPIENT-FULL", "&c{player}'s balance cannot hold that much more.",
                        "{player}", plugin.getHideManager().publicName(target));
            } else if (transferResult.failureReason() == EconomyFailureReason.INSUFFICIENT_FUNDS) {
                sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.NOT-ENOUGH-MONEY")));
            } else {
                sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.TRANSACTION-ERROR")));
            }
            return false;
        }
        economy.markPayment(sender.getUniqueId());
        amount = transferResult.amount();
        double received = Money.toUnits(cents - tax);

        sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                "BALANCE.PAY.SUCCESS-SENDER",
                "{player}", plugin.getHideManager().publicName(target),
                "{amount}", compactMoneyAmount(plugin, amount),
                "{amount_full}", fullMoneyAmount(plugin, amount),
                "{money}", plugin.getCurrencyManager().formatMoneyCompact(amount),
                "{money_full}", fullMoney(plugin, amount))));
        if (tax > 0L) {
            Text.send(sender, "ECONOMY.PAY-TAX", "&7Payment tax: &f{tax}&7. {player} received &f{received}&7.",
                    "{tax}", plugin.getCurrencyManager().formatMoney(Money.toUnits(tax)),
                    "{player}", plugin.getHideManager().publicName(target),
                    "{received}", plugin.getCurrencyManager().formatMoney(received));
        }
        if (targetData.isPayAlertsEnabled()) {
            target.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                    "BALANCE.PAY.SUCCESS-RECEIVER",
                    "{player}", plugin.getHideManager().publicName(sender),
                    "{amount}", compactMoneyAmount(plugin, received),
                    "{amount_full}", fullMoneyAmount(plugin, received),
                    "{money}", plugin.getCurrencyManager().formatMoneyCompact(received),
                    "{money_full}", fullMoney(plugin, received))));
        }
        return true;
    }

    public static boolean transferShards(SmpPlugin plugin, Player sender, String targetName, long amount) {
        if (targetName.equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.CANT-PAY-SELF")));
            return false;
        }
        if (amount <= 0) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.MUST-BE-POSITIVE")));
            return false;
        }

        Player target = plugin.getHideManager().findOnlinePlayer(sender, targetName);
        if (target == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ᴏɴʟɪɴᴇ."));
            return false;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.CANT-PAY-SELF")));
            return false;
        }
        PlayerData senderData = plugin.getPlayerDataManager().get(sender);
        PlayerData targetData = plugin.getPlayerDataManager().get(target);
        if (targetData == null) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.TARGET-PROFILE-NOT-FOUND")));
            return false;
        }
        if (targetData.getPaymentsChoice() == com.bx.smpPlugin.models.ThreeChoice.OFF) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.TARGET-DISABLED-PAYMENTS")));
            return false;
        }
        if (targetData.getPaymentsChoice() == com.bx.smpPlugin.models.ThreeChoice.FRIENDS_FOLLOWED) {
            boolean isFriendOrFollowed = plugin.getFriendsManager() != null && plugin.getFriendsManager().isFollowing(target.getUniqueId(), sender.getUniqueId());
            if (!isFriendOrFollowed) {
                sender.sendMessage(ColorUtils.toComponent("&c" + target.getName() + " only accepts payments from friends/followed."));
                return false;
            }
        }
        if (plugin.getFriendsManager() != null && plugin.getFriendsManager().isPaymentBlocked(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage(ColorUtils.toComponent("&c" + target.getName() + " has disabled payments from you."));
            return false;
        }
        if (senderData == null || !senderData.hasShards(amount)) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.NOT-ENOUGH-SHARDS")));
            return false;
        }

        senderData.removeShards(amount);
        targetData.addShards(amount);

        sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                "SHARD_PAY.SUCCESS-SENDER",
                "{player}", plugin.getHideManager().publicName(target),
                "{amount}", String.valueOf(amount),
                "{shards}", plugin.getCurrencyManager().formatShards(amount))));
        if (targetData.isPayAlertsEnabled()) {
            target.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                    "SHARD_PAY.SUCCESS-RECEIVER",
                    "{player}", plugin.getHideManager().publicName(sender),
                    "{amount}", String.valueOf(amount),
                    "{shards}", plugin.getCurrencyManager().formatShards(amount))));
        }
        return true;
    }

    private static String compactMoneyAmount(SmpPlugin plugin, double amount) {
        return plugin.getCurrencyManager().formatCompactAmount(CurrencyManager.CurrencyType.MONEY, amount);
    }

    private static String fullMoneyAmount(SmpPlugin plugin, double amount) {
        return plugin.getCurrencyManager().formatAmount(CurrencyManager.CurrencyType.MONEY, amount);
    }

    private static String fullMoney(SmpPlugin plugin, double amount) {
        return plugin.getCurrencyManager().format(CurrencyManager.CurrencyType.MONEY, amount, false);
    }
}
