package com.bx.smpPlugin.platform;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * The server software the plugin is running on. Detection is based on classes that
 * only exist in that software, falling back to the server name reported by Bukkit.
 */
public enum ServerPlatform {
    FOLIA("Folia"),
    PURPUR("Purpur"),
    PAPER("Paper"),
    SPIGOT("Spigot"),
    CRAFTBUKKIT("CraftBukkit"),
    UNKNOWN("Unknown");

    static final String FOLIA_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";
    static final String PURPUR_CLASS = "org.purpurmc.purpur.PurpurConfig";
    static final String PAPER_CLASS = "io.papermc.paper.configuration.Configuration";
    static final String SPIGOT_CLASS = "org.spigotmc.SpigotConfig";

    private final String displayName;

    ServerPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True when the platform ships Paper's API and configuration system (Paper and its forks). */
    public boolean isPaperBased() {
        return this == PAPER || this == PURPUR || this == FOLIA;
    }

    /**
     * @param classPresent tells whether a class can be loaded on this server
     * @param serverName   the value of {@code Bukkit.getName()}
     */
    public static ServerPlatform detect(Predicate<String> classPresent, String serverName) {
        if (classPresent.test(FOLIA_CLASS)) {
            return FOLIA;
        }
        if (classPresent.test(PURPUR_CLASS)) {
            return PURPUR;
        }
        if (classPresent.test(PAPER_CLASS)) {
            return PAPER;
        }
        if (classPresent.test(SPIGOT_CLASS)) {
            return SPIGOT;
        }

        String name = serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
        if (name.contains("craftbukkit")) {
            return CRAFTBUKKIT;
        }
        return UNKNOWN;
    }
}
