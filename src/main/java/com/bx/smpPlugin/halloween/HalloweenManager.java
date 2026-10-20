package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.halloween.CalendarRules.DoorState;
import com.bx.smpPlugin.seasonal.EventId;
import com.bx.smpPlugin.seasonal.EventStatus;
import com.bx.smpPlugin.seasonal.SeasonalEvents;
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
 * The Spooky Calendar: which doors a player has opened, what is behind each door, and what day it is.
 *
 * <p>Opened doors are one number per player and year (a bit per door) in the shared player store,
 * so checking a door is a map read. Prizes come from three places, first match wins: the in-game
 * editor ({@code data/halloween-prizes.yml}), {@code halloween.yml} DOORS.&lt;day&gt;, then
 * DOORS.DEFAULT. Console COMMANDS always come from halloween.yml.
 */
public final class HalloweenManager {

    private static final String PRIZE_FILE = "data/halloween-prizes.yml";

    private final SmpPlugin plugin;
    private final PlayerStore store;
    private final SeasonalEvents events;
    private YamlConfiguration overrides = new YamlConfiguration();
    /** Set by /halloween preview; null uses the real date (or PREVIEW-DATE). */
    private volatile LocalDate previewDate;

    public HalloweenManager(SmpPlugin plugin, PlayerStore store, SeasonalEvents events) {
        this.plugin = plugin;
        this.store = store;
        this.events = events;
        loadOverrides();
        events.register(EventId.HALLOWEEN, new SeasonalEvents.Source() {
            @Override
            public boolean configEnabled() {
                return config().getBoolean("ENABLED", true);
            }

            @Override
            public boolean featureEnabled() {
                return featureOn();
            }

            @Override
            public CalendarRules rules() {
                return HalloweenManager.this.rules();
            }

            @Override
            public ZonedDateTime now() {
                return HalloweenManager.this.now();
            }

            @Override
            public String command() {
                return "halloween";
            }
        });
        events.register(EventId.TRICK_OR_TREAT, new SeasonalEvents.Source() {
            @Override
            public boolean configEnabled() {
                return config().getBoolean("ENABLED", true) && config().getBoolean("TRICK-OR-TREAT.ENABLED", true);
            }

            @Override
            public boolean featureEnabled() {
                return featureOn();
            }

            @Override
            public CalendarRules rules() {
                return trickOrTreatRules();
            }

            @Override
            public ZonedDateTime now() {
                return HalloweenManager.this.now();
            }

            @Override
            public String command() {
                return "trickortreat";
            }
        });
    }

    public SeasonalEvents events() {
        return events;
    }

    private boolean featureOn() {
        return plugin.getFeatureManager() == null
                || plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.HALLOWEEN);
    }

    public FileConfiguration config() {
        FileConfiguration config = plugin.getConfigManager().getHalloween();
        return config == null ? new YamlConfiguration() : config;
    }

    /**
     * On in halloween.yml, as a feature (/smpplugin feature HALLOWEEN) and not switched off with /events. The
     * calendar can then be opened; doors still only open on their dates.
     */
    public boolean isEnabled() {
        return events.status(EventId.HALLOWEEN) != EventStatus.DISABLED;
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

    /** Halloween is running: on its dates, or forced on with /events. */
    public boolean inSeason() {
        return events.isActive(EventId.HALLOWEEN);
    }

    /**
     * Trick or Treat's own dates: TRICK-OR-TREAT.FIRST-DAY and LAST-DAY when set, otherwise the calendar's season.
     */
    public CalendarRules trickOrTreatRules() {
        CalendarRules season = rules();
        FileConfiguration c = config();
        int first = c.getInt("TRICK-OR-TREAT.FIRST-DAY", season.firstDay());
        int last = c.getInt("TRICK-OR-TREAT.LAST-DAY", season.lastDay());
        return new CalendarRules(season.month(), first, last, season.mode(), season.catchUp(), season.graceDays());
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
        return "halloween." + year;
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
        return isLastDoor(door) ? "&6&l★ HALLOWEEN ★" : "&6Door " + door;
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

    public Material icon(String key, Material fallback) {
        String raw = config().getString("CALENDAR.ICONS." + key, fallback.name());
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() ? fallback : material;
    }

    private File prizeFile() {
        return new File(plugin.getDataFolder(), PRIZE_FILE);
    }

    public void loadOverrides() {
        File file = prizeFile();
        YamlConfiguration loaded = new YamlConfiguration();
        if (file.exists()) {
            try {
                loaded.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + PRIZE_FILE + "; in-game Halloween prizes are ignored until it is fixed.", exception);
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
                Files.writeString(file.toPath(), "# Prizes set with /halloween edit. Delete a door here to go back to halloween.yml.\n" + text,
                        StandardCharsets.UTF_8);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + PRIZE_FILE, exception);
            }
        });
    }
}
