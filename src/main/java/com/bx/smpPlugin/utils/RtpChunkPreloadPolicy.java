// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RtpChunkPreloadPolicy {

    private static final String RADIUS_SETTING = "SETTINGS.PRELOAD-RADIUS";
    private static final String CHUNKS_PER_TICK_SETTING = "SETTINGS.PRELOAD-CHUNKS-PER-TICK";
    private static final String CHUNK_THROTTLE_SETTING = "SETTINGS.POST-TELEPORT-CHUNK-THROTTLE";
    private static final String VIEW_DISTANCE_SETTING = "SETTINGS.POST-TELEPORT-VIEW-DISTANCE";

    private static final int DEFAULT_RADIUS = 2;
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 4;
    private static final int DEFAULT_CHUNKS_PER_TICK = 2;
    private static final int MIN_CHUNKS_PER_TICK = 2;
    private static final int DEFAULT_VIEW_DISTANCE = 4;
    private static final int MIN_VIEW_DISTANCE = 2;

    private RtpChunkPreloadPolicy() {
    }

    public static int radius(ConfigurationSection rtp) {
        if (rtp == null) {
            return MIN_RADIUS;
        }
        int radius = Math.max(0, Math.min(MAX_RADIUS, rtp.getInt(RADIUS_SETTING, DEFAULT_RADIUS)));
        if (rtp.getBoolean(CHUNK_THROTTLE_SETTING, true)) {
            radius = Math.max(radius, Math.min(MAX_RADIUS, throttledViewDistance(rtp)));
        }
        return Math.max(MIN_RADIUS, radius);
    }

    public static int chunksPerTick(ConfigurationSection rtp) {
        if (rtp == null) {
            return MIN_CHUNKS_PER_TICK;
        }
        return Math.max(MIN_CHUNKS_PER_TICK, rtp.getInt(CHUNKS_PER_TICK_SETTING, DEFAULT_CHUNKS_PER_TICK));
    }

    public static List<int[]> chunkOrder(int centerChunkX, int centerChunkZ, int radius) {
        List<int[]> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            }
        }
        chunks.sort(Comparator.comparingInt(chunk ->
                Math.abs(chunk[0] - centerChunkX) + Math.abs(chunk[1] - centerChunkZ)));
        return chunks;
    }

    private static int throttledViewDistance(ConfigurationSection rtp) {
        return Math.max(MIN_VIEW_DISTANCE, rtp.getInt(VIEW_DISTANCE_SETTING, DEFAULT_VIEW_DISTANCE));
    }
}
