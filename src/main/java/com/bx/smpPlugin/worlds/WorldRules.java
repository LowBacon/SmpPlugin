// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record WorldRules(
        String key,
        boolean enabled,
        String worldName,
        boolean createIfMissing,
        String environment,
        String generator,
        String visibilityGroup,
        String visibility,
        boolean hiddenFromOtherWorlds,
        boolean sidebar,
        boolean pvp,
        boolean blockBreak,
        boolean blockPlace,
        boolean containers,
        boolean entityInteract,
        boolean itemDrop,
        boolean itemPickup,
        boolean hunger,
        boolean fallDamage,
        boolean otherDamage,
        boolean mobSpawning,
        boolean portals,
        boolean leaveByTeleport,
        boolean commandAllowlist,
        Set<String> allowedCommands,
        int protectedRadius,
        boolean respawnInWorld,
        String deathInventory,
        String difficulty,
        long lockTime,
        boolean lockWeather,
        Map<String, String> gameRules,
        String spawn
) {

    public static final String SAME_WORLD = "SAME_WORLD";
    public static final String ALL_ONLINE = "ALL_ONLINE";

    static WorldRules defaults(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        boolean lobby = k.equals("lobby");
        boolean ffa = k.equals("ffa");
        return new WorldRules(k, true, k, true, "NORMAL", "VOID", k, k.equals("spawn") ? ALL_ONLINE : SAME_WORLD, lobby,
                // sidebar, pvp, break, place, containers, entities, drop, pickup, hunger, fall, other damage
                !lobby, ffa, false, false, false, !lobby, !lobby, !lobby, !lobby, !lobby, !lobby,
                // mob spawning, portals, leave by teleport, command allowlist
                !lobby && !ffa, !lobby, !lobby, lobby,
                lobby ? Set.of("help", "list", "msg", "tell", "reply", "rules", "lobby", "spawn") : Set.of(),
                lobby || ffa ? -1 : 100, lobby || ffa, "DEFAULT", lobby ? "PEACEFUL" : "",
                lobby ? 6000L : -1L, lobby, Map.of(), "");
    }

    static WorldRules read(String key, ConfigurationSection section) {
        WorldRules d = defaults(key);
        if (section == null) {
            return d;
        }
        Map<String, String> gameRules = new LinkedHashMap<>();
        ConfigurationSection rules = section.getConfigurationSection("GAMERULES");
        if (rules != null) {
            for (String name : rules.getKeys(false)) {
                gameRules.put(name, String.valueOf(rules.get(name)));
            }
        }
        List<String> commands = section.getStringList("ALLOWED-COMMANDS");
        Set<String> allowed = section.contains("ALLOWED-COMMANDS")
                ? commands.stream().map(WorldRules::normaliseCommand).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet())
                : d.allowedCommands();
        return new WorldRules(
                d.key(),
                section.getBoolean("ENABLED", d.enabled()),
                text(section.getString("WORLD-NAME"), d.worldName()),
                section.getBoolean("CREATE-IF-MISSING", d.createIfMissing()),
                upper(section.getString("ENVIRONMENT"), d.environment()),
                upper(section.getString("GENERATOR"), d.generator()),
                text(section.getString("VISIBILITY-GROUP"), d.visibilityGroup()).toLowerCase(Locale.ROOT),
                visibilityPolicy(section.getString("VISIBILITY"), d.visibility()),
                section.getBoolean("HIDDEN-FROM-OTHER-WORLDS", d.hiddenFromOtherWorlds()),
                section.getBoolean("SIDEBAR", d.sidebar()),
                section.getBoolean("PVP", d.pvp()),
                section.getBoolean("BLOCK-BREAK", d.blockBreak()),
                section.getBoolean("BLOCK-PLACE", d.blockPlace()),
                section.getBoolean("CONTAINERS", d.containers()),
                section.getBoolean("ENTITY-INTERACT", d.entityInteract()),
                section.getBoolean("ITEM-DROP", d.itemDrop()),
                section.getBoolean("ITEM-PICKUP", d.itemPickup()),
                section.getBoolean("HUNGER", d.hunger()),
                section.getBoolean("FALL-DAMAGE", d.fallDamage()),
                section.getBoolean("OTHER-DAMAGE", d.otherDamage()),
                section.getBoolean("MOB-SPAWNING", d.mobSpawning()),
                section.getBoolean("PORTALS", d.portals()),
                section.getBoolean("LEAVE-BY-TELEPORT", d.leaveByTeleport()),
                section.getBoolean("COMMAND-ALLOWLIST", d.commandAllowlist()),
                allowed,
                section.getInt("PROTECTED-RADIUS", d.protectedRadius()),
                section.getBoolean("RESPAWN-IN-WORLD", d.respawnInWorld()),
                upper(section.getString("DEATH-INVENTORY"), d.deathInventory()),
                upper(section.getString("DIFFICULTY"), d.difficulty()),
                section.getLong("LOCK-TIME", d.lockTime()),
                section.getBoolean("LOCK-WEATHER", d.lockWeather()),
                Map.copyOf(gameRules),
                text(section.getString("SPAWN"), d.spawn()));
    }

    public WorldRules withEnabled(boolean value) {
        return new WorldRules(key, value, worldName, createIfMissing, environment, generator, visibilityGroup, visibility,
                hiddenFromOtherWorlds, sidebar, pvp, blockBreak, blockPlace, containers, entityInteract, itemDrop,
                itemPickup, hunger, fallDamage, otherDamage, mobSpawning, portals, leaveByTeleport, commandAllowlist,
                allowedCommands, protectedRadius, respawnInWorld, deathInventory, difficulty, lockTime, lockWeather,
                gameRules, spawn);
    }

    public boolean isProtectedAt(double x, double z, double cx, double cz) {
        if (protectedRadius < 0) {
            return true;
        }
        if (protectedRadius == 0) {
            return false;
        }
        double dx = x - cx;
        double dz = z - cz;
        return dx * dx + dz * dz <= (double) protectedRadius * protectedRadius;
    }

    public static String normaliseCommand(String raw) {
        if (raw == null) {
            return "";
        }
        String label = raw.trim();
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        return label.toLowerCase(Locale.ROOT);
    }

    static String visibilityPolicy(String value, String fallback) {
        String policy = upper(value, fallback).replace('-', '_');
        return switch (policy) {
            case "SAME_GROUP" -> SAME_WORLD;
            case "ALL", "ALL_PLAYERS" -> ALL_ONLINE;
            default -> policy;
        };
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
