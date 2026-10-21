package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Reveal;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /daily: one reward a day. Coming back on consecutive days builds a streak through seven reward
 * days; day 7 is the big one, and every finished week adds a bonus on top.
 */
public final class DailyRewards implements CommandExecutor, Listener {

    public static final String PERMISSION = "smpplugin.daily";
    private static final String SECTION = "daily";

    private final SmpPlugin plugin;
    private final PlayerStore store;

    public DailyRewards(SmpPlugin plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    private ZoneId zone() {
        return CalendarRules.zone(config().getString("DAILY.TIME-ZONE", ""));
    }

    long today() {
        return LocalDate.now(zone()).toEpochDay();
    }

    int graceDays() {
        return Math.max(0, config().getInt("DAILY.GRACE-DAYS", 1));
    }

    /** The streak a player has today (0 once broken), for placeholders. */
    public int currentStreak(UUID uuid) {
        return streak(uuid).currentStreak(today(), graceDays());
    }

    public boolean isReady(UUID uuid) {
        return streak(uuid).canClaim(today());
    }

    DailyStreak streak(UUID uuid) {
        return DailyStreak.parse(store.get(uuid, SECTION));
    }

    long secondsUntilTomorrow() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone());
        return ChronoUnit.SECONDS.between(now, midnight);
    }

    /** The reward for a given streak length, including the weekly bonus. */
    Reward rewardFor(int streak) {
        ConfigurationSection day = config().getConfigurationSection("DAILY.DAYS." + DailyStreak.rewardDay(streak));
        Reward reward = Reward.read(day);
        int weeks = DailyStreak.weeksDone(streak);
        double bonus = config().getDouble("DAILY.WEEKLY-BONUS-MONEY", 0D) * weeks;
        return bonus > 0 ? reward.withMoney(reward.money() + bonus) : reward;
    }

    /** The extra money and shards the player's rank adds (DAILY-BONUS-PERCENT in perks.yml). */
    private Reward rankBonus(Player player, Reward base) {
        com.bx.smpPlugin.perks.Perks perks = plugin.getV3Module() == null ? null : plugin.getV3Module().perks();
        int percent = perks == null ? 0
                : perks.value(node -> PermissionUtils.has(player, node), com.bx.smpPlugin.perks.Perk.DAILY_BONUS_PERCENT);
        if (percent <= 0) {
            return Reward.NONE;
        }
        double money = com.bx.smpPlugin.perks.Perks.withPercent(base.money(), percent) - base.money();
        long shards = com.bx.smpPlugin.perks.Perks.withPercent(base.shards(), percent) - base.shards();
        return new Reward(Math.max(0D, Math.round(money * 100D) / 100D), Math.max(0L, shards), List.of(), List.of(), List.of());
    }

    private String rankName(Player player) {
        com.bx.smpPlugin.perks.Perks perks = plugin.getV3Module() == null ? null : plugin.getV3Module().perks();
        return perks == null ? "" : perks.rankOf(node -> PermissionUtils.has(player, node))
                .map(com.bx.smpPlugin.perks.Rank::display).orElse("");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (!store.isLoaded(player.getUniqueId())) {
            Text.send(player, "GENERAL.LOADING", "&7Your data is still loading. Try again in a moment.");
            return true;
        }
        new DailyMenu(player).open(player);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!config().getBoolean("DAILY.JOIN-REMINDER", true) || !PermissionUtils.has(player, PERMISSION)
                || !plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.DAILY_REWARDS)) {
            return;
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline() && streak(player.getUniqueId()).canClaim(today())) {
                Text.line(player, Text.prefix(),
                        Text.get("DAILY.REMINDER", "&7Your daily reward is ready! "),
                        Text.button(Text.get("DAILY.REMINDER-BUTTON", "&a&l[Claim]"), "daily",
                                Text.get("DAILY.REMINDER-HOVER", "&7Click to open /daily")));
            }
        }, 80L);
    }

    /** The 7-day reward strip. */
    final class DailyMenu extends BaseMenu {

        private static final int INFO_SLOT = 4;
        private static final int CLOSE_SLOT = 22;
        private boolean busy;

        DailyMenu(Player player) {
            super(DailyRewards.this.plugin, Text.get("DAILY.TITLE", "&8✦ &a&lDaily Rewards"), 27);
        }

        @Override
        public void open(Player player) {
            build(player);
            player.openInventory(inventory);
            MenuFx.sound(player, Sound.BLOCK_CHEST_OPEN, 0.5F, 1.2F);
        }

        @Override
        public void build(Player player) {
            clear();
            fill(Material.BLACK_STAINED_GLASS_PANE);
            DailyStreak state = streak(player.getUniqueId());
            long today = today();
            boolean ready = state.canClaim(today);
            int current = state.currentStreak(today, graceDays());
            int next = ready ? state.nextStreak(today, graceDays()) : current;
            int nextDay = DailyStreak.rewardDay(next);
            // Days of this week already claimed: everything before the next reward day.
            int claimedThisWeek = ready ? nextDay - 1 : DailyStreak.rewardDay(current);

            for (int day = 1; day <= 7; day++) {
                int streakOfDay = next - nextDay + day;
                Reward reward = rewardFor(Math.max(1, streakOfDay));
                List<String> extra = new ArrayList<>();
                ItemStack item;
                if (day <= claimedThisWeek) {
                    extra.add("");
                    extra.add("&aClaimed");
                    item = reward.previewItem(plugin, "&8Day " + day + " &a✔", extra, Material.MINECART);
                } else if (ready && day == nextDay) {
                    extra.add("");
                    extra.add("&e&l▶ Click to claim!");
                    item = reward.previewItem(plugin, "&a&lDay " + day + (day == 7 ? " &6★" : ""), extra,
                            day == 7 ? Material.ENDER_CHEST : Material.CHEST_MINECART);
                    ItemUtils.setGlint(item, true);
                } else {
                    extra.add("");
                    extra.add(day == nextDay ? "&7Ready in &f" + Text.duration(secondsUntilTomorrow()) : "&8Keep your streak going");
                    item = reward.previewItem(plugin, "&7Day " + day + (day == 7 ? " &6★" : ""), extra,
                            day == 7 ? Material.ENDER_CHEST : Material.CHEST);
                }
                item.setAmount(day);
                set(9 + day, item);
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Streak: &a" + current + " day" + (current == 1 ? "" : "s"));
            lore.add("&7Rewards claimed: &f" + state.total());
            lore.add("");
            lore.add(ready ? "&aToday's reward is ready!" : "&7Next reward in &f" + Text.duration(secondsUntilTomorrow()));
            lore.add("&8Miss more than " + (graceDays() + 1) + " days and the streak restarts.");
            set(INFO_SLOT, ItemUtils.setGlint(ItemUtils.createItem(Material.CLOCK, "&a&lDaily Rewards", lore), ready));
            set(CLOSE_SLOT, ItemUtils.createItem(Material.BARRIER, "&cClose"));
        }

        @Override
        public void handleClick(int slot, Player player, ClickType clickType) {
            if (busy) {
                return;
            }
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (slot < 10 || slot > 16) {
                return;
            }
            UUID uuid = player.getUniqueId();
            // Checked again on every click: the feature, the permission or the data may have changed meanwhile.
            if (!PermissionUtils.has(player, PERMISSION) || !store.isLoaded(uuid)
                    || !plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.DAILY_REWARDS)) {
                player.closeInventory();
                return;
            }
            DailyStreak state = streak(uuid);
            long today = today();
            if (!state.canClaim(today)) {
                MenuFx.sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.6F);
                Text.send(player, "DAILY.ALREADY", "&7You already claimed today's reward. Come back in &f{time}&7.",
                        "{time}", Text.duration(secondsUntilTomorrow()));
                return;
            }
            int nextDay = DailyStreak.rewardDay(state.nextStreak(today, graceDays()));
            if (slot - 9 != nextDay) {
                return;
            }
            DailyStreak claimed = state.claim(today, graceDays());
            Reward base = rewardFor(claimed.streak());
            Reward bonus = rankBonus(player, base);
            Reward reward = base.plus(bonus);
            // Claim and pay in one step, before the spin: leaving or a crash mid-spin can never pay twice. The store
            // write is queued ahead of the money.
            store.set(uuid, SECTION, claimed.serialize());
            reward.give(plugin, player, com.bx.smpPlugin.models.EconomyReason.REWARD,
                    com.bx.smpPlugin.economy.EconomyContext.of("daily", "daily reward, streak " + claimed.streak()));
            busy = true;
            List<ItemStack> frames = new ArrayList<>();
            for (Material material : List.of(Material.GOLD_NUGGET, Material.GOLD_INGOT, Material.EMERALD,
                    Material.DIAMOND, Material.AMETHYST_SHARD, Material.GOLD_BLOCK)) {
                frames.add(ItemUtils.createItem(material, "&a&l? ? ?"));
            }
            MenuFx.spin(plugin, player, this, inventory, slot, frames, 14, () -> {
                busy = false;
                if (!player.isOnline()) {
                    return;
                }
                List<String> lines = new ArrayList<>(base.describe(plugin));
                if (!bonus.isEmpty()) {
                    lines.add(Text.get("DAILY.RANK-BONUS", "{rank} &7bonus: {bonus}", "{rank}", rankName(player),
                            "{bonus}", String.join("&7, ", bonus.describe(plugin))));
                }
                MenuFx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.3F);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 25, 0.5, 0.6, 0.5, 0);
                Text.title(player, Text.get("DAILY.CLAIMED-TITLE", "&a&lDay {day} claimed!", "{day}", Integer.toString(nextDay)),
                        Text.get("DAILY.CLAIMED-SUBTITLE", "&7Streak: &a{streak}", "{streak}", Integer.toString(claimed.streak())),
                        5, 40, 10);
                Text.send(player, "DAILY.CLAIMED", "&aDaily reward claimed: {prize} &7(streak &a{streak}&7)",
                        "{prize}", String.join("&7, ", lines), "{streak}", Integer.toString(claimed.streak()));
                if (MenuFx.isViewing(player, this)) {
                    build(player);
                }
                plugin.getSpigotScheduler().runEntityLater(player, () -> {
                    if (MenuFx.isViewing(player, this)
                            && config().getBoolean("DAILY.DIALOG-REVEAL", true)
                            && plugin.getDialogManager() != null) {
                        player.closeInventory();
                        List<String> body = new ArrayList<>(lines);
                        body.add(" ");
                        body.add("&7Streak: &a" + claimed.streak() + " day" + (claimed.streak() == 1 ? "" : "s"));
                        Reveal.dialog(plugin, player, "&a&lDaily Reward", "✦", "CHEST_MINECART", body,
                                "&aOpen /daily", "daily");
                    }
                }, 25L);
            });
        }
    }
}
