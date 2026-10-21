package com.bx.smpPlugin.events;

import com.bx.smpPlugin.core.Reward;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A table of rewards picked by weight, read from a section like
 * <pre>
 * small:   {WEIGHT: 60, MONEY: 250}
 * jackpot: {WEIGHT: 3, MONEY: 5000, SHARDS: 25, ANNOUNCE: true}
 * </pre>
 * An entry with a weight of zero or less, or that gives nothing, is left out.
 */
public final class WeightedRewards {

    public record Entry(String id, int weight, Reward reward, boolean announce) {
    }

    private final List<Entry> entries;
    private final int totalWeight;

    private WeightedRewards(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.totalWeight = entries.stream().mapToInt(Entry::weight).sum();
    }

    public static WeightedRewards read(ConfigurationSection section) {
        List<Entry> entries = new ArrayList<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                int weight = entry.getInt("WEIGHT", 1);
                Reward reward = Reward.read(entry);
                if (weight > 0 && !reward.isEmpty()) {
                    entries.add(new Entry(key, weight, reward, entry.getBoolean("ANNOUNCE", false)));
                }
            }
        }
        return new WeightedRewards(entries);
    }

    static WeightedRewards of(List<Entry> entries) {
        return new WeightedRewards(entries.stream().filter(entry -> entry.weight() > 0).toList());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry> entries() {
        return entries;
    }

    /** The chance (0 to 1) of one entry. */
    public double chance(Entry entry) {
        return totalWeight <= 0 ? 0D : (double) entry.weight() / totalWeight;
    }

    /** One entry picked by weight, or null when the table is empty. */
    public Entry roll(RandomGenerator random) {
        if (totalWeight <= 0) {
            return null;
        }
        int pick = random.nextInt(totalWeight);
        for (Entry entry : entries) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }
}
