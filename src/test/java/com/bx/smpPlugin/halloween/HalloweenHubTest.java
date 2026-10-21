package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.V3Module;
import com.bx.smpPlugin.managers.ConfigManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.perks.Perks;
import com.bx.smpPlugin.seasonal.EventControl;
import com.bx.smpPlugin.seasonal.SeasonalEvents;
import com.bx.smpPlugin.testsupport.EconomyHarness;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Candy, milestones, quests, the shop and the daily bag on a real SQLite store and economy, without a server. */
class HalloweenHubTest {

    private static final UUID LUNA = UUID.fromString("60000000-0000-0000-0000-0000000000b1");
    private static final UUID REMY = UUID.fromString("60000000-0000-0000-0000-0000000000b2");
    private static final Logger LOG = Logger.getLogger("HalloweenHubTest");

    private EconomyHarness harness;
    private YamlConfiguration halloweenFile;
    private PlayerStore store;
    private HalloweenHub hub;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        harness = new EconomyHarness();
        halloweenFile = new YamlConfiguration();
        halloweenFile.loadFromString("""
                TIME-ZONE: "UTC"
                PREVIEW-DATE: "2026-10-13"
                LOG-CLAIMS: false
                DAILY-BAG:
                  MONEY: 100
                  CANDY: 10
                MILESTONES:
                  20:
                    MONEY: 500
                  1000:
                    MONEY: 9999
                QUESTS:
                  pumpkins:
                    TYPE: BREAK_BLOCK
                    TARGET: PUMPKIN
                    AMOUNT: 3
                    CANDY: 15
                    REWARD:
                      MONEY: 300
                SHOP:
                  ITEMS:
                    pouch:
                      PRICE: 10
                      LIMIT: 1
                      SHARDS: 5
                TRICK-OR-TREAT:
                  TREATS:
                    small: {WEIGHT: 1, MONEY: 1}
                """);
        ConfigManager config = harness.plugin.getConfigManager();
        EconomyHarness.set(ConfigManager.class, config, "halloween", halloweenFile);
        YamlConfiguration perksFile = YamlConfiguration.loadConfiguration(new File("src/main/resources/perks.yml"));
        EconomyHarness.set(ConfigManager.class, config, "perks", perksFile);
        store = new PlayerStore(harness.plugin);
        store.initialize();
        V3Module module = new V3Module(harness.plugin);
        EconomyHarness.set(V3Module.class, module, "store", store);
        EconomyHarness.set(V3Module.class, module, "perks", new Perks(() -> perksFile, () -> true, LOG));
        EconomyHarness.set(SmpPlugin.class, harness.plugin, "v3Module", module);
        SeasonalEvents events = new SeasonalEvents(new EventControl(new File(harness.dataFolder, "data/events.yml"), LOG),
                LOG, line -> { });
        HalloweenManager halloween = new HalloweenManager(harness.plugin, store, events);
        hub = new HalloweenHub(harness.plugin, halloween, store);
        halloween.attach(hub, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        harness.close();
    }

    private Player join(UUID uuid, String name, String... permissions) throws Exception {
        harness.addOnline(uuid, name, 0D);
        assertTrue(store.loadBlocking(uuid));
        Set<String> granted = Set.of(permissions);
        Player.Spigot spigot = new Player.Spigot() {
            @Override
            public void sendMessage(ChatMessageType position, BaseComponent... components) {
                messages.add(BaseComponent.toPlainText(components));
            }

            @Override
            public void sendMessage(BaseComponent... components) {
                messages.add(BaseComponent.toPlainText(components));
            }
        };
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "isOnline" -> true;
                    case "spigot" -> spigot;
                    case "hasPermission" -> args[0] instanceof String node && granted.contains(node);
                    case "isPermissionSet" -> args[0] instanceof String node && granted.contains(node);
                    case "getEffectivePermissions" -> Set.of();
                    case "sendMessage" -> {
                        if (args[0] instanceof String text) {
                            messages.add(text);
                        }
                        yield null;
                    }
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
    void earningCandyPaysEachMilestoneOnceAndSpendingDoesNotUndoIt() throws Exception {
        Player luna = join(LUNA, "Luna");
        assertEquals(12L, hub.earn(luna, 12, true, "test"));
        assertEquals(0D, data(LUNA).getMoney(), 1e-9);
        hub.earn(luna, 10, true, "test");
        assertEquals(500D, data(LUNA).getMoney(), 1e-9, "the 20-candy milestone");
        assertTrue(hub.spend(luna, 22));
        hub.earn(luna, 1, true, "test");
        assertEquals(500D, data(LUNA).getMoney(), 1e-9, "spending never makes a milestone pay again");
        assertEquals(23L, hub.candy(LUNA).earned());
        assertEquals(1L, hub.candy(LUNA).balance());
    }

    @Test
    void aRankAddsItsCandyBonus() throws Exception {
        Player remy = join(REMY, "Remy", "smpplugin.rank.vipplus");
        assertEquals(12L, hub.earn(remy, 10, true, "test"), "VIP+ adds 20%");
        assertEquals(10L, hub.earn(remy, 10, false, "staff"), "no bonus when not asked for");
    }

    @Test
    void aQuestFinishesOnItsLastStepAndPaysOnce() throws Exception {
        Player luna = join(LUNA, "Luna");
        hub.track(luna, QuestType.BREAK_BLOCK, "PUMPKIN", 1);
        hub.track(luna, QuestType.BREAK_BLOCK, "MELON", 5);
        hub.track(luna, QuestType.BREAK_BLOCK, "PUMPKIN", 1);
        assertEquals(0D, data(LUNA).getMoney(), 1e-9);
        assertEquals(2, hub.progress(LUNA).count("pumpkins"));
        hub.track(luna, QuestType.BREAK_BLOCK, "PUMPKIN", 1);
        assertTrue(hub.progress(LUNA).isDone("pumpkins"));
        assertEquals(300D, data(LUNA).getMoney(), 1e-9);
        assertEquals(15L, hub.candy(LUNA).balance());
        hub.track(luna, QuestType.BREAK_BLOCK, "PUMPKIN", 10);
        assertEquals(300D, data(LUNA).getMoney(), 1e-9, "a finished quest pays once");

        // Finishing is written at once; progress before it only in batches.
        harness.drainWrites();
        assertEquals("pumpkins=done", store.readBlocking(LUNA, "halloween.2026.quests"));
    }

    @Test
    void questProgressIsSavedByTheBatchFlush() throws Exception {
        Player luna = join(LUNA, "Luna");
        hub.track(luna, QuestType.BREAK_BLOCK, "PUMPKIN", 2);
        harness.drainWrites();
        assertEquals(null, store.readBlocking(LUNA, "halloween.2026.quests"), "not written on every block");
        store.flushDeferred(null);
        harness.drainWrites();
        assertEquals("pumpkins=2", store.readBlocking(LUNA, "halloween.2026.quests"));
    }

    @Test
    void theShopTakesCandyOnceAndKeepsToItsLimit() throws Exception {
        Player luna = join(LUNA, "Luna");
        assertEquals(HalloweenHub.Purchase.NOT_ENOUGH, hub.buy(luna, "pouch"));
        hub.earn(luna, 15, false, "test");
        assertEquals(HalloweenHub.Purchase.BOUGHT, hub.buy(luna, "pouch"));
        assertEquals(5L, hub.candy(LUNA).balance());
        assertEquals(5L, data(LUNA).getShards());
        hub.earn(luna, 15, false, "test");
        assertEquals(HalloweenHub.Purchase.LIMIT, hub.buy(luna, "pouch"));
        assertEquals(20L, hub.candy(LUNA).balance(), "a refused purchase takes nothing");
        assertEquals(HalloweenHub.Purchase.UNKNOWN, hub.buy(luna, "nothing"));
    }

    @Test
    void theDailyBagOpensOncePerDay() throws Exception {
        Player luna = join(LUNA, "Luna");
        assertTrue(hub.dailyBagReady(LUNA));
        assertTrue(hub.claimDailyBag(luna).isPresent());
        assertTrue(hub.claimDailyBag(luna).isEmpty());
        assertEquals(100D, data(LUNA).getMoney(), 1e-9);
        assertEquals(10L, hub.candy(LUNA).balance());
        halloweenFile.set("PREVIEW-DATE", "2026-10-14");
        assertTrue(hub.dailyBagReady(LUNA), "a new day, a new bag");
    }

    @Test
    void nothingWorksOutsideTheSeason() throws Exception {
        Player luna = join(LUNA, "Luna");
        halloweenFile.set("PREVIEW-DATE", "2026-07-01");
        assertEquals(HalloweenHub.Purchase.NOT_RUNNING, hub.buy(luna, "pouch"));
        assertTrue(hub.claimDailyBag(luna).isEmpty());
    }

    @Test
    void theBundledHalloweenFilePassesTheCalendarCheck() throws Exception {
        EconomyHarness.set(ConfigManager.class, harness.plugin.getConfigManager(), "halloween",
                YamlConfiguration.loadConfiguration(new File("src/main/resources/halloween.yml")));
        assertEquals(List.of(), hub.halloween().validate());
    }

    @Test
    void staffCanChangeTheCandyOfAnOfflinePlayer() throws Exception {
        AtomicLong result = new AtomicLong(-1);
        hub.adjust(REMY, balance -> balance + 40, result::set);
        // The answer comes back on the main thread, which this test does not have; read the stored value instead.
        harness.drainWrites();
        harness.drainWrites();
        assertEquals("40;40;", store.readBlocking(REMY, "halloween.2026.candy"));
    }
}
