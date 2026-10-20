package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;

/**
 * Starts and stops /dice and /roulette as one unit. On start, any bet a crash left in escrow is
 * given back first. On stop, every roll and round is settled before the escrow file is closed.
 */
public final class Gambling {

    private final GamblingEscrow escrow;
    private final DiceGame dice;
    private final Roulette roulette;

    public Gambling(SmpPlugin plugin) {
        Wallet wallet = new Wallet(plugin);
        this.escrow = new GamblingEscrow(plugin, wallet);
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
        escrow.shutdown();
    }
}
