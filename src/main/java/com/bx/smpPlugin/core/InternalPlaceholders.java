package com.bx.smpPlugin.core;

import com.bx.smpPlugin.api.PlaceholderResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills in SmpPlugin's own placeholders inside the plugin, with or without PlaceholderAPI.
 *
 * <p>Before 4.0 a default scoreboard line such as {@code %economy_kills%} was shown as written on a
 * server without PlaceholderAPI. {@link com.bx.smpPlugin.utils.ColorUtils} now passes all text
 * through {@link #apply} first, so the plugin's own placeholders, the branding tokens and the
 * common {@code %player_name%} / {@code %server_online%} always work.
 */
public final class InternalPlaceholders {

    /** The plugin's own prefixes; every one of them serves the same names. */
    private static final Pattern OWN = Pattern.compile(
            "%(smp|economy|smpplugin|customsmp|finnishsmp|fsmp)_([A-Za-z0-9_\\-]+)%");
    /**
     * A placeholder still left after everything had its turn, e.g. a typo or a plugin that is not
     * installed. It needs an underscore, so plain words between percent signs stay.
     */
    private static final Pattern UNRESOLVED = Pattern.compile("%[A-Za-z0-9]+_[A-Za-z0-9_:\\-]+%");

    private static volatile PlaceholderResolver resolver;

    private InternalPlaceholders() {
    }

    public static void install(PlaceholderResolver value) {
        resolver = value;
    }

    public static void uninstall() {
        resolver = null;
    }

    /** Replaces branding tokens and the plugin's own placeholders. The player may be null. */
    public static String apply(String text, OfflinePlayer player) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        text = Branding.apply(text);
        if (text.indexOf('%') < 0) {
            return text;
        }
        if (text.contains("%server_online%") || text.contains("%server_max_players%")) {
            text = text.replace("%server_online%", Integer.toString(Bukkit.getOnlinePlayers().size()))
                    .replace("%server_max_players%", Integer.toString(Bukkit.getMaxPlayers()));
        }
        if (player != null && text.contains("%player_name%") && player.getName() != null) {
            text = text.replace("%player_name%", player.getName());
        }
        PlaceholderResolver active = resolver;
        if (active == null) {
            return text;
        }
        Matcher matcher = OWN.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length() + 16);
        do {
            String value;
            try {
                value = active.resolve(player, matcher.group(2));
            } catch (RuntimeException exception) {
                value = null;
            }
            // One of our own names that has no value (or does not exist) shows as nothing, never as code.
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value == null ? "" : value));
        } while (matcher.find());
        matcher.appendTail(builder);
        return builder.toString();
    }

    /** Removes placeholders nothing could fill, for the scoreboard and the tab list. */
    public static String stripUnresolved(String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        return UNRESOLVED.matcher(text).replaceAll("");
    }
}
