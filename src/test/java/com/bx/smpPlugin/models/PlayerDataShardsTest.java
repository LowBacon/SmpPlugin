// SmpPlugin by LowBacon
package com.bx.smpPlugin.models;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataShardsTest {

    @Test
    void addingPastTheMaximumKeepsTheMaximumInsteadOfWipingTheBalance() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "Dave");
        data.setShards(10L);
        data.addShards(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, data.getShards());
    }

    @Test
    void aNegativeRemovalIsRefusedRatherThanAddingShards() {
        PlayerData data = new PlayerData(UUID.randomUUID(), "Dave");
        data.setShards(50L);
        assertFalse(data.removeShards(-100L));
        assertEquals(50L, data.getShards());
        assertTrue(data.removeShards(20L));
        assertEquals(30L, data.getShards());
        assertFalse(data.removeShards(31L));
    }
}
