package com.bx.finnishSmp.utils;

import com.bx.finnishSmp.FinnishSmp;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

public final class MobSpawnPolicy {

    private static final String VANILLA_SPAWNER_MOB_KEY = "vanilla_spawner_mob";
    private static final byte TRUE = 1;

    private MobSpawnPolicy() {
    }

    public static boolean isVanillaSpawnerSpawn(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.SPAWNER;
    }

    public static void markVanillaSpawnerMob(FinnishSmp plugin, LivingEntity entity) {
        if (plugin == null || entity == null) {
            return;
        }
        entity.getPersistentDataContainer().set(
                plugin.getKey(VANILLA_SPAWNER_MOB_KEY),
                PersistentDataType.BYTE,
                TRUE
        );
    }

    public static boolean isVanillaSpawnerMob(FinnishSmp plugin, LivingEntity entity) {
        if (plugin == null || entity == null) {
            return false;
        }
        return entity.getPersistentDataContainer().getOrDefault(
                plugin.getKey(VANILLA_SPAWNER_MOB_KEY),
                PersistentDataType.BYTE,
                (byte) 0
        ) == TRUE;
    }

    public static boolean shouldRemoveFromPeriodicCleanup(
            boolean monster,
            EntityType type,
            boolean vanillaSpawnerMob
    ) {
        if (!monster || type == null || vanillaSpawnerMob) {
            return false;
        }
        return switch (type) {
            case PHANTOM, WITHER, ENDER_DRAGON, ELDER_GUARDIAN, WARDEN -> false;
            default -> true;
        };
    }

    public static boolean shouldRemoveFromPeriodicCleanup(
            FinnishSmp plugin,
            LivingEntity entity
    ) {
        return entity != null && shouldRemoveFromPeriodicCleanup(
                entity instanceof Monster || entity instanceof org.bukkit.entity.Slime || entity instanceof org.bukkit.entity.Ghast,
                entity.getType(),
                isVanillaSpawnerMob(plugin, entity)
        );
    }
}
