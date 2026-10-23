// SmpPlugin by LowBacon
package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;

public final class Gambling {

    private final GamblingEscrow escrow;
    private final DiceGame dice;
    private final Roulette roulette;

    public Gambling(SmpPlugin plugin) {
        this.escrow = new GamblingEscrow(plugin);
        Wallet wallet = new Wallet(plugin, escrow);
        escrow.recover();
        this.dice = new DiceGame(plugin, wallet, escrow);
        this.roulette = new Roulette(plugin, wallet, escrow);
    }

    public DiceGame dice() {
        return dice;
    }

    public Roulette roulette() {
        return roulette;
    }

    public void shutdown() {
        dice.shutdown();
        roulette.shutdown();
        if (escrow.size() > 0) {
            // Only possible when a payout was refused; those holds are paid at the next start.
            org.bukkit.Bukkit.getLogger().info("[Gambling] " + escrow.size() + " bet(s) stay held until the next start.");
        }
    }
}
