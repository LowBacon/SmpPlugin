package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.managers.LeaderboardManager.LeaderboardType;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.Team;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.RolePrefixResolver;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /stats: one player's numbers on a single screen, each with their place on the matching
 * leaderboard. Offline players and leaderboard places are read on the database thread, so opening
 * someone's stats never stalls the server.
 */
public class StatsMenu extends BaseMenu {

    private static final String MENU_PATH = "STATS-MENU";
    private static final int SIZE = 45;
    private static final int HEAD_SLOT = 4;
    private static final int LEADERBOARD_SLOT = 39;
    private static final int CLOSE_SLOT = 40;
    private static final int REFRESH_SLOT = 41;
    /** Leaderboards a place is looked up for; the rest just show their number. */
    private static final LeaderboardType[] RANKED = {
            LeaderboardType.MONEY, LeaderboardType.SHARDS, LeaderboardType.KILLS,
            LeaderboardType.PLAYTIME, LeaderboardType.HIGHEST_KILL_STREAK, LeaderboardType.BLOCKS_BROKEN
    };

    private final UUID targetUuid;
    private final String targetName;
    private int loadToken;

    public StatsMenu(SmpPlugin plugin, UUID targetUuid, String targetName) {
        super(plugin, title(plugin, targetName), SIZE);
        this.targetUuid = targetUuid;
        this.targetName = targetName == null ? "Unknown" : targetName;
    }

    @Override
    public void build(Player viewer) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        set(22, ItemUtils.createItem(Material.CLOCK, "&7Loading stats...", List.of()));
        renderButtons();

        int token = ++loadToken;
        PlayerData live = plugin.getPlayerDataManager().get(targetUuid);
        plugin.getDatabaseManager().executeAsync(() -> {
            PlayerData data = live != null ? live : plugin.getDatabaseManager().loadPlayer(targetUuid);
            Map<LeaderboardType, Integer> places = new EnumMap<>(LeaderboardType.class);
            LeaderboardManager leaderboards = plugin.getLeaderboardManager();
            if (data != null && leaderboards != null) {
                for (LeaderboardType type : RANKED) {
                    LeaderboardManager.LeaderboardEntry entry = leaderboards.getPlayerEntry(targetUuid, type);
                    if (entry != null) {
                        places.put(type, entry.position());
                    }
                }
            }
            plugin.getSpigotScheduler().runEntity(viewer, () -> {
                if (token != loadToken || viewer.getOpenInventory().getTopInventory().getHolder() != this) {
                    return;
                }
                render(viewer, data, places);
            });
        });
    }

    private void render(Player viewer, PlayerData data, Map<LeaderboardType, Integer> places) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        renderButtons();
        if (data == null) {
            set(22, ItemUtils.createItem(Material.BARRIER, "&cNo stats yet",
                    List.of("&7" + targetName + " has no recorded stats.")));
            return;
        }

        CurrencyManager currency = plugin.getCurrencyManager();
        set(HEAD_SLOT, headItem(data));

        set(19, stat(Material.GOLD_INGOT, "&#00FC00Money",
                currency.format(CurrencyManager.CurrencyType.MONEY, data.getMoney(), true), places.get(LeaderboardType.MONEY)));
        set(20, stat(Material.AMETHYST_SHARD, "&#A303F9Shards",
                currency.format(CurrencyManager.CurrencyType.SHARDS, data.getShards(), true), places.get(LeaderboardType.SHARDS)));
        set(21, stat(Material.DIAMOND_SWORD, "&#FC0000Kills",
                NumberUtils.format(data.getKills()), places.get(LeaderboardType.KILLS),
                "&7Deaths: &f" + NumberUtils.format(data.getDeaths()),
                "&7K/D: &f" + kd(data.getKills(), data.getDeaths())));
        set(22, stat(Material.BLAZE_POWDER, "&#F97603Kill streak",
                NumberUtils.format(data.getKillStreak()), places.get(LeaderboardType.HIGHEST_KILL_STREAK),
                "&7Best ever: &f" + NumberUtils.format(data.getHighestKillStreak())));
        set(23, stat(Material.CLOCK, "&#FCE300Playtime",
                NumberUtils.formatTimeLong(data.getTotalPlaytimeSeconds()), places.get(LeaderboardType.PLAYTIME)));
        set(24, teamItem());
        set(25, stat(Material.EMERALD, "&aEconomy",
                currency.format(CurrencyManager.CurrencyType.MONEY, data.getMoneyMade(), true) + " &7earned", null,
                "&7Spent: &f" + currency.format(CurrencyManager.CurrencyType.MONEY, data.getMoneySpent(), true)));

        set(29, stat(Material.IRON_PICKAXE, "&bBlocks broken",
                NumberUtils.format(data.getBlocksBroken()), places.get(LeaderboardType.BLOCKS_BROKEN)));
        set(31, stat(Material.GRASS_BLOCK, "&bBlocks placed", NumberUtils.format(data.getBlocksPlaced()), null));
        set(33, stat(Material.ZOMBIE_HEAD, "&bMobs killed", NumberUtils.format(data.getMobsKilled()), null));
    }

    private void renderButtons() {
        set(CLOSE_SLOT, ItemUtils.createItem(Material.BARRIER, "&cClose", List.of()));
        set(REFRESH_SLOT, ItemUtils.createItem(Material.SUNFLOWER, "&eRefresh", List.of("&7Reload these numbers.")));
        if (plugin.getConfigManager().isCommandEnabled("LEADERBOARDS")) {
            set(LEADERBOARD_SLOT, ItemUtils.createItem(Material.GOLDEN_HELMET, "&6Leaderboards",
                    List.of("&7See who is on top.")));
        }
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot == CLOSE_SLOT) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.closeInventory();
        } else if (slot == REFRESH_SLOT) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            build(player);
        } else if (slot == LEADERBOARD_SLOT && plugin.getConfigManager().isCommandEnabled("LEADERBOARDS")) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.closeInventory();
            player.performCommand("leaderboard");
        }
    }

    private ItemStack headItem(PlayerData data) {
        Player online = Bukkit.getPlayer(targetUuid);
        List<String> lore = new ArrayList<>();
        if (online != null) {
            String rank = RolePrefixResolver.resolve(online);
            if (rank != null && !rank.isBlank()) {
                lore.add("&7Rank: " + rank.trim());
            }
            lore.add("&a● Online now");
        } else {
            lore.add("&c● Offline");
        }
        Team team = plugin.getTeamManager().getTeam(targetUuid);
        if (team != null) {
            lore.add("&7Team: &b" + team.getName().toUpperCase(Locale.ROOT));
        }
        lore.add("");
        lore.add("&7K/D &f" + kd(data.getKills(), data.getDeaths())
                + " &8| &7Played &f" + NumberUtils.formatTime(data.getTotalPlaytimeSeconds()));
        // A disguised player gets a plain head so the skin does not give them away.
        boolean disguised = plugin.getHideManager() != null && plugin.getHideManager().isHidden(targetUuid);
        return disguised
                ? ItemUtils.createItem(Material.PLAYER_HEAD, "&f&l" + targetName, lore)
                : ItemUtils.createPlayerHead(Bukkit.getOfflinePlayer(targetUuid), "&f&l" + targetName, lore);
    }

    private ItemStack teamItem() {
        Team team = plugin.getTeamManager().getTeam(targetUuid);
        if (team == null) {
            return ItemUtils.createItem(Material.WHITE_BANNER, "&bTeam", List.of("&fNo team"));
        }
        List<String> lore = new ArrayList<>();
        lore.add("&f" + team.getName().toUpperCase(Locale.ROOT));
        lore.add("&7Members: &f" + team.getMemberCount());
        lore.add(team.isLeader(targetUuid) ? "&6Leader" : "&7Member");
        return ItemUtils.createItem(Material.CYAN_BANNER, "&bTeam", lore);
    }

    private ItemStack stat(Material material, String name, String value, Integer place, String... extra) {
        List<String> lore = new ArrayList<>();
        lore.add("&f" + value);
        for (String line : extra) {
            lore.add(line);
        }
        if (place != null) {
            lore.add("");
            lore.add(placeLine(place));
        }
        return ItemUtils.hideMenuNoise(ItemUtils.createItem(material, name, lore), false);
    }

    static String placeLine(int place) {
        String color = place == 1 ? "&6" : place == 2 ? "&f" : place == 3 ? "&#CD7F32" : "&7";
        return color + "#" + NumberUtils.format(place) + " &7on the leaderboard";
    }

    static String kd(int kills, int deaths) {
        double ratio = deaths <= 0 ? kills : kills / (double) deaths;
        return String.format(Locale.ROOT, "%.2f", ratio);
    }

    private static String title(SmpPlugin plugin, String targetName) {
        String name = targetName == null ? "" : targetName;
        return plugin.getConfigManager().getMenus().getString(MENU_PATH + ".TITLE", "&8{username}'s stats")
                .replace("{username_upper}", name.toUpperCase(Locale.ROOT))
                .replace("{username}", name);
    }
}
