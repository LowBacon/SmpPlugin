package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.coinflip.MatchState;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Item and layout helpers shared by every CoinFlip menu. Colours follow one palette: emerald for success and
 * selection, crimson for the opposing side and warnings, muted gold for notable values, cyan for navigation and
 * neutral information, on midnight and purple panes.
 */
public final class Gui {

    public static final String GOOD = "&a";
    public static final String BAD = "&c";
    public static final String GOLD = "&6";
    public static final String INFO = "&b";
    public static final String MUTED = "&7";
    public static final String DIM = "&8";
    public static final String VALUE = "&f";

    /** Inner slots of a 54-slot menu framed by a one-slot border: 4 rows of 7. */
    public static final int[] INNER_54 = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private static final int HEAD_CACHE_SIZE = 256;
    private static final Map<UUID, ItemStack> HEADS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, ItemStack> eldest) {
            return size() > HEAD_CACHE_SIZE;
        }
    };

    private Gui() {
    }

    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() ? fallback : material;
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = ItemUtils.createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, Arrays.asList(lore));
    }

    public static ItemStack glow(ItemStack item, boolean glow) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** A player head, cached by UUID so redrawing a page never rebuilds dozens of profiles. */
    public static ItemStack head(UUID player, String name, List<String> lore) {
        ItemStack base;
        synchronized (HEADS) {
            base = HEADS.get(player);
            if (base == null) {
                base = ItemUtils.createPlayerHead(Bukkit.getOfflinePlayer(player), "", List.of());
                HEADS.put(player, base);
            }
        }
        ItemStack head = base.clone();
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(com.bx.smpPlugin.utils.ColorUtils.colorize(name));
            meta.setLore(com.bx.smpPlugin.utils.ColorUtils.colorizeList(lore));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Midnight frame: border panes around the edge, accent panes in the corners and the middle of the top and bottom
     * rows. Readable mode uses one plain pane colour.
     */
    public static void frame(Inventory inventory, Material border, Material accent, boolean readable) {
        int size = inventory.getSize();
        int rows = size / 9;
        ItemStack borderPane = item(readable ? Material.GRAY_STAINED_GLASS_PANE : border, " ");
        ItemStack accentPane = item(readable ? Material.GRAY_STAINED_GLASS_PANE : accent, " ");
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean edge = row == 0 || row == rows - 1 || column == 0 || column == 8;
            if (!edge) {
                continue;
            }
            boolean corner = (row == 0 || row == rows - 1) && (column == 0 || column == 8);
            boolean middle = (row == 0 || row == rows - 1) && (column == 3 || column == 5);
            inventory.setItem(slot, corner || middle ? accentPane : borderPane);
        }
    }

    public static String bullet(boolean readable) {
        return readable ? "&7- " : "&8▪ ";
    }

    public static String line(boolean readable, String label, String value) {
        return bullet(readable) + MUTED + label + ": " + VALUE + value;
    }

    public static String sideColour(CoinSide side) {
        return side == CoinSide.HEADS ? GOLD : BAD;
    }

    public static Material sideMaterial(CoinSide side, String headsMaterial, String tailsMaterial) {
        return side == CoinSide.HEADS ? material(headsMaterial, Material.GOLD_BLOCK) : material(tailsMaterial, Material.REDSTONE_BLOCK);
    }

    /** Short coloured status label. */
    public static String badge(MatchState state) {
        return switch (state) {
            case WAITING -> GOOD + "Waiting for an opponent";
            case CREATING, JOINING -> INFO + "Reserving stakes";
            case LOCKED, RESOLVING -> GOLD + "Flipping";
            case SETTLING -> GOLD + "Paying out";
            case CANCELLING -> MUTED + "Refunding";
            case COMPLETED -> GOOD + "Completed";
            case CANCELLED -> MUTED + "Cancelled";
            case EXPIRED -> MUTED + "Expired";
            case RECOVERY_REQUIRED -> BAD + "Held for staff review";
        };
    }

    public static String duration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m " + (seconds % 60L) + "s";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return hours + "h " + (minutes % 60L) + "m";
        }
        return (hours / 24L) + "d";
    }

    public static List<String> lore(String... lines) {
        return new ArrayList<>(Arrays.asList(lines));
    }

    /** Match summary lines used by listings, confirmations and staff views. */
    public static List<String> matchLore(CoinFlipMatch match, CoinFlipModuleView view, boolean readable, long now) {
        List<String> lore = new ArrayList<>();
        lore.add(line(readable, "Match", "#" + match.id()));
        lore.add(line(readable, "Stake each", view.money(match.stakeCents())));
        lore.add(line(readable, "Pot", view.money(match.potCents())));
        lore.add(line(readable, "Fee", match.feeCents() == 0 ? "none" : view.money(match.feeCents())));
        lore.add(line(readable, "Winner receives", view.money(match.payoutCents())));
        lore.add(line(readable, "Creator picked", match.creatorSide().displayName()));
        if (match.state() == MatchState.WAITING) {
            lore.add(line(readable, "Expires in", duration(match.expiresAtMillis() - now)));
        }
        lore.add(bullet(readable) + badge(match.state()));
        return lore;
    }
}
