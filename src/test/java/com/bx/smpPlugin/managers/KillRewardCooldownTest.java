// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillRewardCooldownTest {

    private static final long FIVE_MINUTES = 300_000L;

    @Test
    void theFirstKillOfAVictimPays() {
        assertTrue(ShardManager.killRewardAllowed(null, 1_000L, FIVE_MINUTES, false));
    }

    @Test
    void theSameVictimPaysAgainOnlyAfterTheCooldown() {
        long last = 1_000_000L;
        assertFalse(ShardManager.killRewardAllowed(last, last + 60_000L, FIVE_MINUTES, false));
        assertTrue(ShardManager.killRewardAllowed(last, last + FIVE_MINUTES, FIVE_MINUTES, false));
        assertTrue(ShardManager.killRewardAllowed(last, last + 1L, 0L, false), "0 pays every kill");
    }

    @Test
    void playersOnTheSameAddressNeverPay() {
        assertFalse(ShardManager.killRewardAllowed(null, 1_000L, FIVE_MINUTES, true));
    }
}
