package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.core.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a calendar looks and sounds: the words, colours, icons and effects. Each event has its own defaults (spooky
 * for Halloween, wintry for Advent); every value can be changed in the event's CALENDAR section.
 */
public record CalendarTheme(
        String title,
        String accent,
        String name,
        Material infoIcon,
        List<String> intro,
        String countdownLabel,
        String lastDayGreeting,
        String lastDoorName,
        String openHint,
        String lastOpenHint,
        List<Material> borderColours,
        List<Material> spinItems,
        Map<String, Material> icons,
        String ambientSound,
        String revealSound,
        String revealParticle,
        String revealSymbol,
        Material dialogIcon) {

    public static final CalendarTheme HALLOWEEN = new CalendarTheme(
            "&8🎃 &6&lSpooky Calendar", "&6", "Spooky Calendar", Material.PUMPKIN_PIE,
            List.of("&7Open one door every day and", "&7find a spooky prize behind it."),
            "Halloween in", "&6&lHappy Halloween!", "&6&l★ HALLOWEEN ★",
            "&7Something spooky waits inside...", "&7The big Halloween prize!",
            List.of(Material.ORANGE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE,
                    Material.PURPLE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE),
            List.of(Material.COOKIE, Material.PUMPKIN_PIE, Material.HONEY_BOTTLE, Material.SWEET_BERRIES),
            Map.of("OPEN", Material.JACK_O_LANTERN, "LOCKED", Material.CARVED_PUMPKIN, "CLAIMED", Material.ORANGE_CANDLE,
                    "MISSED", Material.COBWEB, "LAST-DOOR", Material.JACK_O_LANTERN),
            "AMBIENT_CAVE", "ENTITY_WITCH_CELEBRATE", "WITCH", "🎃", Material.JACK_O_LANTERN);

    public static final CalendarTheme ADVENT = new CalendarTheme(
            "&8❄ &b&lAdvent Calendar", "&b", "Advent Calendar", Material.SNOW_BLOCK,
            List.of("&7Open one door every day until", "&7Christmas Eve and find a gift inside."),
            "Christmas Eve in", "&b&lMerry Christmas!", "&b&l★ CHRISTMAS EVE ★",
            "&7A wrapped gift is waiting...", "&7The big Christmas Eve gift!",
            List.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE,
                    Material.CYAN_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE),
            List.of(Material.SNOWBALL, Material.COOKIE, Material.CAKE, Material.SWEET_BERRIES, Material.GLOW_BERRIES),
            Map.of("OPEN", Material.RED_SHULKER_BOX, "LOCKED", Material.SNOW_BLOCK, "CLAIMED", Material.WHITE_CANDLE,
                    "MISSED", Material.PACKED_ICE, "LAST-DOOR", Material.GREEN_SHULKER_BOX),
            "BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_CHIME", "SNOWFLAKE", "❄", Material.SNOW_BLOCK);

    public CalendarTheme {
        intro = List.copyOf(intro);
        borderColours = List.copyOf(borderColours);
        spinItems = List.copyOf(spinItems);
        icons = Map.copyOf(icons);
    }

    /** The theme from a CALENDAR section, each missing or broken value taken from {@code defaults}. */
    public static CalendarTheme read(ConfigurationSection section, CalendarTheme defaults) {
        if (section == null) {
            return defaults;
        }
        Map<String, Material> icons = new HashMap<>(defaults.icons);
        for (String key : List.of("OPEN", "LOCKED", "CLAIMED", "MISSED", "LAST-DOOR")) {
            Material material = item(section.getString("ICONS." + key), null);
            if (material != null) {
                icons.put(key, material);
            }
        }
        List<String> intro = section.getStringList("INTRO");
        return new CalendarTheme(
                section.getString("TITLE", defaults.title),
                section.getString("ACCENT", defaults.accent),
                section.getString("NAME", defaults.name),
                item(section.getString("INFO-ICON"), defaults.infoIcon),
                intro.isEmpty() ? defaults.intro : intro,
                section.getString("COUNTDOWN-LABEL", defaults.countdownLabel),
                section.getString("LAST-DAY-GREETING", defaults.lastDayGreeting),
                section.getString("LAST-DOOR-NAME", defaults.lastDoorName),
                section.getString("OPEN-HINT", defaults.openHint),
                section.getString("LAST-OPEN-HINT", defaults.lastOpenHint),
                items(section.getStringList("BORDER-COLOURS"), defaults.borderColours),
                items(section.getStringList("SPIN-ITEMS"), defaults.spinItems),
                icons,
                sound(section.getString("AMBIENT-SOUND"), defaults.ambientSound),
                sound(section.getString("REVEAL-SOUND"), defaults.revealSound),
                particle(section.getString("REVEAL-PARTICLE"), defaults.revealParticle),
                section.getString("REVEAL-SYMBOL", defaults.revealSymbol),
                item(section.getString("DIALOG-ICON"), defaults.dialogIcon));
    }

    public Material icon(String key) {
        return icons.getOrDefault(key, Material.CHEST);
    }

    public Sound ambient() {
        return Text.sound(ambientSound);
    }

    public Sound reveal() {
        return Text.sound(revealSound);
    }

    public Particle particle() {
        return Text.particle(revealParticle);
    }

    private static Material item(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() || material.isAir() ? fallback : material;
    }

    private static List<Material> items(List<String> raw, List<Material> fallback) {
        List<Material> out = new ArrayList<>();
        for (String entry : raw) {
            Material material = item(entry, null);
            if (material != null) {
                out.add(material);
            }
        }
        return out.isEmpty() ? fallback : out;
    }

    private static String sound(String raw, String fallback) {
        return raw != null && Text.sound(raw) != null ? raw : fallback;
    }

    private static String particle(String raw, String fallback) {
        return raw != null && Text.particle(raw) != null ? raw : fallback;
    }
}
