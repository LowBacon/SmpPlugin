// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.PlayerVisibilityManager;
import com.bx.smpPlugin.utils.LocationUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class WorldSystem {

    public static final String BYPASS_COMMANDS = "smpplugin.world.bypass.commands";
    public static final String BYPASS_BUILD = "smpplugin.world.bypass.build";
    public static final String SEE_ALL = "smpplugin.world.seeall";
    public static final String ADMIN = "smpplugin.admin.world";

    private final SmpPlugin plugin;
    private volatile WorldSettings settings;
    private final Map<String, World> loaded = new ConcurrentHashMap<>();
    private final Set<UUID> inTransit = ConcurrentHashMap.newKeySet();
    private final Map<String, String> loadProblems = new LinkedHashMap<>();

    public WorldSystem(SmpPlugin plugin) {
        this.plugin = plugin;
        this.settings = WorldSettings.read(null);
    }

    // The lobby and spawn-world feature switches (/smpplugin features) turn those worlds off on top
    // of worlds.yml, so one click goes back to joining straight into the SMP.
    private WorldSettings gate(WorldSettings read) {
        var config = plugin.getConfigManager().getConfig();
        WorldSettings gated = read;
        if (!com.bx.smpPlugin.managers.FeatureManager.isEnabled(config, com.bx.smpPlugin.managers.FeatureManager.Feature.LOBBY)) {
            gated = gated.withWorld("lobby", false);
        }
        if (!com.bx.smpPlugin.managers.FeatureManager.isEnabled(config, com.bx.smpPlugin.managers.FeatureManager.Feature.SPAWN_WORLD)) {
            gated = gated.withWorld("spawn", false);
        }
        return gated;
    }

    public void start() {
        WorldSettings read = gate(WorldSettings.read(plugin.getConfigManager().getWorlds()));
        if (!read.problems().isEmpty()) {
            plugin.getLogger().severe("worlds.yml has " + read.problems().size()
                    + " problem(s); the lobby, spawn and FFA worlds are not managed until they are fixed:");
            read.problems().forEach(problem -> plugin.getLogger().severe(" - " + problem));
            settings = WorldSettings.read(null).withEnabled(false);
            return;
        }
        settings = read;
        if (!read.enabled()) {
            plugin.getLogger().info("World management is off (worlds.yml ENABLED: false).");
            return;
        }
        for (String key : WorldSettings.KEYS) {
            WorldRules rules = read.rules(key);
            if (rules.enabled()) {
                loadWorld(rules);
            }
        }
    }

    public List<String> reload() {
        WorldSettings read = gate(WorldSettings.read(plugin.getConfigManager().getWorlds()));
        if (!read.problems().isEmpty()) {
            return read.problems();
        }
        settings = read;
        if (read.enabled()) {
            for (String key : WorldSettings.KEYS) {
                WorldRules rules = read.rules(key);
                if (!rules.enabled()) {
                    loaded.remove(key);
                    continue;
                }
                World world = loaded.get(key);
                if (world == null || !world.getName().equalsIgnoreCase(rules.worldName())) {
                    loaded.remove(key);
                    loadWorld(rules);
                } else {
                    applySettings(world, rules);
                }
            }
        } else {
            loaded.clear();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
        return List.of();
    }

    public void stop() {
        inTransit.clear();
        SmpArrival.clearAll();
    }

    public WorldSettings settings() {
        return settings;
    }

    // ------------------------------------------------------------------ loading

    private void loadWorld(WorldRules rules) {
        String name = rules.worldName();
        World world = Bukkit.getWorld(name);
        if (world == null) {
            boolean exists = worldExists(name);
            if (!exists && !rules.createIfMissing()) {
                warn(rules.key(), "world '" + name + "' does not exist and CREATE-IF-MISSING is false; its rules are off.");
                return;
            }
            try {
                WorldCreator creator = new WorldCreator(name).environment(World.Environment.valueOf(rules.environment()));
                // Bukkit does not remember a plugin's generator, so a void world gets it on every
                // load; otherwise chunks first visited after a restart would get normal terrain.
                if ("VOID".equals(rules.generator())) {
                    creator.generator(new VoidGenerator());
                }
                if (!exists) {
                    switch (rules.generator()) {
                        case "FLAT" -> creator.type(WorldType.FLAT);
                        case "LARGE_BIOMES" -> creator.type(WorldType.LARGE_BIOMES);
                        case "AMPLIFIED" -> creator.type(WorldType.AMPLIFIED);
                        default -> {
                        }
                    }
                    plugin.getLogger().info("Creating the " + rules.key() + " world '" + name + "' (" + rules.generator() + ").");
                }
                world = creator.createWorld();
                if (world != null && !exists && "VOID".equals(rules.generator())) {
                    buildPlatform(world);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the " + rules.key() + " world '" + name + "'", exception);
                warn(rules.key(), "could not be loaded: " + exception.getMessage());
                return;
            }
        }
        if (world == null) {
            warn(rules.key(), "world '" + name + "' could not be loaded.");
            return;
        }
        loadProblems.remove(rules.key());
        loaded.put(rules.key(), world);
        applySettings(world, rules);
    }

    static boolean worldExists(String name) {
        if (new File(new File(Bukkit.getWorldContainer(), name), "level.dat").isFile()) {
            return true;
        }
        String dimension = "dimensions" + File.separator + "minecraft" + File.separator + name.toLowerCase(Locale.ROOT);
        for (World world : Bukkit.getWorlds()) {
            // On 26.x a world reports its own dimension folder; walk up to the level folder.
            File folder = world.getWorldFolder();
            for (int depth = 0; depth < 4 && folder != null; depth++, folder = folder.getParentFile()) {
                if (new File(folder, dimension).isDirectory()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void warn(String key, String message) {
        loadProblems.put(key, message);
        plugin.getLogger().warning("worlds.yml " + key + ": " + message);
    }

    private void buildPlatform(World world) {
        Location spawn = world.getSpawnLocation();
        int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 3, 64));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(spawn.getBlockX() + dx, y, spawn.getBlockZ() + dz).setType(Material.STONE, false);
            }
        }
        world.setSpawnLocation(spawn.getBlockX(), y + 1, spawn.getBlockZ());
    }

    private void applySettings(World world, WorldRules rules) {
        // The world's own PvP switch is off only when the whole world is protected; a spawn with
        // a protected radius keeps PvP for the rest of the world and the listener guards the radius.
        world.setPVP(rules.pvp() || rules.protectedRadius() >= 0);
        if (!rules.difficulty().isEmpty()) {
            world.setDifficulty(Difficulty.valueOf(rules.difficulty()));
        }
        if (rules.lockTime() >= 0) {
            world.setTime(rules.lockTime());
            setRule(world, false, "doDaylightCycle", "advance_time");
        }
        if (rules.lockWeather()) {
            world.setStorm(false);
            world.setThundering(false);
            setRule(world, false, "doWeatherCycle", "advance_weather");
        }
        rules.gameRules().forEach((name, value) -> applyGameRule(world, name, value));
        Location configured = configuredSpawn(rules, world);
        if (configured != null) {
            world.setSpawnLocation(configured);
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private void applyGameRule(World world, String name, String value) {
        GameRule<?> rule = findRule(name);
        if (rule == null) {
            plugin.getLogger().warning("worlds.yml: unknown game rule '" + name + "' for " + world.getName() + "; skipped.");
            return;
        }
        try {
            if (rule.getType() == Boolean.class) {
                world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            } else if (rule.getType() == Integer.class) {
                world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value.trim()));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("worlds.yml: game rule '" + name + "' cannot be set to '" + value + "'.");
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private void setRule(World world, boolean value, String... names) {
        for (String name : names) {
            GameRule<?> rule = findRule(name);
            if (rule != null && rule.getType() == Boolean.class) {
                world.setGameRule((GameRule<Boolean>) rule, value);
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static GameRule<?> findRule(String name) {
        GameRule<?> rule = GameRule.getByName(name);
        if (rule != null) {
            return rule;
        }
        String wanted = name.replace("_", "").replace("minecraft:", "").toLowerCase(Locale.ROOT);
        for (GameRule<?> candidate : GameRule.values()) {
            if (candidate.getName().replace("_", "").replace("minecraft:", "").toLowerCase(Locale.ROOT).equals(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ lookups

    public World world(String key) {
        return key == null ? null : loaded.get(key.toLowerCase(Locale.ROOT));
    }

    public boolean isWorld(World world, String key) {
        World managed = world(key);
        return managed != null && managed.equals(world);
    }

    public WorldRules rulesFor(World world) {
        if (world == null || !settings.enabled()) {
            return null;
        }
        for (Map.Entry<String, World> entry : loaded.entrySet()) {
            if (entry.getValue().equals(world)) {
                return settings.rules(entry.getKey());
            }
        }
        return null;
    }

    public boolean isProtected(Location location, WorldRules rules) {
        if (rules == null || location.getWorld() == null) {
            return false;
        }
        Location centre = location.getWorld().getSpawnLocation();
        return rules.isProtectedAt(location.getX(), location.getZ(), centre.getX(), centre.getZ());
    }

    public boolean sidebarAllowed(World world) {
        WorldRules rules = rulesFor(world);
        return rules == null || rules.sidebar();
    }

    private Location configuredSpawn(WorldRules rules, World world) {
        if (rules.spawn() == null || rules.spawn().isBlank()) {
            return null;
        }
        Location parsed = LocationUtils.parse(rules.spawn());
        if (parsed == null || parsed.getWorld() == null || !parsed.getWorld().equals(world)) {
            return null;
        }
        return parsed;
    }

    public Location spawnLocation(String key) {
        WorldRules rules = settings.rules(key);
        World world = world(key);
        if (rules == null || world == null) {
            return null;
        }
        Location configured = configuredSpawn(rules, world);
        if (configured != null) {
            return configured.clone();
        }
        Location spawn = world.getSpawnLocation().clone().add(0.5D, 0D, 0.5D);
        return safe(spawn);
    }

    static Location safe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return location;
        }
        if (isSafe(world.getBlockAt(location))) {
            return location;
        }
        int top = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        if (top <= world.getMinHeight()) {
            // An empty column (a void world without its platform): the bottom of the world is the
            // worst place to put anyone, so the spot stays as it was.
            return location;
        }
        Location raised = location.clone();
        raised.setY(Math.max(world.getMinHeight() + 1, top + 1));
        return raised;
    }

    private static boolean isSafe(Block feet) {
        Block ground = feet.getRelative(0, -1, 0);
        Block head = feet.getRelative(0, 1, 0);
        return ground.getType().isSolid() && !ground.isLiquid() && feet.isPassable() && head.isPassable()
                && !feet.isLiquid() && feet.getType() != Material.FIRE && ground.getType() != Material.MAGMA_BLOCK;
    }

    public boolean setSpawn(String key, Location location) {
        World world = world(key);
        if (world == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return false;
        }
        var config = plugin.getConfigManager().getWorlds();
        config.set("WORLDS." + key.toUpperCase(Locale.ROOT) + ".SPAWN", LocationUtils.serialize(location));
        if (!plugin.getConfigManager().saveWorlds()) {
            return false;
        }
        world.setSpawnLocation(location);
        settings = gate(WorldSettings.read(config));
        if ("lobby".equals(key) && plugin.getLobbyManager() != null) {
            plugin.getLobbyManager().reload();
        }
        return true;
    }

    public boolean refuseOutside(Player player, String key, String what) {
        World managed = world(key);
        if (managed == null || managed.equals(player.getWorld())) {
            return false;
        }
        player.sendMessage(com.bx.smpPlugin.utils.ColorUtils.toComponent(
                "&cYou can only set " + what + " in the " + key + " dimension &7(world '" + managed.getName() + "')."));
        com.bx.smpPlugin.core.Text.line(player, "&7Go there first: ",
                com.bx.smpPlugin.core.Text.suggest("&e[/smp world tp " + key + "]", "/smp world tp " + key,
                        "&7Teleport to the " + key + " dimension"));
        return true;
    }

    public Map<String, String> loadProblems() {
        return Collections.unmodifiableMap(loadProblems);
    }

    // ------------------------------------------------------------------ visibility

    public void refreshVisibility(Player player) {
        PlayerVisibilityManager visibility = plugin.getPlayerVisibilityManager();
        if (visibility == null || player == null || !player.isOnline()) {
            return;
        }
        WorldSettings current = settings;
        String world = player.getWorld().getName();
        boolean seesAll = PermissionUtils.has(player, SEE_ALL);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            String otherWorld = other.getWorld().getName();
            setHidden(visibility, player, other, !seesAll && !current.sees(world, otherWorld));
            setHidden(visibility, other, player, !current.sees(otherWorld, world) && !PermissionUtils.has(other, SEE_ALL));
        }
    }

    private static void setHidden(PlayerVisibilityManager visibility, Player viewer, Player target, boolean hidden) {
        boolean now = visibility.hasReason(viewer, target, PlayerVisibilityManager.Reason.WORLD);
        if (hidden && !now) {
            visibility.hide(viewer, target, PlayerVisibilityManager.Reason.WORLD);
        } else if (!hidden && now) {
            visibility.show(viewer, target, PlayerVisibilityManager.Reason.WORLD);
        }
    }

    public boolean bypassesCommands(Player player) {
        WorldSettings.CommandBypass bypass = settings.commandBypass();
        if (bypass.ops() && player.isOp()) {
            return true;
        }
        for (String node : bypass.permissions()) {
            if (PermissionUtils.has(player, node)) {
                return true;
            }
        }
        for (String group : bypass.groups()) {
            // LuckPerms gives group.<name> to members only; plain Bukkit permissions never have it.
            if (player.hasPermission("group." + group)) {
                return true;
            }
        }
        return false;
    }

    public void refreshPlayer(Player player) {
        refreshVisibility(player);
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().applyVisibility(player);
        }
        player.updateCommands();
    }

    // ------------------------------------------------------------------ transitions

    public enum Failure {
        NONE, NOT_AVAILABLE, IN_PROGRESS, IN_COMBAT, TELEPORT_FAILED
    }

    public CompletableFuture<Failure> transfer(Player player, String key, boolean ignoreCombat) {
        String target = key == null ? "" : key.toLowerCase(Locale.ROOT);
        Location destination = spawnLocation(target);
        if (destination == null) {
            return CompletableFuture.completedFuture(Failure.NOT_AVAILABLE);
        }
        if (!ignoreCombat && plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            return CompletableFuture.completedFuture(Failure.IN_COMBAT);
        }
        UUID uuid = player.getUniqueId();
        if (!inTransit.add(uuid)) {
            return CompletableFuture.completedFuture(Failure.IN_PROGRESS);
        }
        if ("lobby".equals(target) && plugin.getLobbyManager() != null) {
            boolean sent = plugin.getLobbyManager().sendToLobby(player, false);
            inTransit.remove(uuid);
            return CompletableFuture.completedFuture(sent ? Failure.NONE : Failure.NOT_AVAILABLE);
        }
        CompletableFuture<Failure> result = new CompletableFuture<>();
        plugin.getSpigotScheduler().teleport(player, destination).whenComplete((success, error) ->
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    inTransit.remove(uuid);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING, "Moving " + player.getName() + " to the " + target
                                    + " world failed; they stay where they are.", error);
                        }
                        result.complete(Failure.TELEPORT_FAILED);
                        return;
                    }
                    result.complete(Failure.NONE);
                }));
        return result;
    }

    public static String describe(Failure failure, String key) {
        return switch (failure) {
            case NONE -> "";
            case NOT_AVAILABLE -> "&cThe " + key + " world is not available right now.";
            case IN_PROGRESS -> "&cA teleport is already under way.";
            case IN_COMBAT -> "&cYou cannot change worlds while in combat.";
            case TELEPORT_FAILED -> "&cThe teleport did not go through; you were not moved.";
        };
    }

    public boolean isInTransit(UUID uuid) {
        return inTransit.contains(uuid);
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        WorldSettings current = settings;
        if (!current.enabled()) {
            lines.add("&7World management is off (worlds.yml ENABLED: false or a problem in the file).");
            return lines;
        }
        for (String key : WorldSettings.KEYS) {
            WorldRules rules = current.rules(key);
            World world = world(key);
            String state;
            if (!rules.enabled()) {
                var feature = "lobby".equals(key) ? com.bx.smpPlugin.managers.FeatureManager.Feature.LOBBY
                        : "spawn".equals(key) ? com.bx.smpPlugin.managers.FeatureManager.Feature.SPAWN_WORLD : null;
                boolean switchedOff = feature != null && !com.bx.smpPlugin.managers.FeatureManager.isEnabled(
                        plugin.getConfigManager().getConfig(), feature);
                state = switchedOff ? "&8off &7(/smpplugin features enable " + feature.configKey().toLowerCase(Locale.ROOT) + ")"
                        : "&8off in worlds.yml";
            } else if (world == null) {
                state = "&cnot loaded&7: " + loadProblems.getOrDefault(key, "unknown reason");
            } else {
                state = "&aloaded&7, " + world.getPlayers().size() + " player(s), sees &f" + seesText(rules.visibility(), rules.visibilityGroup())
                        + "&7, PvP " + (rules.pvp() ? "&aon" : "&coff") + "&7, sidebar " + (rules.sidebar() ? "&aon" : "&coff")
                        + "&7, protection " + (rules.protectedRadius() < 0 ? "whole world"
                        : rules.protectedRadius() == 0 ? "off" : rules.protectedRadius() + " blocks");
            }
            lines.add("&f" + key + " &8(" + rules.worldName() + ") &7- " + state);
        }
        lines.add("&7Other worlds (group &f" + current.otherWorldsGroup() + "&7) see &f"
                + seesText(current.otherWorldsVisibility(), current.otherWorldsGroup()) + "&7.");
        return lines;
    }

    static String seesText(String visibility, String group) {
        return WorldRules.ALL_ONLINE.equals(visibility) ? "everyone online" : "only group " + group;
    }

    static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            // Saves the server searching an empty world for land; the platform is built under it.
            return new Location(world, 0.5, 65, 0.5);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
