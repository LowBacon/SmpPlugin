package com.bx.smpPlugin.anticheat;

import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Recent hitboxes of a player, used to lag-compensate other players' reach checks against them. */
final class PositionHistory {

    private record Entry(long time, BoundingBox box) {
    }

    private static final int CAPACITY = 40;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    void record(long nowMillis, BoundingBox box) {
        entries.addLast(new Entry(nowMillis, box.clone()));
        while (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }

    /** Hitboxes from {@code fromMillis} until now, plus the current box. */
    List<BoundingBox> since(long fromMillis, BoundingBox current) {
        List<BoundingBox> boxes = new ArrayList<>();
        boxes.add(current);
        for (Entry entry : entries) {
            if (entry.time() >= fromMillis) {
                boxes.add(entry.box());
            }
        }
        return boxes;
    }

    void clear() {
        entries.clear();
    }
}
