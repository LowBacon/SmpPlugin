// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// The open animation every menu shares: the glass frame shows at once and the buttons sweep in
// diagonally from the top left, a few rows per tick, with a soft page sound. Moving between menus
// stays instant; only opening one from the game plays it. MENU-ANIMATIONS in config.yml.
final class MenuReveal {

    private MenuReveal() {
    }

    static void play(SmpPlugin plugin, Player player, BaseMenu menu) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        if (!config.getBoolean("MENU-ANIMATIONS.ENABLED", true)) {
            return;
        }
        int speed = Math.max(1, Math.min(6, config.getInt("MENU-ANIMATIONS.SPEED", 2)));
        Inventory inventory = menu.getInventory();
        Map<Integer, ItemStack> hidden = new LinkedHashMap<>();
        int lastDiagonal = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isFrame(item)) {
                continue;
            }
            hidden.put(slot, item);
            lastDiagonal = Math.max(lastDiagonal, diagonal(slot));
        }
        if (hidden.isEmpty()) {
            return;
        }
        for (int slot : hidden.keySet()) {
            inventory.setItem(slot, null);
        }
        if (config.getBoolean("MENU-ANIMATIONS.SOUND", true)) {
            MenuFx.sound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.45F, 1.25F);
        }
        int version = menu.renderVersion();
        int finalDiagonal = lastDiagonal;
        AtomicInteger reached = new AtomicInteger(-1);
        AtomicReference<BukkitTask> task = new AtomicReference<>();
        task.set(plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            // Rebuilt or closed meanwhile: the menu already shows its real contents.
            if (!MenuFx.isViewing(player, menu) || menu.renderVersion() != version) {
                cancel(task.get());
                return;
            }
            int upTo = reached.addAndGet(speed);
            for (Map.Entry<Integer, ItemStack> entry : hidden.entrySet()) {
                int slot = entry.getKey();
                int diagonal = diagonal(slot);
                // Only fills a slot still left empty: something the menu set since then stays.
                if (diagonal <= upTo && diagonal > upTo - speed && inventory.getItem(slot) == null) {
                    inventory.setItem(slot, entry.getValue());
                }
            }
            if (upTo >= finalDiagonal) {
                cancel(task.get());
            }
        }, 1L, 1L));
    }

    private static int diagonal(int slot) {
        return slot / 9 + slot % 9;
    }

    private static boolean isFrame(ItemStack item) {
        return item.getType().name().endsWith("GLASS_PANE");
    }

    private static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
