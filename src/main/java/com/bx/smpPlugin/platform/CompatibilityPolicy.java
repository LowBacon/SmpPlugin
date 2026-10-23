// SmpPlugin by LowBacon
package com.bx.smpPlugin.platform;

import java.util.EnumSet;
import java.util.Set;

public final class CompatibilityPolicy {

    public static final MinecraftVersion MINIMUM = MinecraftVersion.parse("1.21.11");

    public static final MinecraftVersion LATEST_TESTED = MinecraftVersion.parse("26.3");

    private static final Set<ServerPlatform> TARGETS =
            EnumSet.of(ServerPlatform.PAPER, ServerPlatform.PURPUR, ServerPlatform.SPIGOT, ServerPlatform.CRAFTBUKKIT);

    private static final Set<ServerPlatform> RUNTIME_VERIFIED = EnumSet.noneOf(ServerPlatform.class);

    public enum Verdict {
        SUPPORTED,
        UNTESTED,
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
                    "Folia is not supported by this build. No Folia server build has been tested;"
                            + " region-thread safety of SmpPlugin's modules is unverified on Folia.");
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
