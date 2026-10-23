// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public final class AuctionHouseSounds {

    public static final String OPEN = "AUCTION_HOUSE.OPEN";
    public static final String CLICK = "AUCTION_HOUSE.CLICK";
    public static final String PAGE_TURN = "AUCTION_HOUSE.PAGE-TURN";
    public static final String SEARCH = "AUCTION_HOUSE.SEARCH";
    public static final String FILTER = "AUCTION_HOUSE.FILTER";
    public static final String REFRESH = "AUCTION_HOUSE.REFRESH";
    public static final String SUCCESS = "AUCTION_HOUSE.SUCCESS";
    public static final String FAIL = "AUCTION_HOUSE.FAIL";
    public static final String LIST_ITEM = "AUCTION_HOUSE.LIST-ITEM";
    public static final String CANCEL = "AUCTION_HOUSE.CANCEL";
    public static final String CLAIM = "AUCTION_HOUSE.CLAIM";
    public static final String CONFIRM = "AUCTION_HOUSE.CONFIRM";
    public static final String TOGGLE = "AUCTION_HOUSE.TOGGLE";
    public static final String BOUGHT_YOUR_ITEM = "AUCTION_HOUSE.BOUGHT-YOUR-ITEM";

    public static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(OPEN, "minecraft:block.chest.open|0.8|1.15"),
            Map.entry(CLICK, "minecraft:ui.button.click|1.0|1.0"),
            Map.entry(PAGE_TURN, "minecraft:item.book.page_turn|1.0|1.0"),
            Map.entry(SEARCH, "minecraft:entity.villager.work_cartographer|1.0|1.2"),
            Map.entry(FILTER, "minecraft:block.note_block.hat|1.0|1.2"),
            Map.entry(REFRESH, "minecraft:item.book.page_turn|1.0|1.15"),
            Map.entry(SUCCESS, "minecraft:entity.experience_orb.pickup|1.0|1.2"),
            Map.entry(FAIL, "minecraft:entity.villager.no|1.0|1.0"),
            Map.entry(LIST_ITEM, "minecraft:entity.villager.work_cartographer|1.0|1.0"),
            Map.entry(CANCEL, "minecraft:entity.item.break|1.0|0.85"),
            Map.entry(CLAIM, "minecraft:entity.item.pickup|1.0|1.15"),
            Map.entry(CONFIRM, "minecraft:ui.button.click|1.0|1.2"),
            Map.entry(TOGGLE, "minecraft:block.note_block.pling|0.9|1.1"),
            Map.entry(BOUGHT_YOUR_ITEM, "minecraft:entity.player.levelup|1.0|1.2")
    );

    private AuctionHouseSounds() {
    }

    public static String fallback(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULTS.get(CLICK);
        }
        String sound = DEFAULTS.get(path.toUpperCase(Locale.ROOT));
        return sound != null ? sound : DEFAULTS.get(CLICK);
    }

    public static void play(Player player, SmpPlugin plugin, String path) {
        if (player == null || plugin == null || plugin.getConfigManager() == null) {
            return;
        }
        SoundUtils.play(player, plugin.getConfigManager().getSound(path));
    }

    public static void click(Player player, SmpPlugin plugin) {
        play(player, plugin, CLICK);
    }

    public static void pageTurn(Player player, SmpPlugin plugin) {
        play(player, plugin, PAGE_TURN);
    }

    public static void fail(Player player, SmpPlugin plugin) {
        play(player, plugin, FAIL);
    }

    public static void success(Player player, SmpPlugin plugin) {
        play(player, plugin, SUCCESS);
    }
}
