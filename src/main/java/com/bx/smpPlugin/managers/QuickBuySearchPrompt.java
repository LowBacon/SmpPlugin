package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.QuickBuyItemSelectMenu;
import com.bx.smpPlugin.menus.QuickBuyMenu;
import com.bx.smpPlugin.menus.QuickBuySounds;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat prompt for Quick Buy search when a Bedrock player has no Floodgate form.
 * A sign editor on Bedrock closes itself and then refuses every container until rejoin.
 */
public final class QuickBuySearchPrompt implements Listener {

    private enum Kind {
        LISTING,
        PICKER
    }

    private record Pending(Kind kind, int slot, QuickBuyMenu.Filter filter, boolean editMode, String query) {
    }

    private final SmpPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public QuickBuySearchPrompt(SmpPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void askListing(Player player, QuickBuyMenu.Filter filter, boolean editMode, String currentQuery) {
        if (player == null) {
            return;
        }
        QuickBuyMenu.Filter safeFilter = filter == null ? QuickBuyMenu.Filter.DEFAULT : filter;
        pending.put(player.getUniqueId(), new Pending(
                Kind.LISTING,
                0,
                safeFilter,
                editMode,
                currentQuery == null ? "" : currentQuery
        ));
        ask(player);
    }

    public void askPicker(Player player, int slot, String currentQuery) {
        if (player == null) {
            return;
        }
        pending.put(player.getUniqueId(), new Pending(
                Kind.PICKER,
                slot,
                QuickBuyMenu.Filter.DEFAULT,
                false,
                currentQuery == null ? "" : currentQuery
        ));
        ask(player);
    }

    public boolean hasPending(UUID playerId) {
        return playerId != null && pending.containsKey(playerId);
    }

    public void handle(Player player, String raw) {
        if (player == null) {
            return;
        }
        Pending request = pending.get(player.getUniqueId());
        if (request == null) {
            return;
        }
        String input = raw == null ? "" : raw.trim();
        if (input.equalsIgnoreCase("cancel")) {
            pending.remove(player.getUniqueId());
            player.sendMessage(ColorUtils.toComponent(text(
                    "QUICK_BUY.BEDROCK.CHAT_SEARCH_CANCELLED",
                    "&7Search cancelled."
            )));
            reopen(player, request, request.query);
            return;
        }
        if (input.isBlank()) {
            player.sendMessage(ColorUtils.toComponent(text(
                    "QUICK_BUY.BEDROCK.CHAT_SEARCH",
                    "&7Type an item name in chat. Type &ccancel &7to go back."
            )));
            return;
        }
        pending.remove(player.getUniqueId());
        QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
        reopen(player, request, input);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void ask(Player player) {
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline() || !pending.containsKey(player.getUniqueId())) {
                return;
            }
            try {
                player.closeInventory();
            } catch (Throwable ignored) {
            }
            player.sendMessage(ColorUtils.toComponent(text(
                    "QUICK_BUY.BEDROCK.CHAT_SEARCH",
                    "&7Type an item name in chat. Type &ccancel &7to go back."
            )));
        }, 1L);
    }

    private void reopen(Player player, Pending request, String query) {
        String safeQuery = query == null ? "" : query;
        if (request.kind == Kind.PICKER) {
            new QuickBuyItemSelectMenu(plugin, request.slot, 0, safeQuery).open(player);
            return;
        }
        new QuickBuyMenu(plugin, request.filter, request.editMode, safeQuery).open(player);
    }

    private String text(String path, String fallback) {
        if (plugin.getLanguageManager() == null) {
            return fallback;
        }
        String value = plugin.getLanguageManager().text(path, null, fallback);
        return value == null ? fallback : value;
    }
}
