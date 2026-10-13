package com.bx.smpPlugin.anticheat;

/**
 * A violation level that grows with each flag and decays linearly over time, so isolated
 * false positives fade away and only repeated or strong evidence reaches action thresholds.
 */
final class ViolationBuffer {

    private double level;
    private long lastUpdateMillis;

    /** Adds {@code amount} after applying decay and returns the new level. */
    double add(double amount, double decayPerSecond, long nowMillis) {
        decay(decayPerSecond, nowMillis);
        level += Math.max(0D, amount);
        return level;
    }

    double current(double decayPerSecond, long nowMillis) {
        decay(decayPerSecond, nowMillis);
        return level;
    }

    void reset() {
        level = 0D;
    }

    private void decay(double decayPerSecond, long nowMillis) {
        if (lastUpdateMillis != 0L && nowMillis > lastUpdateMillis && decayPerSecond > 0D) {
            double elapsedSeconds = (nowMillis - lastUpdateMillis) / 1000D;
            level = Math.max(0D, level - decayPerSecond * elapsedSeconds);
        }
        lastUpdateMillis = Math.max(lastUpdateMillis, nowMillis);
    }
}
