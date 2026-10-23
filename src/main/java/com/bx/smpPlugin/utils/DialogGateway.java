// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

public interface DialogGateway {

    boolean openSettings(Player player);

    boolean openRtpQueue(Player player);

    default boolean openHomes(Player player) {
        return false;
    }

    default boolean openTpaPicker(Player player) {
        return false;
    }

    default boolean openTpaRequest(Player target, UUID requester, String requesterName, boolean tpaHere) {
        return false;
    }

    default boolean openAuctionPurchase(Player player, long listingId) {
        return false;
    }

    default boolean openAuctionSell(Player player) {
        return false;
    }

    default boolean openOrdersChooseItem(Player player) {
        return false;
    }

    default boolean openOrdersSearch(Player player, com.bx.smpPlugin.models.OrderSort sort, String categoryFilter, String query) {
        return false;
    }

    // A chat message whose click runs code instead of a command (Paper 1.21.6+ callbacks).
    default boolean sendClickAction(Player viewer, String legacy, String hoverLegacy, java.util.function.Consumer<Player> onClick) {
        return false;
    }

    default boolean openStats(Player player) {
        return false;
    }

    default boolean openStats(Player player, UUID target, String targetName) {
        return false;
    }

    default boolean showNotice(Player player, NoticeSpec notice) {
        return false;
    }

    default boolean showNoticeAnimated(Player player, java.util.List<NoticeSpec> frames, long periodTicks) {
        return false;
    }

    boolean openQuickBuySearch(Player player, String currentQuery, String source);

    boolean openQuickBuySelection(Player player, int targetSlot, String query);

    boolean openQuickBuyEnchantments(Player player, int targetSlot, Material material);

    void forget(UUID playerId);

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
