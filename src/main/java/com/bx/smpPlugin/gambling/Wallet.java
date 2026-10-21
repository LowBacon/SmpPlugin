package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * The one place /dice and /cf touch money. Amounts are whole cents (see {@link StakeMath}), so a
 * bet, a payout and a refund always add up exactly. Every movement goes through SmpPlugin's own
 * economy (the balances Vault and /pay use) and is written to the player's /logs.
 */
final class Wallet {

    private final SmpPlugin plugin;

    Wallet(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    long balance(Player player) {
        return StakeMath.fromEconomy(plugin.getEconomyManager().getBalance(player));
    }

    /** Takes a bet from an online player. False when they cannot afford it or the economy refused. */
    boolean take(Player player, long cents, String game) {
        if (cents <= 0L || balance(player) < cents) {
            return false;
        }
        EconomyTransactionResult result = plugin.getEconomyManager()
                .withdraw(player, StakeMath.toEconomy(cents), EconomyReason.GAMBLE_STAKE);
        if (result.success()) {
            log(player.getUniqueId(), player.getName(), "GAMBLE_STAKE", "Bet " + money(cents) + " on " + game);
        }
        return result.success();
    }

    /**
     * Pays a player, online or not (an offline player's balance is changed in the database). False
     * only when the economy refused, in which case the caller keeps the money in escrow.
     */
    boolean pay(UUID uuid, String name, long cents, EconomyReason reason, String detail) {
        if (cents <= 0L) {
            return true;
        }
        try {
            Player online = Bukkit.getPlayer(uuid);
            EconomyTransactionResult result = online != null
                    ? plugin.getEconomyManager().deposit(online, StakeMath.toEconomy(cents), reason)
                    : plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(uuid), StakeMath.toEconomy(cents), reason);
            if (result.success()) {
                log(uuid, name, reason.name(), detail + " " + money(cents));
                return true;
            }
            plugin.getLogger().warning("[Gambling] The economy refused to pay " + money(cents) + " to " + name
                    + " (" + result.failureReason() + "); it stays in escrow and is paid at the next start.");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not pay " + money(cents) + " to " + name
                    + "; it stays in escrow and is paid at the next start.", exception);
        }
        return false;
    }

    String money(long cents) {
        return plugin.getCurrencyManager().formatMoney(StakeMath.toEconomy(cents));
    }

    private void log(UUID uuid, String name, String type, String detail) {
        try {
            plugin.getPlayerLogsManager().log(uuid, name, "Economy", type, detail);
        } catch (RuntimeException ignored) {
            // logging must never break a money movement
        }
    }
}
