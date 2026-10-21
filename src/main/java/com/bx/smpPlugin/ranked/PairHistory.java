package com.bx.smpPlugin.ranked;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How often two players met in ranked matches recently, for the anti-boosting limit.
 *
 * <p>Only matches inside the window are kept, so the map holds at most the pairs that played in
 * the last {@code PAIR-WINDOW-HOURS}. Not thread-safe: the ranked manager uses it on the main thread.
 */
public final class PairHistory {

    private final Map<String, Deque<Long>> games = new HashMap<>();
    private long windowMillis;

    public PairHistory(long windowMillis) {
        this.windowMillis = Math.max(0L, windowMillis);
    }

    public void setWindowMillis(long windowMillis) {
        this.windowMillis = Math.max(0L, windowMillis);
    }

    /** The same key whichever player comes first. */
    static String key(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }

    public void record(UUID first, UUID second, long at) {
        games.computeIfAbsent(key(first, second), ignored -> new ArrayDeque<>()).addLast(at);
    }

    /** Games the two played within the window that ends at {@code now}. */
    public int count(UUID first, UUID second, long now) {
        Deque<Long> times = games.get(key(first, second));
        if (times == null) {
            return 0;
        }
        long cutoff = now - windowMillis;
        while (!times.isEmpty() && times.peekFirst() < cutoff) {
            times.removeFirst();
        }
        if (times.isEmpty()) {
            games.remove(key(first, second));
            return 0;
        }
        return times.size();
    }

    /** Drops every pair whose games all fell out of the window. */
    public void prune(long now) {
        long cutoff = now - windowMillis;
        games.values().forEach(times -> {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.removeFirst();
            }
        });
        games.values().removeIf(Deque::isEmpty);
    }

    public int size() {
        return games.size();
    }

    public void clear() {
        games.clear();
    }
}
