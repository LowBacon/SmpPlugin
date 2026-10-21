package com.bx.smpPlugin.halloween;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.List;
import java.util.Locale;

/**
 * Everything that makes a door calendar look like a particular season: its names, texts, default
 * icons, sounds and the command that opens it. The Spooky Calendar (/halloween) and the Advent
 * Calendar (/advent, 4.0) are the same calendar with a different theme.
 *
 * @param id               store and file key: opened doors are saved as {@code <id>.<year>}, in-game
 *                         prizes in {@code data/<id>-prizes.yml}, messages under {@code <ID>.*}
 * @param command          the command that opens the calendar
 * @param configFile       the file its settings come from, for messages
 * @param usePermission    who may open it
 * @param adminPermission  who may edit prizes, preview dates and reset doors
 */
public record CalendarTheme(
        String id,
        String command,
        String configFile,
        String usePermission,
        String adminPermission,
        String defaultTitle,
        String calendarName,
        String accent,
        List<String> intro,
        String countdownLabel,
        String lastDayText,
        String lastDoorDefaultName,
        String lastDoorLore,
        String doorLore,
        String closedText,
        Material infoIcon,
        Material openIcon,
        Material lockedIcon,
        Material claimedIcon,
        Material missedIcon,
        Material lastDoorIcon,
        List<Material> defaultBorder,
        List<Material> defaultSpin,
        Sound ambientSound,
        Sound revealSound,
        Sound missedSound,
        Particle particle,
        String revealEmoji,
        String revealIcon,
        String revealDoneIcon,
        String surpriseText,
        boolean trickOrTreatButton
) {

    public static final CalendarTheme HALLOWEEN = new CalendarTheme(
            "halloween", "halloween", "halloween.yml", "smpplugin.halloween", "smpplugin.admin.halloween",
            "&8🎃 &6&lSpooky Calendar", "Spooky Calendar", "&6",
            List.of("&7Open one door every day and", "&7find a spooky prize behind it."),
            "Halloween in", "&6&lHappy Halloween!", "&6&l★ HALLOWEEN ★",
            "&7The big Halloween prize!", "&7Something spooky waits inside...",
            "&7The Spooky Calendar is closed on this server.",
            Material.PUMPKIN_PIE, Material.JACK_O_LANTERN, Material.CARVED_PUMPKIN, Material.ORANGE_CANDLE,
            Material.COBWEB, Material.JACK_O_LANTERN,
            List.of(Material.ORANGE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE),
            List.of(Material.COOKIE, Material.PUMPKIN_PIE),
            Sound.AMBIENT_CAVE, Sound.ENTITY_WITCH_CELEBRATE, Sound.ENTITY_SPIDER_AMBIENT, Particle.WITCH,
            "🎃", "CARVED_PUMPKIN", "JACK_O_LANTERN", "&7A spooky surprise!", true);

    public static final CalendarTheme ADVENT = new CalendarTheme(
            "advent", "advent", "advent.yml", "smpplugin.advent", "smpplugin.admin.advent",
            "&8🎄 &c&lAdvent Calendar", "Advent Calendar", "&c",
            List.of("&7Open one door every day of", "&7December and find a gift inside."),
            "Christmas Eve in", "&c&lMerry Christmas!", "&c&l★ CHRISTMAS EVE ★",
            "&7The big Christmas gift!", "&7A wrapped gift waits inside...",
            "&7The Advent Calendar is closed on this server.",
            Material.CAKE, Material.CHEST, Material.SPRUCE_DOOR, Material.RED_CANDLE,
            Material.POWDER_SNOW_BUCKET, Material.ENDER_CHEST,
            List.of(Material.RED_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE),
            List.of(Material.SNOWBALL, Material.COOKIE, Material.SWEET_BERRIES),
            Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_SNOW_BREAK, Particle.SNOWFLAKE,
            "🎁", "SPRUCE_DOOR", "CHEST", "&7A Christmas surprise!", false);

    /** The prefix of this calendar's keys in messages.yml, e.g. HALLOWEEN or ADVENT. */
    public String messagePrefix() {
        return id.toUpperCase(Locale.ROOT);
    }

    public String messageKey(String key) {
        return messagePrefix() + "." + key;
    }
}
