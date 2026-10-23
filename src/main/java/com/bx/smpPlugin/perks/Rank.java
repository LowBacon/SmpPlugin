// SmpPlugin by LowBacon
package com.bx.smpPlugin.perks;

import java.util.Map;

public record Rank(String id, String permission, int priority, String display, Map<Perk, Integer> perks) {

    public Rank {
        perks = Map.copyOf(perks);
    }

    public int value(Perk perk) {
        return perks.getOrDefault(perk, 0);
    }
}
