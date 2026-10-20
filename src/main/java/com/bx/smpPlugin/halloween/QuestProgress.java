package com.bx.smpPlugin.halloween;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * How far a player is with each quest this season, and which quests are finished. Stored as
 * {@code id=count;id=done;...}. Pure, so it can be tested.
 */
public record QuestProgress(Map<String, Integer> counts, Set<String> done) {

    public static final QuestProgress EMPTY = new QuestProgress(Map.of(), Set.of());

    public QuestProgress {
        counts = Map.copyOf(counts);
        done = Set.copyOf(done);
    }

    public static QuestProgress parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return EMPTY;
        }
        Map<String, Integer> counts = new TreeMap<>();
        Set<String> done = new TreeSet<>();
        for (String entry : stored.split(";")) {
            int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String id = entry.substring(0, split);
            String value = entry.substring(split + 1);
            if (value.equals("done")) {
                done.add(id);
                continue;
            }
            try {
                counts.put(id, Math.max(0, Integer.parseInt(value)));
            } catch (NumberFormatException ignored) {
                // A damaged entry restarts that quest instead of breaking the others.
            }
        }
        return new QuestProgress(counts, done);
    }

    public String serialize() {
        Map<String, String> all = new TreeMap<>();
        counts.forEach((id, count) -> all.put(id, Integer.toString(count)));
        done.forEach(id -> all.put(id, "done"));
        StringBuilder out = new StringBuilder();
        all.forEach((id, value) -> out.append(out.isEmpty() ? "" : ";").append(id).append('=').append(value));
        return out.toString();
    }

    public int count(String id) {
        return done.contains(id) ? Integer.MAX_VALUE : counts.getOrDefault(id, 0);
    }

    public boolean isDone(String id) {
        return done.contains(id);
    }

    /** Adds to a quest's count; once it reaches {@code goal} the quest is done (and stays done). */
    public QuestProgress add(String id, int amount, int goal) {
        if (done.contains(id) || amount <= 0) {
            return this;
        }
        long next = (long) counts.getOrDefault(id, 0) + amount;
        Map<String, Integer> newCounts = new TreeMap<>(counts);
        Set<String> newDone = new TreeSet<>(done);
        if (next >= goal) {
            newCounts.remove(id);
            newDone.add(id);
        } else {
            newCounts.put(id, (int) next);
        }
        return new QuestProgress(newCounts, newDone);
    }
}
