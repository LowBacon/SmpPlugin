// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import com.bx.smpPlugin.utils.ColorUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DialogText {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern OBJECT_TAG_PATTERN = Pattern.compile(
            "<(item|sprite|head):([a-zA-Z0-9_:/.-]+)>",
            Pattern.CASE_INSENSITIVE
    );

    private DialogText() {
    }

    public static Component of(String raw, Player viewer) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String colorized = ColorUtils.colorize(raw, viewer);
        if (!hasSpriteTag(colorized)) {
            return SERIALIZER.deserialize(colorized)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return parseWithSprites(colorized)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static boolean hasSpriteTag(String text) {
        return text.indexOf('<') >= 0 && (
                text.contains("<item:") || text.contains("<sprite:") || text.contains("<head:")
                        || text.contains("<ITEM:") || text.contains("<SPRITE:") || text.contains("<HEAD:")
        );
    }

    private static Component parseWithSprites(String colorized) {
        Matcher matcher = OBJECT_TAG_PATTERN.matcher(colorized);
        if (!matcher.find()) {
            return SERIALIZER.deserialize(colorized);
        }

        Component result = Component.empty();
        int lastIndex = 0;
        String activeFormatting = "";

        do {
            int start = matcher.start();
            int end = matcher.end();

            if (start > lastIndex) {
                String segment = colorized.substring(lastIndex, start);
                activeFormatting = extractTrailingFormatting(segment, activeFormatting);
                result = result.append(SERIALIZER.deserialize(segment));
            }

            String tagArg = matcher.group(2);
            Component sprite = "head".equalsIgnoreCase(matcher.group(1))
                    ? playerHead(tagArg)
                    : itemSprite(tagArg);
            if (sprite != null) {
                result = result.append(sprite);
            }

            lastIndex = end;
        } while (matcher.find());

        if (lastIndex < colorized.length()) {
            String remainder = colorized.substring(lastIndex);
            if (!activeFormatting.isEmpty()
                    && !remainder.startsWith(String.valueOf(LegacyComponentSerializer.SECTION_CHAR))) {
                result = result.append(SERIALIZER.deserialize(activeFormatting + remainder));
            } else {
                result = result.append(SERIALIZER.deserialize(remainder));
            }
        }

        return result;
    }

    private static final Key ITEMS_ATLAS = Key.key("minecraft", "items");
    private static final Key BLOCKS_ATLAS = Key.key("minecraft", "blocks");
    private static final Key GUI_ATLAS = Key.key("minecraft", "gui");

    public static Component itemSprite(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Key key = parseSpriteKey(raw);
            String path = key.value();
            Key atlas;
            if (path.startsWith("block/")) {
                atlas = BLOCKS_ATLAS;
            } else if (path.startsWith("gui/")) {
                atlas = GUI_ATLAS;
            } else {
                atlas = ITEMS_ATLAS;
            }
            return Component.object(ObjectContents.sprite(atlas, key));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Component playerHead(String rawTexture) {
        if (rawTexture == null || rawTexture.isBlank()) {
            return null;
        }
        try {
            return Component.object(ObjectContents.playerHead()
                    .texture(parseTextureKey(rawTexture))
                    .hat(false)
                    .build());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Key parseTextureKey(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        int colon = trimmed.indexOf(':');
        return colon >= 0
                ? Key.key(trimmed.substring(0, colon), trimmed.substring(colon + 1))
                : Key.key("minecraft", trimmed);
    }

    public static Key parseSpriteKey(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        String namespace = "minecraft";
        String path = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            namespace = trimmed.substring(0, colon);
            path = trimmed.substring(colon + 1);
        }
        if (!path.startsWith("item/") && !path.startsWith("block/") && !path.startsWith("gui/")) {
            path = "item/" + path;
        }
        return Key.key(namespace, path);
    }

    private static String extractTrailingFormatting(String text, String initial) {
        String activeColor = "";
        StringBuilder activeFormats = new StringBuilder();

        if (initial != null && !initial.isEmpty()) {
            int idx = 0;
            while (idx < initial.length()) {
                if (initial.charAt(idx) == LegacyComponentSerializer.SECTION_CHAR && idx + 1 < initial.length()) {
                    int hexLen = legacyHexLength(initial, idx);
                    if (hexLen > 0) {
                        activeColor = initial.substring(idx, idx + hexLen);
                        activeFormats.setLength(0);
                        idx += hexLen;
                        continue;
                    }
                    char code = Character.toLowerCase(initial.charAt(idx + 1));
                    if (code == 'r') {
                        activeColor = "";
                        activeFormats.setLength(0);
                    } else if ("0123456789abcdef".indexOf(code) >= 0) {
                        activeColor = initial.substring(idx, idx + 2);
                        activeFormats.setLength(0);
                    } else if (code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o') {
                        String marker = initial.substring(idx, idx + 2);
                        if (activeFormats.indexOf(marker) < 0) {
                            activeFormats.append(marker);
                        }
                    }
                    idx += 2;
                    continue;
                }
                idx++;
            }
        }

        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == LegacyComponentSerializer.SECTION_CHAR && index + 1 < text.length()) {
                int hexLength = legacyHexLength(text, index);
                if (hexLength > 0) {
                    activeColor = text.substring(index, index + hexLength);
                    activeFormats.setLength(0);
                    index += hexLength;
                    continue;
                }
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == 'r') {
                    activeColor = "";
                    activeFormats.setLength(0);
                } else if ("0123456789abcdef".indexOf(code) >= 0) {
                    activeColor = text.substring(index, index + 2);
                    activeFormats.setLength(0);
                } else if (code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o') {
                    String marker = text.substring(index, index + 2);
                    if (activeFormats.indexOf(marker) < 0) {
                        activeFormats.append(marker);
                    }
                }
                index += 2;
                continue;
            }
            index++;
        }
        return activeColor + activeFormats;
    }

    private static int legacyHexLength(String text, int start) {
        if (start + 14 > text.length()) {
            return 0;
        }
        char marker = text.charAt(start);
        if (Character.toLowerCase(text.charAt(start + 1)) != 'x') {
            return 0;
        }
        for (int i = 0; i < 6; i++) {
            if (text.charAt(start + 2 + i * 2) != marker
                    || Character.digit(text.charAt(start + 3 + i * 2), 16) < 0) {
                return 0;
            }
        }
        return 14;
    }

    public static Component ofOrEmpty(String raw, Player viewer) {
        return raw == null || raw.isBlank() ? Component.empty() : of(raw, viewer);
    }

    public static List<Component> lines(String raw, Player viewer) {
        List<Component> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.replace("\\n", "\n").split("\n", -1)) {
            out.add(of(line, viewer));
        }
        return out;
    }

    public static Component tooltip(String raw, Player viewer) {
        List<Component> lines = lines(raw, viewer);
        if (lines.isEmpty()) {
            return null;
        }
        Component result = lines.get(0);
        for (int index = 1; index < lines.size(); index++) {
            result = result.append(Component.newline()).append(lines.get(index));
        }
        return result;
    }

    public static String plain(String raw) {
        return raw == null ? "" : ColorUtils.stripColorCodesAndPlaceholders(raw);
    }
}
