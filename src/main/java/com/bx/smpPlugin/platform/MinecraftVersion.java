// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {

    private final int[] components;

    private MinecraftVersion(int[] components) {
        this.components = components;
    }

    public static MinecraftVersion parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("version is blank");
        }
        String release = raw.trim().toLowerCase(Locale.ROOT);
        int suffix = release.indexOf('-');
        if (suffix >= 0) {
            release = release.substring(0, suffix);
        }

        List<Integer> parts = new ArrayList<>();
        for (String part : release.split("\\.")) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                break;
            }
            parts.add(Integer.parseInt(part));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("not a Minecraft version: " + raw);
        }

        int[] components = new int[parts.size()];
        for (int i = 0; i < components.length; i++) {
            components[i] = parts.get(i);
        }
        return new MinecraftVersion(components);
    }

    public boolean isAtLeast(MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int length = Math.max(components.length, other.components.length);
        for (int i = 0; i < length; i++) {
            int left = i < components.length ? components[i] : 0;
            int right = i < other.components.length ? other.components[i] : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MinecraftVersion other && compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        // Trailing zero components do not change equality, so they must not change the hash either.
        int end = components.length;
        while (end > 1 && components[end - 1] == 0) {
            end--;
        }
        int hash = 1;
        for (int i = 0; i < end; i++) {
            hash = 31 * hash + components[i];
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(components[i]);
        }
        return builder.toString();
    }
}
