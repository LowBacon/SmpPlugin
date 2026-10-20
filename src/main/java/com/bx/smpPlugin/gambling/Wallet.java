package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The one place /dice and /roulette touch money. Amounts are whole cents (see {@link StakeMath}), so a bet, a
 * payout and a refund always add up exactly. Every movement goes through SmpPlugin's economy (the balances Vault
 * and /pay use), is written to the player's /logs, and opens or closes its escrow hold in the same database
 * transaction as the balance change.
 */
final class Wallet {

    private final SmpPlugin plugin;
    private final GamblingEscrow escrow;

    Wallet(SmpPlugin plugin, GamblingEscrow escrow) {
        this.plugin = plugin;
        this.escrow = escrow;
    }

    static double toEconomy(long cents) {
        return StakeMath.toEconomy(cents);
    }

    long balance(Player player) {
        return StakeMath.fromEconomy(plugin.getEconomyManager().getBalance(player));
    }

    /**
     * Takes a bet from an online player and opens its hold in the same transaction. False (and nothing changed)
     * when they cannot afford it or the economy refused.
     */
    boolean take(Player player, GamblingEscrow.Hold hold) {
        long cents = hold.cents();
        if (cents <= 0L || balance(player) < cents) {
            return false;
        }
        EconomyContext context = EconomyContext.note("Bet on " + hold.game()).withStep(escrow.openStep(hold));
        EconomyTransactionResult result = plugin.getEconomyManager()
                .withdraw(player, toEconomy(cents), EconomyReason.GAMBLE_STAKE, context);
        if (result.success()) {
            escrow.opened(hold);
            log(player.getUniqueId(), player.getName(), "GAMBLE_STAKE", "Bet " + money(cents) + " on " + hold.game());
        }
        return result.success();
    }

    /**
     * Pays a player, online or not, and closes {@code holdIds} in the same transaction. False only when the economy
     * refused, in which case nothing changed and the caller keeps the money held.
     */
    boolean pay(UUID uuid, String name, long cents, EconomyReason reason, String detail, Collection<String> holdIds) {
        if (cents <= 0L) {
            escrow.closeWithoutPayment(holdIds);
            return true;
        }
        try {
            EconomyContext context = EconomyContext.note(detail).withStep(escrow.closeStep(holdIds));
            Player online = Bukkit.getPlayer(uuid);
            EconomyTransactionResult result = online != null
                    ? plugin.getEconomyManager().deposit(online, toEconomy(cents), reason, context)
                    : plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(uuid), toEconomy(cents), reason, context);
            if (result.success()) {
                escrow.closed(holdIds);
                log(uuid, name, reason.name(), detail + " " + money(cents));
                return true;
            }
            plugin.getLogger().warning("[Gambling] The economy refused to pay " + money(cents) + " to " + name
                    + " (" + result.failureReason() + "); it stays held and is paid at the next start.");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not pay " + money(cents) + " to " + name
                    + "; it stays held and is paid at the next start.", exception);
        }
        return false;
    }

    boolean pay(UUID uuid, String name, long cents, EconomyReason reason, String detail, String holdId) {
        return pay(uuid, name, cents, reason, detail, List.of(holdId));
    }

    String money(long cents) {
        return plugin.getCurrencyManager().formatMoney(toEconomy(cents));
    }

    private void log(UUID uuid, String name, String type, String detail) {
        try {
            plugin.getPlayerLogsManager().log(uuid, name, "Economy", type, detail);
        } catch (RuntimeException ignored) {
            // logging must never break a money movement
        }
    }
}
