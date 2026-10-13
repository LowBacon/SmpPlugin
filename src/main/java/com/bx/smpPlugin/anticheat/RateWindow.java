package com.bx.smpPlugin.anticheat;

import java.util.ArrayDeque;

/** Counts events in a sliding time window, with a hard cap on stored timestamps. */
final class RateWindow {

    private final ArrayDeque<Long> timestamps = new ArrayDeque<>();
    private final long windowMillis;
    private final int capacity;

    RateWindow(long windowMillis, int capacity) {
        this.windowMillis = windowMillis;
        this.capacity = capacity;
    }

    /** Records an event and returns how many events fall inside the window, including this one. */
    int record(long nowMillis) {
        prune(nowMillis);
        timestamps.addLast(nowMillis);
        while (timestamps.size() > capacity) {
            timestamps.removeFirst();
        }
        return timestamps.size();
    }

    /** Events in the window ending at {@code nowMillis}. */
    int count(long nowMillis) {
        prune(nowMillis);
        return timestamps.size();
    }

    /** Events recorded within the same 50 ms server tick as {@code nowMillis}. */
    int countInSameTick(long nowMillis) {
        long tick = nowMillis / 50L;
        int count = 0;
        for (Long timestamp : timestamps) {
            if (timestamp / 50L == tick) {
                count++;
            }
        }
        return count;
    }

    double perSecond(long nowMillis) {
        return count(nowMillis) * 1000D / windowMillis;
    }

    void clear() {
        timestamps.clear();
    }

    private void prune(long nowMillis) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= nowMillis - windowMillis) {
            timestamps.removeFirst();
        }
    }
}
