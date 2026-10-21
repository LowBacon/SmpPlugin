package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menu items for the gambling screens. Player heads are cached, so redrawing never rebuilds profiles. */
final class Icons {

    private static final int HEAD_CACHE_SIZE = 256;
    private static final Map<UUID, ItemStack> HEADS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, ItemStack> eldest) {
            return size() > HEAD_CACHE_SIZE;
        }
    };

    private Icons() {
    }

    static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = ItemUtils.createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, Arrays.asList(lore));
    }

    static ItemStack glow(ItemStack item, boolean glow) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null);
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack head(UUID player, String name, List<String> lore) {
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
            meta.setDisplayName(ColorUtils.colorize(name));
            meta.setLore(ColorUtils.colorizeList(lore));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }
}
