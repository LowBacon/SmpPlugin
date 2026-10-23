// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.bukkit.command.Command;

import java.util.Locale;

public final class CommandLabelUtils {

    private CommandLabelUtils() {
    }

    public static String normalizeLabel(String label, Command command) {
        String normalized = label == null || label.isBlank()
                ? (command == null ? "" : command.getName())
                : label;
        if (normalized == null) {
            return "";
        }

        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeLabel(String label) {
        return normalizeLabel(label, null);
    }
}
