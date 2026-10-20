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

    /** The /stats player list; false when dialogs or that screen are off, so the chest menu is used. */
    default boolean openStats(Player player) {
        return false;
    }

    /** One player's stat sheet, for /stats &lt;player&gt;. The caller has already resolved the name. */
    default boolean openStats(Player player, UUID target, String targetName) {
        return false;
    }

    /** A simple pop-up (title, icon, lines, buttons). False when dialogs are unavailable. */
    default boolean showNotice(Player player, NoticeSpec notice) {
        return false;
    }

    /**
     * Plays a short animation by showing each frame in turn, {@code periodTicks} apart; the last
     * frame stays open. False when dialogs are unavailable, so the caller can use chat instead.
     */
    default boolean showNoticeAnimated(Player player, java.util.List<NoticeSpec> frames, long periodTicks) {
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
