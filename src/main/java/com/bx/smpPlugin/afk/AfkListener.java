// SmpPlugin by LowBacon
package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public class AfkListener implements Listener {

    private final SmpPlugin plugin;

    public AfkListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    private AfkManager afk() {
        return plugin.getAfkManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        afk().track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        afk().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        afk().handleMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        afk().recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // /away sets the status itself; counting it as activity would undo it at once.
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (message.equals("/away") || message.startsWith("/away ")) {
            return;
        }
        afk().recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        afk().recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            afk().recordActivity(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        afk().recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        afk().recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            afk().recordActivity(player);
        }
    }
}
