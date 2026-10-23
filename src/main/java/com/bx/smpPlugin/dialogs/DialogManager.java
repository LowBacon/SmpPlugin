// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.screens.QuickBuyItemDialog;
import com.bx.smpPlugin.dialogs.screens.RtpQueueDialog;
import com.bx.smpPlugin.dialogs.screens.SettingsDialog;
import com.bx.smpPlugin.dialogs.screens.NoticeDialog;
import com.bx.smpPlugin.dialogs.screens.StatsDialog;
import com.bx.smpPlugin.utils.NoticeSpec;
import com.bx.smpPlugin.utils.DialogGateway;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class DialogManager implements DialogGateway {

    private final SmpPlugin plugin;
    private final DialogConfig config;
    private final com.bx.smpPlugin.dialogs.screens.HomesDialog homes;
    private final DialogSession.Store sessions = new DialogSession.Store();

    private final SettingsDialog settings;
    private final RtpQueueDialog rtpQueue;
    private final QuickBuyItemDialog quickBuyItem;
    private final StatsDialog stats;
    private final NoticeDialog notice;
    private final com.bx.smpPlugin.dialogs.screens.TpaDialog tpa;
    private final com.bx.smpPlugin.dialogs.screens.AuctionDialog auction;
    private final List<DialogScreen> screens;

    public static DialogManager createIfSupported(SmpPlugin plugin) {
        if (!DialogSupport.isAvailable()) {
            return null;
        }
        DialogManager manager = new DialogManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(new DialogListener(plugin, manager), plugin);
        return manager;
    }

    public DialogManager(SmpPlugin plugin) {
        this.plugin = plugin;
        this.config = new DialogConfig(plugin);
        this.settings = new SettingsDialog(plugin, config, sessions);
        this.rtpQueue = new RtpQueueDialog(plugin, config, sessions);
        this.quickBuyItem = new QuickBuyItemDialog(plugin, config, sessions);
        // Ordered so the cheap exact-match screens answer before the prefix-matching ones.
        this.homes = new com.bx.smpPlugin.dialogs.screens.HomesDialog(plugin, config, sessions);
        this.stats = new StatsDialog(plugin, config, sessions);
        this.notice = new NoticeDialog(plugin, config, sessions);
        this.tpa = new com.bx.smpPlugin.dialogs.screens.TpaDialog(plugin, config, sessions);
        this.auction = new com.bx.smpPlugin.dialogs.screens.AuctionDialog(plugin, config, sessions);
        com.bx.smpPlugin.dialogs.screens.OrdersDialog orders =
                new com.bx.smpPlugin.dialogs.screens.OrdersDialog(plugin, config, sessions);
        this.screens = List.of(settings, rtpQueue, quickBuyItem, tpa, auction, orders, homes, stats, notice);
    }

    @SuppressWarnings("unchecked")
    public <T extends DialogScreen> T getScreen(Class<T> screenClass) {
        for (DialogScreen screen : screens) {
            if (screenClass.isInstance(screen)) {
                return (T) screen;
            }
        }
        return null;
    }

    public DialogConfig getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public boolean openSettings(Player player) {
        return config.isScreenEnabled("SETTINGS") && settings.open(player);
    }

    @Override
    public boolean openRtpQueue(Player player) {
        return config.isScreenEnabled("RTP-QUEUE") && rtpQueue.open(player);
    }

    @Override
    public boolean openTpaPicker(Player player) {
        return config.isScreenEnabled("TPA") && tpa.openPicker(player);
    }

    @Override
    public boolean openTpaRequest(Player target, UUID requester, String requesterName, boolean tpaHere) {
        return config.isScreenEnabled("TPA-REQUEST") && tpa.openRequest(target, requester, requesterName, tpaHere);
    }

    @Override
    public boolean openAuctionPurchase(Player player, long listingId) {
        return config.isScreenEnabled("AUCTION-HOUSE") && auction.openPurchase(player, listingId);
    }

    @Override
    public boolean openOrdersChooseItem(Player player) {
        com.bx.smpPlugin.dialogs.screens.OrdersDialog orders = getScreen(com.bx.smpPlugin.dialogs.screens.OrdersDialog.class);
        return orders != null && orders.openChooseItem(player);
    }

    @Override
    public boolean openOrdersSearch(Player player, com.bx.smpPlugin.models.OrderSort sort, String categoryFilter, String query) {
        com.bx.smpPlugin.dialogs.screens.OrdersDialog orders = getScreen(com.bx.smpPlugin.dialogs.screens.OrdersDialog.class);
        return orders != null && orders.openSearch(player, sort, categoryFilter, query);
    }

    @Override
    public boolean sendClickAction(Player viewer, String legacy, String hoverLegacy, java.util.function.Consumer<Player> onClick) {
        return ChatClickMessages.sendInstantAction(plugin, viewer, legacy, hoverLegacy, onClick);
    }

    @Override
    public boolean openAuctionSell(Player player) {
        return config.isScreenEnabled("AUCTION-HOUSE") && auction.openSell(player);
    }

    @Override
    public boolean openHomes(Player player) {
        return config.isScreenEnabled("HOMES") && homes.open(player);
    }

    @Override
    public boolean openStats(Player player) {
        return config.isScreenEnabled("STATS") && stats.open(player);
    }

    @Override
    public boolean openStats(Player player, UUID target, String targetName) {
        return config.isScreenEnabled("STATS") && stats.openFor(player, target, targetName);
    }

    @Override
    public boolean showNotice(Player player, NoticeSpec spec) {
        return config.isEnabled() && notice.show(player, spec);
    }

    @Override
    public boolean showNoticeAnimated(Player player, List<NoticeSpec> frames, long periodTicks) {
        return config.isEnabled() && notice.animate(player, frames, periodTicks);
    }

    @Override
    public boolean openQuickBuySearch(Player player, String currentQuery, String source) {
        return config.isEnabled() && quickBuyItem.openAuctionSearch(player, currentQuery, source);
    }

    @Override
    public boolean openQuickBuySelection(Player player, int targetSlot, String query) {
        return config.isEnabled() && quickBuyItem.openSelection(player, targetSlot, query);
    }

    @Override
    public boolean openQuickBuyEnchantments(Player player, int targetSlot, Material material) {
        return config.isEnabled() && quickBuyItem.openEnchantments(player, targetSlot, material);
    }

    public void handle(Player player, Key key, DialogResponseView response) {
        if (player == null || !DialogActions.isOurs(key) || !config.isEnabled()) {
            return;
        }
        String action = key.value();
        String commandId = DialogActions.argument(action, DialogActions.RUN_COMMAND);
        if (commandId != null) {
            runCommandButton(player, commandId);
            return;
        }
        for (DialogScreen screen : screens) {
            if (screen.handle(player, action, response)) {
                return;
            }
        }
    }

    private void runCommandButton(Player player, String commandId) {
        String command = sessions.get(player.getUniqueId()).commandFor(commandId);
        if (command == null || command.isBlank()) {
            return;
        }
        String cmdStr = command.trim();
        String trimmedCmd = cmdStr.startsWith("/") ? cmdStr.substring(1) : cmdStr;
        DialogSupport.close(player);
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                plugin.getSpigotScheduler().dispatchPlayerCommand(player, trimmedCmd);
            }
        }, 1L);
    }

    @Override
    public void forget(UUID playerId) {
        sessions.remove(playerId);
        notice.forget(playerId);
        DialogSupport.forget(playerId);
        if (plugin.getPlayerSettingToggles() != null) {
            plugin.getPlayerSettingToggles().dropAuctionCache(playerId);
        }
    }

    public void clear() {
        sessions.clear();
    }
}
