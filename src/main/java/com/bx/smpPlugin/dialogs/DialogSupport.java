package com.bx.smpPlugin.dialogs;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.dialog.DialogLike;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime gate for the 1.21.6+ dialog screens.
 *
 * <p>The plugin compiles against the Spigot API, so every reference to
 * {@code io.papermc.paper.*} lives inside this package and nothing outside it may touch a
 * dialog class without asking here first. On Spigot, or on a Paper build older than 1.21.6,
 * {@link #isAvailable()} returns false and callers fall back to the chest menus. Because the
 * check happens before any dialog class is named, the JVM never has to load one.
 */
public final class DialogSupport {

    private static final boolean AVAILABLE = probe();
    private static final Set<UUID> OPEN_DIALOGS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CLOSING_FOR_DIALOG = ConcurrentHashMap.newKeySet();

    private DialogSupport() {
    }

    private static boolean probe() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
            Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction");
            Class.forName("io.papermc.paper.event.player.PlayerCustomClickEvent");
            Class.forName("net.kyori.adventure.dialog.DialogLike");
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /** True when this server can render dialogs. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Sends a dialog to a player. Returns false when the server cannot render dialogs, so the
     * caller can open its chest menu instead.
     */
    public static boolean show(Player player, DialogLike dialog) {
        if (!AVAILABLE || player == null || dialog == null || !player.isOnline()) {
            return false;
        }
        if (!(player instanceof Audience audience)) {
            return false;
        }
        try {
            // Always drop a real chest first. Player screens (CRAFTING/CREATIVE) are skipped, so
            // replacing an already-open dialog does not closeInventory or recenter the cursor.
            closeContainerIfNeeded(player);
            audience.showDialog(dialog);
            OPEN_DIALOGS.add(player.getUniqueId());
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Closes whatever dialog the player currently has open. */
    public static void close(Player player) {
        if (player != null) {
            OPEN_DIALOGS.remove(player.getUniqueId());
        }
        if (!AVAILABLE || player == null || !(player instanceof Audience audience)) {
            return;
        }
        try {
            audience.closeDialog();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    /** Drops the "this player has a dialog open" mark, used on quit. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            OPEN_DIALOGS.remove(playerId);
        }
    }

    /**
     * True while {@link #show} is closing a chest so a dialog can replace it.
     * Menu ESC-back must ignore that close or Your Orders reopens under the dialog.
     */
    public static boolean isClosingInventoryForDialog(UUID playerId) {
        return playerId != null && CLOSING_FOR_DIALOG.contains(playerId);
    }

    /**
     * True when the open view is a real container that would sit under the dialog.
     *
     * <p>The player's own screen is {@code CRAFTING} in survival and {@code CREATIVE} in creative.
     * Closing either of those is a trip back to the world, which recenters the cursor.
     */
    static boolean shouldCloseOpenInventory(InventoryType type) {
        return type != null && shouldCloseOpenInventory(type.name());
    }

    /** Same rule as {@link #shouldCloseOpenInventory(InventoryType)}, keyed by the enum name. */
    static boolean shouldCloseOpenInventory(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        return !"CRAFTING".equalsIgnoreCase(typeName) && !"CREATIVE".equalsIgnoreCase(typeName);
    }

    private static void closeContainerIfNeeded(Player player) {
        try {
            if (player.getOpenInventory() != null && shouldCloseOpenInventory(player.getOpenInventory().getType())) {
                CLOSING_FOR_DIALOG.add(player.getUniqueId());
                try {
                    player.closeInventory();
                } finally {
                    CLOSING_FOR_DIALOG.remove(player.getUniqueId());
                }
            }
        } catch (Throwable ignored) {
            CLOSING_FOR_DIALOG.remove(player.getUniqueId());
        }
    }
}
