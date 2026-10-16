package com.bx.smpPlugin.utils;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * The pop-up dialog screens (Paper and Purpur 1.21.6+), seen from the rest of the plugin.
 *
 * <p>The implementation lives in {@code com.bx.smpPlugin.dialogs}, which uses the Paper dialog API.
 * The Spigot jar does not contain that package at all, so nothing outside it may name a dialog
 * class directly. Callers ask {@link SmpPlugin#getDialogManager()}: null means "use the chest
 * menu", and every open method returns false when the screen is turned off in dialog.yml.</p>
 */
public interface DialogGateway {

    boolean openSettings(Player player);

    boolean openRtpQueue(Player player);

    /** The homes grid; false when dialogs or that screen are off, so the chest menu is used. */
    default boolean openHomes(Player player) {
        return false;
    }

    /** The item search used by the Auction House and Quick Buy. */
    boolean openQuickBuySearch(Player player, String currentQuery, String source);

    boolean openQuickBuySelection(Player player, int targetSlot, String query);

    boolean openQuickBuyEnchantments(Player player, int targetSlot, Material material);

    /** Drops per-player state when a player leaves. */
    void forget(UUID playerId);

    /**
     * Creates the dialog screens when this jar and server support them, registering their
     * listener. Returns null on the Spigot jar, on Spigot servers and on servers without dialogs.
     */
    static DialogGateway create(SmpPlugin plugin) {
        try {
            Class<?> type = Class.forName("com.bx.smpPlugin.dialogs.DialogManager");
            Object manager = type.getMethod("createIfSupported", SmpPlugin.class).invoke(null, plugin);
            return (DialogGateway) manager;
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Dialog screens could not start; chest menus will be used.", error);
            return null;
        }
    }
}
