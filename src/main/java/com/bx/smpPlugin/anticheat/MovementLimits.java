package com.bx.smpPlugin.anticheat;

/**
 * Movement physics limits derived from the player's live attributes, so potions, enchantments
 * (Soul Speed, Swift Sneak, Depth Strider) and other plugins' attribute modifiers are honoured
 * without hard-coded exceptions.
 */
public final class MovementLimits {

    /**
     * Ground speed in blocks per tick for a movement-speed attribute value. Vanilla walking (0.1)
     * covers about 0.2158 blocks per tick; sprinting adds +30% to the attribute (0.13 → 0.2806).
     */
    static final double BLOCKS_PER_TICK_PER_SPEED = 2.158D;

    /** Vanilla gravity and air drag, used when the gravity attribute is unavailable. */
    static final double DEFAULT_GRAVITY = 0.08D;
    static final double AIR_DRAG = 0.98D;

    private MovementLimits() {
    }

    /**
     * Largest legitimate horizontal distance for one movement update.
     *
     * @param movementSpeed      the MOVEMENT_SPEED attribute value (includes sprint and Speed)
     * @param recentlyAirborne   off the ground now or within the last few ticks (jump momentum)
     * @param recentlyOnIce      on ice, packed ice or blue ice recently (low friction keeps momentum)
     * @param recentlyOnSlime    on slime blocks recently
     * @param inLiquid           in water or lava
     * @param tolerance          extra allowance from config
     */
    public static double maxHorizontalPerTick(double movementSpeed, boolean recentlyAirborne, boolean recentlyOnIce,
                                              boolean recentlyOnSlime, boolean inLiquid, double tolerance) {
        double base = Math.max(0D, movementSpeed) * BLOCKS_PER_TICK_PER_SPEED;
        double limit;
        if (inLiquid) {
            limit = base * 1.6D + 0.1D;
        } else if (recentlyAirborne) {
            // A sprint-jump adds a 0.2 impulse on top of momentum that then decays with air drag.
            limit = base * 1.75D + 0.2D;
        } else {
            limit = base * 1.3D + 0.05D;
        }
        if (recentlyOnIce) {
            limit *= 2.4D;
        } else if (recentlyOnSlime) {
            limit *= 1.5D;
        }
        return limit + Math.max(0D, tolerance);
    }

    /**
     * Highest a player can rise above their take-off point from a jump.
     *
     * @param jumpStrength      the JUMP_STRENGTH attribute value (vanilla 0.42)
     * @param jumpBoostLevel    Jump Boost amplifier + 1, or 0 without the effect
     * @param gravity           the GRAVITY attribute value (vanilla 0.08)
     */
    public static double maxJumpHeight(double jumpStrength, int jumpBoostLevel, double gravity) {
        double velocity = Math.max(0D, jumpStrength) + 0.1D * Math.max(0, jumpBoostLevel);
        double g = gravity > 0D ? gravity : DEFAULT_GRAVITY;
        double height = 0D;
        for (int tick = 0; tick < 200 && velocity > 0D; tick++) {
            height += velocity;
            velocity = (velocity - g) * AIR_DRAG;
        }
        return height;
    }

    /**
     * In mid-air, gravity only ever reduces vertical speed. An increase is impossible without an
     * external push (knockback, wind charges, levitation), which callers exempt separately.
     */
    public static boolean isAirJump(double previousDy, double currentDy, double tolerance) {
        return currentDy > 0D && currentDy > previousDy + Math.max(0D, tolerance);
    }
}
