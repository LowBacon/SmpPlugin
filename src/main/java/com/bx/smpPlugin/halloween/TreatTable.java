// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.core.Reward;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public record TreatTable(List<Treat> treats, List<String> problems) {

    public record Treat(String id, int weight, Reward reward, long candy, boolean announce) {
    }

    public static final Treat FALLBACK = new Treat("fallback", 1, new Reward(100D, 0L, List.of(), List.of(), List.of()),
            1L, false);

    public TreatTable {
        treats = List.copyOf(treats);
        problems = List.copyOf(problems);
    }

    public static TreatTable read(ConfigurationSection section) {
        List<Treat> treats = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (section == null) {
            problems.add("TRICK-OR-TREAT.TREATS is missing; every treat is $100.");
            return new TreatTable(treats, problems);
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection treat = section.getConfigurationSection(id);
            if (treat == null) {
                continue;
            }
            int weight = treat.getInt("WEIGHT", 0);
            if (weight <= 0) {
                problems.add("Treat '" + id + "' has no WEIGHT above 0 and is never picked.");
                continue;
            }
            Reward reward = Reward.read(treat);
            long candy = Math.max(0L, treat.getLong("CANDY", 0L));
            if (reward.isEmpty() && candy <= 0L) {
                problems.add("Treat '" + id + "' gives nothing and is left out.");
                continue;
            }
            treats.add(new Treat(id, weight, reward, candy, treat.getBoolean("ANNOUNCE", false)));
        }
        if (treats.isEmpty()) {
            problems.add("TRICK-OR-TREAT.TREATS has no usable treat; every treat is $100.");
        }
        return new TreatTable(treats, problems);
    }

    public long totalWeight() {
        long total = 0L;
        for (Treat treat : treats) {
            total += treat.weight();
        }
        return total;
    }

    public Treat pick(RandomGenerator random) {
        long total = totalWeight();
        if (total <= 0L) {
            return FALLBACK;
        }
        long roll = random.nextLong(total);
        for (Treat treat : treats) {
            roll -= treat.weight();
            if (roll < 0L) {
                return treat;
            }
        }
        return treats.get(treats.size() - 1);
    }

    public double chancePercent(Treat treat) {
        long total = totalWeight();
        return total <= 0L ? 0D : treat.weight() * 100D / total;
    }
}
