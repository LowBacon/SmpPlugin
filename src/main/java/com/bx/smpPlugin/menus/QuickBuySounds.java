package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Dedicated Quick Buy sounds from {@code sounds.yml}, matching the Orders pattern.
 */
public final class QuickBuySounds {

    public static final String OPEN = "QUICK_BUY.OPEN";
    public static final String CLICK = "QUICK_BUY.CLICK";
    public static final String PAGE_TURN = "QUICK_BUY.PAGE-TURN";
    public static final String SEARCH = "QUICK_BUY.SEARCH";
    public static final String FILTER = "QUICK_BUY.FILTER";
    public static final String REFRESH = "QUICK_BUY.REFRESH";
    public static final String BUY_SUCCESS = "QUICK_BUY.BUY-SUCCESS";
    public static final String FAIL = "QUICK_BUY.FAIL";
    public static final String EDIT = "QUICK_BUY.EDIT";
    public static final String SAVE = "QUICK_BUY.SAVE";
    public static final String PIN = "QUICK_BUY.PIN";
    public static final String REMOVE = "QUICK_BUY.REMOVE";
    public static final String CANCEL = "QUICK_BUY.CANCEL";
    public static final String LIST_ITEM = "QUICK_BUY.LIST-ITEM";

    public static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(OPEN, "minecraft:block.chest.open|0.8|1.15"),
            Map.entry(CLICK, "minecraft:ui.button.click|1.0|1.0"),
            Map.entry(PAGE_TURN, "minecraft:item.book.page_turn|1.0|1.0"),
            Map.entry(SEARCH, "minecraft:entity.villager.work_cartographer|1.0|1.2"),
            Map.entry(FILTER, "minecraft:block.note_block.hat|1.0|1.2"),
            Map.entry(REFRESH, "minecraft:item.book.page_turn|1.0|1.15"),
            Map.entry(BUY_SUCCESS, "minecraft:entity.experience_orb.pickup|1.0|1.2"),
            Map.entry(FAIL, "minecraft:entity.villager.no|1.0|1.0"),
            Map.entry(EDIT, "minecraft:block.note_block.pling|0.9|1.1"),
            Map.entry(SAVE, "minecraft:entity.experience_orb.pickup|1.0|1.35"),
            Map.entry(PIN, "minecraft:entity.item.pickup|1.0|1.15"),
            Map.entry(REMOVE, "minecraft:entity.item.break|1.0|0.85"),
            Map.entry(CANCEL, "minecraft:ui.button.click|1.0|0.8"),
            Map.entry(LIST_ITEM, "minecraft:entity.villager.work_cartographer|1.0|1.0")
    );

    private QuickBuySounds() {
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

    /**
     * Purchase success: {@code shop.yml} {@code QUICK-BUY.PURCHASE-SOUND} overrides
     * {@code sounds.yml} {@code QUICK_BUY.BUY-SUCCESS} when set.
     */
    public static void playPurchase(Player player, SmpPlugin plugin) {
        if (player == null || plugin == null || plugin.getConfigManager() == null) {
            return;
        }
        String override = null;
        if (plugin.getConfigManager().getShop() != null) {
            override = plugin.getConfigManager().getShop().getString("QUICK-BUY.PURCHASE-SOUND");
        }
        if (override != null && !override.isBlank()) {
            SoundUtils.play(player, override);
            return;
        }
        play(player, plugin, BUY_SUCCESS);
    }
}
