package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CurrencyManager;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /stats: one small screen. The player's head on top, five numbers in a row, and two buttons.
 *
 * <pre>
 *  . . . . [head] . . . .
 *  . . [$] [◆] [⚔] [⌚] [⛏] . .
 *  . . . [top] . [x] . . .
 * </pre>
 *
 * Offline players are read on the database thread, so opening someone's stats never stalls the server.
 */
public class StatsMenu extends BaseMenu {

    private static final String MENU_PATH = "STATS-MENU";
    private static final int SIZE = 27;
    private static final int HEAD_SLOT = 4;
    private static final int LEADERBOARD_SLOT = 21;
    private static final int CLOSE_SLOT = 23;

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
        set(13, ItemUtils.createItem(Material.CLOCK, "&7Loading..."));
        renderButtons();

        int token = ++loadToken;
        PlayerData live = plugin.getPlayerDataManager().get(targetUuid);
        if (live != null) {
            render(live);
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            PlayerData data = plugin.getDatabaseManager().loadPlayer(targetUuid);
            plugin.getSpigotScheduler().runEntity(viewer, () -> {
                if (token == loadToken && viewer.getOpenInventory().getTopInventory().getHolder() == this) {
                    render(data);
                }
            });
        });
    }

    private void render(PlayerData data) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        renderButtons();
        if (data == null) {
            set(13, ItemUtils.createItem(Material.BARRIER, "&cNo stats yet", List.of("&7" + targetName + " has no recorded stats.")));
            return;
        }
        CurrencyManager currency = plugin.getCurrencyManager();
        set(HEAD_SLOT, headItem(data));
        set(11, stat(Material.GOLD_INGOT, "&aMoney", currency.format(CurrencyManager.CurrencyType.MONEY, data.getMoney(), true)));
        set(12, stat(Material.AMETHYST_SHARD, "&dShards", currency.format(CurrencyManager.CurrencyType.SHARDS, data.getShards(), true)));
        set(13, stat(Material.IRON_SWORD, "&cKills",
                NumberUtils.format(data.getKills()) + " &7kills, &f" + NumberUtils.format(data.getDeaths()) + " &7deaths",
                "&7K/D &f" + kd(data.getKills(), data.getDeaths())));
        set(14, stat(Material.CLOCK, "&ePlaytime", NumberUtils.formatTimeLong(data.getTotalPlaytimeSeconds())));
        set(15, stat(Material.IRON_PICKAXE, "&bBlocks mined", NumberUtils.format(data.getBlocksBroken())));
    }

    private void renderButtons() {
        if (plugin.getConfigManager().isCommandEnabled("LEADERBOARDS")) {
            set(LEADERBOARD_SLOT, ItemUtils.createItem(Material.GOLDEN_HELMET, "&6Leaderboards", List.of("&7See who is on top.")));
        }
        set(CLOSE_SLOT, ItemUtils.createItem(Material.BARRIER, "&cClose"));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        if (slot == CLOSE_SLOT) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.closeInventory();
        } else if (slot == LEADERBOARD_SLOT && plugin.getConfigManager().isCommandEnabled("LEADERBOARDS")) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.closeInventory();
            player.performCommand("leaderboard");
        }
    }

    private ItemStack headItem(PlayerData data) {
        Player online = Bukkit.getPlayer(targetUuid);
        List<String> lore = new ArrayList<>();
        lore.add(online != null ? "&a● Online" : "&7● Offline");
        if (online != null) {
            String rank = RolePrefixResolver.resolve(online);
            if (rank != null && !rank.isBlank()) {
                lore.add("&7Rank: " + rank.trim());
            }
        }
        Team team = plugin.getTeamManager() == null ? null : plugin.getTeamManager().getTeam(targetUuid);
        if (team != null) {
            lore.add("&7Team: &b" + team.getName().toUpperCase(Locale.ROOT));
        }
        // A disguised player gets a plain head so the skin does not give them away.
        boolean disguised = plugin.getHideManager() != null && plugin.getHideManager().isHidden(targetUuid);
        return disguised
                ? ItemUtils.createItem(Material.PLAYER_HEAD, "&f&l" + targetName, lore)
                : ItemUtils.createPlayerHead(Bukkit.getOfflinePlayer(targetUuid), "&f&l" + targetName, lore);
    }

    private ItemStack stat(Material material, String name, String value, String... extra) {
        List<String> lore = new ArrayList<>();
        lore.add("&f" + value);
        for (String line : extra) {
            lore.add(line);
        }
        return ItemUtils.hideMenuNoise(ItemUtils.createItem(material, name, lore), false);
    }

    /** "#3 on the leaderboard", coloured gold, silver and bronze for the top three. */
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
