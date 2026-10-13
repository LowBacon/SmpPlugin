package com.bx.smpPlugin.anticheat;

/**
 * Detects a sped-up game clock. A vanilla client sends one movement update per 50 ms tick. Every update
 * adds 50 ms of "expected" time and subtracts the time that really passed: the balance drifts upward only
 * when updates arrive faster than the clock allows.
 *
 * <p>Lag is handled in both directions: a delay pushes the balance negative (bounded by the lag allowance,
 * so a player cannot bank unlimited time by idling), and the burst of queued updates after the delay
 * merely brings it back towards zero. A network stall can deliver many queued updates in the same server
 * tick; such bursts (more than {@link #MAX_BURST} updates within a few milliseconds) are never counted as
 * gained time. Large speed-ups are caught by the speed check instead.</p>
 */
final class TimerBalance {

    private static final double TICK_MILLIS = 50D;
    /** Updates closer together than this arrived in the same server tick. */
    private static final long SAME_TICK_MILLIS = 5L;
    static final int MAX_BURST = 4;

    private double balanceMillis;
    private long lastMillis = -1L;
    private int burst;

    /**
     * Records one movement update.
     *
     * @return milliseconds the client is ahead of the clock beyond {@code maxAheadMillis}, or 0
     */
    double update(long nowMillis, double maxAheadMillis, double lagAllowanceMillis) {
        if (lastMillis < 0L) {
            lastMillis = nowMillis;
            return 0D;
        }
        long elapsed = Math.max(0L, nowMillis - lastMillis);
        lastMillis = nowMillis;
        burst = elapsed < SAME_TICK_MILLIS ? burst + 1 : 0;
        balanceMillis += TICK_MILLIS - elapsed;
        balanceMillis = Math.max(-Math.abs(lagAllowanceMillis), balanceMillis);
        if (burst >= MAX_BURST) {
            // Queued network traffic, not a faster clock.
            balanceMillis = Math.min(balanceMillis, 0D);
            return 0D;
        }
        if (balanceMillis > maxAheadMillis) {
            double excess = balanceMillis - maxAheadMillis;
            balanceMillis = 0D;
            return excess;
        }
        return 0D;
    }

    double balanceMillis() {
        return balanceMillis;
    }

    void reset() {
        balanceMillis = 0D;
        lastMillis = -1L;
        burst = 0;
    }
}
