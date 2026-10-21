package com.bx.smpPlugin.advent;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.V3Module;
import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.economy.LedgerEntry;
import com.bx.smpPlugin.halloween.CalendarRules.DoorState;
import com.bx.smpPlugin.managers.ConfigManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.perks.Perks;
import com.bx.smpPlugin.seasonal.EventControl;
import com.bx.smpPlugin.seasonal.EventId;
import com.bx.smpPlugin.seasonal.EventMode;
import com.bx.smpPlugin.seasonal.SeasonCalendar;
import com.bx.smpPlugin.seasonal.SeasonalEvents;
import com.bx.smpPlugin.testsupport.EconomyHarness;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Advent Calendar end to end on a real SQLite player store and economy, without a server. */
class AdventCalendarTest {

    private static final UUID HOLLY = UUID.fromString("50000000-0000-0000-0000-0000000000a1");
    private static final UUID NOEL = UUID.fromString("50000000-0000-0000-0000-0000000000a2");

    private EconomyHarness harness;
    private YamlConfiguration advent;
    private PlayerStore store;
    private SeasonalEvents events;
    private AdventManager calendar;
    private final List<String> broadcasts = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        harness = new EconomyHarness();
        advent = new YamlConfiguration();
        advent.loadFromString("""
                MONTH: 12
                FIRST-DAY: 1
                LAST-DAY: 3
                CATCH-UP: true
                TIME-ZONE: "UTC"
                PREVIEW-DATE: "2026-12-01"
                STREAK:
                  ENABLED: true
                  BONUSES:
                    2:
                      MONEY: 200
                COMPLETION:
                  MONEY: 1000
                DOORS:
                  DEFAULT:
                    MONEY: 750
                    SHARDS: 3
                  3:
                    MONEY: 5000
                """);
        ConfigManager config = harness.plugin.getConfigManager();
        EconomyHarness.set(ConfigManager.class, config, "advent", advent);
        YamlConfiguration perksFile = YamlConfiguration.loadConfiguration(new File("src/main/resources/perks.yml"));
        EconomyHarness.set(ConfigManager.class, config, "perks", perksFile);

        store = new PlayerStore(harness.plugin);
        store.initialize();
        V3Module module = new V3Module(harness.plugin);
        EconomyHarness.set(V3Module.class, module, "store", store);
        EconomyHarness.set(V3Module.class, module, "perks",
                new Perks(() -> perksFile, () -> true, Logger.getLogger("AdventCalendarTest")));
        EconomyHarness.set(SmpPlugin.class, harness.plugin, "v3Module", module);

        events = new SeasonalEvents(new EventControl(new File(harness.dataFolder, "data/events.yml"),
                Logger.getLogger("AdventCalendarTest")), Logger.getLogger("AdventCalendarTest"), broadcasts::add);
        calendar = new AdventManager(harness.plugin, store, events);
    }

    @AfterEach
    void tearDown() throws Exception {
        harness.close();
    }

    private Player join(UUID uuid, String name, String... permissions) throws Exception {
        harness.addOnline(uuid, name, 0D);
        assertTrue(store.loadBlocking(uuid));
        Set<String> granted = Set.of(permissions);
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "isOnline" -> true;
                    case "hasPermission" -> args[0] instanceof String node && granted.contains(node);
                    case "isPermissionSet" -> args[0] instanceof String node && granted.contains(node);
                    case "getEffectivePermissions" -> Set.of();
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player[" + name + "]";
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        harness.online.put(uuid, player);
        return player;
    }

    private PlayerData data(UUID uuid) {
        return harness.plugin.getPlayerDataManager().get(harness.online.get(uuid));
    }

    @Test
    void openingTodaysDoorPaysAtOnceAndOnlyOnce() throws Exception {
        Player holly = join(HOLLY, "Holly");

        Optional<SeasonCalendar.Granted> first = calendar.claim(holly, 1);
        assertTrue(first.isPresent());
        assertFalse(first.get().test());
        assertEquals(750D, data(HOLLY).getMoney(), 1e-9, "the money is there before any animation");
        assertEquals(3L, data(HOLLY).getShards());
        assertEquals(DoorState.CLAIMED, calendar.state(HOLLY, 1));

        assertTrue(calendar.claim(holly, 1).isEmpty(), "a door opens once");
        assertTrue(calendar.claim(holly, 2).isEmpty(), "tomorrow's door is locked");
        assertEquals(750D, data(HOLLY).getMoney(), 1e-9);

        harness.drainWrites();
        LedgerEntry row = EconomyStore.history(harness.connection(), HOLLY, 1, 0).get(0);
        assertEquals("EVENT_REWARD", row.reason());
        assertEquals("advent door 1 (2026)", row.note());
        // A fresh read of the store (a restart) still knows the door is open.
        store.unload(HOLLY);
        assertTrue(store.loadBlocking(HOLLY));
        assertEquals(DoorState.CLAIMED, calendar.state(HOLLY, 1));
    }

    @Test
    void aRankAddsItsPercentToMoneyAndShardsOnly() throws Exception {
        Player noel = join(NOEL, "Noel", "smpplugin.rank.vipplus");

        SeasonCalendar.Granted granted = calendar.claim(noel, 1).orElseThrow();
        assertEquals(150D, granted.rankBonus().money(), 1e-9, "20% of 750");
        assertEquals(0L, granted.rankBonus().shards(), "20% of 3 shards rounds down to nothing");
        assertEquals(900D, data(NOEL).getMoney(), 1e-9);
    }

    @Test
    void streakBonusesAndTheCompletionPrizeArePaidOnce() throws Exception {
        Player holly = join(HOLLY, "Holly");
        calendar.claim(holly, 1).orElseThrow();
        advent.set("PREVIEW-DATE", "2026-12-02");
        SeasonCalendar.Granted second = calendar.claim(holly, 2).orElseThrow();
        assertEquals(2, second.streak());
        assertEquals(1, second.extras().size(), "the 2-day streak bonus");
        assertEquals(750D + 750D + 200D, data(HOLLY).getMoney(), 1e-9);

        advent.set("PREVIEW-DATE", "2026-12-03");
        SeasonCalendar.Granted last = calendar.claim(holly, 3).orElseThrow();
        assertEquals(1, last.extras().size(), "only the completion prize; the streak bonus was given already");
        assertEquals(750D + 750D + 200D + 5000D + 1000D, data(HOLLY).getMoney(), 1e-9);
        assertTrue(calendar.streak(HOLLY).completed());
    }

    @Test
    void catchUpOpensMissedDoorsButDoesNotRestoreTheStreak() throws Exception {
        Player holly = join(HOLLY, "Holly");
        advent.set("PREVIEW-DATE", "2026-12-03");
        calendar.claim(holly, 1).orElseThrow();
        SeasonCalendar.Granted second = calendar.claim(holly, 2).orElseThrow();
        assertEquals(1, second.streak(), "two doors on one day are one day of the streak");
        assertTrue(second.extras().isEmpty());
    }

    @Test
    void testModeOnlyPretendsAndGivesNothing() throws Exception {
        Player holly = join(HOLLY, "Holly", "smpplugin.admin.advent");
        calendar.setTestDate(HOLLY, java.time.LocalDate.of(2026, 12, 3));

        SeasonCalendar.Granted granted = calendar.claim(holly, 3).orElseThrow();
        assertTrue(granted.test());
        assertEquals(0D, data(HOLLY).getMoney(), 1e-9);
        assertEquals(DoorState.CLAIMED, calendar.state(HOLLY, 3), "the test sees its own opened door");

        calendar.setTestDate(HOLLY, null);
        assertEquals(DoorState.LOCKED, calendar.state(HOLLY, 3), "the real calendar is untouched");
        harness.drainWrites();
        assertEquals(0, harness.ledgerRows(HOLLY));
        assertEquals(null, store.readBlocking(HOLLY, "advent.2026"));
    }

    @Test
    void switchingTheEventOffStopsClaims() throws Exception {
        Player holly = join(HOLLY, "Holly");
        events.setMode(EventId.ADVENT, EventMode.OFF, "Admin");
        assertTrue(calendar.claim(holly, 1).isEmpty());
        assertEquals(0D, data(HOLLY).getMoney(), 1e-9);
        events.setMode(EventId.ADVENT, EventMode.AUTO, "Admin");
        assertTrue(calendar.claim(holly, 1).isPresent());
    }

    @Test
    void theBundledFilesPassTheCheckAndMistakesAreNamed() throws Exception {
        ConfigManager config = harness.plugin.getConfigManager();
        EconomyHarness.set(ConfigManager.class, config, "advent",
                YamlConfiguration.loadConfiguration(new File("src/main/resources/advent.yml")));
        assertEquals(List.of(), calendar.validate());

        YamlConfiguration broken = new YamlConfiguration();
        broken.loadFromString("""
                TIME-ZONE: "Mars/Olympus"
                MONTH: 13
                FIRST-DAY: 20
                LAST-DAY: 10
                PREVIEW-DATE: "2026-12-01"
                DOORS:
                  christmas: {MONEY: 1}
                  40: {MONEY: 1}
                STREAK:
                  BONUSES:
                    week: {MONEY: 1}
                """);
        EconomyHarness.set(ConfigManager.class, config, "advent", broken);
        List<String> problems = calendar.validate();
        assertEquals(7, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Mars/Olympus")), problems.toString());
    }

    @Test
    void aPlayerWhoseDataDidNotLoadCannotOpenDoors() throws Exception {
        Player holly = join(HOLLY, "Holly");
        store.unload(HOLLY);
        assertTrue(calendar.claim(holly, 1).isEmpty());
        assertEquals(0D, data(HOLLY).getMoney(), 1e-9);
    }
}
