package com.bx.smpPlugin.platform;

import java.util.EnumSet;
import java.util.Set;

/**
 * Decides whether this artifact may run on a given platform and Minecraft version.
 *
 * <p>The artifact is compiled against the Spigot 26.3 API and declares
 * {@code api-version: 26.3}, so older servers refuse to load it before this check runs.
 * The policy still guards against the cases plugin.yml cannot express:</p>
 * <ul>
 *   <li>Folia: no Folia 26.3 build has been tested, so the plugin refuses to run there.</li>
 *   <li>Versions newer than the tested one: allowed, but reported as untested.</li>
 * </ul>
 */
public final class CompatibilityPolicy {

    /** Minimum Minecraft version this artifact supports. Keep in sync with {@code minecraft.api.version} in pom.xml. */
    public static final MinecraftVersion MINIMUM = MinecraftVersion.parse("26.3");

    /** Newest Minecraft version this artifact was built against. */
    public static final MinecraftVersion LATEST_TESTED = MinecraftVersion.parse("26.3");

    /** Platforms this build targets. */
    private static final Set<ServerPlatform> TARGETS =
            EnumSet.of(ServerPlatform.PAPER, ServerPlatform.PURPUR, ServerPlatform.SPIGOT, ServerPlatform.CRAFTBUKKIT);

    /**
     * Targets on which this build has been booted and exercised (menus, teleports, transactions, shutdown).
     * Empty until that verification is done and recorded in COMPATIBILITY.md; until then every target
     * reports {@link Verdict#UNTESTED} instead of claiming support.
     */
    private static final Set<ServerPlatform> RUNTIME_VERIFIED = EnumSet.noneOf(ServerPlatform.class);

    public enum Verdict {
        /** Supported platform and the tested Minecraft version. */
        SUPPORTED,
        /** Allowed to run, but this combination has not been tested. */
        UNTESTED,
        /** Must not run. */
        REFUSED
    }

    public record Decision(Verdict verdict, String reason) {
        public boolean allowsEnable() {
            return verdict != Verdict.REFUSED;
        }
    }

    private CompatibilityPolicy() {
    }

    public static Decision evaluate(ServerPlatform platform, MinecraftVersion version) {
        if (platform == ServerPlatform.FOLIA) {
            return new Decision(Verdict.REFUSED,
                    "Folia is not supported by this build. No Folia 26.3 server build has been tested;"
                            + " region-thread safety of SmpPlugin's modules is unverified on Folia 26.3.");
        }
        if (version.compareTo(MINIMUM) < 0) {
            return new Decision(Verdict.REFUSED,
                    "Minecraft " + version + " is older than the minimum supported version " + MINIMUM + ".");
        }
        if (!TARGETS.contains(platform)) {
            return new Decision(Verdict.UNTESTED,
                    "Unrecognised server software. Only Paper, Purpur, Spigot and CraftBukkit are covered.");
        }
        if (version.compareTo(LATEST_TESTED) > 0) {
            return new Decision(Verdict.UNTESTED,
                    "Minecraft " + version + " is newer than the version this build targets (" + LATEST_TESTED + ").");
        }
        if (!RUNTIME_VERIFIED.contains(platform)) {
            return new Decision(Verdict.UNTESTED,
                    platform.displayName() + " " + version + " is a build target, but this build has not yet been"
                            + " verified on a running " + platform.displayName() + " server.");
        }
        return new Decision(Verdict.SUPPORTED, platform.displayName() + " " + version + " is supported and verified.");
    }
}
