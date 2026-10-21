package com.bx.smpPlugin.world;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Real Minecraft world borders around the spawn and the lobby, set up with /border.
 *
 * <p>Each zone (spawn, lobby) is a square with the vanilla border settings, saved in borders.yml and
 * applied again at every start. A zone works in one of two ways:
 * <ul>
 *   <li><b>PLAYER</b> (default): a player standing inside the square is sent their own border with
 *       {@link Player#setWorldBorder}, so they see the real blue wall and their client stops them at
 *       it. The server also stops them walking, riding, flying or pearling out, which a modified
 *       client could otherwise ignore. Teleport commands (/rtp, /home, /warp...) still take them out,
 *       and the border goes away as soon as they are outside. Players elsewhere in the same world are
 *       not affected, so this is safe when the spawn is in the survival world.</li>
 *   <li><b>WORLD</b>: the world's own border is changed, so the whole world ends at the edge for
 *       everyone. Meant for a world that only holds the lobby or spawn. The world's previous border is
 *       remembered and put back when the zone is turned off or switched to PLAYER. Using it on the
 *       server's main world needs an extra "confirm".</li>
 * </ul>
 * Staff with {@code smpplugin.bypass.border}, and players in spectator mode, are never held back.
 */
public final class BorderManager implements Listener {

    public static final String BYPASS_PERMISSION = "smpplugin.bypass.border";
    public static final List<String> ZONES = List.of("spawn", "lobby");
    private static final Set<String> HELD_TELEPORTS = Set.of("ENDER_PEARL", "CHORUS_FRUIT", "CONSUMABLE_EFFECT");

    private record Active(String zone, int version, WorldBorder border) {
    }

    private final SmpPlugin plugin;
    private final Map<String, BorderZone> zones = new LinkedHashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private int version;

    public BorderManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ settings

    private FileConfiguration config() {
        return plugin.getConfigManager().getBorders();
    }

    /** Reads borders.yml and applies it to worlds and online players. */
    public void load() {
        zones.clear();
        FileConfiguration config = config();
        for (String name : ZONES) {
            ConfigurationSection section = config == null ? null : config.getConfigurationSection(name.toUpperCase(Locale.ROOT));
            zones.put(name, read(name, section));
        }
        version++;
        for (BorderZone zone : zones.values()) {
            applyWorldBorder(zone);
        }
        refreshAll();
    }

    private static BorderZone read(String name, ConfigurationSection section) {
        if (section == null) {
            return new BorderZone(name, false, "", 0D, 0D, 200D, BorderZone.Mode.PLAYER, 5, 15, 0.2D, 5D);
        }
        return new BorderZone(name,
                section.getBoolean("ENABLED", false),
                section.getString("WORLD", ""),
                section.getDouble("CENTER-X", 0D),
                section.getDouble("CENTER-Z", 0D),
                section.getDouble("SIZE", 200D),
                BorderZone.Mode.parse(section.getString("MODE"), BorderZone.Mode.PLAYER),
                section.getInt("WARNING-DISTANCE", 5),
                section.getInt("WARNING-TIME", 15),
                section.getDouble("DAMAGE-AMOUNT", 0.2D),
                section.getDouble("DAMAGE-BUFFER", 5D));
    }

    public BorderZone zone(String name) {
        return zones.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Stores a changed zone in borders.yml and applies it. A zone that moves from WORLD to PLAYER mode,
     * or is turned off, gets its world's old border back first.
     */
    public boolean update(BorderZone updated) {
        BorderZone previous = zones.get(updated.name());
        if (previous != null && previous.mode() == BorderZone.Mode.WORLD && previous.active()
                && (!updated.active() || updated.mode() != BorderZone.Mode.WORLD || !updated.world().equals(previous.world()))) {
            restoreWorldBorder(previous);
        }
        FileConfiguration config = config();
        if (config == null) {
            return false;
        }
        String path = updated.name().toUpperCase(Locale.ROOT);
        config.set(path + ".ENABLED", updated.enabled());
        config.set(path + ".WORLD", updated.world());
        config.set(path + ".CENTER-X", updated.centerX());
        config.set(path + ".CENTER-Z", updated.centerZ());
        config.set(path + ".SIZE", updated.size());
        config.set(path + ".MODE", updated.mode().name());
        config.set(path + ".WARNING-DISTANCE", updated.warningDistance());
        config.set(path + ".WARNING-TIME", updated.warningTime());
        config.set(path + ".DAMAGE-AMOUNT", updated.damageAmount());
        config.set(path + ".DAMAGE-BUFFER", updated.damageBuffer());
        boolean saved = plugin.getConfigManager().saveBorders();
        zones.put(updated.name(), updated);
        version++;
        applyWorldBorder(updated);
        refreshAll();
        return saved;
    }

    /** True when the zone's world is the server's main world (the first one loaded). */
    public static boolean isMainWorld(String world) {
        List<World> worlds = Bukkit.getWorlds();
        return !worlds.isEmpty() && worlds.getFirst().getName().equals(world);
    }

    /** How many players are held by a zone's border right now. */
    public int playersInside(String zone) {
        int count = 0;
        for (Active entry : active.values()) {
            if (entry.zone().equals(zone)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ WORLD mode

    @SuppressWarnings("deprecation")
    private void applyWorldBorder(BorderZone zone) {
        if (!zone.active() || zone.mode() != BorderZone.Mode.WORLD) {
            return;
        }
        World world = Bukkit.getWorld(zone.world());
        if (world == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        rememberWorldBorder(zone, border);
        if (border.getCenter().getX() != zone.centerX() || border.getCenter().getZ() != zone.centerZ()) {
            border.setCenter(zone.centerX(), zone.centerZ());
        }
        if (border.getSize() != zone.size()) {
            border.setSize(zone.size());
        }
        configure(border, zone);
    }

    @SuppressWarnings("deprecation")
    private static void configure(WorldBorder border, BorderZone zone) {
        if (border.getWarningDistance() != zone.warningDistance()) {
            border.setWarningDistance(zone.warningDistance());
        }
        if (border.getWarningTime() != zone.warningTime()) {
            border.setWarningTime(zone.warningTime());
        }
        if (border.getDamageAmount() != zone.damageAmount()) {
            border.setDamageAmount(zone.damageAmount());
        }
        if (border.getDamageBuffer() != zone.damageBuffer()) {
            border.setDamageBuffer(zone.damageBuffer());
        }
    }

    /** Saves the world's own border the first time a zone takes it over, so it can be put back. */
    @SuppressWarnings("deprecation")
    private void rememberWorldBorder(BorderZone zone, WorldBorder border) {
        FileConfiguration config = config();
        String path = zone.name().toUpperCase(Locale.ROOT) + ".PREVIOUS-WORLD-BORDER";
        if (config == null || config.isConfigurationSection(path)) {
            return;
        }
        config.set(path + ".WORLD", zone.world());
        config.set(path + ".CENTER-X", border.getCenter().getX());
        config.set(path + ".CENTER-Z", border.getCenter().getZ());
        config.set(path + ".SIZE", border.getSize());
        config.set(path + ".WARNING-DISTANCE", border.getWarningDistance());
        config.set(path + ".WARNING-TIME", border.getWarningTime());
        config.set(path + ".DAMAGE-AMOUNT", border.getDamageAmount());
        config.set(path + ".DAMAGE-BUFFER", border.getDamageBuffer());
        plugin.getConfigManager().saveBorders();
    }

    @SuppressWarnings("deprecation")
    private void restoreWorldBorder(BorderZone zone) {
        FileConfiguration config = config();
        String path = zone.name().toUpperCase(Locale.ROOT) + ".PREVIOUS-WORLD-BORDER";
        ConfigurationSection saved = config == null ? null : config.getConfigurationSection(path);
        if (saved == null) {
            return;
        }
        World world = Bukkit.getWorld(saved.getString("WORLD", zone.world()));
        if (world == null) {
            // Keep it for when the world is loaded again.
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(saved.getDouble("CENTER-X", 0D), saved.getDouble("CENTER-Z", 0D));
        border.setSize(BorderZone.clampSize(saved.getDouble("SIZE", BorderZone.MAX_SIZE)));
        border.setWarningDistance(saved.getInt("WARNING-DISTANCE", 5));
        border.setWarningTime(saved.getInt("WARNING-TIME", 15));
        border.setDamageAmount(saved.getDouble("DAMAGE-AMOUNT", 0.2D));
        border.setDamageBuffer(saved.getDouble("DAMAGE-BUFFER", 5D));
        config.set(path, null);
        plugin.getConfigManager().saveBorders();
    }

    // ------------------------------------------------------------------ PLAYER mode

    private boolean exempt(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR || PermissionUtils.has(player, BYPASS_PERMISSION);
    }

    /** The active zone (either mode) the location is inside, or null. */
    private BorderZone zoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        for (BorderZone zone : zones.values()) {
            if (zone.active() && zone.world().equals(world) && zone.contains(location.getX(), location.getZ())) {
                return zone;
            }
        }
        return null;
    }

    private boolean anyActive() {
        for (BorderZone zone : zones.values()) {
            if (zone.active()) {
                return true;
            }
        }
        return false;
    }

    /** Gives or takes away the player's own border to match where they stand. */
    public void refresh(Player player) {
        if (!player.isOnline()) {
            return;
        }
        BorderZone wanted = exempt(player) ? null : zoneAt(player.getLocation());
        if (wanted != null && wanted.mode() != BorderZone.Mode.PLAYER) {
            wanted = null;
        }
        Active current = active.get(player.getUniqueId());
        if (wanted == null) {
            if (current != null) {
                active.remove(player.getUniqueId());
                player.setWorldBorder(null);
            }
            return;
        }
        if (current != null && current.zone().equals(wanted.name()) && current.version() == version) {
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(wanted.centerX(), wanted.centerZ());
        border.setSize(wanted.size());
        configure(border, wanted);
        player.setWorldBorder(border);
        active.put(player.getUniqueId(), new Active(wanted.name(), version, border));
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getSpigotScheduler().runEntity(player, () -> refresh(player));
        }
    }

    /** Removes every player border. Called while the plugin stops; nothing is scheduled. */
    public void shutdown() {
        for (UUID uuid : List.copyOf(active.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setWorldBorder(null);
            }
        }
        active.clear();
        lastWarning.clear();
    }

    private void warn(Player player, BorderZone zone) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < 2_000L) {
            return;
        }
        lastWarning.put(player.getUniqueId(), now);
        Text.actionBar(player, Text.get("BORDER.EDGE", "&cThis is the edge of the {zone}. &7Use a teleport to leave.",
                "{zone}", zone.name()));
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getSpigotScheduler().runEntityLater(player, () -> refresh(player), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
        lastWarning.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getSpigotScheduler().runEntityLater(player, () -> refresh(player), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getSpigotScheduler().runEntityLater(player, () -> refresh(player), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (BorderZone zone : zones.values()) {
            if (zone.world().equals(event.getWorld().getName())) {
                applyWorldBorder(zone);
            }
        }
    }

    /** Ender pearls and chorus fruit cannot carry a player out; teleport commands can. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!anyActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (HELD_TELEPORTS.contains(event.getCause().name()) && !exempt(player)) {
            BorderZone from = zoneAt(event.getFrom());
            Location to = event.getTo();
            if (from != null && to != null && (to.getWorld() != event.getFrom().getWorld() || !from.contains(to.getX(), to.getZ()))) {
                event.setCancelled(true);
                warn(player, from);
                return;
            }
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> refresh(player), 1L);
    }

    /** Server-side check, so a client that ignores the border still cannot walk, fly or swim out. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) || !anyActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }
        BorderZone zone = zoneAt(from);
        if (zone != null && !zone.contains(to.getX(), to.getZ())) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            warn(player, zone);
            return;
        }
        // Walked into a PLAYER-mode area: give them the border now.
        Active current = active.get(player.getUniqueId());
        BorderZone entered = zoneAt(to);
        boolean shouldHave = entered != null && entered.mode() == BorderZone.Mode.PLAYER;
        if (shouldHave != (current != null)) {
            refresh(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!anyActive()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        BorderZone zone = zoneAt(from);
        if (zone == null || zone.contains(to.getX(), to.getZ())) {
            return;
        }
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && !exempt(player)) {
                event.getVehicle().eject();
                double[] inside = zone.clamp(from.getX(), from.getZ(), 1D);
                Location safe = new Location(from.getWorld(), inside[0], from.getY(), inside[1], player.getLocation().getYaw(),
                        player.getLocation().getPitch());
                plugin.getSpigotScheduler().runEntity(player, () -> plugin.getSpigotScheduler().teleport(player, safe));
                warn(player, zone);
                return;
            }
        }
    }
}
