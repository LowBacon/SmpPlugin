package com.bx.smpPlugin.coinflip;

import com.bx.smpPlugin.economy.Money;

import java.util.Optional;
import java.util.UUID;

/**
 * The only way CoinFlip touches balances. Calls happen on the server thread. Only online players' balances are
 * changed directly; anything owed to an offline player is stored as a claim and paid when they are next online,
 * because the server economy keeps online balances in memory and saves them on its own schedule.
 */
public interface EconomyGateway {

    boolean isOnline(UUID player);

    /** Current balance in cents, or 0 when unknown. */
    long balanceCents(UUID player);

    /** Takes {@code cents} from an online player's balance. Returns false (and changes nothing) if it cannot. */
    boolean withdraw(UUID player, long cents);

    /** Adds {@code cents} to an online player's balance. Returns false (and changes nothing) if it cannot. */
    boolean deposit(UUID player, long cents);

    /**
     * The balance exactly as the economy will save it, with its balance version, for writing together with the
     * journal row. Empty when the player's data is not loaded.
     */
    Optional<StoredBalance> storedBalance(UUID player);

    /** A balance in whole cents and the version it was saved under (see {@code players.money_version}). */
    record StoredBalance(long cents, long version) {
        /** The balance as the double the {@code money} column holds next to the exact {@code money_cents}. */
        public double money() {
            return Money.toUnits(cents);
        }
    }
}
