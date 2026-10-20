package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small animations for chest menus: a slot that spins through items before a reveal, a wave that
 * fills the menu when it opens, and a border that shimmers while it stays open.
 *
 * <p>Every animation runs on the viewer's own scheduler, touches only the slots it owns, and stops
 * by itself as soon as the viewer is looking at something else, so a closed menu costs nothing.
 */
public final class MenuFx {

    private MenuFx() {
    }

    /** True while this player still has the menu open. */
    public static boolean isViewing(Player player, InventoryHolder holder) {
        return player != null && player.isOnline()
                && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    /**
     * Plays a sound by its Bukkit name. For services that also run without a server (in tests the sound registry
     * does not exist); unknown names are skipped.
     */
    public static void sound(Player player, String name, float volume, float pitch) {
        if (player == null) {
            return;
        }
        try {
            Sound sound = Text.sound(name);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (LinkageError noRegistry) {
            // No server, no sounds.
        }
    }

    public static void sound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * Spins one slot through {@code frames} for {@code steps} steps, slowing down at the end like
     * a wheel, with a rising note on each step. {@code done} always runs once, even if the player
     * closes the menu half way, so a prize is never lost to an impatient click.
     */
    public static void spin(SmpPlugin plugin, Player player, InventoryHolder holder, Inventory inventory, int slot,
                            List<ItemStack> frames, int steps, Runnable done) {
        if (frames.isEmpty() || steps <= 0) {
            done.run();
            return;
        }
        AtomicInteger step = new AtomicInteger();
        AtomicInteger wait = new AtomicInteger();
        AtomicReference<BukkitTask> task = new AtomicReference<>();
        task.set(plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            int current = step.get();
            // Ease out: the last few steps wait longer between frames.
            int delay = current < steps - 6 ? 0 : (current - (steps - 6));
            if (wait.getAndIncrement() < delay) {
                return;
            }
            wait.set(0);
            if (current >= steps || !player.isOnline()) {
                cancel(task.get());
                done.run();
                return;
            }
            if (isViewing(player, holder)) {
                inventory.setItem(slot, frames.get(current % frames.size()));
                float pitch = 0.6F + (1.4F * current / Math.max(1, steps));
                sound(player, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, pitch);
            }
            step.incrementAndGet();
        }, 1L, 1L));
    }

    /**
     * Fills a menu with a diagonal wave: items appear from the top-left corner outwards over a
     * few ticks. Slots not in {@code items} are left alone.
     */
    public static void wave(SmpPlugin plugin, Player player, InventoryHolder holder, Inventory inventory,
                            Map<Integer, ItemStack> items, ItemStack cover) {
        List<Integer> slots = new ArrayList<>(items.keySet());
        slots.sort(Comparator.comparingInt(slot -> (slot / 9) + (slot % 9)));
        for (int slot : slots) {
            inventory.setItem(slot, cover);
        }
        int lastWave = slots.isEmpty() ? 0 : (slots.get(slots.size() - 1) / 9) + (slots.get(slots.size() - 1) % 9);
        AtomicInteger wave = new AtomicInteger();
        AtomicReference<BukkitTask> task = new AtomicReference<>();
        task.set(plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            int current = wave.getAndIncrement();
            if (current > lastWave || !isViewing(player, holder)) {
                cancel(task.get());
                return;
            }
            for (int slot : slots) {
                if ((slot / 9) + (slot % 9) == current) {
                    inventory.setItem(slot, items.get(slot));
                }
            }
            if (current % 2 == 0) {
                sound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 0.8F + current * 0.05F);
            }
        }, 1L, 1L));
    }

    /**
     * Cycles the given border slots through the colours while the menu is open. Each slot is one
     * step behind its neighbour, which reads as light running round the frame.
     */
    public static void shimmer(SmpPlugin plugin, Player player, InventoryHolder holder, Inventory inventory,
                               List<Integer> border, List<Material> colours, long periodTicks) {
        if (border.isEmpty() || colours.isEmpty()) {
            return;
        }
        List<ItemStack> panes = colours.stream().map(ItemUtils::createGlassPane).toList();
        AtomicInteger offset = new AtomicInteger();
        AtomicReference<BukkitTask> task = new AtomicReference<>();
        task.set(plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            if (!isViewing(player, holder)) {
                cancel(task.get());
                return;
            }
            int shift = offset.getAndIncrement();
            for (int i = 0; i < border.size(); i++) {
                inventory.setItem(border.get(i), panes.get(Math.floorMod(i + shift, panes.size())));
            }
        }, periodTicks, periodTicks));
    }

    /** The outer ring of a chest menu with the given number of rows. */
    public static List<Integer> borderSlots(int rows) {
        List<Integer> out = new ArrayList<>();
        int size = rows * 9;
        for (int i = 0; i < 9; i++) {
            out.add(i);
        }
        for (int row = 1; row < rows - 1; row++) {
            out.add(row * 9 + 8);
        }
        for (int i = size - 1; i >= size - 9; i--) {
            out.add(i);
        }
        for (int row = rows - 2; row >= 1; row--) {
            out.add(row * 9);
        }
        return out;
    }

    private static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
