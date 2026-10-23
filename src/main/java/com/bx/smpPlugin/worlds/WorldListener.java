// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.LobbyManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.EnumSet;
import java.util.Set;

public final class WorldListener implements Listener {

    private static final Set<PlayerTeleportEvent.TeleportCause> ESCAPES = EnumSet.of(
            PlayerTeleportEvent.TeleportCause.ENDER_PEARL, PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT,
            PlayerTeleportEvent.TeleportCause.NETHER_PORTAL, PlayerTeleportEvent.TeleportCause.END_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_GATEWAY, PlayerTeleportEvent.TeleportCause.SPECTATE);

    private final SmpPlugin plugin;
    private final WorldSystem worlds;
    private final java.util.Map<java.util.UUID, Location> departures = new java.util.concurrent.ConcurrentHashMap<>();

    public WorldListener(SmpPlugin plugin, WorldSystem worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    // ------------------------------------------------------------------ helpers

    private WorldRules protecting(Location location) {
        WorldRules rules = worlds.rulesFor(location.getWorld());
        return rules != null && worlds.isProtected(location, rules) ? rules : null;
    }

    private static boolean bypass(Player player) {
        return PermissionUtils.has(player, WorldSystem.BYPASS_BUILD);
    }

    private void deny(Player player) {
        String message = plugin.getConfigManager().getWorlds().getString("MESSAGES.PROTECTED",
                "&cYou can't do that here.");
        if (message != null && !message.isBlank()) {
            com.bx.smpPlugin.core.Text.actionBar(player, message);
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // ------------------------------------------------------------------ joining and changing world

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Now, while the join event runs: the server sends the new player's tab entry to the
        // others only after this event and only to those who can see them, so nobody in another
        // world sees them flash in. Visibility only here; the sidebar and command list wait.
        worlds.refreshVisibility(player);
        // Again one tick later, after vanish and the other join handlers have run.
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                worlds.refreshPlayer(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        LobbyManager lobby = plugin.getLobbyManager();
        boolean fromLobby = worlds.isWorld(event.getFrom(), "lobby");
        boolean toLobby = worlds.isWorld(player.getWorld(), "lobby");
        if (lobby != null && fromLobby && !toLobby) {
            // However they left (/lobby survival, the NPC, /spawn, a plugin), the survival
            // inventory saved on the way in comes back. Only the first call finds one.
            lobby.restoreAfterLeaving(player, false);
        } else if (lobby != null && toLobby && !fromLobby && !lobby.hasSavedInventory(player.getUniqueId())
                && !bypass(player)
                && plugin.getConfigManager().getWorlds().getBoolean("WORLDS.LOBBY.SAVE-INVENTORY-ON-ENTRY", true)) {
            // Arrived without going through /lobby (a staff /tp, a plugin): same as /lobby.
            lobby.saveSmpLocation(player, departures.get(player.getUniqueId()));
            lobby.saveAndClearInventory(player);
        }
        departures.remove(player.getUniqueId());
        worlds.refreshPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrossWorldTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to != null && to.getWorld() != null && !to.getWorld().equals(event.getFrom().getWorld())) {
            departures.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        departures.remove(event.getPlayer().getUniqueId());
        SmpArrival.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRespawn(PlayerRespawnEvent event) {
        WorldRules rules = worlds.rulesFor(event.getPlayer().getWorld());
        if (rules == null || !rules.respawnInWorld()) {
            return;
        }
        Location spawn = worlds.spawnLocation(rules.key());
        if (spawn != null) {
            // LOW priority: the FFA and duel systems set their own respawn later and win.
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        WorldRules rules = worlds.rulesFor(event.getEntity().getWorld());
        if (rules == null) {
            return;
        }
        switch (rules.deathInventory()) {
            case "KEEP" -> {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
            case "DROP" -> event.setKeepInventory(false);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ building

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        WorldRules rules = protecting(event.getBlock().getLocation());
        if (rules != null && !rules.blockBreak() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        WorldRules rules = protecting(event.getBlock().getLocation());
        if (rules != null && !rules.blockPlace() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        WorldRules rules = protecting(event.getBlockClicked().getLocation());
        if (rules != null && !rules.blockPlace() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        WorldRules rules = protecting(event.getBlockClicked().getLocation());
        if (rules != null && !rules.blockBreak() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || bypass(event.getPlayer())) {
            return;
        }
        WorldRules rules = protecting(block.getLocation());
        if (rules == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL && !rules.blockBreak()
                && block.getType().name().equals("FARMLAND")) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !rules.containers() && block.getState() instanceof Container) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!(target instanceof Hanging || target instanceof ArmorStand || target instanceof Vehicle)) {
            return;
        }
        WorldRules rules = protecting(target.getLocation());
        if (rules != null && !rules.entityInteract() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        WorldRules rules = protecting(event.getRightClicked().getLocation());
        if (rules != null && !rules.entityInteract() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        WorldRules rules = protecting(event.getEntity().getLocation());
        if (rules == null || rules.blockBreak()) {
            return;
        }
        Player player = attacker(event.getRemover());
        if (player == null || !bypass(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ fighting and damage

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        WorldRules rules = protecting(event.getEntity().getLocation());
        if (rules == null) {
            return;
        }
        if (event.getEntity() instanceof Player victim && !victim.equals(attacker)) {
            if (!rules.pvp()) {
                event.setCancelled(true);
            }
            return;
        }
        if ((event.getEntity() instanceof Hanging || event.getEntity() instanceof ArmorStand)
                && !rules.entityInteract() && !bypass(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event instanceof EntityDamageByEntityEvent) {
            return;
        }
        WorldRules rules = protecting(player.getLocation());
        if (rules == null) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID && (!rules.otherDamage() || !rules.pvp())) {
            // Falling off a protected island (the void lobby or spawn) puts the player back at its
            // spawn instead of killing them and losing their items. In a PvP area (FFA) the void
            // stays deadly: being knocked off is part of the fight.
            event.setCancelled(true);
            Location spawn = worlds.spawnLocation(rules.key());
            if (spawn != null) {
                plugin.getSpigotScheduler().teleport(player, spawn);
            }
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL ? !rules.fallDamage() : !rules.otherDamage()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        WorldRules rules = protecting(player.getLocation());
        if (rules != null && !rules.hunger()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ items

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        WorldRules rules = protecting(event.getPlayer().getLocation());
        if (rules != null && !rules.itemDrop() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        WorldRules rules = protecting(player.getLocation());
        if (rules != null && !rules.itemPickup() && !bypass(player)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ the world itself

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            // Staff, plugins and spawn eggs may always place a mob.
            case CUSTOM, COMMAND, SPAWNER_EGG, DEFAULT -> {
                return;
            }
            default -> {
            }
        }
        WorldRules rules = protecting(event.getLocation());
        if (rules != null && !rules.mobSpawning()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        WorldRules rules = worlds.rulesFor(event.getWorld());
        if (rules != null && rules.lockWeather() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        WorldRules rules = worlds.rulesFor(event.getWorld());
        if (rules != null && rules.lockWeather() && event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ leaving a world

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        WorldRules rules = worlds.rulesFor(event.getFrom().getWorld());
        if (rules != null && !rules.portals() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        WorldRules rules = worlds.rulesFor(event.getFrom().getWorld());
        if (rules != null && !rules.portals()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || to.getWorld().equals(event.getFrom().getWorld())
                || !ESCAPES.contains(event.getCause())) {
            return;
        }
        WorldRules rules = worlds.rulesFor(event.getFrom().getWorld());
        if (rules != null && !rules.leaveByTeleport() && !bypass(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

}
