// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// A place inside a protected world where players may fight anyway, e.g. an arena ring around a spawn
// town. Written in worlds.yml PVP-AREAS as "ring <min-radius> <max-radius> <centre-x> <centre-z>"
// or "box <x1> <z1> <x2> <z2>". Building stays protected there; only player damage is allowed.
public record PvpArea(Shape shape, double a, double b, double c, double d) {

    public enum Shape { RING, BOX }

    public boolean contains(double x, double z) {
        if (shape == Shape.RING) {
            double dx = x - c;
            double dz = z - d;
            double distanceSquared = dx * dx + dz * dz;
            return distanceSquared >= a * a && distanceSquared <= b * b;
        }
        return x >= Math.min(a, c) && x <= Math.max(a, c) + 1 && z >= Math.min(b, d) && z <= Math.max(b, d) + 1;
    }

    public static Optional<PvpArea> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 3) {
            return Optional.empty();
        }
        try {
            String type = parts[0].toLowerCase(Locale.ROOT);
            if (type.equals("ring")) {
                double min = Double.parseDouble(parts[1]);
                double max = Double.parseDouble(parts[2]);
                double cx = parts.length > 4 ? Double.parseDouble(parts[3]) : 0D;
                double cz = parts.length > 4 ? Double.parseDouble(parts[4]) : 0D;
                if (!finite(min, max, cx, cz) || min < 0D || max <= min) {
                    return Optional.empty();
                }
                return Optional.of(new PvpArea(Shape.RING, min, max, cx, cz));
            }
            if (type.equals("box") && parts.length >= 5) {
                double x1 = Double.parseDouble(parts[1]);
                double z1 = Double.parseDouble(parts[2]);
                double x2 = Double.parseDouble(parts[3]);
                double z2 = Double.parseDouble(parts[4]);
                return finite(x1, z1, x2, z2) ? Optional.of(new PvpArea(Shape.BOX, x1, z1, x2, z2)) : Optional.empty();
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static List<PvpArea> parseAll(List<String> raw) {
        List<PvpArea> areas = new ArrayList<>();
        for (String line : raw) {
            parse(line).ifPresent(areas::add);
        }
        return List.copyOf(areas);
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
