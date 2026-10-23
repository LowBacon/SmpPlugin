// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.EconomyGateway;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

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
        return data == null ? 0L : data.getMoneyCents();
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
    public Optional<StoredBalance> storedBalance(UUID player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return Optional.empty();
        }
        PlayerData.MoneyState state = data.moneyState();
        return Optional.of(new StoredBalance(state.cents(), state.version()));
    }

    private void log(Player player, String type, String detail) {
        try {
            plugin.getPlayerLogsManager().log(player.getUniqueId(), player.getName(), "Economy", type, detail);
        } catch (RuntimeException ignored) {
            // logging must never break a money movement
        }
    }
}
