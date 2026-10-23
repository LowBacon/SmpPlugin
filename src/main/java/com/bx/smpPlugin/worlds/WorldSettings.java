// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record WorldSettings(
        boolean enabled,
        Map<String, WorldRules> worlds,
        String otherWorldsGroup,
        String otherWorldsVisibility,
        boolean visibilityByWorld,
        CommandBypass commandBypass,
        List<String> problems
) {

    public record CommandBypass(boolean ops, List<String> permissions, List<String> groups) {
        static CommandBypass read(ConfigurationSection section) {
            if (section == null) {
                return new CommandBypass(true, List.of(WorldSystem.BYPASS_COMMANDS), DEFAULT_GROUPS);
            }
            List<String> permissions = section.contains("PERMISSIONS")
                    ? section.getStringList("PERMISSIONS").stream().map(String::trim).filter(s -> !s.isEmpty()).toList()
                    : List.of(WorldSystem.BYPASS_COMMANDS);
            List<String> groups = section.contains("GROUPS")
                    ? section.getStringList("GROUPS").stream().map(CommandBypass::normaliseGroup).filter(s -> !s.isEmpty())
                    .distinct().toList()
                    : DEFAULT_GROUPS;
            return new CommandBypass(section.getBoolean("OPS", true), permissions, groups);
        }

        public static String normaliseGroup(String name) {
            return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        }
    }

    static final List<String> DEFAULT_GROUPS = List.of("owner", "coowner", "co-owner", "dev", "developer", "manager");

    public static final List<String> KEYS = List.of("lobby", "spawn", "ffa");

    private static final Set<String> ENVIRONMENTS = Set.of("NORMAL", "NETHER", "THE_END");
    private static final Set<String> GENERATORS = Set.of("NORMAL", "FLAT", "LARGE_BIOMES", "AMPLIFIED", "VOID");
    private static final Set<String> DIFFICULTIES = Set.of("", "PEACEFUL", "EASY", "NORMAL", "HARD");
    private static final Set<String> DEATH_INVENTORY = Set.of("DEFAULT", "KEEP", "DROP");
    private static final Set<String> VISIBILITY = Set.of(WorldRules.SAME_WORLD, WorldRules.ALL_ONLINE);

    public static WorldSettings read(ConfigurationSection root) {
        Map<String, WorldRules> worlds = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        boolean enabled = root == null || root.getBoolean("ENABLED", true);
        String otherGroup = root == null ? "spawn" : root.getString("OTHER-WORLDS-GROUP", "spawn");
        boolean visibility = root == null || root.getBoolean("VISIBILITY-BY-WORLD", true);
        String otherVisibility = WorldRules.visibilityPolicy(root == null ? null : root.getString("OTHER-WORLDS-VISIBILITY"),
                WorldRules.ALL_ONLINE);
        if (!VISIBILITY.contains(otherVisibility)) {
            problems.add("OTHER-WORLDS-VISIBILITY must be SAME_WORLD or ALL_ONLINE.");
        }
        CommandBypass bypass = CommandBypass.read(root == null ? null : root.getConfigurationSection("COMMAND-BYPASS"));
        Map<String, String> seenNames = new LinkedHashMap<>();
        for (String key : KEYS) {
            ConfigurationSection section = root == null ? null : root.getConfigurationSection("WORLDS." + key.toUpperCase(Locale.ROOT));
            if (section == null && root != null) {
                section = root.getConfigurationSection("WORLDS." + key);
            }
            WorldRules rules = WorldRules.read(key, section);
            worlds.put(key, rules);
            if (!rules.enabled()) {
                continue;
            }
            String where = "WORLDS." + key.toUpperCase(Locale.ROOT);
            if (!rules.worldName().matches("[A-Za-z0-9_\\-]{1,64}")) {
                problems.add(where + ".WORLD-NAME '" + rules.worldName() + "' may only use letters, digits, - and _.");
            }
            String other = seenNames.putIfAbsent(rules.worldName().toLowerCase(Locale.ROOT), key);
            if (other != null) {
                problems.add(where + ".WORLD-NAME '" + rules.worldName() + "' is also used by " + other + ".");
            }
            if (!ENVIRONMENTS.contains(rules.environment())) {
                problems.add(where + ".ENVIRONMENT must be one of " + ENVIRONMENTS + ".");
            }
            if (!GENERATORS.contains(rules.generator())) {
                problems.add(where + ".GENERATOR must be one of " + GENERATORS + ".");
            }
            if (!VISIBILITY.contains(rules.visibility())) {
                problems.add(where + ".VISIBILITY must be SAME_WORLD or ALL_ONLINE.");
            }
            if (!DIFFICULTIES.contains(rules.difficulty())) {
                problems.add(where + ".DIFFICULTY must be empty or one of PEACEFUL, EASY, NORMAL, HARD.");
            }
            if (!DEATH_INVENTORY.contains(rules.deathInventory())) {
                problems.add(where + ".DEATH-INVENTORY must be DEFAULT, KEEP or DROP.");
            }
            if (rules.commandAllowlist() && rules.allowedCommands().isEmpty()) {
                problems.add(where + ".COMMAND-ALLOWLIST is on but ALLOWED-COMMANDS is empty: players could run no command.");
            }
            if (rules.lockTime() > 24000L) {
                problems.add(where + ".LOCK-TIME must be -1 (off) or 0 to 24000.");
            }
        }
        return new WorldSettings(enabled, Collections.unmodifiableMap(worlds),
                otherGroup == null || otherGroup.isBlank() ? "spawn" : otherGroup.trim().toLowerCase(Locale.ROOT),
                otherVisibility, visibility, bypass, List.copyOf(problems));
    }

    public WorldRules byWorldName(String worldName) {
        if (!enabled || worldName == null) {
            return null;
        }
        for (WorldRules rules : worlds.values()) {
            if (rules.enabled() && rules.worldName().equalsIgnoreCase(worldName)) {
                return rules;
            }
        }
        return null;
    }

    public String groupOf(String worldName) {
        WorldRules rules = byWorldName(worldName);
        return rules == null ? otherWorldsGroup : rules.visibilityGroup();
    }

    public String visibilityOf(String worldName) {
        WorldRules rules = byWorldName(worldName);
        return rules == null ? otherWorldsVisibility : rules.visibility();
    }

    public boolean sees(String viewerWorld, String targetWorld) {
        if (!enabled || !visibilityByWorld) {
            return true;
        }
        boolean sameGroup = groupOf(viewerWorld).equals(groupOf(targetWorld));
        if (sameGroup) {
            return true;
        }
        WorldRules target = byWorldName(targetWorld);
        if (target != null && target.hiddenFromOtherWorlds()) {
            return false;
        }
        return WorldRules.ALL_ONLINE.equals(visibilityOf(viewerWorld));
    }

    public WorldSettings withWorld(String key, boolean value) {
        WorldRules rules = rules(key);
        if (rules == null || rules.enabled() == value) {
            return this;
        }
        Map<String, WorldRules> copy = new LinkedHashMap<>(worlds);
        copy.put(rules.key(), rules.withEnabled(value));
        return new WorldSettings(enabled, Collections.unmodifiableMap(copy), otherWorldsGroup, otherWorldsVisibility,
                visibilityByWorld, commandBypass, problems);
    }

    public WorldSettings withEnabled(boolean value) {
        return new WorldSettings(value, worlds, otherWorldsGroup, otherWorldsVisibility, visibilityByWorld, commandBypass, problems);
    }

    public WorldRules rules(String key) {
        return key == null ? null : worlds.get(key.toLowerCase(Locale.ROOT));
    }
}
