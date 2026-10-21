package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.halloween.CalendarRules.DoorState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * A door calendar: which doors a player has opened, what is behind each door, and what day it is.
 * The Spooky Calendar (/halloween) and, since 4.0, the Advent Calendar (/advent) are both this
 * class with a different {@link CalendarTheme} and config file.
 *
 * <p>Opened doors are one number per player and year (a bit per door) in the shared player store,
 * so checking a door is a map read. Prizes come from three places, first match wins: the in-game
 * editor ({@code data/<id>-prizes.yml}), the config file's DOORS.&lt;day&gt;, then DOORS.DEFAULT.
 * Console COMMANDS always come from the config file.
 */
public class HalloweenManager {

    private final SmpPlugin plugin;
    private final PlayerStore store;
    private final CalendarTheme theme;
    private final java.util.function.Supplier<FileConfiguration> configSource;
    private final java.util.function.BooleanSupplier switchedOn;
    private final String prizeFile;
    private YamlConfiguration overrides = new YamlConfiguration();
    /** Set by /halloween preview; null uses the real date (or PREVIEW-DATE). */
    private volatile LocalDate previewDate;

    /** The Spooky Calendar, on while halloween.yml and the HALLOWEEN feature are on. */
    public HalloweenManager(SmpPlugin plugin, PlayerStore store) {
        this(plugin, store, CalendarTheme.HALLOWEEN, () -> plugin.getConfigManager().getHalloween(),
                () -> plugin.getFeatureManager() == null
                        || plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.HALLOWEEN));
    }

    /**
     * A calendar with its own theme and config file. {@code switchedOn} is checked together with the
     * file's ENABLED, so the event system can turn the calendar off without touching the file.
     */
    public HalloweenManager(SmpPlugin plugin, PlayerStore store, CalendarTheme theme,
                            java.util.function.Supplier<FileConfiguration> configSource,
                            java.util.function.BooleanSupplier switchedOn) {
        this.plugin = plugin;
        this.store = store;
        this.theme = theme;
        this.configSource = configSource;
        this.switchedOn = switchedOn;
        this.prizeFile = "data/" + theme.id() + "-prizes.yml";
        loadOverrides();
    }

    public CalendarTheme theme() {
        return theme;
    }

    public FileConfiguration config() {
        FileConfiguration config = configSource.get();
        return config == null ? new YamlConfiguration() : config;
    }

    /** On in its config file and switched on (the HALLOWEEN feature, or the event system for Advent). */
    public boolean isEnabled() {
        return config().getBoolean("ENABLED", true) && switchedOn.getAsBoolean();
    }

    public CalendarRules rules() {
        FileConfiguration c = config();
        return CalendarRules.of(c.getInt("MONTH", 10), c.getInt("FIRST-DAY", 1), c.getInt("LAST-DAY", 31),
                c.getString("MODE", "DAILY"), c.getBoolean("CATCH-UP", true), c.getInt("GRACE-DAYS", 0));
    }

    public ZoneId zone() {
        return CalendarRules.zone(config().getString("TIME-ZONE", ""));
    }

    /** Now, moved to the preview date when one is set. */
    public ZonedDateTime now() {
        ZoneId zone = zone();
        ZonedDateTime real = ZonedDateTime.now(zone);
        LocalDate preview = previewDate;
        if (preview == null) {
            preview = parseDate(config().getString("PREVIEW-DATE", ""));
        }
        return preview == null ? real : preview.atTime(LocalTime.from(real)).atZone(zone);
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public int seasonYear() {
        return rules().seasonYear(today());
    }

    public boolean inSeason() {
        return isEnabled() && rules().inSeason(today());
    }

    public void setPreviewDate(LocalDate date) {
        previewDate = date;
    }

    public LocalDate previewDate() {
        return previewDate;
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ doors and claims

    private String claimSection(int year) {
        return theme.id() + "." + year;
    }

    public long claimedMask(UUID uuid) {
        return CalendarRules.parseMask(store.get(uuid, claimSection(seasonYear())));
    }

    public DoorState state(UUID uuid, int door) {
        long mask = claimedMask(uuid);
        return rules().state(door, today(), CalendarRules.isClaimed(mask, door));
    }

    public int openedCount(UUID uuid) {
        return Long.bitCount(claimedMask(uuid));
    }

    /** Doors the player could open right now. */
    public int claimableCount(UUID uuid) {
        if (!inSeason()) {
            return 0;
        }
        CalendarRules rules = rules();
        int year = seasonYear();
        long mask = claimedMask(uuid);
        LocalDate today = today();
        int count = 0;
        for (int door = rules.firstDay(); door <= rules.lastDayIn(year); door++) {
            if (rules.state(door, today, CalendarRules.isClaimed(mask, door)) == DoorState.OPEN) {
                count++;
            }
        }
        return count;
    }

    /**
     * Marks a door as opened if it may be opened now. The caller hands out the prize. Returns false
     * when the door is not open (already opened, locked, missed) or the player's data is not loaded.
     */
    public boolean claim(Player player, int door) {
        UUID uuid = player.getUniqueId();
        if (!isEnabled() || !store.isLoaded(uuid)) {
            return false;
        }
        int year = seasonYear();
        long mask = claimedMask(uuid);
        if (rules().state(door, today(), CalendarRules.isClaimed(mask, door)) != DoorState.OPEN) {
            return false;
        }
        store.set(uuid, claimSection(year), Long.toString(CalendarRules.withClaimed(mask, door)));
        return true;
    }

    /** Re-closes one door (or every door when {@code door <= 0}) for a player, online or not. */
    public void reset(UUID uuid, int door, Consumer<Boolean> done) {
        String section = claimSection(seasonYear());
        store.getAnywhere(uuid, section, stored -> {
            long mask = CalendarRules.parseMask(stored);
            long updated = door <= 0 ? 0L : CalendarRules.withoutClaimed(mask, door);
            store.set(uuid, section, updated == 0L ? null : Long.toString(updated));
            done.accept(mask != updated);
        });
    }

    /** How many players opened each door this season. Answers on the main thread. */
    public void doorStatistics(Consumer<Map<Integer, Integer>> callback) {
        String section = claimSection(seasonYear());
        plugin.getDatabaseManager().executeAsync(() -> {
            Map<UUID, String> rows = store.readSectionBlocking(section);
            Map<Integer, Integer> counts = new HashMap<>();
            for (String value : rows.values()) {
                long mask = CalendarRules.parseMask(value);
                for (int door = 1; door <= 31; door++) {
                    if (CalendarRules.isClaimed(mask, door)) {
                        counts.merge(door, 1, Integer::sum);
                    }
                }
            }
            plugin.getSpigotScheduler().runGlobal(() -> callback.accept(counts));
        });
    }

    // ------------------------------------------------------------------ prizes

    private ConfigurationSection doorSection(int door) {
        ConfigurationSection doors = config().getConfigurationSection("DOORS");
        return doors == null ? null : doors.getConfigurationSection(Integer.toString(door));
    }

    public boolean isLastDoor(int door) {
        return door == rules().lastDayIn(seasonYear());
    }

    public String doorName(int door) {
        ConfigurationSection section = doorSection(door);
        String name = section == null ? null : section.getString("NAME");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return isLastDoor(door) ? theme.lastDoorDefaultName() : theme.accent() + "Door " + door;
    }

    /** The prize behind a door, with any in-game edits applied. */
    public Reward prize(int door) {
        ConfigurationSection configured = doorSection(door);
        if (configured == null) {
            configured = config().getConfigurationSection("DOORS.DEFAULT");
        }
        Reward base = Reward.read(configured);
        ConfigurationSection edited = overrides.getConfigurationSection("DOORS." + door);
        if (edited == null) {
            return base;
        }
        Reward custom = Reward.read(edited);
        // Edited prizes keep the console commands from halloween.yml but drop its DISPLAY lines,
        // which would no longer describe what the door holds.
        return new Reward(custom.money(), custom.shards(), custom.items(), base.commands(), custom.display());
    }

    public boolean hasOverride(int door) {
        return overrides.isConfigurationSection("DOORS." + door);
    }

    /** Saves an in-game edit of a door's prize (money, shards and items). */
    public void saveOverride(int door, Reward reward) {
        overrides.set("DOORS." + door, null);
        ConfigurationSection section = overrides.createSection("DOORS." + door);
        reward.withItems(reward.items()).write(section);
        section.set("COMMANDS", null);
        writeOverrides();
    }

    public void clearOverride(int door) {
        overrides.set("DOORS." + door, null);
        writeOverrides();
    }

    /** A calendar icon from CALENDAR.ICONS, or the theme's own default. */
    public Material icon(String key) {
        Material fallback = switch (key) {
            case "OPEN" -> theme.openIcon();
            case "LOCKED" -> theme.lockedIcon();
            case "CLAIMED" -> theme.claimedIcon();
            case "MISSED" -> theme.missedIcon();
            default -> theme.lastDoorIcon();
        };
        return icon(key, fallback);
    }

    public Material icon(String key, Material fallback) {
        String raw = config().getString("CALENDAR.ICONS." + key, fallback.name());
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() ? fallback : material;
    }

    private File prizeFile() {
        return new File(plugin.getDataFolder(), prizeFile);
    }

    public void loadOverrides() {
        File file = prizeFile();
        YamlConfiguration loaded = new YamlConfiguration();
        if (file.exists()) {
            try {
                loaded.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + prizeFile + "; in-game " + theme.calendarName()
                        + " prizes are ignored until it is fixed.", exception);
            }
        }
        overrides = loaded;
    }

    private void writeOverrides() {
        String text = overrides.saveToString();
        File file = prizeFile();
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                Files.writeString(file.toPath(), "# Prizes set with /" + theme.command() + " edit. Delete a door here to go back to "
                        + theme.configFile() + ".\n" + text, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + prizeFile, exception);
            }
        });
    }
}
