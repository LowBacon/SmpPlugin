package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Messages for the 3.0 features.
 *
 * <p>Every line has its default written next to the code that sends it, and can be changed in
 * {@code messages.yml} under {@code V3.<key>} (or in a language file under {@code MESSAGES.V3.<key>}).
 * Placeholders are written {@code {name}}. Clickable lines use the Bungee chat API, which both
 * Paper and Spigot ship, so the same code runs in both jars.
 */
public final class Text {

    private Text() {
    }

    private static SmpPlugin plugin() {
        return SmpPlugin.getInstance();
    }

    /** The configured text for {@code V3.<key>}, with placeholders filled in. */
    public static String get(String key, String fallback, String... placeholders) {
        SmpPlugin plugin = plugin();
        String raw = plugin == null || plugin.getConfigManager() == null
                ? fallback
                : plugin.getConfigManager().getMessageOrDefault("V3." + key, fallback);
        return fill(raw == null ? fallback : raw, placeholders);
    }

    /** Replaces {@code {key}} pairs given as key, value, key, value... */
    public static String fill(String text, String... placeholders) {
        if (text == null) {
            return "";
        }
        String out = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            out = out.replace(placeholders[i], placeholders[i + 1] == null ? "" : placeholders[i + 1]);
        }
        return out;
    }

    public static String prefix() {
        return get("PREFIX", "&#FF9A1F✦ &r");
    }

    /** Sends a configured message with the 3.0 prefix. Blank messages are skipped. */
    public static void send(CommandSender to, String key, String fallback, String... placeholders) {
        raw(to, prefix() + get(key, fallback, placeholders));
    }

    /** Sends text as given (colour codes translated), without the prefix. */
    public static void raw(CommandSender to, String legacy) {
        if (to == null || legacy == null || legacy.isBlank() || legacy.equals(prefix())) {
            return;
        }
        to.sendMessage(ColorUtils.toComponent(legacy));
    }

    public static void broadcast(String legacy) {
        if (legacy == null || legacy.isBlank()) {
            return;
        }
        String colored = ColorUtils.toComponent(legacy);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(colored);
        }
        Bukkit.getConsoleSender().sendMessage(colored);
    }

    public static String color(String legacy) {
        return ColorUtils.toComponent(legacy == null ? "" : legacy);
    }

    public static void actionBar(Player player, String legacy) {
        if (player != null && legacy != null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components(color(legacy)));
        }
    }

    /**
     * Turns text with section-sign colour codes (including the {@code §x§r§r§g§g§b§b} hex form)
     * into chat components. Unlike Bungee's own parser it never turns words with dots in them
     * (permission nodes, domain-like names) into web links.
     */
    public static BaseComponent[] components(String colored) {
        java.util.List<BaseComponent> out = new java.util.ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        net.md_5.bungee.api.ChatColor[] color = {null};
        boolean[] format = new boolean[5]; // bold, italic, underlined, strikethrough, obfuscated
        String text = colored == null ? "" : colored;
        Runnable flush = () -> {
            if (buffer.isEmpty()) {
                return;
            }
            TextComponent part = new TextComponent(buffer.toString());
            part.setColor(color[0] == null ? net.md_5.bungee.api.ChatColor.WHITE : color[0]);
            part.setBold(format[0]);
            part.setItalic(format[1]);
            part.setUnderlined(format[2]);
            part.setStrikethrough(format[3]);
            part.setObfuscated(format[4]);
            out.add(part);
            buffer.setLength(0);
        };
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '§' || i + 1 >= text.length()) {
                buffer.append(c);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if (code == 'x' && i + 13 < text.length()) {
                StringBuilder hex = new StringBuilder("#");
                for (int j = i + 3; j <= i + 13; j += 2) {
                    hex.append(text.charAt(j));
                }
                flush.run();
                try {
                    color[0] = net.md_5.bungee.api.ChatColor.of(hex.toString());
                } catch (IllegalArgumentException ignored) {
                    color[0] = null;
                }
                java.util.Arrays.fill(format, false);
                i += 13;
                continue;
            }
            net.md_5.bungee.api.ChatColor parsed = net.md_5.bungee.api.ChatColor.getByChar(code);
            if (parsed == null) {
                buffer.append(c);
                continue;
            }
            flush.run();
            int formatIndex = switch (code) {
                case 'l' -> 0;
                case 'o' -> 1;
                case 'n' -> 2;
                case 'm' -> 3;
                case 'k' -> 4;
                default -> -1;
            };
            if (formatIndex >= 0) {
                format[formatIndex] = true;
            } else {
                color[0] = code == 'r' ? null : parsed;
                java.util.Arrays.fill(format, false);
            }
            i++;
        }
        flush.run();
        return out.toArray(new BaseComponent[0]);
    }

    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player != null) {
            player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
        }
    }

    /** A piece of chat that runs a command when clicked and explains itself on hover. */
    public static BaseComponent[] button(String label, String command, String hover) {
        TextComponent component = new TextComponent(components(color(label)));
        if (command != null && !command.isBlank()) {
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    command.startsWith("/") ? command : "/" + command));
        }
        if (hover != null && !hover.isBlank()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text(color(hover))));
        }
        return new BaseComponent[]{component};
    }

    /** Like {@link #button}, but clicking puts the command in the chat box for the player to finish. */
    public static BaseComponent[] suggest(String label, String command, String hover) {
        TextComponent component = new TextComponent(components(color(label)));
        if (command != null && !command.isBlank()) {
            component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    command.startsWith("/") ? command : "/" + command));
        }
        if (hover != null && !hover.isBlank()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.hover.content.Text(color(hover))));
        }
        return new BaseComponent[]{component};
    }

    /** Sends a line made of plain text and buttons, e.g. {@code line(p, "Ready! ", button(...))}. */
    public static void line(CommandSender to, Object... parts) {
        ComponentBuilder builder = new ComponentBuilder();
        for (Object part : parts) {
            if (part instanceof BaseComponent[] array) {
                builder.append(array, ComponentBuilder.FormatRetention.NONE);
            } else if (part instanceof BaseComponent single) {
                // A lone button passed as line(sender, button(...)) arrives one component at a time.
                builder.append(single, ComponentBuilder.FormatRetention.NONE);
            } else if (part != null) {
                builder.append(components(color(part.toString())), ComponentBuilder.FormatRetention.NONE);
            }
        }
        if (to instanceof Player player) {
            player.spigot().sendMessage(builder.create());
        } else if (to != null) {
            to.sendMessage(TextComponent.toLegacyText(builder.create()));
        }
    }

    /** A sound by its Bukkit name (BLOCK_NOTE_BLOCK_CHIME), or null when the server does not know it. */
    @SuppressWarnings({"deprecation", "removal"})
    public static org.bukkit.Sound sound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return org.bukkit.Sound.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** A particle by its Bukkit name (HEART, FIREWORK), or null when the server does not know it. */
    public static org.bukkit.Particle particle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return org.bukkit.Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Formats a duration in seconds as "2d 4h", "3h 12m", "4m 10s" or "9s". */
    public static String duration(long seconds) {
        long s = Math.max(0L, seconds);
        long days = s / 86_400L;
        long hours = (s % 86_400L) / 3_600L;
        long minutes = (s % 3_600L) / 60L;
        long secs = s % 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    /** A text progress bar such as {@code &a■■■■&7■■■■■■}. */
    public static String bar(double fraction, int length, String done, String left) {
        double clamped = Math.max(0D, Math.min(1D, fraction));
        int filled = (int) Math.round(clamped * length);
        return done + "■".repeat(filled) + left + "■".repeat(Math.max(0, length - filled));
    }
}
