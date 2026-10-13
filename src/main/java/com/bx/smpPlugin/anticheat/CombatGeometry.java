package com.bx.smpPlugin.anticheat;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;

/** Hitbox geometry for reach and aim checks. Pure maths on Bukkit's value types. */
public final class CombatGeometry {

    private CombatGeometry() {
    }

    /** The point of {@code box} closest to {@code point}. */
    public static Vector closestPoint(BoundingBox box, Vector point) {
        return new Vector(
                clamp(point.getX(), box.getMinX(), box.getMaxX()),
                clamp(point.getY(), box.getMinY(), box.getMaxY()),
                clamp(point.getZ(), box.getMinZ(), box.getMaxZ()));
    }

    /** Distance from {@code eye} to the nearest point of {@code box}; 0 when the eye is inside it. */
    public static double distanceToBox(Vector eye, BoundingBox box) {
        return eye.distance(closestPoint(box, eye));
    }

    /**
     * Shortest distance from the eye to any of the target's recent hitboxes. Using where the target
     * was during the attacker's latency window avoids flagging laggy players as reach cheaters.
     */
    public static double lagCompensatedDistance(Vector eye, Collection<BoundingBox> candidateBoxes) {
        double best = Double.MAX_VALUE;
        for (BoundingBox box : candidateBoxes) {
            best = Math.min(best, distanceToBox(eye, box));
        }
        return best;
    }

    /** Angle in degrees between the look direction and the direction to {@code targetPoint}. */
    public static double angleTo(Vector eye, Vector lookDirection, Vector targetPoint) {
        Vector toTarget = targetPoint.clone().subtract(eye);
        double lengths = Math.sqrt(toTarget.lengthSquared() * lookDirection.lengthSquared());
        if (lengths < 1.0E-6) {
            return 0D;
        }
        // Vector#angle works in float precision; compute in double and clamp against rounding.
        double cosine = Math.max(-1D, Math.min(1D, toTarget.dot(lookDirection) / lengths));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
