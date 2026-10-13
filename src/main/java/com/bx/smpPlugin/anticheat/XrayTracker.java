package com.bx.smpPlugin.anticheat;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Tracks how often a player finds <em>hidden</em> rare-ore veins compared with how much ordinary
 * rock they mine, for one ore family (e.g. diamonds or ancient debris).
 *
 * <ul>
 *   <li>Several ore blocks broken close together count as one vein.</li>
 *   <li>Ore that touched cave air, water or lava was visible from a cave and does not count; only
 *       veins fully enclosed in rock count. X-ray users tunnel straight to enclosed veins, while
 *       legitimate branch miners find them at a much lower rate.</li>
 * </ul>
 */
final class XrayTracker {

    private record OreBreak(long time, int x, int y, int z) {
    }

    private final ArrayDeque<Long> fillerBreaks = new ArrayDeque<>();
    private final ArrayDeque<Long> hiddenVeins = new ArrayDeque<>();
    private final ArrayDeque<OreBreak> recentOre = new ArrayDeque<>();
    private final long windowMillis;
    private final int veinRadius;

    XrayTracker(long windowMillis, int veinRadius) {
        this.windowMillis = windowMillis;
        this.veinRadius = veinRadius;
    }

    void recordFiller(long nowMillis) {
        prune(nowMillis);
        fillerBreaks.addLast(nowMillis);
        while (fillerBreaks.size() > 20_000) {
            fillerBreaks.removeFirst();
        }
    }

    /**
     * Records a broken ore block.
     *
     * @param exposed whether the ore touched cave air or liquid (visible without x-ray)
     * @return true when this block starts a new hidden vein
     */
    boolean recordOre(long nowMillis, int x, int y, int z, boolean exposed) {
        prune(nowMillis);
        boolean partOfKnownVein = false;
        for (OreBreak previous : recentOre) {
            if (Math.abs(previous.x() - x) <= veinRadius && Math.abs(previous.y() - y) <= veinRadius
                    && Math.abs(previous.z() - z) <= veinRadius) {
                partOfKnownVein = true;
                break;
            }
        }
        recentOre.addLast(new OreBreak(nowMillis, x, y, z));
        while (recentOre.size() > 512) {
            recentOre.removeFirst();
        }
        if (partOfKnownVein || exposed) {
            return false;
        }
        hiddenVeins.addLast(nowMillis);
        return true;
    }

    int hiddenVeins(long nowMillis) {
        prune(nowMillis);
        return hiddenVeins.size();
    }

    int fillerBlocks(long nowMillis) {
        prune(nowMillis);
        return fillerBreaks.size();
    }

    /** Hidden veins per 1000 ordinary blocks mined, with a floor on the divisor so tiny samples stay calm. */
    double veinsPerThousand(long nowMillis, int minimumFiller) {
        prune(nowMillis);
        int filler = Math.max(fillerBreaks.size(), Math.max(1, minimumFiller));
        return hiddenVeins.size() * 1000D / filler;
    }

    private void prune(long nowMillis) {
        long cutoff = nowMillis - windowMillis;
        while (!fillerBreaks.isEmpty() && fillerBreaks.peekFirst() < cutoff) {
            fillerBreaks.removeFirst();
        }
        while (!hiddenVeins.isEmpty() && hiddenVeins.peekFirst() < cutoff) {
            hiddenVeins.removeFirst();
        }
        // Vein grouping only needs a few minutes of history.
        long veinCutoff = nowMillis - 5L * 60_000L;
        for (Iterator<OreBreak> iterator = recentOre.iterator(); iterator.hasNext(); ) {
            if (iterator.next().time() < veinCutoff) {
                iterator.remove();
            } else {
                break;
            }
        }
    }
}
