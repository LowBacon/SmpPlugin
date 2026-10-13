package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.EconomyGateway;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Connects CoinFlip to SmpPlugin's own economy (the same balances Vault and /pay use). Only players who are online
 * and whose data is loaded are changed; CoinFlip pays everyone else through claims. Each change is also written to
 * the player's log so it shows up in /logs.
 */
public final class SmpEconomyGateway implements EconomyGateway {

    private final SmpPlugin plugin;

    public SmpEconomyGateway(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isOnline(UUID player) {
        Player online = Bukkit.getPlayer(player);
        return online != null && online.isOnline() && plugin.getPlayerDataManager().isLoaded(player);
    }

    @Override
    public long balanceCents(UUID player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        return data == null ? 0L : StakeMath.fromEconomy(data.getMoney());
    }

    @Override
    public boolean withdraw(UUID player, long cents) {
        Player online = Bukkit.getPlayer(player);
        if (online == null || !isOnline(player) || balanceCents(player) < cents) {
            return false;
        }
        EconomyTransactionResult result = plugin.getEconomyManager()
                .withdraw(online, StakeMath.toEconomy(cents), EconomyReason.COINFLIP_STAKE);
        if (result.success()) {
            log(online, "COINFLIP_STAKE", "Staked " + plugin.getCurrencyManager().formatMoney(StakeMath.toEconomy(cents))
                    + " in CoinFlip");
        }
        return result.success();
    }

    @Override
    public boolean deposit(UUID player, long cents) {
        Player online = Bukkit.getPlayer(player);
        if (online == null || !isOnline(player)) {
            return false;
        }
        EconomyTransactionResult result = plugin.getEconomyManager()
                .deposit(online, StakeMath.toEconomy(cents), EconomyReason.COINFLIP_PAYOUT);
        if (result.success()) {
            log(online, "COINFLIP_CREDIT", "Received " + plugin.getCurrencyManager().formatMoney(StakeMath.toEconomy(cents))
                    + " from CoinFlip");
        }
        return result.success();
    }

    @Override
    public OptionalDouble storedBalance(UUID player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        return data == null ? OptionalDouble.empty() : OptionalDouble.of(data.getMoney());
    }

    private void log(Player player, String type, String detail) {
        try {
            plugin.getPlayerLogsManager().log(player.getUniqueId(), player.getName(), "Economy", type, detail);
        } catch (RuntimeException ignored) {
            // logging must never break a money movement
        }
    }
}
