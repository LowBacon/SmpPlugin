package com.bx.smpPlugin.world;

import java.util.Locale;

/**
 * One configured border (the spawn's or the lobby's), with no server code so it can be tested.
 *
 * <p>Minecraft's border is always a square: {@code size} is the length of one side, centred on
 * {@code centerX, centerZ}. The settings mirror the vanilla /worldborder command: the red warning
 * starts {@code warningDistance} blocks from the edge (or {@code warningTime} seconds before a moving
 * edge reaches you), and a player outside takes {@code damageAmount} per block past
 * {@code damageBuffer}.
 */
public record BorderZone(String name, boolean enabled, String world, double centerX, double centerZ, double size,
                         Mode mode, int warningDistance, int warningTime, double damageAmount, double damageBuffer) {

    /** Smallest and largest side length allowed (the vanilla limit is just under 60 million). */
    public static final double MIN_SIZE = 4D;
    public static final double MAX_SIZE = 59_999_968D;

    public enum Mode {
        /**
         * Only players inside the area get the border (sent to their client), and the server stops them
         * from walking, riding or pearling out. The rest of the world is not affected. Safe to use when the
         * spawn shares its world with survival.
         */
        PLAYER,
        /** The world's own border is changed: the whole world ends at the edge, for everyone. */
        WORLD;

        public static Mode parse(String raw, Mode fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                return fallback;
            }
        }
    }

    public BorderZone {
        size = clampSize(size);
        warningDistance = Math.max(0, Math.min(10_000, warningDistance));
        warningTime = Math.max(0, Math.min(3_600, warningTime));
        damageAmount = Double.isFinite(damageAmount) ? Math.max(0D, Math.min(100D, damageAmount)) : 0.2D;
        damageBuffer = Double.isFinite(damageBuffer) ? Math.max(0D, Math.min(10_000D, damageBuffer)) : 5D;
        world = world == null ? "" : world;
        mode = mode == null ? Mode.PLAYER : mode;
    }

    public static double clampSize(double size) {
        return Double.isFinite(size) ? Math.max(MIN_SIZE, Math.min(MAX_SIZE, size)) : 200D;
    }

    /** True once a world has been chosen; a zone without one does nothing. */
    public boolean configured() {
        return !world.isBlank();
    }

    public boolean active() {
        return enabled && configured();
    }

    public double half() {
        return size / 2D;
    }

    /** Whether a point is inside (or on) the square. */
    public boolean contains(double x, double z) {
        return Math.abs(x - centerX) <= half() && Math.abs(z - centerZ) <= half();
    }

    /** Whether a point is inside the square shrunk by {@code margin} blocks on every side. */
    public boolean containsWithin(double x, double z, double margin) {
        double limit = Math.max(0D, half() - margin);
        return Math.abs(x - centerX) <= limit && Math.abs(z - centerZ) <= limit;
    }

    /** The point moved straight back inside the square (at least {@code margin} from the edge). */
    public double[] clamp(double x, double z, double margin) {
        double limit = Math.max(0D, half() - margin);
        return new double[]{
                Math.max(centerX - limit, Math.min(centerX + limit, x)),
                Math.max(centerZ - limit, Math.min(centerZ + limit, z))
        };
    }

    public BorderZone withEnabled(boolean value) {
        return new BorderZone(name, value, world, centerX, centerZ, size, mode, warningDistance, warningTime, damageAmount, damageBuffer);
    }

    public BorderZone withArea(String newWorld, double x, double z, double newSize) {
        return new BorderZone(name, enabled, newWorld, x, z, newSize, mode, warningDistance, warningTime, damageAmount, damageBuffer);
    }

    public BorderZone withMode(Mode value) {
        return new BorderZone(name, enabled, world, centerX, centerZ, size, value, warningDistance, warningTime, damageAmount, damageBuffer);
    }

    public BorderZone withWarning(int distance, int time) {
        return new BorderZone(name, enabled, world, centerX, centerZ, size, mode, distance, time, damageAmount, damageBuffer);
    }

    public BorderZone withDamage(double amount, double buffer) {
        return new BorderZone(name, enabled, world, centerX, centerZ, size, mode, warningDistance, warningTime, amount, buffer);
    }

    /** "200 x 200 at 0, 0 in world" */
    public String describe() {
        if (!configured()) {
            return "not set up";
        }
        return String.format(Locale.ROOT, "%.0f x %.0f around %d, %d in %s", size, size, (long) Math.floor(centerX), (long) Math.floor(centerZ), world);
    }
}
