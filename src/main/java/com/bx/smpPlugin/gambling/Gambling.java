package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;

/**
 * Starts and stops /dice and /cf as one unit. On start, any bet a crash left in escrow is given
 * back first, and money the old (pre-3.2) CoinFlip still held is returned. On stop, every roll and
 * flip is settled before the escrow file is closed.
 */
public final class Gambling {

    private final GamblingEscrow escrow;
    private final DiceGame dice;
    private final CoinFlip coinFlip;

    public Gambling(SmpPlugin plugin) {
        Wallet wallet = new Wallet(plugin);
        this.escrow = new GamblingEscrow(plugin, wallet);
        escrow.recover();
        LegacyCoinFlipCleanup.run(plugin, wallet);
        this.dice = new DiceGame(plugin, wallet, escrow);
        this.coinFlip = new CoinFlip(plugin, wallet, escrow);
    }

    public DiceGame dice() {
        return dice;
    }

    public CoinFlip coinFlip() {
        return coinFlip;
    }

    public void shutdown() {
        dice.shutdown();
        coinFlip.shutdown();
        escrow.shutdown();
    }
}
