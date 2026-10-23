// SmpPlugin by LowBacon
package com.bx.smpPlugin.core;

import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MenuStyle {

    public static final String ACCENT = "&#00E5FF";
    public static final String GOOD = "&#3DDC84";
    public static final String WARN = "&#FFC857";
    public static final String BAD = "&#FF5370";
    public static final String TEXT = "&#D6DEEB";
    public static final String MUTED = "&7";
    public static final String DIM = "&8";
    public static final String DIVIDER = "&8&m                              ";

    private MenuStyle() {
    }

    public static ItemStack pane(Material material) {
        return ItemUtils.createGlassPane(material);
    }

    public static void frame(Inventory inventory, Material accent) {
        int rows = inventory.getSize() / 9;
        ItemStack dark = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack bright = pane(accent);
        for (int slot : MenuFx.borderSlots(rows)) {
            boolean corner = slot == 0 || slot == 8 || slot == inventory.getSize() - 9 || slot == inventory.getSize() - 1;
            inventory.setItem(slot, corner ? bright : dark);
        }
    }

    public static ItemStack close() {
        return ItemUtils.createItem(Material.BARRIER, BAD + "&lClose", List.of(MUTED + "Close this menu."));
    }

    public static ItemStack back(String to) {
        return ItemUtils.createItem(Material.ARROW, TEXT + "&lBack", List.of(MUTED + "Back to " + to + "."));
    }

    public static String action(String text) {
        return ACCENT + "▶ " + TEXT + text;
    }

    public static List<String> lore(List<String> body, String... actions) {
        List<String> lore = new ArrayList<>(body);
        if (actions.length > 0) {
            lore.add("");
            for (String action : actions) {
                lore.add(action(action));
            }
        }
        return lore;
    }
}
