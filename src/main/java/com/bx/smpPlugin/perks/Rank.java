package com.bx.smpPlugin.perks;

import java.util.Map;

/**
 * One rank from perks.yml: who has it (a permission), how it ranks against the others (a priority, higher wins)
 * and what it adds. A player gets the perks of their highest rank only; ranks never add up.
 */
public record Rank(String id, String permission, int priority, String display, Map<Perk, Integer> perks) {

    public Rank {
        perks = Map.copyOf(perks);
    }

    /** The perk's value for this rank (already capped), or 0. */
    public int value(Perk perk) {
        return perks.getOrDefault(perk, 0);
    }
}
