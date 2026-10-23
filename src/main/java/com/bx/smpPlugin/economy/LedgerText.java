// SmpPlugin by LowBacon
package com.bx.smpPlugin.economy;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.EconomyManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class LedgerText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH);

    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("ADMIN_ADD", "Added by staff"),
            Map.entry("ADMIN_REMOVE", "Removed by staff"),
            Map.entry("ADMIN_SET", "Set by staff"),
            Map.entry("ADMIN_RESET", "Reset by staff"),
            Map.entry("PLAYER_PAY", "Payment"),
            Map.entry("SHOP_PURCHASE", "Shop purchase"),
            Map.entry("SHOP_REFUND", "Shop refund"),
            Map.entry("SELL_PAYOUT", "Items sold"),
            Map.entry("AUCTION_LISTING_FEE", "Auction listing fee"),
            Map.entry("AUCTION_PURCHASE", "Auction purchase"),
            Map.entry("AUCTION_REFUND", "Auction refund"),
            Map.entry("AUCTION_CLAIM", "Auction sale"),
            Map.entry("ORDER_CREATE_ESCROW", "Buy order budget"),
            Map.entry("ORDER_CREATE_FEE", "Buy order fee"),
            Map.entry("ORDER_DELIVERY_PAYOUT", "Buy order delivery"),
            Map.entry("ORDER_REFUND", "Buy order refund"),
            Map.entry("BOUNTY_PLACE", "Bounty placed"),
            Map.entry("BOUNTY_REWARD", "Bounty reward"),
            Map.entry("BILLFORD_REWARD", "Billford trade"),
            Map.entry("AMETHYST_SELL", "Sell axe"),
            Map.entry("COINFLIP_STAKE", "CoinFlip stake"),
            Map.entry("COINFLIP_PAYOUT", "CoinFlip win"),
            Map.entry("COINFLIP_REFUND", "CoinFlip refund"),
            Map.entry("REWARD", "Reward"),
            Map.entry("GAMBLE_STAKE", "Bet"),
            Map.entry("GAMBLE_PAYOUT", "Bet won"),
            Map.entry("GAMBLE_REFUND", "Bet returned"),
            Map.entry("CRATE_REWARD", "Crate reward"),
            Map.entry("VAULT_DEPOSIT", "Paid by another plugin"),
            Map.entry("VAULT_WITHDRAW", "Charged by another plugin"),
            Map.entry("EVENT_REWARD", "Event reward")
    );

    private LedgerText() {
    }

    public static String reason(String reason) {
        String key = reason == null ? "UNKNOWN" : reason;
        String fallback = REASONS.getOrDefault(key, key.toLowerCase(Locale.ROOT).replace('_', ' '));
        return Text.get("ECONOMY.REASON." + key, fallback);
    }

    public static String line(SmpPlugin plugin, LedgerEntry entry, EconomyManager.HistoryPage page) {
        boolean credit = entry.amountCents() >= 0L;
        String amount = (credit ? "&a+" : "&c-")
                + plugin.getCurrencyManager().formatMoney(Money.toUnits(Math.abs(entry.amountCents())));
        String what = reason(entry.reason());
        if (entry.counterparty() != null) {
            what += credit
                    ? Text.get("ECONOMY.HISTORY-FROM", " from {player}", "{player}", page.nameOf(entry.counterparty()))
                    : Text.get("ECONOMY.HISTORY-TO", " to {player}", "{player}", page.nameOf(entry.counterparty()));
        }
        String time = TIME.format(Instant.ofEpochMilli(entry.createdAt()).atZone(ZoneId.systemDefault()));
        return Text.get("ECONOMY.HISTORY-LINE", "&8{time} {amount} &f{what} &8· &7balance &f{balance}",
                "{time}", time,
                "{amount}", amount,
                "{what}", what,
                "{balance}", plugin.getCurrencyManager().formatMoney(Money.toUnits(entry.balanceAfterCents())));
    }
}
