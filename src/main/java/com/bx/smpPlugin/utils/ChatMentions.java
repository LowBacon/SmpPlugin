package com.bx.smpPlugin.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds and highlights player names written into a chat message, with or without an {@code @}. */
public final class ChatMentions {

    private ChatMentions() {
    }

    /** True when {@code name} appears in {@code message} as a whole word, ignoring case. */
    public static boolean mentions(String message, String name) {
        if (message == null || name == null || name.length() < 3) {
            return false;
        }
        return pattern(name).matcher(message).find();
    }

    /**
     * Rewrites every mention of {@code name} as {@code highlight + "@" + name + resume}. The name is
     * written the way the player spells it, whatever case the sender typed it in.
     */
    public static String highlight(String message, String name, String highlight, String resume) {
        if (!mentions(message, name)) {
            return message;
        }
        Matcher matcher = pattern(name).matcher(message);
        StringBuilder out = new StringBuilder(message.length() + 16);
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(highlight + "@" + name + resume));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Pattern pattern(String name) {
        return Pattern.compile("(?i)(?<![A-Za-z0-9_])@?" + Pattern.quote(name) + "(?![A-Za-z0-9_])");
    }
}
