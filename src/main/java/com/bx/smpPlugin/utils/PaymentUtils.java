package com.bx.smpPlugin.utils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.entity.Player;

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
        PayRules rules = PayRules.read(plugin);
        String ruleProblem = rules.check(sender, amount);
        if (ruleProblem != null) {
            sender.sendMessage(ColorUtils.toComponent(ruleProblem));
            return false;
        }
        if (!plugin.getEconomyManager().has(sender, amount)) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.NOT-ENOUGH-MONEY")));
            return false;
        }

        long amountCents = com.bx.smpPlugin.economy.Money.toCents(amount).orElse(0L);
        long feeCents = com.bx.smpPlugin.economy.Money.fee(amountCents, rules.feePercent());
        double fee = com.bx.smpPlugin.economy.Money.toDouble(feeCents);
        if (feeCents > 0L && !plugin.getEconomyManager().withdraw(sender, fee, EconomyReason.PAY_FEE).success()) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("BALANCE.PAY.NOT-ENOUGH-MONEY")));
            return false;
        }
        double sent = com.bx.smpPlugin.economy.Money.toDouble(amountCents - feeCents);
        var transferResult = sent <= 0D ? null
                : plugin.getEconomyManager().transfer(sender, target, sent, EconomyReason.PLAYER_PAY);
        if (transferResult == null || !transferResult.success()) {
            if (feeCents > 0L) {
                plugin.getEconomyManager().deposit(sender, fee, EconomyReason.PAY_FEE);
            }
            sender.sendMessage(ColorUtils.toComponent(transferResult == null
                    ? plugin.getConfigManager().getMessage("BALANCE.PAY.TRANSACTION-ERROR")
                    : com.bx.smpPlugin.economy.EconomyMessages.failure(plugin, transferResult.failureReason())));
            return false;
        }
        rules.markPaid(sender);
        if (feeCents > 0L) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ECONOMY.PAY-FEE", "&7A fee of &f{fee} &7was taken; {player} received &f{received}&7.",
                    "{fee}", plugin.getCurrencyManager().formatMoney(fee),
                    "{received}", plugin.getCurrencyManager().formatMoney(sent),
                    "{player}", plugin.getHideManager().publicName(target))));
        }

        sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                "BALANCE.PAY.SUCCESS-SENDER",
                "{player}", plugin.getHideManager().publicName(target),
                "{amount}", compactMoneyAmount(plugin, amount),
                "{amount_full}", fullMoneyAmount(plugin, amount),
                "{money}", plugin.getCurrencyManager().formatMoneyCompact(amount),
                "{money_full}", fullMoney(plugin, amount))));
        if (targetData.isPayAlertsEnabled()) {
            target.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                    "BALANCE.PAY.SUCCESS-RECEIVER",
                    "{player}", plugin.getHideManager().publicName(sender),
                    "{amount}", compactMoneyAmount(plugin, sent),
                    "{amount_full}", fullMoneyAmount(plugin, sent),
                    "{money}", plugin.getCurrencyManager().formatMoneyCompact(sent),
                    "{money_full}", fullMoney(plugin, sent))));
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

        var shardTransfer = plugin.getEconomyManager().transferShards(sender.getUniqueId(), sender.getName(),
                target.getUniqueId(), target.getName(), amount, com.bx.smpPlugin.models.EconomyReason.SHARD_PAY);
        if (!shardTransfer.success()) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("SHARD_PAY.NOT-ENOUGH-SHARDS")));
            return false;
        }

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

    /** ECONOMY.PAY in config.yml: smallest and largest payment, wait between payments, fee. */
    record PayRules(double min, double max, long cooldownMillis, double feePercent) {

        private static final java.util.Map<java.util.UUID, Long> LAST_PAYMENT = new java.util.concurrent.ConcurrentHashMap<>();

        static PayRules read(SmpPlugin plugin) {
            var config = plugin.getConfigManager().getConfig();
            return new PayRules(
                    Math.max(0D, config.getDouble("ECONOMY.PAY.MIN-AMOUNT", 0.01D)),
                    Math.max(0D, config.getDouble("ECONOMY.PAY.MAX-AMOUNT", 0D)),
                    Math.max(0L, config.getLong("ECONOMY.PAY.COOLDOWN-SECONDS", 0L)) * 1000L,
                    Math.max(0D, Math.min(100D, config.getDouble("ECONOMY.PAY.FEE-PERCENT", 0D))));
        }

        /** The message to show when this payment is not allowed, or null when it is. */
        String check(Player sender, double amount) {
            if (min > 0D && amount + 1e-9 < min) {
                return "&cThe smallest payment is " + com.bx.smpPlugin.SmpPlugin.getInstance().getCurrencyManager().formatMoney(min) + ".";
            }
            if (max > 0D && amount > max + 1e-9 && !PermissionUtils.has(sender, "smpplugin.bypass.paylimit")) {
                return "&cThe largest payment is " + com.bx.smpPlugin.SmpPlugin.getInstance().getCurrencyManager().formatMoney(max) + ".";
            }
            if (cooldownMillis > 0L && !PermissionUtils.has(sender, "smpplugin.bypass.paycooldown")) {
                Long last = LAST_PAYMENT.get(sender.getUniqueId());
                long waited = last == null ? Long.MAX_VALUE : System.currentTimeMillis() - last;
                if (waited < cooldownMillis) {
                    long seconds = Math.max(1L, (cooldownMillis - waited + 999L) / 1000L);
                    return "&cWait " + seconds + "s before paying again.";
                }
            }
            return null;
        }

        void markPaid(Player sender) {
            if (cooldownMillis <= 0L) {
                return;
            }
            long now = System.currentTimeMillis();
            LAST_PAYMENT.put(sender.getUniqueId(), now);
            if (LAST_PAYMENT.size() > 512) {
                LAST_PAYMENT.values().removeIf(time -> now - time > cooldownMillis);
            }
        }
    }
}
