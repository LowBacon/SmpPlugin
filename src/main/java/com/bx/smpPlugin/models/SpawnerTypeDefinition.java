// SmpPlugin by LowBacon
package com.bx.smpPlugin.models;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record SpawnerTypeDefinition(
        String key,
        String displayName,
        EntityType entityType,
        Material iconMaterial,
        long baseItemsPerCycle,
        List<DropDefinition> drops,
        double xpPerCycle
) {

    public SpawnerTypeDefinition {
        displayName = displayName == null || displayName.isBlank() ? key : displayName;
        iconMaterial = iconMaterial == null ? Material.SPAWNER : iconMaterial;
        baseItemsPerCycle = Math.max(1L, baseItemsPerCycle);
        drops = List.copyOf(drops == null ? List.of() : drops);
        xpPerCycle = Double.isFinite(xpPerCycle) ? Math.max(0D, xpPerCycle) : 0D;
    }

    public static double defaultXpPerCycle(EntityType entityType) {
        if (entityType == null) {
            return 0D;
        }
        return switch (entityType) {
            case IRON_GOLEM, SNOW_GOLEM, VILLAGER, WANDERING_TRADER -> 0D;
            case BLAZE, GUARDIAN, ELDER_GUARDIAN, EVOKER -> 10D;
            default -> {
                Class<?> type = entityType.getEntityClass();
                if (type != null && org.bukkit.entity.Monster.class.isAssignableFrom(type)) {
                    yield 5D;
                }
                yield 2D;
            }
        };
    }

    public record DropDefinition(
            String key,
            Material material,
            long min,
            long max,
            double chance
    ) {
        public DropDefinition {
            key = key == null ? "" : key.trim();
            material = material == null ? Material.STONE : material;
            min = Math.max(0L, min);
            max = Math.max(min, max);
            chance = Math.max(0D, Math.min(1D, chance));
        }

        public double averageDropAmount() {
            return (min + max) / 2.0D;
        }
    }
}
