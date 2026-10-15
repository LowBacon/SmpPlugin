package com.bx.smpPlugin.utils;

import org.bukkit.permissions.Permissible;

/**
 * Decides whether colour codes a player typed into their own message survive.
 *
 * <p>Private messages and staff chat drop the message into a format and colour the whole line
 * afterwards, so without this anyone could colour their text, hide it behind {@code &k}, or wipe
 * the format's colours with {@code &r}. The codes come out unless the sender holds
 * {@link #PERMISSION}.
 */
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

    /** Removes {@code &x}, {@code &#RRGGBB} and section-sign codes, leaving the rest of the text. */
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
