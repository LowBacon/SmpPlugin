package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The AFK system.
 *
 * <ul>
 *   <li><b>Status.</b> A player who stops giving input for {@code STATUS.IDLE-SECONDS} is AFK; {@code /away}
 *   sets it by hand. Any real input clears it. AFK players are tagged in the tab list and pause shard
 *   regions.</li>
 *   <li><b>Zones.</b> Areas where waiting around pays shards on a timer, with a countdown on screen.
 *   {@code /afk} takes players there, staff build them with {@code /afkzone}.</li>
 *   <li><b>Idle actions.</b> Optionally move long-idle players into a zone, or kick them.</li>
 * </ul>
 *
 * Maps are concurrent: chat arrives off the main thread and Folia ticks players on region threads.
 */
public class AfkManager {

    public enum Display { BOSSBAR, ACTIONBAR, NONE }

    private static final double ROTATION_EPSILON = 0.5D;

    /** Where one player stands with respect to the zones. */
    private static final class ZoneState {
        String zoneId;
        int seconds;
        long earned;
        BossBar bar;
    }

    private final SmpPlugin plugin;
    private final AfkZoneStore store;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();
    private final Set<UUID> kickWarned = ConcurrentHashMap.newKeySet();
    private final Set<UUID> movedToZone = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ZoneState> zoneStates = new ConcurrentHashMap<>();
    private final Map<UUID, Location[]> selections = new ConcurrentHashMap<>();
    private final Map<UUID, Long> outlineUntil = new ConcurrentHashMap<>();

    public AfkManager(SmpPlugin plugin) {
        this.plugin = plugin;
        this.store = new AfkZoneStore(plugin);
        store.load();
    }

    public void reload() {
        plugin.getConfigManager().reloadAfk();
        store.load();
        for (Map.Entry<UUID, ZoneState> entry : zoneStates.entrySet()) {
            ZoneState state = entry.getValue();
            if (state.bar != null && display() != Display.BOSSBAR) {
                state.bar.removeAll();
                state.bar = null;
            }
        }
    }

    FileConfiguration config() {
        return plugin.getConfigManager().getAfk();
    }

    public boolean isEnabled() {
        return plugin.getFeatureManager() == null || plugin.getFeatureManager().isEnabled(FeatureManager.Feature.AFK);
    }

    // ── Activity ──────────────────────────────────────────────────────────────

    public void track(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
        afkSince.remove(uuid);
        kickWarned.remove(uuid);
        movedToZone.remove(uuid);
        selections.remove(uuid);
        outlineUntil.remove(uuid);
        ZoneState state = zoneStates.remove(uuid);
        if (state != null && state.bar != null) {
            state.bar.removeAll();
        }
    }

    /** Real input from the player: resets the idle timer and ends the AFK status. */
    public void recordActivity(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        kickWarned.remove(uuid);
        movedToZone.remove(uuid);
        if (afkSince.containsKey(uuid)) {
            plugin.getSpigotScheduler().runEntity(player, () -> clearAfk(player, true));
        }
    }

    /**
     * Head turns always count. Walking counts unless the player is only being carried (in a vehicle,
     * or drifting in water), so AFK pools and water streams cannot fake activity.
     */
    public void handleMove(Player player, Location from, Location to) {
        if (from == null || to == null) {
            return;
        }
        boolean turned = Math.abs(angleDelta(from.getYaw(), to.getYaw())) > ROTATION_EPSILON
                || Math.abs(from.getPitch() - to.getPitch()) > ROTATION_EPSILON;
        if (turned) {
            recordActivity(player);
            return;
        }
        boolean changedBlock = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (!changedBlock) {
            return;
        }
        if (config().getBoolean("DETECTION.IGNORE-PASSIVE-MOVEMENT", true) && isPassiveMovement(player)) {
            return;
        }
        recordActivity(player);
    }

    static double angleDelta(float a, float b) {
        double delta = (b - a) % 360.0D;
        if (delta > 180.0D) {
            delta -= 360.0D;
        } else if (delta < -180.0D) {
            delta += 360.0D;
        }
        return delta;
    }

    private static boolean isPassiveMovement(Player player) {
        return player.isInsideVehicle() || (player.isInWater() && !player.isSwimming() && !player.isSprinting());
    }

    /** Seconds since the player last gave real input, or Long.MAX_VALUE if unknown. */
    public long getIdleSeconds(UUID uuid) {
        Long last = lastActivity.get(uuid);
        return last == null ? Long.MAX_VALUE : Math.max(0L, (System.currentTimeMillis() - last) / 1000L);
    }

    /** True when the player did something within the last {@code windowSeconds}; a window of 0 always passes. */
    public boolean hasRecentActivity(UUID uuid, int windowSeconds) {
        if (windowSeconds <= 0) {
            return true;
        }
        Long last = lastActivity.get(uuid);
        return last != null && System.currentTimeMillis() - last <= windowSeconds * 1000L;
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public boolean isAfk(UUID uuid) {
        return uuid != null && afkSince.containsKey(uuid);
    }

    public long getAfkSeconds(UUID uuid) {
        Long since = afkSince.get(uuid);
        return since == null ? 0L : Math.max(0L, (System.currentTimeMillis() - since) / 1000L);
    }

    /** Shown as AFK in the tab list: the status, or standing in an AFK zone. */
    public boolean isShownAsAfk(Player player) {
        return player != null && (isAfk(player.getUniqueId()) || getCurrentZone(player) != null);
    }

    public String getTabTag(Player player) {
        return isShownAsAfk(player) ? config().getString("STATUS.TAB-TAG", "&7[AFK] ") : "";
    }

    public boolean greysOutNames() {
        return config().getBoolean("STATUS.GREY-NAME", true);
    }

    /** {@code /away}. Returns the new state. */
    public boolean toggleAfk(Player player) {
        if (isAfk(player.getUniqueId())) {
            clearAfk(player, true);
            lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
            return false;
        }
        setAfk(player);
        // Typing the command must not count as coming back.
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    public void setAfk(Player player) {
        if (afkSince.putIfAbsent(player.getUniqueId(), System.currentTimeMillis()) != null) {
            return;
        }
        announce(player, "NOW-AFK", "&7* &f{player} &7is now AFK.", "NOW-AFK-SELF", "&7You are now AFK.", 0L);
        refreshTab(player);
    }

    public void clearAfk(Player player, boolean announce) {
        UUID uuid = player.getUniqueId();
        Long since = afkSince.remove(uuid);
        if (since == null) {
            return;
        }
        if (announce) {
            long seconds = Math.max(0L, (System.currentTimeMillis() - since) / 1000L);
            announce(player, "NO-LONGER-AFK", "&7* &f{player} &7is back. &8(AFK for {time})",
                    "NO-LONGER-AFK-SELF", "&7Welcome back! You were AFK for &f{time}&7.", seconds);
        }
        refreshTab(player);
    }

    private void announce(Player player, String broadcastKey, String broadcastDefault,
                          String selfKey, String selfDefault, long seconds) {
        String time = NumberUtils.formatCountdown(seconds);
        boolean vanished = plugin.getStaffModeManager() != null && plugin.getStaffModeManager().isVanished(player.getUniqueId());
        if (config().getBoolean("STATUS.BROADCAST", true) && !vanished) {
            String message = message(broadcastKey, broadcastDefault).replace("{player}", player.getName()).replace("{time}", time);
            if (!message.isBlank()) {
                String colored = ColorUtils.toComponent(message);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(colored);
                }
            }
            return;
        }
        send(player, message(selfKey, selfDefault).replace("{player}", player.getName()).replace("{time}", time));
    }

    private void refreshTab(Player player) {
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().updateTablistName(player);
        }
    }

    // ── Zones ─────────────────────────────────────────────────────────────────

    public Collection<AfkZone> getZones() {
        return store.all();
    }

    public AfkZone getZone(String id) {
        return store.get(id);
    }

    public boolean saveZone(AfkZone zone) {
        return store.put(zone);
    }

    public boolean deleteZone(String id) {
        return store.remove(id);
    }

    public static boolean isValidZoneId(String id) {
        return AfkZoneStore.isValidId(AfkZoneStore.normalizeId(id));
    }

    /** The zone whose box contains this location, if any. */
    public AfkZone zoneAt(Location location) {
        for (AfkZone zone : store.all()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    /** The zone the player was in at the last tick (cheap, used by the tab list and placeholders). */
    public AfkZone getCurrentZone(Player player) {
        ZoneState state = zoneStates.get(player.getUniqueId());
        return state == null || state.zoneId == null ? null : store.get(state.zoneId);
    }

    public int countPlayersIn(AfkZone zone) {
        int count = 0;
        for (ZoneState state : zoneStates.values()) {
            if (zone.id().equals(state.zoneId)) {
                count++;
            }
        }
        return count;
    }

    public long getEarnedThisVisit(UUID uuid) {
        ZoneState state = zoneStates.get(uuid);
        return state == null || state.zoneId == null ? 0L : state.earned;
    }

    public long getSecondsToNextReward(Player player) {
        ZoneState state = zoneStates.get(player.getUniqueId());
        AfkZone zone = getCurrentZone(player);
        return zone == null ? 0L : Math.max(0, zone.rewardSeconds() - state.seconds);
    }

    /** Zones a player can be sent to, ones that pay first, then nearest in the same world. */
    public List<AfkZone> destinationsFor(Player player) {
        List<AfkZone> zones = new ArrayList<>();
        for (AfkZone zone : store.all()) {
            if (zone.spawnLocation() != null) {
                zones.add(zone);
            }
        }
        Location here = player == null ? null : player.getLocation();
        zones.sort(Comparator
                .comparing((AfkZone zone) -> !zone.paysRewards())
                .thenComparingDouble(zone -> distance(here, zone.spawnLocation())));
        return zones;
    }

    private static double distance(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return Double.MAX_VALUE;
        }
        return from.distanceSquared(to);
    }

    /** Teleports straight away, used for idle players and portals. */
    public void teleport(Player player, AfkZone zone, String message) {
        Location spawn = zone == null ? null : zone.spawnLocation();
        if (spawn == null) {
            return;
        }
        plugin.getSpigotScheduler().teleport(player, spawn).thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && message != null && !message.isBlank()) {
                plugin.getSpigotScheduler().runEntity(player, () -> send(player, message.replace("{zone}", zone.displayName())));
            }
        });
    }

    /** {@code /afk}: goes through the normal teleport warm-up. */
    public void teleportWithWarmup(Player player, AfkZone zone) {
        Location spawn = zone.spawnLocation();
        if (spawn == null) {
            send(player, message("ZONE-UNAVAILABLE", "&cThat AFK zone's world is not loaded."));
            return;
        }
        plugin.getTeleportManager().queue(player, spawn, "AFK",
                done -> send(done, message("TELEPORTED", "&aTeleported to &f{zone}&a.").replace("{zone}", zone.displayName())));
    }

    // ── Admin selection ───────────────────────────────────────────────────────

    public void setCorner(Player player, int index, Location location) {
        selections.computeIfAbsent(player.getUniqueId(), ignored -> new Location[2])[index] = location.clone();
    }

    /** Both corners in one world, or null. */
    public AfkZone.Region getSelection(Player player) {
        Location[] corners = selections.get(player.getUniqueId());
        if (corners == null || corners[0] == null || corners[1] == null
                || corners[0].getWorld() == null || !corners[0].getWorld().equals(corners[1].getWorld())) {
            return null;
        }
        return AfkZone.Region.ofCorners(corners[0], corners[1]);
    }

    public void showOutline(Player player, long millis) {
        outlineUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    // ── Ticking (once a second per player, on the player's thread) ────────────

    public void tick(Player player) {
        if (!isEnabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long idle = getIdleSeconds(uuid);

        int idleSeconds = config().getInt("STATUS.IDLE-SECONDS", 300);
        if (idleSeconds > 0 && idle != Long.MAX_VALUE && idle >= idleSeconds && !isAfk(uuid)
                && !PermissionUtils.has(player, "smpplugin.afk.statusbypass")) {
            setAfk(player);
        }

        tickZone(player);
        tickIdleActions(player, idle);
        drawOutlines(player);
    }

    private void tickZone(Player player) {
        UUID uuid = player.getUniqueId();
        boolean canEarn = !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
        AfkZone zone = canEarn ? zoneAt(player.getLocation()) : null;
        ZoneState state = zoneStates.computeIfAbsent(uuid, ignored -> new ZoneState());

        String previous = state.zoneId;
        String current = zone == null ? null : zone.id();
        if (previous != null && !previous.equals(current)) {
            AfkZone left = store.get(previous);
            leaveZone(player, state, left);
        }
        if (zone == null) {
            return;
        }
        if (!zone.id().equals(previous)) {
            state.zoneId = zone.id();
            state.seconds = 0;
            state.earned = 0L;
            send(player, zoneMessage("ENTER-ZONE",
                    "&aYou entered &f{zone}&a. &7Earning &b{amount} shard(s) &7every &f{interval}&7.", zone));
            refreshTab(player);
        }
        if (!zone.paysRewards()) {
            return;
        }

        state.seconds++;
        if (state.seconds >= zone.rewardSeconds()) {
            state.seconds = 0;
            reward(player, zone, state);
        }
        showCountdown(player, zone, state);
    }

    private void leaveZone(Player player, ZoneState state, AfkZone zone) {
        state.zoneId = null;
        state.seconds = 0;
        if (state.bar != null) {
            state.bar.removeAll();
            state.bar = null;
        }
        if (display() == Display.ACTIONBAR) {
            PlayerSettingUtils.clearActionBar(player);
        }
        if (zone != null) {
            send(player, zoneMessage("LEAVE-ZONE", "&cYou left &f{zone}&c. &7Shard rewards paused.", zone));
        }
        refreshTab(player);
    }

    private void reward(Player player, AfkZone zone, ZoneState state) {
        long multiplier = config().getBoolean("REWARD.USE-SHARD-BOOSTER", true) && plugin.getShardManager() != null
                ? Math.max(1, plugin.getShardManager().getMultiplier(player.getUniqueId())) : 1L;
        long shards = zone.rewardAmount() * multiplier;
        plugin.getShardManager().giveShards(player, shards, false, com.bx.smpPlugin.models.EconomyReason.SHARD_AFK);
        state.earned += shards;

        PlayerData data = plugin.getPlayerDataManager().get(player);
        String total = String.valueOf(data == null ? 0L : data.getShards());
        String title = config().getString("REWARD.TITLE", "&b&l+{shards} Shard");
        String subtitle = config().getString("REWARD.SUBTITLE", "&7You now have &b{total} &7shards");
        if (!title.isBlank() || !subtitle.isBlank()) {
            player.sendTitle(
                    ColorUtils.toComponent(fillReward(title, zone, shards, total, state)),
                    ColorUtils.toComponent(fillReward(subtitle, zone, shards, total, state)),
                    5, 30, 10);
        }
        send(player, fillReward(config().getString("REWARD.CHAT", ""), zone, shards, total, state));
        String sound = config().getString("REWARD.SOUND", "minecraft:entity.experience_orb.pickup|0.8|1.4");
        if (sound != null && !sound.isBlank()) {
            SoundUtils.play(player, sound);
        }
    }

    private String fillReward(String text, AfkZone zone, long shards, String total, ZoneState state) {
        return zoneText(text, zone)
                .replace("{shards}", String.valueOf(shards))
                .replace("{total}", total)
                .replace("{earned}", String.valueOf(state.earned));
    }

    private void showCountdown(Player player, AfkZone zone, ZoneState state) {
        long remaining = Math.max(0, zone.rewardSeconds() - state.seconds);
        String time = NumberUtils.formatCountdown(remaining);
        switch (display()) {
            case BOSSBAR -> {
                String title = ColorUtils.toComponent(zoneText(config().getString("DISPLAY.BOSSBAR-TITLE",
                                "&d&l{zone} &8» &fnext shard in &b{time} &8| &7earned &b{earned}"), zone)
                        .replace("{time}", time).replace("{earned}", String.valueOf(state.earned)));
                if (state.bar == null) {
                    state.bar = Bukkit.createBossBar(title, barColor(), BarStyle.SEGMENTED_10);
                    state.bar.addPlayer(player);
                } else {
                    state.bar.setTitle(title);
                }
                state.bar.setProgress(Math.max(0D, Math.min(1D, state.seconds / (double) zone.rewardSeconds())));
            }
            case ACTIONBAR -> PlayerSettingUtils.sendActionBar(plugin, player,
                    zoneText(config().getString("DISPLAY.ACTIONBAR", "&dNext shard in &f{time}"), zone).replace("{time}", time));
            case NONE -> {
            }
        }
    }

    private void tickIdleActions(Player player, long idle) {
        if (idle == Long.MAX_VALUE) {
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean inZone = getCurrentZone(player) != null;

        int sendAfter = config().getInt("IDLE-ACTIONS.SEND-TO-ZONE-SECONDS", 0);
        if (sendAfter > 0 && idle >= sendAfter && !inZone && movedToZone.add(uuid)
                && plugin.getCombatManager() != null && !plugin.getCombatManager().isInCombat(uuid)) {
            List<AfkZone> destinations = destinationsFor(player);
            if (!destinations.isEmpty() && destinations.getFirst().paysRewards()) {
                teleport(player, destinations.getFirst(),
                        message("SENT-TO-ZONE", "&7You were idle, so you were moved to &f{zone}&7."));
            }
        }

        int kickAfter = config().getInt("IDLE-ACTIONS.KICK-SECONDS", 0);
        if (kickAfter <= 0 || inZone || PermissionUtils.has(player, "smpplugin.afk.kickbypass")
                || Bukkit.getOnlinePlayers().size() < config().getInt("IDLE-ACTIONS.KICK-MIN-PLAYERS", 0)) {
            return;
        }
        if (idle >= kickAfter) {
            kickWarned.remove(uuid);
            player.kickPlayer(ColorUtils.toComponent(message("KICK", "&cYou were kicked for being AFK too long.")));
            return;
        }
        int warnBefore = Math.max(0, config().getInt("IDLE-ACTIONS.KICK-WARNING-SECONDS", 60));
        if (warnBefore > 0 && idle >= kickAfter - warnBefore && kickWarned.add(uuid)) {
            send(player, message("KICK-WARNING", "&cYou will be kicked for being AFK in &f{time}&c. Move to stay online.")
                    .replace("{time}", NumberUtils.formatCountdown(kickAfter - idle)));
        }
    }

    /**
     * Outlines zone edges with particles only the viewer sees: near the edge of a zone when particles
     * are on, and the whole nearby edge for 30 seconds after {@code /afkzone show}.
     */
    private void drawOutlines(Player player) {
        Long until = outlineUntil.get(player.getUniqueId());
        boolean forced = until != null && until > System.currentTimeMillis();
        if (!forced && !config().getBoolean("DISPLAY.PARTICLES", true)) {
            return;
        }
        Particle particle = particle();
        if (particle == null) {
            return;
        }
        Location location = player.getLocation();
        double near = 8D;
        for (AfkZone zone : store.all()) {
            AfkZone.Region box = zone.region();
            if (box == null || player.getWorld() == null || !player.getWorld().getName().equals(box.world())) {
                continue;
            }
            double x = location.getX();
            double z = location.getZ();
            boolean nearBox = x >= box.minX() - near && x <= box.maxX() + near && z >= box.minZ() - near && z <= box.maxZ() + near;
            double edgeDistance = Math.min(Math.min(Math.abs(x - box.minX()), Math.abs(x - box.maxX())),
                    Math.min(Math.abs(z - box.minZ()), Math.abs(z - box.maxZ())));
            if (!forced && (!nearBox || edgeDistance > near)) {
                continue;
            }
            double span = forced ? 24D : 10D;
            double y = Math.max(box.minY(), Math.min(box.maxY() - 2D, location.getY()));
            for (double offset = -span; offset <= span; offset += 1D) {
                double alongX = x + offset;
                double alongZ = z + offset;
                if (alongX >= box.minX() && alongX <= box.maxX()) {
                    edge(player, particle, alongX, y, box.minZ());
                    edge(player, particle, alongX, y, box.maxZ());
                }
                if (alongZ >= box.minZ() && alongZ <= box.maxZ()) {
                    edge(player, particle, box.minX(), y, alongZ);
                    edge(player, particle, box.maxX(), y, alongZ);
                }
            }
        }
    }

    private static void edge(Player player, Particle particle, double x, double y, double z) {
        player.spawnParticle(particle, x, y + 0.2D, z, 1, 0D, 0D, 0D, 0D);
        player.spawnParticle(particle, x, y + 1.6D, z, 1, 0D, 0D, 0D, 0D);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Display display() {
        try {
            return Display.valueOf(config().getString("DISPLAY.MODE", "BOSSBAR").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Display.BOSSBAR;
        }
    }

    private BarColor barColor() {
        try {
            return BarColor.valueOf(config().getString("DISPLAY.BOSSBAR-COLOR", "PURPLE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BarColor.PURPLE;
        }
    }

    private Particle particle() {
        try {
            return Particle.valueOf(config().getString("DISPLAY.PARTICLE", "HAPPY_VILLAGER").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String message(String key, String fallback) {
        String value = config().getString("MESSAGES." + key, fallback);
        return value == null ? "" : value;
    }

    String zoneText(String text, AfkZone zone) {
        return (text == null ? "" : text)
                .replace("{zone}", zone.displayName())
                .replace("{amount}", String.valueOf(zone.rewardAmount()))
                .replace("{interval}", NumberUtils.formatCountdown(zone.rewardSeconds()));
    }

    private String zoneMessage(String key, String fallback, AfkZone zone) {
        if (!zone.paysRewards() && key.equals("ENTER-ZONE")) {
            return "";
        }
        return zoneText(message(key, fallback), zone);
    }

    static void send(Player player, String message) {
        if (message != null && !message.isBlank()) {
            player.sendMessage(ColorUtils.toComponent(message));
        }
    }

    public void shutdown() {
        for (ZoneState state : zoneStates.values()) {
            if (state.bar != null) {
                state.bar.removeAll();
                state.bar = null;
            }
        }
    }
}
