// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.MenuStyle;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.seasonal.CalendarMenu;
import com.bx.smpPlugin.seasonal.EventId;
import com.bx.smpPlugin.seasonal.EventStatus;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class HalloweenMenus {

    private static final String ORANGE = "&#FF9A1F";
    private static final String PURPLE = "&#B07CFF";

    private final SmpPlugin plugin;
    private final HalloweenHub hub;
    private final TrickOrTreatCommand trickOrTreat;

    public HalloweenMenus(SmpPlugin plugin, HalloweenHub hub, TrickOrTreatCommand trickOrTreat) {
        this.plugin = plugin;
        this.hub = hub;
        this.trickOrTreat = trickOrTreat;
    }

    private HalloweenManager halloween() {
        return hub.halloween();
    }

    public void openHub(Player player) {
        new Hub().open(player);
    }

    private boolean allowed(Player player) {
        if (!PermissionUtils.has(player, HalloweenManager.USE_PERMISSION) || !halloween().isEnabled()) {
            player.closeInventory();
            Text.send(player, "HALLOWEEN.DISABLED", "&7Halloween is closed on this server right now.");
            return false;
        }
        return true;
    }

    private boolean running(Player player) {
        if (halloween().inSeason()) {
            return true;
        }
        MenuFx.sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.6F);
        long wait = halloween().events().secondsUntilStart(EventId.HALLOWEEN);
        Text.send(player, "HALLOWEEN.NOT-RUNNING", "&7Halloween is not running right now{when}.",
                "{when}", wait > 0 ? "; it starts in &f" + Text.duration(wait) + "&7" : "");
        return false;
    }

    private static String bar(long done, long goal) {
        return Text.bar(goal <= 0 ? 1D : (double) done / goal, 20, ORANGE, "&8");
    }

    private static Material material(String raw, Material fallback) {
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() || material.isAir() ? fallback : material;
    }

    private abstract class Page extends BaseMenu {

        Page(String title, int size) {
            super(HalloweenMenus.this.plugin, title, size);
        }

        void frame() {
            MenuStyle.frame(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        }

        @Override
        public final void handleClick(int slot, Player player, ClickType clickType) {
            if (!allowed(player)) {
                return;
            }
            click(slot, player, clickType);
        }

        abstract void click(int slot, Player player, ClickType clickType);
    }

    // ------------------------------------------------------------------ hub

    private final class Hub extends Page {

        private static final int INFO = 4;
        private static final int TOP = 13;
        private static final int CALENDAR = 20;
        private static final int TRICK = 22;
        private static final int BAG = 24;
        private static final int QUESTS = 29;
        private static final int SHOP = 31;
        private static final int MILESTONES = 33;
        private static final int CLOSE = 40;

        Hub() {
            super(Text.get("HALLOWEEN.HUB-TITLE", "&8🎃 " + ORANGE + "&lHalloween"), 45);
        }

        @Override
        public void open(Player player) {
            super.open(player);
            MenuFx.sound(player, Sound.ENTITY_WITCH_AMBIENT, 0.5F, 1.1F);
        }

        @Override
        public void build(Player player) {
            clear();
            frame();
            HalloweenManager halloween = halloween();
            CandyAccount candy = hub.candy(player.getUniqueId());
            String candyName = hub.candyName();
            EventStatus status = halloween.events().status(EventId.HALLOWEEN);
            List<String> info = new ArrayList<>();
            info.add(MenuStyle.MUTED + "Open doors, knock for treats,");
            info.add(MenuStyle.MUTED + "finish quests and spend your");
            info.add(MenuStyle.MUTED + candyName.toLowerCase(Locale.ROOT) + " in the shop.");
            info.add("");
            info.add(MenuStyle.TEXT + candyName + ": " + ORANGE + candy.balance() + MenuStyle.DIM + " (earned " + candy.earned() + ")");
            if (status == EventStatus.ACTIVE) {
                long untilHalloween = halloween.rules().secondsUntilLastDay(halloween.now());
                info.add(untilHalloween > 0 ? MenuStyle.MUTED + "Halloween in &f" + Text.duration(untilHalloween)
                        : ORANGE + "&lHappy Halloween!");
                info.add(MenuStyle.DIM + candyName + " is for this season only.");
            } else {
                long wait = halloween.events().secondsUntilStart(EventId.HALLOWEEN);
                info.add(MenuStyle.WARN + (wait > 0 ? "Starts in " + Text.duration(wait) : "Over for this year"));
            }
            set(INFO, ItemUtils.setGlint(ItemUtils.createItem(Material.JACK_O_LANTERN,
                    ORANGE + "&lHalloween &8" + halloween.seasonYear(), info), status == EventStatus.ACTIVE));

            CalendarRules rules = halloween.rules();
            int ready = halloween.claimableCount(player.getUniqueId());
            set(CALENDAR, ItemUtils.setGlint(ItemUtils.createItem(Material.CARVED_PUMPKIN, ORANGE + "&lSpooky Calendar",
                    MenuStyle.lore(List.of(
                            MenuStyle.MUTED + "A door a day with a prize inside.",
                            "",
                            MenuStyle.TEXT + "Opened: " + ORANGE + halloween.openedCount(player.getUniqueId()) + "&7/"
                                    + rules.doorCount(halloween.seasonYear()),
                            ready > 0 ? MenuStyle.GOOD + ready + " ready to open!" : MenuStyle.MUTED + "Nothing to open right now."),
                            "Click to open")), ready > 0));

            if (halloween.trickOrTreatShown()) {
                List<String> lore = new ArrayList<>();
                lore.add(MenuStyle.MUTED + "Knock on doors for treats");
                lore.add(MenuStyle.MUTED + "(or a harmless trick).");
                lore.add("");
                int left = trickOrTreat.knocksLeft(player);
                if (left >= 0) {
                    lore.add(MenuStyle.TEXT + "Knocks left today: " + ORANGE + left);
                }
                long wait = trickOrTreat.secondsUntilKnock(player);
                lore.add(wait > 0 ? MenuStyle.MUTED + "Next knock in &f" + Text.duration(wait) : MenuStyle.GOOD + "Ready to knock!");
                set(TRICK, ItemUtils.createItem(Material.CAULDRON, PURPLE + "&lTrick or Treat", MenuStyle.lore(lore, "Click to knock")));
            }

            if (hub.dailyBagEnabled()) {
                boolean bagReady = hub.dailyBagReady(player.getUniqueId());
                set(BAG, ItemUtils.setGlint(ItemUtils.createItem(Material.BUNDLE, ORANGE + "&lDaily Treat Bag",
                        bagReady ? MenuStyle.lore(List.of(MenuStyle.MUTED + "A free bag of treats every day.", "",
                                MenuStyle.GOOD + "Ready!"), "Click to open")
                                : List.of(MenuStyle.MUTED + "A free bag of treats every day.", "",
                                MenuStyle.MUTED + "Come back tomorrow.")), bagReady));
            }

            List<Quest> quests = hub.quests();
            QuestProgress progress = hub.progress(player.getUniqueId());
            long done = quests.stream().filter(quest -> progress.isDone(quest.id())).count();
            set(QUESTS, ItemUtils.createItem(Material.WRITABLE_BOOK, ORANGE + "&lQuests", MenuStyle.lore(List.of(
                    MenuStyle.MUTED + "Season goals that pay in " + candyName.toLowerCase(Locale.ROOT) + ".", "",
                    MenuStyle.TEXT + "Done: " + ORANGE + done + "&7/" + quests.size(), bar(done, quests.size())),
                    "Click to see them")));
            set(SHOP, ItemUtils.createItem(Material.CAKE, ORANGE + "&lCandy Shop", MenuStyle.lore(List.of(
                    MenuStyle.MUTED + "Spend your " + candyName.toLowerCase(Locale.ROOT) + " on treats.", "",
                    MenuStyle.TEXT + "You have " + ORANGE + candy.balance()), "Click to shop")));
            Map<Long, ConfigurationSection> milestones = hub.milestones();
            Optional<Long> next = milestones.keySet().stream().filter(threshold -> threshold > candy.earned()).findFirst();
            List<String> milestoneLore = new ArrayList<>();
            milestoneLore.add(MenuStyle.MUTED + "Prizes for the " + candyName.toLowerCase(Locale.ROOT) + " you earn.");
            milestoneLore.add("");
            if (next.isPresent()) {
                milestoneLore.add(MenuStyle.TEXT + "Next at " + ORANGE + next.get() + MenuStyle.TEXT + " earned");
                milestoneLore.add(bar(candy.earned(), next.get()));
            } else {
                milestoneLore.add(MenuStyle.GOOD + "Every milestone reached!");
            }
            set(MILESTONES, ItemUtils.createItem(Material.EXPERIENCE_BOTTLE, ORANGE + "&lMilestones",
                    MenuStyle.lore(milestoneLore, "Click to see them")));
            set(TOP, ItemUtils.createItem(Material.WITHER_SKELETON_SKULL, ORANGE + "&lCandy Leaderboard",
                    MenuStyle.lore(List.of(MenuStyle.MUTED + "Who earned the most this season."), "Click to see it")));
            set(CLOSE, MenuStyle.close());
        }

        @Override
        void click(int slot, Player player, ClickType clickType) {
            switch (slot) {
                case CALENDAR -> new CalendarMenu(plugin, halloween()).open(player);
                case TRICK -> {
                    if (halloween().trickOrTreatShown()) {
                        player.closeInventory();
                        trickOrTreat.knock(player, null);
                    }
                }
                case BAG -> {
                    if (!running(player)) {
                        return;
                    }
                    Optional<List<String>> bag = hub.claimDailyBag(player);
                    if (bag.isEmpty()) {
                        MenuFx.sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.6F);
                        Text.send(player, "HALLOWEEN.BAG-ALREADY", "&7You opened today's treat bag already.");
                        return;
                    }
                    MenuFx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);
                    Text.send(player, "HALLOWEEN.BAG-OPENED", "&6Treat bag! &7You got {prize}&7.",
                            "{prize}", String.join("&7, ", bag.get()));
                    build(player);
                }
                case QUESTS -> new Quests().open(player);
                case SHOP -> new Shop().open(player);
                case MILESTONES -> new Milestones().open(player);
                case TOP -> new Leaderboard().open(player);
                case CLOSE -> player.closeInventory();
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------ quests

    private final class Quests extends Page {

        private static final int BACK = 49;

        Quests() {
            super(Text.get("HALLOWEEN.QUESTS-TITLE", "&8🎃 " + ORANGE + "&lHalloween Quests"), 54);
        }

        @Override
        public void build(Player player) {
            clear();
            frame();
            QuestProgress progress = hub.progress(player.getUniqueId());
            List<Integer> slots = contentSlots();
            List<Quest> quests = hub.quests();
            for (int i = 0; i < quests.size() && i < slots.size(); i++) {
                Quest quest = quests.get(i);
                boolean done = progress.isDone(quest.id());
                int count = done ? quest.amount() : Math.min(quest.amount(), progress.count(quest.id()));
                List<String> lore = new ArrayList<>();
                quest.description().forEach(line -> lore.add(MenuStyle.MUTED + line));
                lore.add("");
                lore.add(MenuStyle.TEXT + "Progress: " + ORANGE + count + "&7/" + quest.amount());
                lore.add(bar(count, quest.amount()));
                lore.add("");
                lore.add(MenuStyle.TEXT + "Reward:");
                if (quest.candy() > 0L) {
                    lore.add("&8• &6" + quest.candy() + " " + hub.candyName());
                }
                quest.reward().describe(plugin).forEach(line -> lore.add("&8• " + line));
                if (done) {
                    lore.add("");
                    lore.add(MenuStyle.GOOD + "✔ Done");
                }
                ItemStack item = ItemUtils.createItem(done ? Material.LIME_DYE : material(quest.icon(), Material.PAPER),
                        (done ? MenuStyle.GOOD : ORANGE) + "&l" + Text.color(quest.name()), lore);
                set(slots.get(i), ItemUtils.setGlint(item, done));
            }
            if (quests.isEmpty()) {
                set(22, ItemUtils.createItem(Material.GRAY_DYE, MenuStyle.MUTED + "No quests this season"));
            }
            set(BACK, MenuStyle.back("the Halloween hub"));
        }

        @Override
        void click(int slot, Player player, ClickType clickType) {
            if (slot == BACK) {
                openHub(player);
            }
        }
    }

    // ------------------------------------------------------------------ shop

    private final class Shop extends Page {

        private static final int BACK = 49;
        private static final int BALANCE = 4;
        private final Map<Integer, HalloweenHub.ShopItem> bySlot = new HashMap<>();
        private int armed = -1;
        private long armedAt;

        Shop() {
            super(Text.get("HALLOWEEN.SHOP-TITLE", "&8🎃 " + ORANGE + "&lCandy Shop"), 54);
        }

        @Override
        public void build(Player player) {
            clear();
            bySlot.clear();
            frame();
            String candyName = hub.candyName();
            CandyAccount candy = hub.candy(player.getUniqueId());
            set(BALANCE, ItemUtils.createItem(Material.SUGAR, ORANGE + "&l" + candy.balance() + " " + candyName,
                    List.of(MenuStyle.MUTED + "Earn more from treats, quests", MenuStyle.MUTED + "and the daily treat bag.")));
            List<Integer> slots = contentSlots();
            List<HalloweenHub.ShopItem> items = hub.shop();
            for (int i = 0; i < items.size() && i < slots.size(); i++) {
                HalloweenHub.ShopItem item = items.get(i);
                int slot = slots.get(i);
                bySlot.put(slot, item);
                int bought = hub.bought(player.getUniqueId(), item.id());
                boolean soldOut = item.limit() > 0 && bought >= item.limit();
                boolean affordable = candy.balance() >= item.price();
                List<String> lore = new ArrayList<>();
                item.description().forEach(line -> lore.add(MenuStyle.MUTED + line));
                item.reward().describe(plugin).forEach(line -> lore.add("&8• " + line));
                lore.add("");
                lore.add(MenuStyle.TEXT + "Price: " + (affordable ? ORANGE : MenuStyle.BAD) + item.price() + " " + candyName);
                if (item.limit() > 0) {
                    lore.add(MenuStyle.TEXT + "Bought: " + bought + "&7/" + item.limit());
                }
                lore.add("");
                if (soldOut) {
                    lore.add(MenuStyle.MUTED + "Sold out for you this season.");
                } else if (!affordable) {
                    lore.add(MenuStyle.BAD + "Not enough " + candyName.toLowerCase(Locale.ROOT) + ".");
                } else if (armed == slot) {
                    lore.add(MenuStyle.WARN + "&lClick again to buy!");
                } else {
                    lore.add(MenuStyle.action("Click to buy"));
                }
                ItemStack icon = ItemUtils.createItem(soldOut ? Material.BARRIER : material(item.icon(), Material.CHEST),
                        ORANGE + "&l" + Text.color(item.name()), lore);
                set(slot, ItemUtils.setGlint(icon, armed == slot));
            }
            if (items.isEmpty()) {
                set(22, ItemUtils.createItem(Material.GRAY_DYE, MenuStyle.MUTED + "The shop is empty"));
            }
            set(BACK, MenuStyle.back("the Halloween hub"));
        }

        @Override
        void click(int slot, Player player, ClickType clickType) {
            if (slot == BACK) {
                openHub(player);
                return;
            }
            HalloweenHub.ShopItem item = bySlot.get(slot);
            if (item == null || !running(player)) {
                return;
            }
            long now = System.currentTimeMillis();
            if (armed != slot || now - armedAt > 5000L) {
                // A first click only asks for a second one, so a stray click never spends candy.
                armed = slot;
                armedAt = now;
                MenuFx.sound(player, Sound.UI_BUTTON_CLICK, 0.6F, 1.6F);
                build(player);
                return;
            }
            armed = -1;
            HalloweenHub.Purchase result = hub.buy(player, item.id());
            switch (result) {
                case BOUGHT -> {
                    MenuFx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
                    Text.send(player, "HALLOWEEN.BOUGHT", "&aBought {item}&a for &6{price} {candy}&a.",
                            "{item}", item.name(), "{price}", Long.toString(item.price()), "{candy}", hub.candyName());
                }
                case NOT_ENOUGH -> Text.send(player, "HALLOWEEN.NOT-ENOUGH", "&cYou need &6{price} {candy}&c for that.",
                        "{price}", Long.toString(item.price()), "{candy}", hub.candyName());
                case LIMIT -> Text.send(player, "HALLOWEEN.SOLD-OUT", "&7You bought all of those you can this season.");
                case LOADING -> Text.send(player, "GENERAL.LOADING", "&7Your data is still loading. Try again in a moment.");
                case NOT_RUNNING, UNKNOWN -> Text.send(player, "HALLOWEEN.SHOP-CLOSED", "&7The candy shop is closed right now.");
            }
            build(player);
        }
    }

    // ------------------------------------------------------------------ milestones

    private final class Milestones extends Page {

        private static final int BACK = 49;

        Milestones() {
            super(Text.get("HALLOWEEN.MILESTONES-TITLE", "&8🎃 " + ORANGE + "&lCandy Milestones"), 54);
        }

        @Override
        public void build(Player player) {
            clear();
            frame();
            CandyAccount candy = hub.candy(player.getUniqueId());
            List<Integer> slots = contentSlots();
            int i = 0;
            for (Map.Entry<Long, ConfigurationSection> milestone : hub.milestones().entrySet()) {
                if (i >= slots.size()) {
                    break;
                }
                boolean reached = candy.hasMilestone(milestone.getKey());
                List<String> lore = new ArrayList<>();
                lore.add(MenuStyle.TEXT + "Earn " + ORANGE + milestone.getKey() + " " + hub.candyName());
                lore.add(bar(Math.min(candy.earned(), milestone.getKey()), milestone.getKey()));
                lore.add("");
                com.bx.smpPlugin.core.Reward.read(milestone.getValue()).describe(plugin).forEach(line -> lore.add("&8• " + line));
                lore.add("");
                lore.add(reached ? MenuStyle.GOOD + "✔ Reached and given" : MenuStyle.MUTED + "Given the moment you reach it.");
                String name = milestone.getValue().getString("NAME", ORANGE + milestone.getKey() + " " + hub.candyName());
                set(slots.get(i++), ItemUtils.setGlint(ItemUtils.createItem(reached ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                        name, lore), reached));
            }
            set(BACK, MenuStyle.back("the Halloween hub"));
        }

        @Override
        void click(int slot, Player player, ClickType clickType) {
            if (slot == BACK) {
                openHub(player);
            }
        }
    }

    // ------------------------------------------------------------------ leaderboard

    private final class Leaderboard extends Page {

        private static final int BACK = 49;
        private static final int[] PODIUM = {13, 21, 23, 29, 30, 31, 32, 33, 39, 41};

        Leaderboard() {
            super(Text.get("HALLOWEEN.TOP-TITLE", "&8🎃 " + ORANGE + "&lCandy Leaderboard"), 54);
        }

        @Override
        public void build(Player player) {
            clear();
            frame();
            set(22, ItemUtils.createItem(Material.CLOCK, MenuStyle.MUTED + "Loading..."));
            set(BACK, MenuStyle.back("the Halloween hub"));
            hub.leaderboard(PODIUM.length, rows -> {
                if (!MenuFx.isViewing(player, this)) {
                    return;
                }
                set(22, null);
                for (int i = 0; i < rows.size(); i++) {
                    HalloweenHub.Ranked row = rows.get(i);
                    Material icon = i == 0 ? Material.GOLD_BLOCK : i == 1 ? Material.IRON_BLOCK : i == 2 ? Material.COPPER_BLOCK
                            : Material.PUMPKIN;
                    set(PODIUM[i], ItemUtils.createItem(icon, ORANGE + "#" + (i + 1) + " &f" + row.name(),
                            List.of(MenuStyle.TEXT + row.earned() + " " + hub.candyName() + " earned")));
                }
                if (rows.isEmpty()) {
                    set(22, ItemUtils.createItem(Material.GRAY_DYE, MenuStyle.MUTED + "Nobody has earned any yet"));
                }
            });
        }

        @Override
        void click(int slot, Player player, ClickType clickType) {
            if (slot == BACK) {
                openHub(player);
            }
        }
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots;
    }
}
