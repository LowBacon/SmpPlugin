// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import com.bx.smpPlugin.economy.Money;

import java.util.Optional;
import java.util.UUID;

public interface EconomyGateway {

    boolean isOnline(UUID player);

    long balanceCents(UUID player);

    boolean withdraw(UUID player, long cents);

    boolean deposit(UUID player, long cents);

    Optional<StoredBalance> storedBalance(UUID player);

    record StoredBalance(long cents, long version) {
        public double money() {
            return Money.toUnits(cents);
        }
    }
}
