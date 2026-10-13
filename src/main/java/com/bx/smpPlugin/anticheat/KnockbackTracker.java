package com.bx.smpPlugin.anticheat;

/**
 * Follows the movement updates after the server applies knockback. A vanilla client always moves along
 * the knockback direction for several ticks (ground friction leaves about 1.8x the first-tick velocity,
 * air drag about 3.5x). "Anti-knockback" clients move a small fraction of that, or not at all.
 */
final class KnockbackTracker {

    private double directionX;
    private double directionZ;
    private double velocity;
    private double travelled;
    private int updates;
    private boolean active;

    void start(double velocityX, double velocityZ) {
        double horizontal = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (horizontal < 1.0E-6D) {
            active = false;
            return;
        }
        directionX = velocityX / horizontal;
        directionZ = velocityZ / horizontal;
        velocity = horizontal;
        travelled = 0D;
        updates = 0;
        active = true;
    }

    /** Adds one movement update; returns the number of updates recorded since knockback. */
    int record(double dx, double dz) {
        if (!active) {
            return 0;
        }
        travelled += dx * directionX + dz * directionZ;
        return ++updates;
    }

    /** Distance travelled along the knockback direction, as a multiple of the applied velocity. */
    double ratio() {
        return velocity <= 0D ? 1D : travelled / velocity;
    }

    double velocity() {
        return velocity;
    }

    boolean active() {
        return active;
    }

    double directionX() {
        return directionX;
    }

    double directionZ() {
        return directionZ;
    }

    void stop() {
        active = false;
    }
}
