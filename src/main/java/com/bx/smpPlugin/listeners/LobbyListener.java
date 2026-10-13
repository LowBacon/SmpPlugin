package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

/**
 * The lobby's event handling: teleports players into the lobby on join, saves their SMP
 * location on the way out, enforces the square border via a direct move/teleport clamp, and
 * blocks every world-modifying interaction inside the lobby per lobby.yml's PROTECTION section.
 *
 * Runs alongside, not instead of, the existing maintenance-mode / maintenance-location-restore
 * logic in {@link PlayerJoinQuitListener} - see onJoin() below for how the two are kept from
 * fighting over where a joining player ends up.
 */
public final class LobbyListener implements Listener {

    private final SmpPlugin plugin;

    public LobbyListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Join / quit
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().getLobby().getBoolean("JOIN.ENABLED", true)) {
            return;
        }
        if (!plugin.getLobbyManager().hasLobbyLocation()) {
            return; // Not configured yet - avoid warning-spamming the console on every join.
        }

        Player player = event.getPlayer();

        // Maintenance mode (with the player lacking bypass) already redirected them via
        // PlayerJoinQuitListener - don't also send them to the lobby on top of that.
        if (plugin.getMaintenanceManager() != null && plugin.getMaintenanceManager().isMaintenanceActive()) {
            String bypassPerm = plugin.getConfigManager().getNetwork()
                    .getString("MAINTENANCE.BYPASS_PERMISSION", "smpplugin.ADMIN.MAINTENANCE.BYPASS");
            if (!player.hasPermission(bypassPerm)) {
                return;
            }
        } else if (plugin.getMaintenanceManager() != null) {
            // Maintenance isn't active right now, but if this player has a pending
            // maintenance-location restore queued (from PlayerJoinQuitListener), let that
            // finish landing them first rather than racing it with a lobby teleport.
            String localServerId = plugin.getConfigManager().getNetwork().getString("NETWORK.LOCAL_SERVER_ID", "local");
            if (plugin.getDatabaseManager().getMaintenanceLocation(player.getUniqueId(), localServerId) != null) {
                return;
            }
        }

        long delayTicks = plugin.getConfigManager().getLobby().getLong("JOIN.TELEPORT-DELAY-TICKS", 2);
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                // Clear inventory before sending to lobby since sendToLobby will save it
                // We don't want to save the empty inventory, so clear it after the check
                plugin.getLobbyManager().sendToLobby(player, true);
            }
        }, Math.max(1, delayTicks));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getLobbyManager().saveSmpLocationIfOutsideLobby(player);
        
        // Clean up saved inventory on logout to prevent inventory duplication
        // Only clear if they're currently in the lobby, otherwise they'll lose their items
        if (plugin.getLobbyManager().isInLobby(player)) {
            plugin.getLobbyManager().clearSavedInventory(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------
    // Border enforcement - see LobbyManager's Javadoc for why this is a movement clamp rather
    // than a vanilla WorldBorder (Player#setWorldBorder is Paper-only; this project compiles
    // against spigot-api).
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        double dx = event.getFrom().getX() - to.getX();
        double dz = event.getFrom().getZ() - to.getZ();
        if (dx == 0 && dz == 0) {
            return; // Pure look-around / vertical-only change - nothing to check.
        }
        Player player = event.getPlayer();
        if (bypassesBorder(player)) {
            return;
        }

        Location clamped = plugin.getLobbyManager().clampIfOutside(to);
        if (clamped != null) {
            event.setTo(clamped);
            plugin.getLobbyManager().warnIfNearEdge(player, clamped);
        } else {
            plugin.getLobbyManager().warnIfNearEdge(player, to);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Player player = event.getPlayer();
        if (to == null || bypassesBorder(player)) {
            return;
        }
        if (plugin.getLobbyManager().isInLobby(player) || player.getWorld().equals(to.getWorld())) {
            Location clamped = plugin.getLobbyManager().clampIfOutside(to);
            if (clamped != null) {
                event.setTo(clamped);
            }
        }
    }

    private boolean bypassesBorder(Player player) {
        return player.hasPermission("smpplugin.bypass.lobby") || player.hasPermission("smpplugin.bypass.lobby.border");
    }

    // ------------------------------------------------------------------
    // Protection - all gated by lobby.yml's PROTECTION section and lobbyManager.isInLobby(),
    // and skipped entirely for smpplugin.bypass.lobby.
    // ------------------------------------------------------------------

    private boolean protectedFor(Player player) {
        return !player.hasPermission("smpplugin.bypass.lobby") && plugin.getLobbyManager().isInLobby(player);
    }

    private void deny(Player player) {
        plugin.getLobbyManager().sendProtectionDeniedMessage(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protectedFor(event.getPlayer()) || !plugin.getLobbyManager().isProtectionEnabled("BLOCK-PLACE")) return;
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectedFor(event.getPlayer()) || !plugin.getLobbyManager().isProtectionEnabled("BLOCK-BREAK")) return;
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || !protectedFor(event.getPlayer())) {
            return;
        }
        Material type = block.getType();
        var lobbyManager = plugin.getLobbyManager();

        if (isContainer(type) && lobbyManager.isProtectionEnabled("CONTAINER-INTERACT")) {
            event.setCancelled(true);
            deny(event.getPlayer());
        } else if (isRedstoneComponent(type) && lobbyManager.isProtectionEnabled("REDSTONE-INTERACT")) {
            event.setCancelled(true);
            deny(event.getPlayer());
        } else if (isDoor(type) && lobbyManager.isProtectionEnabled("DOOR-INTERACT")) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!protectedFor(event.getPlayer()) || !plugin.getLobbyManager().isProtectionEnabled("BUCKET-USE")) return;
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!protectedFor(event.getPlayer()) || !plugin.getLobbyManager().isProtectionEnabled("BUCKET-USE")) return;
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!plugin.getLobbyManager().isProtectionEnabled("FIRE-IGNITE")) return;
        if (!plugin.getLobbyManager().isLocationInLobby(event.getBlock().getLocation())) return;
        if (event.getPlayer() != null && event.getPlayer().hasPermission("smpplugin.bypass.lobby")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !protectedFor(damager)) {
            return;
        }
        boolean targetIsPlayer = event.getEntity() instanceof Player;
        String key = targetIsPlayer ? "PLAYER-DAMAGE" : "ENTITY-DAMAGE";
        if (plugin.getLobbyManager().isProtectionEnabled(key)) {
            event.setCancelled(true);
            deny(damager);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectedFor(player)) return;
        if (!plugin.getLobbyManager().isProtectionEnabled("ITEM-PICKUP")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!protectedFor(event.getPlayer()) || !plugin.getLobbyManager().isProtectionEnabled("ITEM-DROP")) return;
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.getLobbyManager().isProtectionEnabled("HANGING-MODIFY")) return;
        if (!plugin.getLobbyManager().isLocationInLobby(event.getEntity().getLocation())) return;
        if (event instanceof HangingBreakByEntityEvent byEntity && byEntity.getRemover() instanceof Player p
                && p.hasPermission("smpplugin.bypass.lobby")) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand) && !(entity instanceof Hanging)) return;
        if (!protectedFor(event.getPlayer()) || !plugin.getLobbyManager().isProtectionEnabled("ARMOR-STAND-MODIFY")) return;
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player) || !protectedFor(player)) return;
        if (!plugin.getLobbyManager().isProtectionEnabled("VEHICLE-USE")) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!plugin.getLobbyManager().isProtectionEnabled("REDSTONE-INTERACT")) return;
        if (!plugin.getLobbyManager().isLocationInLobby(event.getBlock().getLocation())) return;
        event.setNewCurrent(event.getOldCurrent());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectedFor(player)) return;
        if (!plugin.getLobbyManager().isProtectionEnabled("HUNGER-LOSS")) return;
        if (event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectedFor(player)) return;
        var lobbyManager = plugin.getLobbyManager();
        boolean cancel = switch (event.getCause()) {
            case LIGHTNING -> lobbyManager.isProtectionEnabled("WEATHER-DAMAGE");
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> lobbyManager.isProtectionEnabled("EXPLOSION-DAMAGE");
            case LAVA -> lobbyManager.isProtectionEnabled("LAVA-DAMAGE");
            case FIRE, FIRE_TICK, HOT_FLOOR -> lobbyManager.isProtectionEnabled("FIRE-DAMAGE");
            case PROJECTILE -> lobbyManager.isProtectionEnabled("PROJECTILE-DAMAGE");
            default -> false;
        };
        if (cancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!plugin.getLobbyManager().isProtectionEnabled("MOB-GRIEFING")) return;
        if (!plugin.getLobbyManager().isLocationInLobby(event.getLocation())) return;
        event.blockList().clear();
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (!plugin.getLobbyManager().isProtectionEnabled("MOB-GRIEFING")) return;
        if (!plugin.getLobbyManager().isLocationInLobby(event.getBlock().getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getLobbyManager().isProtectionEnabled("MOB-SPAWNING")) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!plugin.getLobbyManager().isLocationInLobby(event.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!protectedFor(player) || !plugin.getLobbyManager().isProtectionEnabled("DROP-ITEMS-ON-DEATH")) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        // No per-player visual border object to sync in this project (see LobbyManager's
        // Javadoc) - nothing to do here beyond what the move/teleport handlers already cover.
    }


    private boolean isContainer(Material material) {
        String name = material.name();
        return name.contains("CHEST") || name.contains("BARREL") || name.contains("FURNACE")
                || name.contains("SHULKER_BOX") || name.contains("HOPPER") || name.contains("DISPENSER")
                || name.contains("DROPPER") || name.contains("BREWING_STAND") || name.contains("BEACON")
                || name.contains("ANVIL") || name.contains("ENDER_CHEST") || name.contains("LECTERN")
                || name.contains("CAMPFIRE");
    }

    private boolean isRedstoneComponent(Material material) {
        String name = material.name();
        return name.contains("LEVER") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE")
                || name.contains("TRIPWIRE") || name.contains("REPEATER") || name.contains("COMPARATOR")
                || name.contains("DAYLIGHT_DETECTOR");
    }

    private boolean isDoor(Material material) {
        String name = material.name();
        return name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("FENCE_GATE");
    }
}
