package com.bx.smpPlugin.ranked;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The /ranked screen, three rows:
 * <pre>
 *  row 2   queue button | your rating and record | the top 10
 *  row 3   how ranked works | tiers | close
 * </pre>
 * Buttons only call {@link RankedManager}, which checks everything again.
 */
final class RankedMenu extends BaseMenu {

    private static final int QUEUE_SLOT = 11;
    private static final int PROFILE_SLOT = 13;
    private static final int TOP_SLOT = 15;
    private static final int HELP_SLOT = 21;
    private static final int TIERS_SLOT = 22;
    private static final int CLOSE_SLOT = 23;

    private final RankedManager ranked;

    RankedMenu(SmpPlugin plugin, RankedManager ranked) {
        super(plugin, "&6&lRanked PvP", 27);
        this.ranked = ranked;
    }

    @Override
    public void build(Player player) {
        inventory.clear();
        fill(Material.BLACK_STAINED_GLASS_PANE);

        boolean queued = ranked.isQueued(player.getUniqueId());
        set(QUEUE_SLOT, queued
                ? ItemUtils.createItem(Material.BARRIER, "&c&lLeave the queue", List.of(
                        "&7Players waiting: &f" + ranked.queueSize(), "", "&e▶ Click to leave"))
                : ItemUtils.createItem(Material.DIAMOND_SWORD, "&a&lFind a ranked match", List.of(
                        "&7You are matched with a player", "&7close to your rating.",
                        "&7Players waiting: &f" + ranked.queueSize(), "", "&e▶ Click to join the queue")));

        RankedRepository.Profile profile = ranked.profile(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        if (profile == null) {
            lore.add("&7Loading...");
        } else {
            int position = ranked.position(player.getUniqueId());
            lore.add("&7Rating: &f" + profile.rating() + " " + ranked.tier(profile.rating()));
            lore.add("&7Peak: &f" + profile.peak());
            lore.add("&7Record: &a" + profile.wins() + "W &c" + profile.losses() + "L &7" + profile.draws() + "D");
            lore.add("&7Position: &f" + (position == 0 ? "not ranked yet" : "#" + position));
        }
        set(PROFILE_SLOT, ItemUtils.createPlayerHead(player, "&6&lYour rank", lore));

        List<String> top = new ArrayList<>();
        List<RankedRepository.Profile> board = ranked.leaderboard();
        if (board.isEmpty()) {
            top.add("&7Nobody has played yet. Be the first!");
        }
        for (int i = 0; i < Math.min(10, board.size()); i++) {
            RankedRepository.Profile entry = board.get(i);
            top.add("&6#" + (i + 1) + " &f" + entry.name() + " &7" + entry.rating());
        }
        top.add("");
        top.add("&e▶ Click for the full list");
        set(TOP_SLOT, ItemUtils.createItem(Material.GOLDEN_HELMET, "&6&lTop players", top));

        set(HELP_SLOT, ItemUtils.createItem(Material.BOOK, "&e&lHow ranked works", List.of(
                "&7Win to raise your rating, lose to", "&7lower it. Beating a stronger player",
                "&7is worth more. Your first games move", "&7your rating faster.", "",
                "&7Ranked matches use the duel arenas", "&7and the duel rules.")));
        List<String> tiers = new ArrayList<>();
        for (Map.Entry<Integer, String> tier : ranked.tiers()) {
            tiers.add(tier.getValue() + " &8" + tier.getKey() + "+");
        }
        set(TIERS_SLOT, ItemUtils.createItem(Material.NETHER_STAR, "&b&lTiers", tiers));
        set(CLOSE_SLOT, ItemUtils.createItem(Material.OAK_DOOR, "&cClose"));
    }

    @Override
    public void handleClick(int slot, Player player) {
        switch (slot) {
            case QUEUE_SLOT -> {
                if (ranked.isQueued(player.getUniqueId())) {
                    ranked.leave(player, false);
                } else {
                    ranked.join(player);
                }
                if (player.getOpenInventory().getTopInventory().getHolder() == this) {
                    build(player);
                }
            }
            case TOP_SLOT -> {
                player.closeInventory();
                player.performCommand("ranked top");
            }
            case CLOSE_SLOT -> player.closeInventory();
            default -> {
            }
        }
    }
}
