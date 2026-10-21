package com.bx.smpPlugin.events;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedRewardsTest {

    private static WeightedRewards table(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return WeightedRewards.read(config.getConfigurationSection("REWARDS"));
    }

    @Test
    void entriesWithoutWeightOrRewardAreLeftOut() throws Exception {
        WeightedRewards rewards = table("""
                REWARDS:
                  coins: {WEIGHT: 3, MONEY: 100}
                  nothing: {WEIGHT: 5}
                  zero: {WEIGHT: 0, MONEY: 50}
                  shards: {WEIGHT: 1, SHARDS: 2, ANNOUNCE: true}
                """);
        assertEquals(2, rewards.entries().size());
        assertEquals(0.75D, rewards.chance(rewards.entries().get(0)), 1e-9);
        assertTrue(rewards.entries().get(1).announce());
    }

    @Test
    void rollsFollowTheWeights() throws Exception {
        WeightedRewards rewards = table("""
                REWARDS:
                  common: {WEIGHT: 90, MONEY: 1}
                  rare: {WEIGHT: 10, MONEY: 1000}
                """);
        SplittableRandom random = new SplittableRandom(42L);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 20_000; i++) {
            counts.merge(rewards.roll(random).id(), 1, Integer::sum);
        }
        double rare = counts.getOrDefault("rare", 0) / 20_000D;
        assertEquals(0.10D, rare, 0.01D);
    }

    @Test
    void anEmptyTableRollsNothing() throws Exception {
        assertTrue(table("REWARDS: {}").isEmpty());
        assertNull(table("OTHER: 1").roll(new SplittableRandom(1L)));
    }
}
