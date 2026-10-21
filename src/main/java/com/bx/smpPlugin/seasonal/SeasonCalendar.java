package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.halloween.CalendarRules;
import com.bx.smpPlugin.halloween.CalendarRules.DoorState;
import com.bx.smpPlugin.managers.FeatureManager.Feature;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.perks.Perk;
import com.bx.smpPlugin.perks.Perks;
import com.bx.smpPlugin.perks.Rank;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * A calendar with one door per day of a season, shared by the Advent Calendar and the Spooky Calendar.
 *
 * <p>Opened doors are one number per player and season (a bit per door) in the player store, so checking a door is
 * a map read. A door is marked opened and its prize handed over in the same step, before any animation: leaving
 * mid-spin or a crash can never give a prize twice or open a door without its prize. Items that do not fit go to
 * the reward vault.
 *
 * <p>Prizes come from three places, first match wins: the in-game editor ({@code data/<key>-prizes.yml}), the
 * event file's DOORS.&lt;day&gt;, then DOORS.DEFAULT. Console COMMANDS always come from the event file. A rank from
 * perks.yml adds its CALENDAR-BONUS-PERCENT to the money and shards (never to items or commands). STREAK.BONUSES and
 * COMPLETION add one-off prizes for opening doors on days in a row and for opening every door.
 *
 * <p>Staff test the calendar with a test date: only for themselves, and nothing is given or saved while testing.
 */
public class SeasonCalendar {

    /** What one opened door gave (or, in test mode, would have given). */
    public record Granted(int door, Reward prize, Reward rankBonus, String rankDisplay, List<Reward> extras,
                          List<String> extraNames, int streak, boolean test) {

        public List<String> lines(SmpPlugin plugin) {
            List<String> lines = new ArrayList<>(prize.describe(plugin));
            if (!rankBonus.isEmpty()) {
                List<String> bonus = rankBonus.describe(plugin);
                lines.add(rankDisplay + " &7bonus: " + String.join("&7, ", bonus));
            }
            for (int i = 0; i < extras.size(); i++) {
                lines.add(extraNames.get(i) + "&7: " + String.join("&7, ", extras.get(i).describe(plugin)));
            }
            return lines;
        }
    }

    /** A button the event adds to its calendar's bottom row. */
    public record MenuButton(ItemStack item, Consumer<Player> onClick) {
    }

    protected final SmpPlugin plugin;
    protected final PlayerStore store;
    protected final SeasonalEvents events;
    private final EventId id;
    private final String fileName;
    private final Supplier<FileConfiguration> configSupplier;
    private final Feature feature;
    private final CalendarRules defaultRules;
    private final CalendarTheme defaultTheme;
    private final String usePermission;
    private final String adminPermission;
    private final Map<UUID, LocalDate> testDates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> testMasks = new ConcurrentHashMap<>();
    private final Map<UUID, ClaimStreak> testStreaks = new ConcurrentHashMap<>();
    private volatile YamlConfiguration overrides = new YamlConfiguration();

    protected SeasonCalendar(SmpPlugin plugin, PlayerStore store, SeasonalEvents events, EventId id, String fileName,
                             Supplier<FileConfiguration> config, Feature feature, CalendarRules defaultRules,
                             CalendarTheme defaultTheme, String usePermission, String adminPermission) {
        this.plugin = plugin;
        this.store = store;
        this.events = events;
        this.id = id;
        this.fileName = fileName;
        this.configSupplier = config;
        this.feature = feature;
        this.defaultRules = defaultRules;
        this.defaultTheme = defaultTheme;
        this.usePermission = usePermission;
        this.adminPermission = adminPermission;
        loadOverrides();
        for (String problem : validate()) {
            plugin.getLogger().warning("[" + id.displayName() + "] " + problem);
        }
        events.register(id, new SeasonalEvents.Source() {
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
                return SeasonCalendar.this.rules();
            }

            @Override
            public ZonedDateTime now() {
                return SeasonCalendar.this.now();
            }

            @Override
            public String command() {
                return SeasonCalendar.this.command();
            }
        });
    }

    /**
     * What is wrong in the event file, in plain words. Nothing here stops the calendar: bad values are clamped or
     * skipped, and this list says which.
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        FileConfiguration c = config();
        String zone = c.getString("TIME-ZONE", "");
        if (zone != null && !zone.isBlank()) {
            try {
                ZoneId.of(zone.trim());
            } catch (RuntimeException unknown) {
                problems.add("TIME-ZONE \"" + zone + "\" is not a time zone; the server's own zone is used.");
            }
        }
        int month = c.getInt("MONTH", defaultRules.month());
        if (month < 1 || month > 12) {
            problems.add("MONTH " + month + " is not a month (1-12).");
        }
        int first = c.getInt("FIRST-DAY", defaultRules.firstDay());
        int last = c.getInt("LAST-DAY", defaultRules.lastDay());
        if (first < 1 || first > 31 || last < first || last > 31) {
            problems.add("FIRST-DAY " + first + " and LAST-DAY " + last + " are not a range of days (1-31, first <= last).");
        }
        if (!c.getString("PREVIEW-DATE", "").isBlank()) {
            problems.add("PREVIEW-DATE is set: every player sees that date and gets real prizes. Clear it on a live"
                    + " server and use /" + command() + " test instead.");
        }
        ConfigurationSection doors = c.getConfigurationSection("DOORS");
        if (doors != null) {
            for (String key : doors.getKeys(false)) {
                if (key.equalsIgnoreCase("DEFAULT")) {
                    continue;
                }
                try {
                    int door = Integer.parseInt(key.trim());
                    if (door < 1 || door > 31) {
                        problems.add("DOORS." + key + " is not a day of the month and is never used.");
                    }
                } catch (NumberFormatException notADay) {
                    problems.add("DOORS." + key + " is not a day of the month and is never used.");
                }
            }
        }
        ConfigurationSection bonuses = c.getConfigurationSection("STREAK.BONUSES");
        if (bonuses != null) {
            for (String key : bonuses.getKeys(false)) {
                try {
                    int length = Integer.parseInt(key.trim());
                    if (length < 1 || length > 62) {
                        problems.add("STREAK.BONUSES." + key + " must be a streak of 1 to 62 days.");
                    }
                } catch (NumberFormatException notANumber) {
                    problems.add("STREAK.BONUSES." + key + " is not a number of days and is never given.");
                }
            }
        }
        return problems;
    }

    // ------------------------------------------------------------------ identity and settings

    public EventId id() {
        return id;
    }

    /** The store and file prefix, and the player command: "halloween" or "advent". */
    public String key() {
        return id.key();
    }

    public String command() {
        return id.key();
    }

    /** The event's config file name, for messages ("advent.yml"). */
    public String fileName() {
        return fileName;
    }

    public String usePermission() {
        return usePermission;
    }

    public String adminPermission() {
        return adminPermission;
    }

    public SeasonalEvents events() {
        return events;
    }

    public FileConfiguration config() {
        FileConfiguration config = configSupplier.get();
        return config == null ? new YamlConfiguration() : config;
    }

    protected boolean featureOn() {
        return plugin.getFeatureManager() == null || plugin.getFeatureManager().isEnabled(feature);
    }

    /** STREAK.ENABLED, with the event's own default. */
    public boolean streaksEnabled() {
        return config().getBoolean("STREAK.ENABLED", streaksByDefault());
    }

    protected boolean streaksByDefault() {
        return false;
    }

    public CalendarTheme theme() {
        return CalendarTheme.read(config().getConfigurationSection("CALENDAR"), defaultTheme);
    }

    public CalendarRules rules() {
        FileConfiguration c = config();
        return CalendarRules.of(c.getInt("MONTH", defaultRules.month()), c.getInt("FIRST-DAY", defaultRules.firstDay()),
                c.getInt("LAST-DAY", defaultRules.lastDay()), c.getString("MODE", defaultRules.mode().name()),
                c.getBoolean("CATCH-UP", defaultRules.catchUp()), c.getInt("GRACE-DAYS", defaultRules.graceDays()));
    }

    public ZoneId zone() {
        return CalendarRules.zone(config().getString("TIME-ZONE", ""));
    }

    /** Can the calendar be opened at all: on in its file, as a feature, and not switched off with /events. */
    public boolean isEnabled() {
        return events.status(id) != EventStatus.DISABLED;
    }

    /** The event is running: on its dates, or forced on with /events. */
    public boolean inSeason() {
        return events.isActive(id);
    }

    // ------------------------------------------------------------------ time and test dates

    /** Now for the server: the real time, or PREVIEW-DATE from the file (a test-server setting). */
    public ZonedDateTime now() {
        ZoneId zone = zone();
        ZonedDateTime real = ZonedDateTime.now(zone);
        LocalDate preview = parseDate(config().getString("PREVIEW-DATE", ""));
        return preview == null ? real : preview.atTime(LocalTime.from(real)).atZone(zone);
    }

    /** Now as one player sees it: their test date when they set one. */
    public ZonedDateTime now(UUID viewer) {
        LocalDate test = viewer == null ? null : testDates.get(viewer);
        if (test == null) {
            return now();
        }
        ZoneId zone = zone();
        return test.atTime(LocalTime.from(ZonedDateTime.now(zone))).atZone(zone);
    }

    public LocalDate today(UUID viewer) {
        return now(viewer).toLocalDate();
    }

    public int seasonYear() {
        return rules().seasonYear(now().toLocalDate());
    }

    public int seasonYear(UUID viewer) {
        return rules().seasonYear(today(viewer));
    }

    /** True while this player tests the calendar: they see their test date and nothing is given or saved. */
    public boolean isTesting(UUID viewer) {
        return viewer != null && testDates.containsKey(viewer);
    }

    public LocalDate testDate(UUID viewer) {
        return viewer == null ? null : testDates.get(viewer);
    }

    /** Starts (or moves) test mode for one staff member, or ends it with {@code null}. */
    public void setTestDate(UUID viewer, LocalDate date) {
        if (date == null) {
            testDates.remove(viewer);
            testMasks.remove(viewer);
            testStreaks.remove(viewer);
        } else {
            testDates.put(viewer, date);
        }
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

    protected String claimSection(int year) {
        return key() + "." + year;
    }

    protected String streakSection(int year) {
        return key() + "." + year + ".streak";
    }

    public long claimedMask(UUID uuid) {
        if (isTesting(uuid)) {
            return testMasks.getOrDefault(uuid, 0L);
        }
        return CalendarRules.parseMask(store.get(uuid, claimSection(seasonYear(uuid))));
    }

    public ClaimStreak streak(UUID uuid) {
        if (isTesting(uuid)) {
            return testStreaks.getOrDefault(uuid, ClaimStreak.NEW);
        }
        return ClaimStreak.parse(store.get(uuid, streakSection(seasonYear(uuid))));
    }

    /** The streak as it stands for the player today (0 once broken). */
    public int currentStreak(Player player) {
        return streak(player.getUniqueId()).current(today(player.getUniqueId()).toEpochDay(), streakGrace(player));
    }

    public DoorState state(UUID uuid, int door) {
        return rules().state(door, today(uuid), CalendarRules.isClaimed(claimedMask(uuid), door));
    }

    public int openedCount(UUID uuid) {
        return Long.bitCount(claimedMask(uuid));
    }

    /** Doors the player could open right now. */
    public int claimableCount(UUID uuid) {
        if (!inSeason() && !isTesting(uuid)) {
            return 0;
        }
        CalendarRules rules = rules();
        int year = seasonYear(uuid);
        long mask = claimedMask(uuid);
        LocalDate today = today(uuid);
        int count = 0;
        for (int door = rules.firstDay(); door <= rules.lastDayIn(year); door++) {
            if (rules.state(door, today, CalendarRules.isClaimed(mask, door)) == DoorState.OPEN) {
                count++;
            }
        }
        return count;
    }

    /**
     * Opens a door and hands over its prize, both at once. Empty when the door cannot be opened now (already
     * opened, locked, missed, the event is off, or the player's data is still loading). In test mode the door only
     * opens for this player's test and nothing is given.
     */
    public Optional<Granted> claim(Player player, int door) {
        UUID uuid = player.getUniqueId();
        boolean test = isTesting(uuid);
        if (!test && (!isEnabled() || !store.isLoaded(uuid))) {
            return Optional.empty();
        }
        CalendarRules rules = rules();
        int year = seasonYear(uuid);
        LocalDate today = today(uuid);
        long mask = claimedMask(uuid);
        if (rules.state(door, today, CalendarRules.isClaimed(mask, door)) != DoorState.OPEN) {
            return Optional.empty();
        }
        Reward prize = prize(door);
        Optional<Rank> rank = rankOf(player);
        Reward bonus = rankBonus(prize, rank.map(r -> r.value(Perk.CALENDAR_BONUS_PERCENT)).orElse(0));
        long opened = CalendarRules.withClaimed(mask, door);

        // The door is marked opened before anything is handed over: the store write is queued ahead of the
        // money, so a crash can lose a prize but never give one twice.
        if (test) {
            testMasks.put(uuid, opened);
        } else {
            store.set(uuid, claimSection(year), Long.toString(opened));
        }

        ClaimStreak before = streak(uuid);
        ClaimStreak after = before.claimOn(today.toEpochDay(), streakGrace(player));
        List<Reward> extras = new ArrayList<>();
        List<String> extraNames = new ArrayList<>();
        FileConfiguration config = config();
        if (streaksEnabled()) {
            for (Map.Entry<Integer, ConfigurationSection> milestone : streakBonuses(config).entrySet()) {
                int length = milestone.getKey();
                if (after.streak() >= length && !after.hasMilestone(length)) {
                    extras.add(Reward.read(milestone.getValue()));
                    extraNames.add(milestone.getValue().getString("NAME", "&e" + length + "-day streak"));
                    after = after.withMilestone(length);
                }
            }
        }
        ConfigurationSection completion = config.getConfigurationSection("COMPLETION");
        if (completion != null && !after.completed() && allOpened(rules, year, opened)) {
            extras.add(Reward.read(completion));
            extraNames.add(completion.getString("NAME", "&6Every door opened"));
            after = after.withCompleted();
        }
        if (!after.equals(before)) {
            if (test) {
                testStreaks.put(uuid, after);
            } else {
                store.set(uuid, streakSection(year), after.serialize());
            }
        }

        Granted granted = new Granted(door, prize, bonus, rank.map(Rank::display).orElse(""), List.copyOf(extras),
                List.copyOf(extraNames), after.streak(), test);
        if (!test) {
            EconomyContext context = EconomyContext.of(key(), key() + " door " + door + " (" + year + ")");
            prize.plus(bonus).give(plugin, player, EconomyReason.EVENT_REWARD, context);
            for (Reward extra : extras) {
                extra.give(plugin, player, EconomyReason.EVENT_REWARD, context);
            }
            if (config.getBoolean("LOG-CLAIMS", true)) {
                plugin.getLogger().info("[" + id.displayName() + "] " + player.getName() + " opened door " + door + " ("
                        + year + "): " + String.join(", ", stripped(granted.lines(plugin))));
            }
            onDoorOpened(player, granted);
        }
        return Optional.of(granted);
    }

    private static List<String> stripped(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(line.replaceAll("&#[0-9A-Fa-f]{6}|&[0-9A-FK-ORa-fk-or]", ""));
        }
        return out;
    }

    private static boolean allOpened(CalendarRules rules, int year, long mask) {
        for (int door = rules.firstDay(); door <= rules.lastDayIn(year); door++) {
            if (!CalendarRules.isClaimed(mask, door)) {
                return false;
            }
        }
        return true;
    }

    /** STREAK.BONUSES.&lt;days&gt; sections, shortest first. */
    static Map<Integer, ConfigurationSection> streakBonuses(FileConfiguration config) {
        Map<Integer, ConfigurationSection> out = new TreeMap<>();
        ConfigurationSection bonuses = config.getConfigurationSection("STREAK.BONUSES");
        if (bonuses == null) {
            return out;
        }
        for (String key : bonuses.getKeys(false)) {
            try {
                int length = Integer.parseInt(key.trim());
                ConfigurationSection section = bonuses.getConfigurationSection(key);
                if (length >= 1 && length <= 62 && section != null) {
                    out.put(length, section);
                }
            } catch (NumberFormatException ignored) {
                // Not a streak length; validate() reports it.
            }
        }
        return out;
    }

    /** The extra money and shards a rank's percent adds to a prize (never items or commands). */
    static Reward rankBonus(Reward prize, int percent) {
        if (percent <= 0) {
            return Reward.NONE;
        }
        double money = Perks.withPercent(prize.money(), percent) - prize.money();
        long shards = Perks.withPercent(prize.shards(), percent) - prize.shards();
        return new Reward(Math.max(0D, Math.round(money * 100D) / 100D), Math.max(0L, shards), List.of(), List.of(),
                List.of());
    }

    protected Optional<Rank> rankOf(Player player) {
        Perks perks = plugin.getV3Module() == null ? null : plugin.getV3Module().perks();
        return perks == null ? Optional.empty() : perks.rankOf(node -> PermissionUtils.has(player, node));
    }

    protected int streakGrace(Player player) {
        return rankOf(player).map(rank -> rank.value(Perk.CALENDAR_STREAK_GRACE_DAYS)).orElse(0);
    }

    /** Re-closes one door (or every door and the streak when {@code door <= 0}) for a player, online or not. */
    public void reset(UUID uuid, int door, Consumer<Boolean> done) {
        int year = seasonYear();
        String section = claimSection(year);
        store.getAnywhere(uuid, section, stored -> {
            long mask = CalendarRules.parseMask(stored);
            long updated = door <= 0 ? 0L : CalendarRules.withoutClaimed(mask, door);
            store.set(uuid, section, updated == 0L ? null : Long.toString(updated));
            if (door <= 0) {
                store.set(uuid, streakSection(year), null);
            }
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

    /** Extra buttons for the calendar's bottom row (slots 45, 50, 51 and 52). */
    public Map<Integer, MenuButton> extraButtons(Player viewer) {
        return Map.of();
    }

    /** The command that opens the calendar itself (not a hub around it). */
    public String calendarCommand() {
        return command();
    }

    /** Called after a real (not test) door was opened and paid, on the player's thread. */
    protected void onDoorOpened(Player player, Granted granted) {
    }

    /** Opens what the player command opens. The calendar itself by default. */
    public void open(Player player) {
        new CalendarMenu(plugin, this).open(player);
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
        return isLastDoor(door) ? theme().lastDoorName() : theme().accent() + "Door " + door;
    }

    /** The prize behind a door, with any in-game edits applied (without rank bonuses). */
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
        // Edited prizes keep the console commands from the file but drop its DISPLAY lines, which would no
        // longer describe what the door holds.
        return new Reward(custom.money(), custom.shards(), custom.items(), base.commands(), custom.display());
    }

    /** The rank bonus a player would get on a door, for the menu. */
    public Reward bonusFor(Player player, int door) {
        return rankBonus(prize(door), rankOf(player).map(r -> r.value(Perk.CALENDAR_BONUS_PERCENT)).orElse(0));
    }

    public boolean hasOverride(int door) {
        return overrides.isConfigurationSection("DOORS." + door);
    }

    /** Saves an in-game edit of a door's prize (money, shards and items). */
    public void saveOverride(int door, Reward reward) {
        YamlConfiguration copy = copyOverrides();
        copy.set("DOORS." + door, null);
        ConfigurationSection section = copy.createSection("DOORS." + door);
        reward.write(section);
        section.set("COMMANDS", null);
        overrides = copy;
        writeOverrides(copy);
        plugin.getLogger().info("[" + id.displayName() + "] Door " + door + " prize edited in game.");
    }

    public void clearOverride(int door) {
        YamlConfiguration copy = copyOverrides();
        copy.set("DOORS." + door, null);
        overrides = copy;
        writeOverrides(copy);
    }

    private YamlConfiguration copyOverrides() {
        YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(overrides.saveToString());
        } catch (InvalidConfigurationException impossible) {
            throw new IllegalStateException(impossible);
        }
        return copy;
    }

    private File prizeFile() {
        return new File(plugin.getDataFolder(), "data/" + key() + "-prizes.yml");
    }

    public final void loadOverrides() {
        File file = prizeFile();
        YamlConfiguration loaded = new YamlConfiguration();
        if (file.exists()) {
            try {
                loaded.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read " + file.getPath() + "; in-game "
                        + id.displayName() + " prizes are ignored until it is fixed.", exception);
            }
        }
        overrides = loaded;
    }

    private void writeOverrides(YamlConfiguration snapshot) {
        String text = snapshot.saveToString();
        File file = prizeFile();
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                Files.createDirectories(file.getParentFile().toPath());
                File temp = new File(file.getParentFile(), file.getName() + ".tmp");
                Files.writeString(temp.toPath(), "# Prizes set with /" + command() + " edit. Delete a door here to go back"
                        + " to " + fileName + ".\n" + text, StandardCharsets.UTF_8);
                Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + file.getPath(), exception);
            }
        });
    }
}
