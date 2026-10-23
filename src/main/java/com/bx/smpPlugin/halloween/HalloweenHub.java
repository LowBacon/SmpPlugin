// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.perks.Perk;
import com.bx.smpPlugin.perks.Perks;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class HalloweenHub {

    public record ShopItem(String id, String name, String icon, long price, int limit, Reward reward,
                           List<String> description) {
    }

    public record Ranked(String name, long earned) {
    }

    public enum Purchase { BOUGHT, NOT_RUNNING, UNKNOWN, NOT_ENOUGH, LIMIT, LOADING }

    private static final long LEADERBOARD_TTL_MILLIS = 60_000L;

    private final SmpPlugin plugin;
    private final HalloweenManager halloween;
    private final PlayerStore store;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private volatile List<Ranked> leaderboard = List.of();
    private volatile long leaderboardYear;
    private volatile long leaderboardAt;
    private volatile boolean leaderboardLoading;
    private volatile FileConfiguration questsFrom;
    private volatile List<Quest> quests = List.of();
    private volatile boolean running;
    private volatile long runningAt;

    public HalloweenHub(SmpPlugin plugin, HalloweenManager halloween, PlayerStore store) {
        this.plugin = plugin;
        this.halloween = halloween;
        this.store = store;
        List<String> problems = new ArrayList<>();
        Quest.readAll(config().getConfigurationSection("QUESTS"), problems);
        problems.addAll(TreatTable.read(config().getConfigurationSection("TRICK-OR-TREAT.TREATS")).problems());
        for (String problem : problems) {
            plugin.getLogger().warning("[Halloween] " + problem);
        }
    }

    public HalloweenManager halloween() {
        return halloween;
    }

    FileConfiguration config() {
        return halloween.config();
    }

    private Object lock(UUID uuid) {
        return locks.computeIfAbsent(uuid, ignored -> new Object());
    }

    private String section(String part) {
        return "halloween." + halloween.seasonYear() + "." + part;
    }

    public String candyName() {
        return config().getString("CANDY.NAME", "Candy");
    }

    // ------------------------------------------------------------------ candy

    public CandyAccount candy(UUID uuid) {
        return CandyAccount.parse(store.get(uuid, section("candy")));
    }

    public long earn(Player player, long amount, boolean withBonus, String why) {
        UUID uuid = player.getUniqueId();
        if (amount <= 0L || !store.isLoaded(uuid)) {
            return 0L;
        }
        long added = withBonus ? Perks.withPercent(amount, perk(player, Perk.CANDY_BONUS_PERCENT)) : amount;
        List<Map.Entry<Long, ConfigurationSection>> reached = new ArrayList<>();
        synchronized (lock(uuid)) {
            CandyAccount before = candy(uuid);
            CandyAccount after = before.earn(added);
            for (Map.Entry<Long, ConfigurationSection> milestone : milestones().entrySet()) {
                if (after.earned() >= milestone.getKey() && !after.hasMilestone(milestone.getKey())) {
                    after = after.withMilestone(milestone.getKey());
                    reached.add(milestone);
                }
            }
            added = after.balance() - before.balance();
            store.set(uuid, section("candy"), after.serialize());
        }
        if (added > 0L) {
            Text.actionBar(player, Text.get("HALLOWEEN.CANDY-EARNED", "&6+{amount} {candy} &8({why})",
                    "{amount}", Long.toString(added), "{candy}", candyName(), "{why}", why));
        }
        for (Map.Entry<Long, ConfigurationSection> milestone : reached) {
            Reward reward = Reward.read(milestone.getValue());
            reward.give(plugin, player, EconomyReason.EVENT_REWARD,
                    EconomyContext.of("halloween", "candy milestone " + milestone.getKey()));
            String name = milestone.getValue().getString("NAME", "&6" + milestone.getKey() + " " + candyName());
            Text.send(player, "HALLOWEEN.MILESTONE", "&6&lMilestone! &7{name}&7: {prize}", "{name}", name,
                    "{prize}", String.join("&7, ", reward.describe(plugin)));
            com.bx.smpPlugin.core.MenuFx.soundKey(player, "ui.toast.challenge_complete", 0.7F, 1.2F);
        }
        return added;
    }

    public boolean spend(Player player, long amount) {
        UUID uuid = player.getUniqueId();
        if (!store.isLoaded(uuid)) {
            return false;
        }
        synchronized (lock(uuid)) {
            Optional<CandyAccount> after = candy(uuid).spend(amount);
            after.ifPresent(account -> store.set(uuid, section("candy"), account.serialize()));
            return after.isPresent();
        }
    }

    public void adjust(UUID uuid, java.util.function.LongUnaryOperator change, Consumer<Long> done) {
        String section = section("candy");
        if (store.isLoaded(uuid)) {
            long result;
            synchronized (lock(uuid)) {
                CandyAccount account = candy(uuid);
                CandyAccount after = account.withBalance(change.applyAsLong(account.balance()));
                store.set(uuid, section, after.serialize());
                result = after.balance();
            }
            done.accept(result);
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            CandyAccount account = CandyAccount.parse(store.readBlocking(uuid, section));
            CandyAccount after = account.withBalance(change.applyAsLong(account.balance()));
            store.set(uuid, section, after.serialize());
            plugin.getSpigotScheduler().runGlobal(() -> done.accept(after.balance()));
        });
    }

    public Map<Long, ConfigurationSection> milestones() {
        Map<Long, ConfigurationSection> out = new TreeMap<>();
        ConfigurationSection section = config().getConfigurationSection("MILESTONES");
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            try {
                long threshold = Long.parseLong(key.trim());
                ConfigurationSection milestone = section.getConfigurationSection(key);
                if (threshold > 0L && milestone != null) {
                    out.put(threshold, milestone);
                }
            } catch (NumberFormatException ignored) {
                // Not a number; reported nowhere else, so say it once here.
                plugin.getLogger().warning("[Halloween] MILESTONES." + key + " is not a number of candy.");
            }
        }
        return out;
    }

    private int perk(Player player, Perk perk) {
        Perks perks = plugin.getV3Module() == null ? null : plugin.getV3Module().perks();
        return perks == null ? 0 : perks.value(node -> PermissionUtils.has(player, node), perk);
    }

    // ------------------------------------------------------------------ quests

    public List<Quest> quests() {
        FileConfiguration current = config();
        if (current != questsFrom) {
            quests = Quest.readAll(current.getConfigurationSection("QUESTS"), new ArrayList<>());
            questsFrom = current;
        }
        return quests;
    }

    public boolean running() {
        long now = System.currentTimeMillis();
        if (now - runningAt > 1000L) {
            running = halloween.inSeason();
            runningAt = now;
        }
        return running;
    }

    public QuestProgress progress(UUID uuid) {
        return QuestProgress.parse(store.get(uuid, section("quests")));
    }

    public void track(Player player, QuestType type, String target, int amount) {
        UUID uuid = player.getUniqueId();
        if (!running() || !store.isLoaded(uuid)) {
            return;
        }
        boolean counts = false;
        for (Quest quest : quests()) {
            counts |= quest.counts(type, target);
        }
        if (!counts) {
            return;
        }
        List<Quest> finished = new ArrayList<>();
        synchronized (lock(uuid)) {
            QuestProgress before = progress(uuid);
            QuestProgress after = before;
            for (Quest quest : quests()) {
                if (quest.counts(type, target) && !after.isDone(quest.id())) {
                    after = after.add(quest.id(), amount, quest.amount());
                    if (after.isDone(quest.id())) {
                        finished.add(quest);
                    }
                }
            }
            if (after.equals(before)) {
                return;
            }
            if (finished.isEmpty()) {
                store.setDeferred(uuid, section("quests"), after.serialize());
            } else {
                store.set(uuid, section("quests"), after.serialize());
            }
        }
        for (Quest quest : finished) {
            quest.reward().give(plugin, player, EconomyReason.EVENT_REWARD,
                    EconomyContext.of("halloween", "quest " + quest.id()));
            earn(player, quest.candy(), true, Text.color(quest.name()));
            List<String> prize = new ArrayList<>(quest.reward().describe(plugin));
            if (quest.candy() > 0L) {
                prize.add("&6" + quest.candy() + " " + candyName());
            }
            Text.send(player, "HALLOWEEN.QUEST-DONE", "&6&lQuest done! &7{quest}&7: {prize}", "{quest}", quest.name(),
                    "{prize}", String.join("&7, ", prize));
            com.bx.smpPlugin.core.MenuFx.soundKey(player, "ui.toast.challenge_complete", 0.8F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ daily treat bag

    public boolean dailyBagEnabled() {
        return config().getBoolean("DAILY-BAG.ENABLED", true);
    }

    private long todayEpochDay() {
        return halloween.now().toLocalDate().toEpochDay();
    }

    public boolean dailyBagReady(UUID uuid) {
        if (!dailyBagEnabled() || !halloween.inSeason() || !store.isLoaded(uuid)) {
            return false;
        }
        String last = store.get(uuid, section("bag"));
        return last == null || !last.equals(Long.toString(todayEpochDay()));
    }

    public Optional<List<String>> claimDailyBag(Player player) {
        UUID uuid = player.getUniqueId();
        Reward reward;
        long candy;
        synchronized (lock(uuid)) {
            if (!dailyBagReady(uuid)) {
                return Optional.empty();
            }
            store.set(uuid, section("bag"), Long.toString(todayEpochDay()));
            ConfigurationSection bag = config().getConfigurationSection("DAILY-BAG");
            reward = Reward.read(bag);
            candy = bag == null ? 0L : Math.max(0L, bag.getLong("CANDY", 0L));
        }
        reward.give(plugin, player, EconomyReason.EVENT_REWARD, EconomyContext.of("halloween",
                "daily treat bag " + LocalDate.ofEpochDay(todayEpochDay())));
        long added = earn(player, candy, true, Text.get("HALLOWEEN.BAG-REASON", "treat bag"));
        List<String> lines = new ArrayList<>(reward.describe(plugin));
        if (added > 0L) {
            lines.add("&6" + added + " " + candyName());
        }
        return Optional.of(lines);
    }

    // ------------------------------------------------------------------ candy shop

    public List<ShopItem> shop() {
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection section = config().getConfigurationSection("SHOP.ITEMS");
        if (section == null) {
            return items;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null || item.getLong("PRICE", 0L) <= 0L) {
                continue;
            }
            items.add(new ShopItem(id, item.getString("NAME", "&6" + id), item.getString("ICON", "CHEST"),
                    item.getLong("PRICE"), Math.max(0, item.getInt("LIMIT", 0)), Reward.read(item),
                    item.getStringList("DESCRIPTION")));
        }
        return items;
    }

    public int bought(UUID uuid, String id) {
        return QuestProgress.parse(store.get(uuid, section("shop"))).counts().getOrDefault(id, 0);
    }

    public Purchase buy(Player player, String id) {
        UUID uuid = player.getUniqueId();
        if (!halloween.inSeason()) {
            return Purchase.NOT_RUNNING;
        }
        if (!store.isLoaded(uuid)) {
            return Purchase.LOADING;
        }
        ShopItem item = shop().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
        if (item == null) {
            return Purchase.UNKNOWN;
        }
        synchronized (lock(uuid)) {
            QuestProgress purchases = QuestProgress.parse(store.get(uuid, section("shop")));
            int count = purchases.counts().getOrDefault(id, 0);
            if (item.limit() > 0 && count >= item.limit()) {
                return Purchase.LIMIT;
            }
            Optional<CandyAccount> after = candy(uuid).spend(item.price());
            if (after.isEmpty()) {
                return Purchase.NOT_ENOUGH;
            }
            store.set(uuid, section("candy"), after.get().serialize());
            store.set(uuid, section("shop"), purchases.add(id, 1, Integer.MAX_VALUE).serialize());
        }
        item.reward().give(plugin, player, EconomyReason.EVENT_REWARD, EconomyContext.of("halloween", "candy shop " + id));
        plugin.getLogger().info("[Halloween] " + player.getName() + " bought " + id + " for " + item.price() + " candy.");
        return Purchase.BOUGHT;
    }

    // ------------------------------------------------------------------ leaderboard

    public void leaderboard(int size, Consumer<List<Ranked>> callback) {
        long year = halloween.seasonYear();
        long now = System.currentTimeMillis();
        if (leaderboardYear == year && now - leaderboardAt < LEADERBOARD_TTL_MILLIS || leaderboardLoading) {
            callback.accept(leaderboard.subList(0, Math.min(size, leaderboard.size())));
            return;
        }
        leaderboardLoading = true;
        String section = section("candy");
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Ranked> ranked = new ArrayList<>();
            try {
                List<Map.Entry<UUID, Long>> rows = new ArrayList<>();
                store.readSectionBlocking(section).forEach((uuid, stored) -> {
                    long earned = CandyAccount.parse(stored).earned();
                    if (earned > 0L) {
                        rows.add(Map.entry(uuid, earned));
                    }
                });
                rows.sort(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()));
                for (Map.Entry<UUID, Long> row : rows.subList(0, Math.min(10, rows.size()))) {
                    String name = plugin.getDatabaseManager().getLastKnownUsername(row.getKey());
                    ranked.add(new Ranked(name == null ? row.getKey().toString().substring(0, 8) : name, row.getValue()));
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not build the Halloween candy leaderboard", exception);
            }
            List<Ranked> result = List.copyOf(ranked);
            plugin.getSpigotScheduler().runGlobal(() -> {
                leaderboard = result;
                leaderboardYear = year;
                leaderboardAt = System.currentTimeMillis();
                leaderboardLoading = false;
                callback.accept(result.subList(0, Math.min(size, result.size())));
            });
        });
    }
}
