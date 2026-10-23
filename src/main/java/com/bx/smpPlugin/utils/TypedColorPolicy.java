// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import org.bukkit.permissions.Permissible;

public final class TypedColorPolicy {

    public static final String PERMISSION = "smpplugin.chat.color";

    private TypedColorPolicy() {
    }

    public static String apply(Permissible sender, String message) {
        if (message == null || message.isEmpty() || mayColour(sender)) {
            return message;
        }
        return stripCodes(message);
    }

    public static boolean mayColour(Permissible sender) {
        return sender != null && PermissionUtils.has(sender, PERMISSION);
    }

    public static String stripCodes(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replaceAll("(?i)\\{#[0-9a-f]{6}}", "")
                .replaceAll("(?i)</?#[0-9a-f]{6}>", "")
                .replaceAll("(?i)[&§]#[0-9a-f]{6}", "")
                .replaceAll("(?i)[&§][0-9a-fk-orx]", "");
    }
}
