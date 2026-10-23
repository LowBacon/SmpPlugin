// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.dialog.DialogLike;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    public static boolean isAvailable() {
        return AVAILABLE;
    }

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

    public static void forget(UUID playerId) {
        if (playerId != null) {
            OPEN_DIALOGS.remove(playerId);
        }
    }

    public static boolean isClosingInventoryForDialog(UUID playerId) {
        return playerId != null && CLOSING_FOR_DIALOG.contains(playerId);
    }

    static boolean shouldCloseOpenInventory(InventoryType type) {
        return type != null && shouldCloseOpenInventory(type.name());
    }

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
