package com.bx.smpPlugin.anticheat;

import org.bukkit.block.BlockFace;

/**
 * Exact geometry for block interactions. A face of a full cube can only be clicked from the side it
 * faces: the client's crosshair ray enters the block through a face whose plane the eye is in front of.
 */
public final class FaceGeometry {

    private FaceGeometry() {
    }

    /**
     * Whether an eye at ({@code eyeX}, {@code eyeY}, {@code eyeZ}) is in front of {@code face} of the full
     * cube whose minimum corner is ({@code blockX}, {@code blockY}, {@code blockZ}).
     *
     * @param tolerance blocks of slack for position rounding and the client's in-between positions
     */
    public static boolean canSeeFace(double eyeX, double eyeY, double eyeZ,
                                     int blockX, int blockY, int blockZ, BlockFace face, double tolerance) {
        return switch (face) {
            case UP -> eyeY >= blockY + 1D - tolerance;
            case DOWN -> eyeY <= blockY + tolerance;
            case NORTH -> eyeZ <= blockZ + tolerance;
            case SOUTH -> eyeZ >= blockZ + 1D - tolerance;
            case WEST -> eyeX <= blockX + tolerance;
            case EAST -> eyeX >= blockX + 1D - tolerance;
            default -> true;
        };
    }

    /**
     * Whether a ray from the eye along {@code (dirX, dirY, dirZ)} passes through the cube grown by
     * {@code grow} on every side (slab test). Used to see if the player was aiming anywhere near a block.
     */
    public static boolean rayHitsBox(double eyeX, double eyeY, double eyeZ, double dirX, double dirY, double dirZ,
                                     double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                     double grow, double maxDistance) {
        double tMin = 0D;
        double tMax = maxDistance;
        double[] origin = {eyeX, eyeY, eyeZ};
        double[] direction = {dirX, dirY, dirZ};
        double[] low = {minX - grow, minY - grow, minZ - grow};
        double[] high = {maxX + grow, maxY + grow, maxZ + grow};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-9D) {
                if (origin[axis] < low[axis] || origin[axis] > high[axis]) {
                    return false;
                }
                continue;
            }
            double t1 = (low[axis] - origin[axis]) / direction[axis];
            double t2 = (high[axis] - origin[axis]) / direction[axis];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) {
                return false;
            }
        }
        return true;
    }
}
