package com.bx.smpPlugin.managers;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrateHistoryTracker {

    private static final int MAX_HISTORY_PER_PLAYER = 50;

    private record Entry(
            UUID playerId,
            String playerName,
            String crateId,
            String crateDisplayName,
            String rewardId,
            String rewardDisplayName,
            long timestampMillis
    ) {}

    private final Deque<Entry> globalHistory = new ArrayDeque<>();
    private final ConcurrentHashMap<UUID, Deque<Entry>> playerHistories = new ConcurrentHashMap<>();

    public void recordClaim(UUID playerId, String playerName, String crateId, String crateDisplayName,
                            String rewardId, String rewardDisplayName) {
        long now = System.currentTimeMillis();
        Entry entry = new Entry(playerId, playerName, crateId, crateDisplayName, rewardId, rewardDisplayName, now);

        synchronized (globalHistory) {
            globalHistory.addFirst(entry);
            while (globalHistory.size() > MAX_HISTORY_PER_PLAYER * 5) {
                globalHistory.removeLast();
            }
        }

        playerHistories.computeIfAbsent(playerId, id -> {
            Deque<Entry> deque = new ArrayDeque<>();
            return deque;
        });
        Deque<Entry> playerDeque = playerHistories.get(playerId);
        synchronized (playerDeque) {
            playerDeque.addFirst(entry);
            while (playerDeque.size() > MAX_HISTORY_PER_PLAYER) {
                playerDeque.removeLast();
            }
        }
    }

    public Deque<CrateManager.CrateHistoryEntry> getPlayerHistory(UUID playerId) {
        Deque<Entry> playerDeque = playerHistories.get(playerId);
        if (playerDeque == null || playerDeque.isEmpty()) {
            return new ArrayDeque<>();
        }

        Deque<CrateManager.CrateHistoryEntry> result = new ArrayDeque<>();
        synchronized (playerDeque) {
            for (Entry entry : playerDeque) {
                result.add(new CrateManager.CrateHistoryEntry(
                        entry.crateDisplayName(),
                        entry.rewardDisplayName(),
                        entry.timestampMillis()
                ));
            }
        }
        return result;
    }

    public void clearPlayerHistory(UUID playerId) {
        Deque<Entry> removed = playerHistories.remove(playerId);
        if (removed != null) {
            synchronized (removed) {
                removed.clear();
            }
        }
    }

    public void clearAll() {
        synchronized (globalHistory) {
            globalHistory.clear();
        }
        for (Deque<Entry> deque : playerHistories.values()) {
            synchronized (deque) {
                deque.clear();
            }
        }
        playerHistories.clear();
    }
}
