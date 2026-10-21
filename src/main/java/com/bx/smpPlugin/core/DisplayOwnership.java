package com.bx.smpPlugin.core;

import org.bukkit.Bukkit;

import java.util.List;

/** Finds another plugin that already draws the scoreboard or the tab list. */
public final class DisplayOwnership {

    private DisplayOwnership() {
    }

    /** The first of these plugin names that is installed on this server, or null. */
    public static String firstInstalled(List<String> pluginNames) {
        if (pluginNames == null) {
            return null;
        }
        for (String name : pluginNames) {
            if (name != null && !name.isBlank() && !name.equalsIgnoreCase("SmpPlugin")
                    && Bukkit.getPluginManager().getPlugin(name.trim()) != null) {
                return name.trim();
            }
        }
        return null;
    }
}
