package com.bx.smpPlugin.platform;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the startup and {@code /smpplugin status} report: platform, Java runtime,
 * storage, optional integrations and native anti-Xray, each with an explicit status.
 *
 * <p>Optional plugins that are installed are reported as {@link CapabilityStatus#UNTESTED}:
 * SmpPlugin loads them, but their compatibility with Minecraft 26.3 is their authors'
 * responsibility and has not been verified here.</p>
 */
public final class PlatformReport {

    /** Optional plugin name → SmpPlugin features that depend on it. */
    static final Map<String, String> OPTIONAL_DEPENDENCIES = optionalDependencies();

    private PlatformReport() {
    }

    private static Map<String, String> optionalDependencies() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("PlaceholderAPI", "placeholder expansions and external placeholders");
        dependencies.put("Vault", "exposing SmpPlugin's economy to other plugins");
        dependencies.put("LuckPerms", "role prefixes and tablist refresh on rank changes");
        dependencies.put("ProtocolLib", "explosion particle filter, /hide, /fakeplayer and packet worth display");
        dependencies.put("SkinsRestorer", "disguise skins and tablist refresh on skin changes");
        dependencies.put("Apollo", "Lunar Client rich presence and teammate overlay");
        dependencies.put("floodgate", "Bedrock forms for Orders");
        dependencies.put("FancyNpcs", "lobby and Billford NPCs");
        dependencies.put("Multiverse-Core", "multi-world helpers");
        return dependencies;
    }

    public static boolean isOptionalDependency(String name) {
        return OPTIONAL_DEPENDENCIES.containsKey(name);
    }

    public static ServerPlatform detectPlatform(SmpPlugin plugin) {
        return ServerPlatform.detect(name -> isClassPresent(plugin, name), Bukkit.getName());
    }

    public static MinecraftVersion detectMinecraftVersion() {
        return MinecraftVersion.parse(Bukkit.getBukkitVersion());
    }

    public static List<Capability> collect(SmpPlugin plugin) {
        List<Capability> capabilities = new ArrayList<>();
        ServerPlatform platform = detectPlatform(plugin);
        MinecraftVersion version = detectMinecraftVersion();

        CompatibilityPolicy.Decision decision = CompatibilityPolicy.evaluate(platform, version);
        capabilities.add(new Capability(
                "Platform",
                switch (decision.verdict()) {
                    case SUPPORTED -> CapabilityStatus.SUPPORTED;
                    case UNTESTED -> CapabilityStatus.UNTESTED;
                    case REFUSED -> CapabilityStatus.UNAVAILABLE;
                },
                platform.displayName() + " " + version + " (" + Bukkit.getVersion() + "). " + decision.reason()
        ));

        BuildInfo build = BuildInfo.load(plugin.getClass().getClassLoader());
        String mismatch = build.mismatchWarning(platform);
        capabilities.add(new Capability(
                "Build",
                mismatch != null ? CapabilityStatus.DEGRADED
                        : build.flavor().equals(BuildInfo.UNKNOWN) ? CapabilityStatus.UNTESTED : CapabilityStatus.SUPPORTED,
                mismatch != null ? mismatch : build.flavor() + " build, compiled against " + build.serverApi() + "."
        ));

        int javaFeature = Runtime.version().feature();
        capabilities.add(new Capability(
                "Java runtime",
                javaFeature >= 25 ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNAVAILABLE,
                "Java " + Runtime.version() + (javaFeature >= 25 ? "" : " — Java 25 or newer is required.")
        ));

        if (plugin.wasReloadedInProcess()) {
            capabilities.add(new Capability(
                    "Lifecycle",
                    CapabilityStatus.DEGRADED,
                    "The plugin was re-enabled inside the same server process (/reload or a plugin manager)."
                            + " This is not supported; restart the server to get a clean state."
            ));
        }

        List<String> failedListeners = plugin.getFailedListeners();
        if (!failedListeners.isEmpty()) {
            capabilities.add(new Capability(
                    "Event listeners",
                    CapabilityStatus.DEGRADED,
                    "failed to start: " + String.join(", ", failedListeners)
                            + ". Their features are off; the startup log has the error."
            ));
        }

        capabilities.add(storageCapability(plugin));
        capabilities.add(redisCapability(plugin));

        for (Map.Entry<String, String> dependency : OPTIONAL_DEPENDENCIES.entrySet()) {
            Plugin dependent = Bukkit.getPluginManager().getPlugin(dependency.getKey());
            if (dependent == null || !dependent.isEnabled()) {
                capabilities.add(new Capability(dependency.getKey(), CapabilityStatus.UNAVAILABLE,
                        "not installed — disabled: " + dependency.getValue() + "."));
            } else {
                capabilities.add(new Capability(dependency.getKey(), CapabilityStatus.UNTESTED,
                        "v" + dependent.getDescription().getVersion() + " installed — used for "
                                + dependency.getValue() + ". Not verified against Minecraft " + version + "."));
            }
        }

        capabilities.add(antiCheatCapability(plugin));
        capabilities.addAll(antiXrayCapabilities(platform));
        return List.copyOf(capabilities);
    }

    private static Capability antiCheatCapability(SmpPlugin plugin) {
        var antiCheat = plugin.getAntiCheatManager();
        if (antiCheat == null) {
            return new Capability("Anti-cheat", CapabilityStatus.UNAVAILABLE, "failed to start; see the startup log.");
        }
        if (!antiCheat.isActive()) {
            return new Capability("Anti-cheat", CapabilityStatus.UNAVAILABLE,
                    "turned off (anticheat.yml ENABLED or /smpplugin features).");
        }
        return new Capability("Anti-cheat", CapabilityStatus.UNTESTED,
                antiCheat.enabledCheckCount() + " server-side checks on; alerts and evidence active."
                        + " Thresholds have not been tuned on a live 26.3 server yet. Details: /anticheat status");
    }

    private static Capability storageCapability(SmpPlugin plugin) {
        DatabaseManager database = plugin.getDatabaseManager();
        if (database == null || !database.isConnected()) {
            return new Capability("Storage", CapabilityStatus.UNAVAILABLE,
                    "the database did not start. Check the console for the error and fix database.yml.");
        }
        return switch (database.getDatabaseType()) {
            case SQLITE -> new Capability("Storage", CapabilityStatus.SUPPORTED,
                    "SQLite (WAL journal). Local to this server only — do not share the file between servers.");
            case MYSQL -> new Capability("Storage", CapabilityStatus.SUPPORTED,
                    "MySQL. Shared between servers that point at the same database.");
            case MONGODB -> new Capability("Storage", CapabilityStatus.DEGRADED,
                    "MongoDB bridge: gameplay runs on a local SQLite cache that is copied to MongoDB on autosave."
                            + " It is not a transactional shared store; do not run several servers on one MongoDB"
                            + " database for economy or market data.");
        };
    }

    private static Capability redisCapability(SmpPlugin plugin) {
        var redis = plugin.getRedisManager();
        if (redis == null || !redis.isEnabled()) {
            return new Capability("Redis", CapabilityStatus.UNAVAILABLE,
                    "disabled — network staff chat, alerts, server status and cross-server sync are local only.");
        }
        if (!redis.isConnected()) {
            return new Capability("Redis", CapabilityStatus.DEGRADED,
                    "enabled but not connected — network messages fall back to this server only.");
        }
        return new Capability("Redis", CapabilityStatus.SUPPORTED,
                "connected. Used for messaging and cache only, never as the owner of money or items.");
    }

    private static List<Capability> antiXrayCapabilities(ServerPlatform platform) {
        if (!platform.isPaperBased()) {
            return List.of(new Capability("Native anti-Xray", CapabilityStatus.UNAVAILABLE,
                    platform.displayName() + " has no built-in anti-Xray. Use Paper or Purpur for native ore hiding."));
        }

        Map<String, File> worldFolders = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            worldFolders.put(world.getName(), world.getWorldFolder());
        }
        NativeAntiXrayInspector.Report report = NativeAntiXrayInspector.inspect(paperConfigDirectory(), worldFolders);
        if (!report.defaultsFound()) {
            return List.of(new Capability("Native anti-Xray", CapabilityStatus.UNTESTED,
                    "could not find config/" + NativeAntiXrayInspector.DEFAULTS_FILE
                            + " (custom --paper-settings-directory?). Anti-Xray state is unknown."));
        }

        List<Capability> capabilities = new ArrayList<>();
        for (NativeAntiXrayInspector.WorldStatus world : report.worlds()) {
            capabilities.add(new Capability(
                    "Native anti-Xray: " + world.worldName(),
                    world.enabled() ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNAVAILABLE,
                    world.enabled()
                            ? "on, engine-mode " + world.engineMode()
                              + ", max-block-height " + (world.maxBlockHeight() == Integer.MIN_VALUE ? "?" : world.maxBlockHeight())
                              + ", " + (world.hiddenBlocks() < 0 ? "?" : world.hiddenBlocks()) + " hidden block types"
                              + (world.usePermission() ? ", bypass via paper.antixray.bypass" : "")
                              + (world.hasWorldOverride() ? " (per-world override)" : "")
                            : "off. Enable anticheat.anti-xray in paper-world-defaults.yml and restart the server."
            ));
        }
        return capabilities;
    }

    /** Paper's default settings directory is {@code config/} in the server root. */
    private static File paperConfigDirectory() {
        File relative = new File("config");
        if (relative.isDirectory()) {
            return relative;
        }
        return new File(Bukkit.getWorldContainer(), "config");
    }

    private static boolean isClassPresent(SmpPlugin plugin, String className) {
        try {
            Class.forName(className, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
