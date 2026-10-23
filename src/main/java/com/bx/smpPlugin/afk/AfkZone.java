// SmpPlugin by LowBacon
package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.utils.LocationUtils;
import org.bukkit.Location;

public record AfkZone(
        String id,
        String displayName,
        String spawn,
        Region region,
        long rewardAmount,
        int rewardSeconds
) {

    public AfkZone {
        displayName = displayName == null || displayName.isBlank() ? "&d" + id : displayName;
        spawn = spawn == null ? "" : spawn;
        rewardAmount = Math.max(0L, rewardAmount);
        rewardSeconds = Math.max(1, rewardSeconds);
    }

    public record Region(String world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public Region {
            double lowX = Math.min(minX, maxX);
            double lowY = Math.min(minY, maxY);
            double lowZ = Math.min(minZ, maxZ);
            maxX = Math.max(minX, maxX);
            maxY = Math.max(minY, maxY);
            maxZ = Math.max(minZ, maxZ);
            minX = lowX;
            minY = lowY;
            minZ = lowZ;
        }

        public static Region ofCorners(Location first, Location second) {
            int lowY = Math.min(first.getBlockY(), second.getBlockY());
            int highY = Math.max(first.getBlockY(), second.getBlockY());
            // Corners picked at one height still give players room to stand and jump.
            if (highY - lowY < 3) {
                lowY -= 3;
                highY += 6;
            }
            return new Region(first.getWorld().getName(),
                    Math.min(first.getBlockX(), second.getBlockX()), lowY, Math.min(first.getBlockZ(), second.getBlockZ()),
                    Math.max(first.getBlockX(), second.getBlockX()) + 1D, highY + 1D,
                    Math.max(first.getBlockZ(), second.getBlockZ()) + 1D);
        }

        public static Region square(Location center, int radius) {
            double cx = center.getBlockX() + 0.5D;
            double cz = center.getBlockZ() + 0.5D;
            return new Region(center.getWorld().getName(),
                    cx - radius, center.getWorld().getMinHeight(), cz - radius,
                    cx + radius, center.getWorld().getMaxHeight(), cz + radius);
        }

        public boolean contains(Location location) {
            return location != null && location.getWorld() != null
                    && location.getWorld().getName().equals(world)
                    && contains(location.getX(), location.getY(), location.getZ());
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public String describe() {
            return world + " " + (int) Math.floor(minX) + "," + (int) Math.floor(minZ)
                    + " to " + (int) Math.floor(maxX) + "," + (int) Math.floor(maxZ)
                    + " (" + (int) (maxX - minX) + "x" + (int) (maxZ - minZ) + ")";
        }
    }

    public boolean hasRegion() {
        return region != null;
    }

    public boolean paysRewards() {
        return region != null && rewardAmount > 0L;
    }

    public boolean contains(Location location) {
        return region != null && region.contains(location);
    }

    public Location spawnLocation() {
        return LocationUtils.parse(spawn);
    }

    public AfkZone withRegion(Region newRegion) {
        return new AfkZone(id, displayName, spawn, newRegion, rewardAmount, rewardSeconds);
    }

    public AfkZone withSpawn(Location location) {
        return new AfkZone(id, displayName, LocationUtils.serialize(location), region, rewardAmount, rewardSeconds);
    }

    public AfkZone withReward(long amount, int seconds) {
        return new AfkZone(id, displayName, spawn, region, amount, seconds);
    }

    public AfkZone withDisplayName(String name) {
        return new AfkZone(id, name, spawn, region, rewardAmount, rewardSeconds);
    }
}
