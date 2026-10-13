package com.bx.smpPlugin.anticheat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Feeds server events into the checks. Every handler runs on the main thread, ignores events other
 * plugins already cancelled, and keeps per-event work small (a handful of block lookups at most).
 */
public final class AntiCheatListener implements Listener {

    private static final Set<Material> ICE = Set.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);
    private static final Set<Material> SPECIAL_SUPPORT = Set.of(Material.COBWEB, Material.POWDER_SNOW,
            Material.SCAFFOLDING, Material.HONEY_BLOCK, Material.BUBBLE_COLUMN, Material.SWEET_BERRY_BUSH);
    private static final Set<Material> CAVE_EXPOSURE = Set.of(Material.CAVE_AIR, Material.WATER, Material.LAVA,
            Material.BUBBLE_COLUMN);
    private static final Set<Material> PISTON_PARTS = Set.of(Material.PISTON, Material.STICKY_PISTON,
            Material.MOVING_PISTON, Material.PISTON_HEAD);
    /** Paper reports whether a hit was critical; Spigot does not, so the check falls back to vanilla's rule. */
    private static final Method IS_CRITICAL = findIsCritical();

    private final AntiCheatManager manager;

    public AntiCheatListener(AntiCheatManager manager) {
        this.manager = manager;
    }

    // ── Lifecycle and exemption triggers ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerState state = manager.state(player);
        state.resetMovement(player.getLocation());
        state.lastSafe = player.getLocation();
        manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("JOIN"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        PlayerState state = manager.state(player);
        state.resetMovement(to);
        if (to != null) {
            state.lastSafe = to.clone();
        }
        manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("TELEPORT"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerState state = manager.state(event.getPlayer());
        state.resetMovement(event.getRespawnLocation());
        state.lastSafe = event.getRespawnLocation().clone();
        manager.exempt(event.getPlayer(), CheckCategory.MOVEMENT, manager.config().exemptionMillis("RESPAWN"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PlayerState state = manager.state(player);
        state.resetMovement(player.getLocation());
        state.lastSafe = player.getLocation();
        manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("TELEPORT"));
    }

    /** Knockback, explosions, wind charges, mace wind burst and spear lunge all arrive as velocity. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Vector velocity = event.getVelocity();
        double speed = velocity.length();
        long duration = manager.config().exemptionMillis("VELOCITY-BASE")
                + (long) (manager.config().exemptionMillis("VELOCITY-PER-BLOCK") * speed * 4D);
        Player player = event.getPlayer();
        manager.exempt(player, CheckCategory.MOVEMENT, duration);

        PlayerState state = manager.state(player);
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        if (horizontal >= manager.config().settings(CheckType.VELOCITY).param("MIN-VELOCITY", 0.3D)) {
            state.knockback.start(velocity.getX(), velocity.getZ());
            state.knockbackMillis = System.currentTimeMillis();
        } else {
            state.knockback.stop();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("DAMAGE"));
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                manager.state(player).groundSpoofedThisFall = false;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        manager.exempt(event.getPlayer(), CheckCategory.MOVEMENT, manager.config().exemptionMillis("FLIGHT-CHANGE"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        manager.exempt(event.getPlayer(), CheckCategory.MOVEMENT, manager.config().exemptionMillis("GAMEMODE-CHANGE"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            manager.state(player).resetMovement(player.getLocation());
            manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("VEHICLE-EXIT"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && !event.isGliding()) {
            manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("GLIDE-END"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent event) {
        manager.exempt(event.getPlayer(), CheckCategory.MOVEMENT, manager.config().exemptionMillis("RIPTIDE"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerState state = manager.state(player);
        long now = System.currentTimeMillis();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            state.lastRightClickMillis = now;
        }
        if (isSpear(event.getItem() == null ? null : event.getItem().getType())) {
            markSpear(player, state, now);
        }
    }

    // ── Movement: TIMER, FLY, SPEED, NOFALL, STEP, JESUS, and knockback tracking ───────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        PlayerState state = manager.state(player);

        // Hitbox history for lag-compensated reach checks against this player.
        Location current = player.getLocation();
        state.positions.record(now, player.getBoundingBox().clone()
                .shift(to.getX() - current.getX(), to.getY() - current.getY(), to.getZ() - current.getZ()));

        if (!manager.isActive()) {
            return;
        }

        double dy = to.getY() - from.getY();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        Support support = support(player, to);
        if (!support.supported()) {
            state.lastAirMillis = now;
        }
        if (support.bouncy()) {
            state.lastBounceMillis = now;
        }
        if (support.inLiquid()) {
            state.lastLiquidMillis = now;
        }
        trackCriticalBaseline(state, support, to);

        if (movementIgnored(player)) {
            rebaseline(state, to, support, now, dy);
            state.timer.reset();
            state.knockback.stop();
            return;
        }

        Location setback = checkTimer(player, state, from, now);
        checkKnockback(player, state, support, to, dx, dz, now);
        checkInventoryMove(player, state, support, horizontal, now);

        boolean flyChecked = manager.shouldCheck(player, CheckType.FLY, now);
        if (!flyChecked) {
            // Exempt windows (knockback, teleports...) move the baseline so a long fall afterwards is not "rising".
            state.groundY = Math.min(state.groundY, to.getY());
            if (state.exemptions.isExempt(CheckType.FLY, now)) {
                state.groundY = to.getY();
                state.airTicks = 0;
                state.hoverTicks = 0;
                state.slowAirTicks = 0;
            }
        }

        if (setback == null) {
            setback = checkStep(player, state, support, to, dy, now);
        }
        if (setback == null) {
            setback = checkNoFall(player, state, support, to, dy, now);
        }
        if (setback == null) {
            setback = checkWaterWalk(player, state, support, to, dy, horizontal, now);
        }

        if (support.supported()) {
            state.airTicks = 0;
            state.hoverTicks = 0;
            state.slowAirTicks = 0;
            state.groundY = to.getY();
            state.lastSupportedMillis = now;
            if (support.onIce()) {
                state.lastIceMillis = now;
            }
            if (support.onSlime()) {
                state.lastSlimeMillis = now;
            }
            if (now - state.lastMovementFlagMillis > 1_000L && isSafe(to)) {
                state.lastSafe = to.clone();
            }
        } else if (flyChecked && setback == null) {
            setback = checkFly(player, state, to, dy, now);
        }

        if (setback == null && horizontal > 0D && manager.shouldCheck(player, CheckType.SPEED, now)) {
            setback = checkSpeed(player, state, horizontal, support, now);
        }

        state.lastDy = dy;
        state.wasSupported = support.supported();
        state.lastHorizontal = horizontal;
        state.lastMoveMillis = now;
        state.lastMoveOnSolidGround = support.solid() && !support.onIce() && !support.inLiquid();

        if (setback != null) {
            Location target = setback.clone();
            target.setYaw(to.getYaw());
            target.setPitch(to.getPitch());
            event.setTo(target);
            state.resetMovement(target);
            state.lastMovementFlagMillis = now;
        }
    }

    /** A sped-up game clock sends more movement updates per second than the 20 the server ticks at. */
    private Location checkTimer(Player player, PlayerState state, Location from, long now) {
        if (!manager.shouldCheck(player, CheckType.TIMER, now) || manager.recentServerLag(now)) {
            state.timer.reset();
            return null;
        }
        CheckSettings settings = manager.config().settings(CheckType.TIMER);
        double excess = state.timer.update(now, settings.param("MAX-AHEAD-MS", 250D),
                settings.param("LAG-ALLOWANCE-MS", 1_500D));
        if (excess <= 0D) {
            return null;
        }
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.TIMER, 1D + excess / 100D,
                String.format(Locale.ROOT, "%.0f ms ahead of the game clock", settings.param("MAX-AHEAD-MS", 250D) + excess), now);
        return result.act() ? from.clone() : null;
    }

    /** Measures how far the player really moved along the knockback the server applied. */
    private void checkKnockback(Player player, PlayerState state, Support support, Location to, double dx, double dz, long now) {
        KnockbackTracker knockback = state.knockback;
        if (!knockback.active()) {
            return;
        }
        int ping = Math.min(400, AntiCheatManager.safePing(player));
        if (now - state.knockbackMillis > 1_500L + ping || support.inLiquid() || support.special()
                || manager.recentServerLag(now)) {
            knockback.stop();
            return;
        }
        // The velocity packet takes about one ping to arrive; earlier movement says nothing about it.
        if (now - state.knockbackMillis < ping + 50L) {
            return;
        }
        CheckSettings settings = manager.config().settings(CheckType.VELOCITY);
        int updates = knockback.record(dx, dz);
        if (updates < (int) settings.param("SAMPLE-UPDATES", 5D)) {
            return;
        }
        double ratio = knockback.ratio();
        knockback.stop();
        if (!manager.shouldCheck(player, CheckType.VELOCITY, now)
                || ratio >= settings.param("MIN-RATIO", 0.2D)
                || blockedAlong(to, knockback.directionX(), knockback.directionZ())) {
            return;
        }
        manager.flag(player, CheckType.VELOCITY, 1D + (settings.param("MIN-RATIO", 0.2D) - ratio) * 2D,
                String.format(Locale.ROOT, "moved %.0f%% of %.2f knockback", Math.max(0D, ratio) * 100D, knockback.velocity()), now);
    }

    /** With an inventory open a vanilla client releases movement keys, so ground speed must decay. */
    private void checkInventoryMove(Player player, PlayerState state, Support support, double horizontal, long now) {
        if (now > state.inventoryCheckUntilMillis || state.inventoryReferenceSpeed <= 0D) {
            return;
        }
        CheckSettings settings = manager.config().settings(CheckType.INVENTORY);
        boolean groundOnly = support.solid() && !support.onIce() && !support.inLiquid();
        double required = Math.max(settings.param("MIN-MOVE-SPEED", 0.2D),
                state.inventoryReferenceSpeed * settings.param("MOVE-SPEED-RATIO", 0.9D));
        double reference = state.inventoryReferenceSpeed;
        state.inventoryReferenceSpeed = 0D;
        if (!groundOnly || horizontal < required || now - state.knockbackMillis < 1_500L
                || !manager.shouldCheck(player, CheckType.INVENTORY, now)) {
            return;
        }
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.INVENTORY, 1D,
                String.format(Locale.ROOT, "kept %.2f b/t (was %.2f) with an inventory open", horizontal, reference), now);
        if (result.act()) {
            player.closeInventory();
        }
    }

    /** Rising more than one step or jump allows in a single update while on the ground. */
    private Location checkStep(Player player, PlayerState state, Support support, Location to, double dy, long now) {
        if (dy <= 0.5D || !state.wasSupported || support.inLiquid() || !manager.shouldCheck(player, CheckType.STEP, now)
                || now - state.lastBounceMillis < 2_000L || now - state.lastLiquidMillis < 1_000L) {
            return null;
        }
        double stepHeight = attribute(player, Attribute.STEP_HEIGHT, 0.6D);
        double jump = attribute(player, Attribute.JUMP_STRENGTH, 0.42D) + 0.1D * jumpBoostLevel(player);
        double limit = Math.max(stepHeight, jump) + manager.config().settings(CheckType.STEP).param("TOLERANCE", 0.05D);
        if (dy <= limit || nearPiston(to)) {
            return null;
        }
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.STEP, 1D + (dy - limit) * 2D,
                String.format(Locale.ROOT, "stepped up %.2f, max %.2f", dy, limit), now);
        return result.act() ? validSetback(state, to) : null;
    }

    /**
     * NoFall clients claim to stand on the ground while falling, so the server never accumulates fall distance.
     * The server's own ground detection disagrees; when the check acts, the fall damage the player skipped is
     * applied on the real landing (respecting Feather Falling and armour, via a fall damage source).
     */
    private Location checkNoFall(Player player, PlayerState state, Support support, Location to, double dy, long now) {
        if (support.supported()) {
            if (state.groundSpoofedThisFall) {
                applySkippedFallDamage(player, state, to);
            }
            state.trackedFallDistance = 0D;
            state.groundSpoofedThisFall = false;
            return null;
        }
        if (dy < 0D) {
            state.trackedFallDistance -= dy;
        }
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING) || player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            state.trackedFallDistance = 0D;
            return null;
        }
        CheckSettings settings = manager.config().settings(CheckType.NOFALL);
        if (!player.isOnGround() || dy > -settings.param("MIN-FALL-SPEED", 0.08D)
                || !manager.shouldCheck(player, CheckType.NOFALL, now)) {
            return null;
        }
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.NOFALL, 1D,
                String.format(Locale.ROOT, "claimed ground while falling %.2f b/t, %.1f blocks fallen", -dy,
                        state.trackedFallDistance), now);
        if (result.act()) {
            state.groundSpoofedThisFall = true;
        }
        return null;
    }

    private void applySkippedFallDamage(Player player, PlayerState state, Location landing) {
        double safe = attribute(player, Attribute.SAFE_FALL_DISTANCE, 3D) + jumpBoostLevel(player);
        double multiplier = attribute(player, Attribute.FALL_DAMAGE_MULTIPLIER, 1D) * landingFactor(player, landing);
        double damage = Math.ceil((state.trackedFallDistance - safe) * multiplier);
        if (damage <= 0D || player.isDead()) {
            return;
        }
        try {
            player.damage(damage, DamageSource.builder(DamageType.FALL).build());
        } catch (RuntimeException | LinkageError error) {
            player.damage(damage);
        }
    }

    /** Blocks that soften or cancel a landing, as in vanilla. */
    private static double landingFactor(Player player, Location landing) {
        Block feet = landing.getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        for (Block block : new Block[]{feet, below}) {
            Material type = block.getType();
            if (block.isLiquid() || isWaterlogged(block) || SPECIAL_SUPPORT.contains(type) || Tag.CLIMBABLE.isTagged(type)) {
                return 0D;
            }
            if (type == Material.SLIME_BLOCK && !player.isSneaking()) {
                return 0D;
            }
            if (type == Material.HAY_BLOCK || type == Material.HONEY_BLOCK) {
                return 0.2D;
            }
            if (Tag.BEDS.isTagged(type)) {
                return 0.5D;
            }
        }
        return 1D;
    }

    /** Standing still on top of water or lava, with nothing solid underneath. */
    private Location checkWaterWalk(Player player, PlayerState state, Support support, Location to, double dy,
                                    double horizontal, long now) {
        if (support.solid() || !manager.shouldCheck(player, CheckType.JESUS, now)) {
            state.waterWalkTicks = 0;
            return null;
        }
        World world = to.getWorld();
        int bx = floor(to.getX());
        int bz = floor(to.getZ());
        Block surface = world.getBlockAt(bx, floor(to.getY() - 0.1D), bz);
        Block body = world.getBlockAt(bx, floor(to.getY() + 0.3D), bz);
        boolean onTop = (surface.isLiquid() || isWaterlogged(surface)) && !body.isLiquid() && !isWaterlogged(body)
                && to.getY() - surface.getY() >= 0.8D && Math.abs(dy) < 0.02D && horizontal > 0.03D;
        state.waterWalkTicks = onTop ? state.waterWalkTicks + 1 : 0;
        if (state.waterWalkTicks <= manager.config().settings(CheckType.JESUS).param("MIN-TICKS", 6D)) {
            return null;
        }
        state.waterWalkTicks = 0;
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.JESUS, 1D,
                "walked on " + surface.getType().name().toLowerCase(Locale.ROOT), now);
        return result.act() ? validSetback(state, to) : null;
    }

    private Location checkFly(Player player, PlayerState state, Location to, double dy, long now) {
        CheckSettings settings = manager.config().settings(CheckType.FLY);
        state.airTicks++;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            state.groundY = to.getY();
            return null;
        }
        boolean slowFalling = player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
        // Water physics (swimming up, bubble columns) push players out of liquid without a server velocity update.
        boolean leftLiquidRecently = now - state.lastLiquidMillis < 2_000L;
        AntiCheatManager.FlagResult result = AntiCheatManager.FlagResult.NONE;

        // 1. Rising higher than the jump-strength attribute allows (bounces on slime or beds excepted).
        if (now - state.lastBounceMillis > 3_000L && !leftLiquidRecently) {
            double maxHeight = MovementLimits.maxJumpHeight(
                    attribute(player, Attribute.JUMP_STRENGTH, 0.42D), jumpBoostLevel(player),
                    attribute(player, Attribute.GRAVITY, MovementLimits.DEFAULT_GRAVITY))
                    + settings.param("HEIGHT-TOLERANCE", 0.35D);
            double rise = to.getY() - state.groundY;
            if (rise > maxHeight) {
                result = strongest(result, manager.flag(player, CheckType.FLY, 1D + (rise - maxHeight),
                        String.format(Locale.ROOT, "rose %.2f, max %.2f", rise, maxHeight), now));
                state.groundY = to.getY() - maxHeight;
            }
        }

        // 2. Speeding up vertically in mid-air: gravity only ever slows an upward motion.
        if (state.airTicks > 1 && !state.wasSupported && !leftLiquidRecently
                && MovementLimits.isAirJump(state.lastDy, dy, settings.param("AIR-JUMP-TOLERANCE", 0.03D))) {
            result = strongest(result, manager.flag(player, CheckType.FLY, 1.5D,
                    String.format(Locale.ROOT, "mid-air boost dy %.3f -> %.3f", state.lastDy, dy), now));
        }

        if (!slowFalling) {
            // 3. Hovering: no vertical movement for several consecutive mid-air updates.
            state.hoverTicks = Math.abs(dy) < 0.005D ? state.hoverTicks + 1 : 0;
            if (state.hoverTicks > settings.param("MAX-HOVER-TICKS", 4D)) {
                result = strongest(result, manager.flag(player, CheckType.FLY, 1D,
                        "hovered " + state.hoverTicks + " updates", now));
            }
            // 4. Gliding: airborne for a long time without ever falling at a real speed.
            state.slowAirTicks = dy > -0.08D ? state.slowAirTicks + 1 : 0;
            if (state.slowAirTicks > settings.param("MAX-SLOW-AIR-TICKS", 30D)) {
                result = strongest(result, manager.flag(player, CheckType.FLY, 1D,
                        "airborne " + state.slowAirTicks + " updates without falling", now));
                state.slowAirTicks = 0;
            }
        }
        return result.act() ? validSetback(state, to) : null;
    }

    private Location checkSpeed(Player player, PlayerState state, double horizontal, Support support, long now) {
        if (support.inLiquid() && player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
            return null;
        }
        CheckSettings settings = manager.config().settings(CheckType.SPEED);
        double movementSpeed = attribute(player, Attribute.MOVEMENT_SPEED, 0.1D);
        double effectiveSpeed = Math.max(movementSpeed, state.lastMovementSpeed);
        state.lastMovementSpeed = movementSpeed;

        double limit = MovementLimits.maxHorizontalPerTick(effectiveSpeed,
                now - state.lastAirMillis <= 200L,
                now - state.lastIceMillis <= 1_500L,
                now - state.lastSlimeMillis <= 1_000L,
                support.inLiquid(),
                settings.param("TOLERANCE", 0.06D));
        state.speedBalance = Math.max(0D, state.speedBalance + (horizontal - limit));
        double balanceLimit = settings.param("BALANCE-LIMIT", 0.6D);
        if (state.speedBalance <= balanceLimit) {
            return null;
        }
        double excess = state.speedBalance;
        state.speedBalance = 0D;
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.SPEED, 1D + excess,
                String.format(Locale.ROOT, "%.3f b/t, limit %.3f", horizontal, limit), now);
        return result.act() ? validSetback(state, player.getLocation()) : null;
    }

    /** Tracks the last exact ground level and the highest point reached since, for the criticals check. */
    private static void trackCriticalBaseline(PlayerState state, Support support, Location to) {
        if (support.exactGround()) {
            state.groundYForCrits = to.getY();
            state.highestSinceGround = 0D;
        } else {
            state.highestSinceGround = Math.max(state.highestSinceGround, to.getY() - state.groundYForCrits);
        }
    }

    // ── Vehicles: VEHICLE_FLY ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat) || vehicle.getPassengers().isEmpty()
                || !(vehicle.getPassengers().getFirst() instanceof Player player)) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerState state = manager.state(player);
        if (!manager.shouldCheck(player, CheckType.VEHICLE_FLY, now)) {
            state.boatRiseTicks = 0;
            return;
        }
        Location to = event.getTo();
        double dy = to.getY() - event.getFrom().getY();
        Block at = to.getBlock();
        Block below = at.getRelative(BlockFace.DOWN);
        boolean liquidNear = at.isLiquid() || below.isLiquid() || isWaterlogged(at) || isWaterlogged(below)
                || at.getType() == Material.BUBBLE_COLUMN || below.getType() == Material.BUBBLE_COLUMN;
        state.boatRiseTicks = dy > 0.01D && !liquidNear ? state.boatRiseTicks + 1 : 0;
        if (state.boatRiseTicks <= manager.config().settings(CheckType.VEHICLE_FLY).param("MIN-RISE-TICKS", 6D)) {
            return;
        }
        state.boatRiseTicks = 0;
        AntiCheatManager.FlagResult result = manager.flag(player, CheckType.VEHICLE_FLY, 1D,
                String.format(Locale.ROOT, "boat rose %.2f b/t with no water below", dy), now);
        if (result.act()) {
            vehicle.eject();
        }
    }

    // ── Combat: REACH, WALL_HIT, KILLAURA, CRITICALS ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerState state = manager.state(attacker);
        Material held = attacker.getInventory().getItemInMainHand().getType();
        boolean spear = isSpear(held);
        if (spear) {
            markSpear(attacker, state, now);
        }
        if (!manager.isActive()) {
            return;
        }

        Entity target = event.getEntity();
        Location eyeLocation = attacker.getEyeLocation();
        Vector eye = eyeLocation.toVector();
        int ping = Math.min(AntiCheatManager.safePing(attacker),
                (int) manager.config().settings(CheckType.REACH).param("MAX-PING-MS", 400D));
        BoundingBox currentBox = target.getBoundingBox();
        List<BoundingBox> candidates;
        if (target instanceof Player targetPlayer) {
            candidates = manager.state(targetPlayer).positions.since(now - ping - 150L, currentBox);
        } else {
            double drift = 0.1D + target.getVelocity().length() * (ping / 50D + 2D);
            candidates = List.of(currentBox.clone().expand(drift));
        }
        double distance = CombatGeometry.lagCompensatedDistance(eye, candidates);
        boolean cancel = false;

        if (manager.shouldCheck(attacker, CheckType.REACH, now)) {
            CheckSettings settings = manager.config().settings(CheckType.REACH);
            double range = attribute(attacker, Attribute.ENTITY_INTERACTION_RANGE, 3D)
                    + (spear ? settings.param("SPEAR-EXTRA-RANGE", 2D) : 0D)
                    + settings.param("TOLERANCE", 0.35D);
            if (distance > range) {
                cancel |= manager.flag(attacker, CheckType.REACH, 0.5D + (distance - range) * 2D,
                        String.format(Locale.ROOT, "%.2f blocks, range %.2f", distance, range), now).act();
            }
        }

        double currentDistance = CombatGeometry.distanceToBox(eye, currentBox);
        if (manager.shouldCheck(attacker, CheckType.WALL_HIT, now)
                && currentDistance > manager.config().settings(CheckType.WALL_HIT).param("MIN-DISTANCE", 1D)
                && isBlockedByWall(eyeLocation, currentBox)) {
            cancel |= manager.flag(attacker, CheckType.WALL_HIT, 1D,
                    "target behind " + wallMaterial(eyeLocation, currentBox), now).act();
        }

        if (manager.shouldCheck(attacker, CheckType.KILLAURA, now)) {
            cancel |= checkKillAura(attacker, state, target, eyeLocation, eye, currentBox, currentDistance, spear, now);
        }

        if (!cancel && manager.shouldCheck(attacker, CheckType.CRITICALS, now) && isCritical(event, attacker)
                && checkCriticals(attacker, state, now)) {
            // Remove the 1.5x critical bonus the client faked.
            event.setDamage(event.getDamage() / 1.5D);
        }

        if (cancel) {
            event.setCancelled(true);
        }
    }

    private boolean checkKillAura(Player attacker, PlayerState state, Entity target, Location eyeLocation, Vector eye,
                                  BoundingBox currentBox, double currentDistance, boolean spear, long now) {
        CheckSettings settings = manager.config().settings(CheckType.KILLAURA);
        boolean cancel = false;
        Vector toTarget = CombatGeometry.closestPoint(currentBox, eye).subtract(eye);
        double angle = CombatGeometry.angleTo(eye, eyeLocation.getDirection(), CombatGeometry.closestPoint(currentBox, eye));
        double maxAngle = settings.param("MAX-ANGLE", 75D);
        if (currentDistance > settings.param("MIN-DISTANCE", 1.5D) && angle > maxAngle) {
            cancel |= manager.flag(attacker, CheckType.KILLAURA, 0.5D + (angle - maxAngle) / 30D,
                    String.format(Locale.ROOT, "hit %.0f° off view", angle), now).act();
        }

        // A vanilla client discards attack clicks while an item is in use (eating, drawing a bow, raising a shield).
        if (!spear && attacker.isHandRaised()) {
            cancel |= manager.flag(attacker, CheckType.KILLAURA, 1D, "attacked while using an item", now).act();
        }

        long tick = now / 50L;
        if (state.attackTick != tick) {
            state.attackTick = tick;
            state.attackTargetsThisTick.clear();
        }
        state.attackTargetsThisTick.add(target.getUniqueId());
        int targets = state.attackTargetsThisTick.size();
        if (targets > settings.param("MAX-TARGETS-PER-TICK", 3D)) {
            cancel |= manager.flag(attacker, CheckType.KILLAURA, 2D,
                    "hit " + targets + " entities in one tick", now).act();
        }

        // Two different targets in the same tick, far apart: no mouse turns that fast between two clicks.
        if (toTarget.lengthSquared() > 1.0E-6D && currentDistance > settings.param("MIN-DISTANCE", 1.5D)) {
            Vector direction = toTarget.clone().normalize();
            if (state.lastAttackTick == tick && state.lastAttackDirection != null
                    && !target.getUniqueId().equals(state.lastAttackTarget)) {
                double switchAngle = Math.toDegrees(Math.acos(Math.max(-1D, Math.min(1D,
                        direction.dot(state.lastAttackDirection)))));
                double maxSwitch = settings.param("MAX-SWITCH-ANGLE", 60D);
                if (switchAngle > maxSwitch) {
                    cancel |= manager.flag(attacker, CheckType.KILLAURA, 1.5D,
                            String.format(Locale.ROOT, "switched targets %.0f° within one tick", switchAngle), now).act();
                }
            }
            state.lastAttackTick = tick;
            state.lastAttackDirection = direction;
            state.lastAttackTarget = target.getUniqueId();
        }
        return cancel;
    }

    /**
     * Packet "criticals" fake a tiny hop so the server sees a falling player. A real critical needs a real jump
     * (or a fall from a ledge); a hit counted as critical while the player never rose more than a hair above the
     * ground they are standing on is fake.
     */
    private boolean checkCriticals(Player attacker, PlayerState state, long now) {
        Location location = attacker.getLocation();
        double maxRise = manager.config().settings(CheckType.CRITICALS).param("MAX-RISE", 0.12D);
        double aboveGround = location.getY() - state.groundYForCrits;
        if (state.highestSinceGround >= maxRise || aboveGround < -0.01D || aboveGround > maxRise
                || attacker.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || !collisionTopWithin(attacker, location, maxRise)) {
            return false;
        }
        return manager.flag(attacker, CheckType.CRITICALS, 1D, String.format(Locale.ROOT,
                "critical hit %.3f above the ground without a jump", Math.max(0D, aboveGround)), now).act();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        PlayerState state = manager.state(player);
        long now = System.currentTimeMillis();
        // Holding left-click on a block and right-click interactions also swing the arm; they are not clicks.
        if (state.diggingBlockKey != null || now - state.lastRightClickMillis < 100L) {
            return;
        }
        int cps = state.clicks.record(now);
        if (!manager.shouldCheck(player, CheckType.AUTOCLICKER, now)) {
            return;
        }
        double maxCps = manager.config().settings(CheckType.AUTOCLICKER).param("MAX-CPS", 22D);
        if (cps > maxCps) {
            manager.flag(player, CheckType.AUTOCLICKER, 0.25D + (cps - maxCps) / 4D, cps + " clicks/s", now);
        }
    }

    // ── World: FAST_BREAK, NUKER, FAST_PLACE, BLOCK_REACH, SCAFFOLD, GHOST_HAND, XRAY ─

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        PlayerState state = manager.state(event.getPlayer());
        state.diggingBlockKey = blockKey(event.getBlock());
        state.diggingStartMillis = System.currentTimeMillis();
        state.diggingStartSpeed = event.getInstaBreak() ? 1F : event.getBlock().getBreakSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        PlayerState state = manager.state(event.getPlayer());
        if (blockKey(event.getBlock()).equals(state.diggingBlockKey)) {
            state.diggingBlockKey = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !isContainer(event.getClickedBlock().getType())) {
            return;
        }
        if (checkGhostHand(event.getPlayer(), event.getClickedBlock(), "opened", System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        long now = System.currentTimeMillis();
        PlayerState state = manager.state(player);
        String key = blockKey(block);
        boolean wasDigging = key.equals(state.diggingBlockKey);
        long digStart = state.diggingStartMillis;
        float startSpeed = state.diggingStartSpeed;
        state.diggingBlockKey = null;
        if (!manager.isActive()) {
            return;
        }

        boolean cancel = checkBlockReach(player, block, "break", now);
        cancel |= checkGhostHand(player, block, "broke", now);

        int perSecond = state.breaks.record(now);
        int perTick = state.breaks.countInSameTick(now);
        if (manager.shouldCheck(player, CheckType.NUKER, now)) {
            CheckSettings settings = manager.config().settings(CheckType.NUKER);
            double maxPerTick = settings.param("MAX-PER-TICK", 4D);
            double maxPerSecond = settings.param("MAX-PER-SECOND", 22D);
            if (perTick > maxPerTick) {
                cancel |= manager.flag(player, CheckType.NUKER, perTick - maxPerTick, perTick + " blocks in one tick", now).act();
            } else if (perSecond > maxPerSecond) {
                cancel |= manager.flag(player, CheckType.NUKER, (perSecond - maxPerSecond) / 4D, perSecond + " blocks/s", now).act();
            }
        }

        if (wasDigging && manager.shouldCheck(player, CheckType.FAST_BREAK, now)) {
            float bestSpeed = Math.max(startSpeed, block.getBreakSpeed(player));
            if (bestSpeed > 0F && bestSpeed < 1F) {
                CheckSettings settings = manager.config().settings(CheckType.FAST_BREAK);
                double expectedTicks = Math.ceil(1D / bestSpeed);
                double elapsedTicks = (now - digStart) / 50D;
                double required = expectedTicks * settings.param("PROGRESS-FLOOR", 0.7D)
                        - settings.param("LATENCY-TICKS", 2D) - AntiCheatManager.safePing(player) / 50D;
                if (elapsedTicks < required) {
                    cancel |= manager.flag(player, CheckType.FAST_BREAK, 1D + (required - elapsedTicks) / expectedTicks * 2D,
                            String.format(Locale.ROOT, "%s in %.0f ticks, needs %.0f",
                                    block.getType().name().toLowerCase(Locale.ROOT), elapsedTicks, expectedTicks), now).act();
                }
            }
        }

        if (cancel) {
            event.setCancelled(true);
            return;
        }
        checkXray(player, state, block, now);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (!manager.isActive()) {
            return;
        }
        PlayerState state = manager.state(player);
        // Vanilla measures placement reach to the clicked block face, not to the new block.
        boolean cancel = checkBlockReach(player, event.getBlockAgainst(), "place", now);

        int perSecond = state.places.record(now);
        int perTick = state.places.countInSameTick(now);
        if (manager.shouldCheck(player, CheckType.FAST_PLACE, now)) {
            CheckSettings settings = manager.config().settings(CheckType.FAST_PLACE);
            double maxPerTick = settings.param("MAX-PER-TICK", 4D);
            double maxPerSecond = settings.param("MAX-PER-SECOND", 15D);
            if (perTick > maxPerTick) {
                cancel |= manager.flag(player, CheckType.FAST_PLACE, perTick - maxPerTick, perTick + " blocks in one tick", now).act();
            } else if (perSecond > maxPerSecond) {
                cancel |= manager.flag(player, CheckType.FAST_PLACE, (perSecond - maxPerSecond) / 4D, perSecond + " blocks/s", now).act();
            }
        }
        if (manager.shouldCheck(player, CheckType.SCAFFOLD, now)) {
            cancel |= checkScaffold(player, state, event.getBlockAgainst(), event.getBlockPlaced(), now);
        }
        if (cancel) {
            event.setCancelled(true);
        }
    }

    /**
     * Three scaffold signals: a face the eye is behind cannot be clicked (exact for full cubes); the crosshair
     * must point roughly at the block; and towering straight up is limited by jump physics.
     */
    private boolean checkScaffold(Player player, PlayerState state, Block against, Block placed, long now) {
        CheckSettings settings = manager.config().settings(CheckType.SCAFFOLD);
        BlockFace face = against.getFace(placed);
        Location eyeLocation = player.getEyeLocation();
        boolean cancel = false;
        if (face != null && against.getType().isOccluding() && !FaceGeometry.canSeeFace(eyeLocation.getX(),
                eyeLocation.getY(), eyeLocation.getZ(), against.getX(), against.getY(), against.getZ(), face,
                settings.param("FACE-TOLERANCE", 0.05D))) {
            cancel |= manager.flag(player, CheckType.SCAFFOLD, 1.5D,
                    "placed on the hidden " + face.name().toLowerCase(Locale.ROOT) + " face", now).act();
        }

        Vector direction = eyeLocation.getDirection();
        double range = attribute(player, Attribute.BLOCK_INTERACTION_RANGE, 4.5D) + 1D;
        if (face != null && !FaceGeometry.rayHitsBox(eyeLocation.getX(), eyeLocation.getY(), eyeLocation.getZ(),
                direction.getX(), direction.getY(), direction.getZ(),
                against.getX(), against.getY(), against.getZ(), against.getX() + 1D, against.getY() + 1D, against.getZ() + 1D,
                settings.param("LOOK-TOLERANCE", 0.6D), range)) {
            cancel |= manager.flag(player, CheckType.SCAFFOLD, 0.5D, "placed a block without looking at it", now).act();
        }

        Location feet = player.getLocation();
        boolean underFeet = placed.getY() == floor(feet.getY() - 0.01D) - 1 || placed.getY() == floor(feet.getY()) - 1;
        boolean inColumn = Math.abs(placed.getX() + 0.5D - feet.getX()) < 0.85D && Math.abs(placed.getZ() + 0.5D - feet.getZ()) < 0.85D;
        if (underFeet && inColumn && state.lastDy > 0D) {
            int perSecond = state.towerPlaces.record(now);
            double limit = settings.param("MAX-TOWER-PER-SECOND", 3.5D) + 1.5D * jumpBoostLevel(player);
            if (perSecond > limit) {
                cancel |= manager.flag(player, CheckType.SCAFFOLD, perSecond - limit,
                        perSecond + " blocks towered in one second", now).act();
            }
        }
        return cancel;
    }

    /** True when the block is completely hidden behind other view-blocking blocks from the player's eye. */
    private boolean checkGhostHand(Player player, Block block, String action, long now) {
        if (!manager.shouldCheck(player, CheckType.GHOST_HAND, now)) {
            return false;
        }
        Location eye = player.getEyeLocation();
        BoundingBox box = BoundingBox.of(block);
        if (CombatGeometry.distanceToBox(eye.toVector(), box)
                <= manager.config().settings(CheckType.GHOST_HAND).param("MIN-DISTANCE", 1.5D)) {
            return false;
        }
        if (!completelyHidden(eye, block)) {
            return false;
        }
        return manager.flag(player, CheckType.GHOST_HAND, 1D, action + " " + block.getType().name().toLowerCase(Locale.ROOT)
                + " through " + wallMaterial(eye, box), now).act();
    }

    private boolean checkBlockReach(Player player, Block block, String action, long now) {
        if (!manager.shouldCheck(player, CheckType.BLOCK_REACH, now)) {
            return false;
        }
        Vector eye = player.getEyeLocation().toVector();
        BoundingBox box = BoundingBox.of(block);
        double distance = CombatGeometry.distanceToBox(eye, box);
        double range = attribute(player, Attribute.BLOCK_INTERACTION_RANGE, 4.5D)
                + manager.config().settings(CheckType.BLOCK_REACH).param("TOLERANCE", 0.5D);
        if (distance <= range) {
            return false;
        }
        return manager.flag(player, CheckType.BLOCK_REACH, 1D + (distance - range),
                String.format(Locale.ROOT, "%s at %.2f blocks, range %.2f", action, distance, range), now).act();
    }

    private void checkXray(Player player, PlayerState state, Block block, long now) {
        if (!manager.shouldCheck(player, CheckType.XRAY, now)) {
            return;
        }
        World world = block.getWorld();
        CheckSettings settings = manager.config().settings(CheckType.XRAY);
        double maxY = switch (world.getEnvironment()) {
            case NORMAL -> settings.param("OVERWORLD-MAX-Y", 16D);
            case NETHER -> settings.param("NETHER-MAX-Y", 119D);
            default -> Double.NEGATIVE_INFINITY;
        };
        if (block.getY() > maxY) {
            return;
        }
        String material = block.getType().name();
        String dimension = world.getEnvironment().name();
        long window = (long) (settings.param("WINDOW-MINUTES", 60D) * 60_000D);
        int radius = (int) settings.param("VEIN-RADIUS", 3D);

        if (manager.config().isXrayFiller(material)) {
            for (String family : manager.config().xrayOreFamilies().keySet()) {
                tracker(state, family, dimension, window, radius).recordFiller(now);
            }
            return;
        }
        for (Map.Entry<String, Set<String>> family : manager.config().xrayOreFamilies().entrySet()) {
            if (!family.getValue().contains(material)) {
                continue;
            }
            XrayTracker tracker = tracker(state, family.getKey(), dimension, window, radius);
            boolean newHiddenVein = tracker.recordOre(now, block.getX(), block.getY(), block.getZ(), isExposed(block));
            if (!newHiddenVein) {
                return;
            }
            int veins = tracker.hiddenVeins(now);
            double rate = tracker.veinsPerThousand(now, (int) settings.param("MIN-FILLER", 200D));
            if (veins >= settings.param("MIN-HIDDEN-VEINS", 5D) && rate > settings.param("MAX-VEINS-PER-THOUSAND", 12D)) {
                manager.flag(player, CheckType.XRAY, 1D, String.format(Locale.ROOT,
                        "%s: %d hidden veins, %.1f per 1000 blocks mined in %.0f min",
                        family.getKey().toLowerCase(Locale.ROOT), veins, rate, window / 60_000D), now);
            }
            return;
        }
    }

    private XrayTracker tracker(PlayerState state, String family, String dimension, long window, int radius) {
        return state.xray.computeIfAbsent(family + ":" + dimension, ignored -> new XrayTracker(window, radius));
    }

    /** Ore touching cave air or liquid was visible from a cave, so finding it needs no x-ray. */
    private static boolean isExposed(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            if (CAVE_EXPOSURE.contains(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Movement the checks never judge: authorised flight, gliding, riptide, riding, sleeping. */
    private static boolean movementIgnored(Player player) {
        return player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isRiptiding()
                || player.isInsideVehicle() || player.isSleeping() || player.isDead();
    }

    private static void rebaseline(PlayerState state, Location to, Support support, long now, double dy) {
        state.groundY = to.getY();
        state.airTicks = 0;
        state.hoverTicks = 0;
        state.slowAirTicks = 0;
        state.speedBalance = 0D;
        state.trackedFallDistance = 0D;
        state.groundSpoofedThisFall = false;
        state.waterWalkTicks = 0;
        state.lastDy = dy;
        state.wasSupported = support.supported();
        if (support.supported()) {
            state.lastSupportedMillis = now;
        }
    }

    /**
     * @param supported   something holds the player up: ground, liquid, a climbable or sticky block, or an entity
     * @param solid       the support is solid ground, a special block or an entity (not just liquid)
     * @param exactGround the feet rest exactly on a collision box top, as after a real landing
     * @param special     cobweb, powder snow, scaffolding, honey, bubble column or berry bush
     */
    private record Support(boolean supported, boolean solid, boolean exactGround, boolean special, boolean inLiquid,
                           boolean onIce, boolean onSlime, boolean bouncy) {
    }

    /**
     * Server-side ground detection. The client's own "on ground" flag is never trusted. A player is
     * supported when a collision box tops out at their feet, when they are in liquid or on climbable or
     * sticky blocks, or when they stand on another entity (boats, shulkers, happy ghasts, players).
     */
    private static Support support(Player player, Location to) {
        World world = to.getWorld();
        double feetY = to.getY();
        boolean supported = false;
        boolean solid = false;
        boolean exact = false;
        boolean special = false;
        boolean liquid = false;
        boolean ice = false;
        boolean slime = false;
        boolean bouncy = false;
        Set<Long> columns = new HashSet<>();
        // Sample just inside the 0.6-wide hitbox: at exactly ±0.3 a player merely touching a wall would
        // sample the wall's column and count it as ground, letting wall-climbing ("spider") through.
        double edge = 0.2999D;
        double[][] offsets = {{0D, 0D}, {edge, edge}, {edge, -edge}, {-edge, edge}, {-edge, -edge}};
        for (double[] offset : offsets) {
            int bx = floor(to.getX() + offset[0]);
            int bz = floor(to.getZ() + offset[1]);
            if (!columns.add(((long) bx << 32) ^ (bz & 0xFFFFFFFFL))) {
                continue;
            }
            for (double yOffset : new double[]{1.0D, 0.0D, -0.5001D, -1.0D}) {
                Block block = world.getBlockAt(bx, floor(feetY + yOffset), bz);
                Material type = block.getType();
                if (block.isLiquid() || isWaterlogged(block)) {
                    liquid = true;
                    supported = true;
                    continue;
                }
                if (yOffset > 0D) {
                    if (Tag.CLIMBABLE.isTagged(type)) {
                        supported = true;
                        solid = true;
                        special = true;
                    }
                    continue;
                }
                if (SPECIAL_SUPPORT.contains(type) || Tag.CLIMBABLE.isTagged(type)) {
                    supported = true;
                    solid = true;
                    special = true;
                }
                if (block.isPassable()) {
                    continue;
                }
                // Full cubes (most terrain) need no shape lookup; partial blocks use their real collision boxes.
                List<Double> tops = type.isOccluding()
                        ? List.of(block.getY() + 1D)
                        : block.getCollisionShape().getBoundingBoxes().stream()
                        .map(shape -> block.getY() + shape.getMaxY()).toList();
                for (double top : tops) {
                    if (top <= feetY + 0.01D && top >= feetY - 0.51D) {
                        supported = true;
                        solid = true;
                        if (Math.abs(top - feetY) < 0.001D) {
                            exact = true;
                        }
                        if (ICE.contains(type)) {
                            ice = true;
                        }
                        if (type == Material.SLIME_BLOCK) {
                            slime = true;
                            bouncy = true;
                        }
                        if (Tag.BEDS.isTagged(type)) {
                            bouncy = true;
                        }
                    }
                }
            }
        }
        if (!solid && standingOnEntity(player, to)) {
            supported = true;
            solid = true;
            exact = true;
        }
        return new Support(supported, solid, exact, special, liquid, ice, slime, bouncy);
    }

    /** Whether a block collision top lies between the feet and {@code depth} below them. */
    private static boolean collisionTopWithin(Player player, Location location, double depth) {
        World world = location.getWorld();
        double feetY = location.getY();
        double edge = 0.2999D;
        double[][] offsets = {{0D, 0D}, {edge, edge}, {edge, -edge}, {-edge, edge}, {-edge, -edge}};
        for (double[] offset : offsets) {
            int bx = floor(location.getX() + offset[0]);
            int bz = floor(location.getZ() + offset[1]);
            for (int by = floor(feetY - depth - 0.01D); by <= floor(feetY); by++) {
                Block block = world.getBlockAt(bx, by, bz);
                if (block.isPassable() || block.isLiquid()) {
                    continue;
                }
                for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                    double top = by + shape.getMaxY();
                    if (top <= feetY + 0.001D && top >= feetY - depth - 0.01D) {
                        return true;
                    }
                }
            }
        }
        return standingOnEntity(player, location);
    }

    private static boolean standingOnEntity(Player player, Location to) {
        BoundingBox feet = player.getBoundingBox().clone()
                .shift(to.getX() - player.getLocation().getX(), to.getY() - player.getLocation().getY(),
                        to.getZ() - player.getLocation().getZ())
                .expand(0.3D, 0D, 0.3D);
        for (Entity entity : player.getNearbyEntities(1.5D, 1.5D, 1.5D)) {
            if (entity instanceof Item || entity instanceof ExperienceOrb || entity instanceof Projectile) {
                continue;
            }
            BoundingBox box = entity.getBoundingBox();
            double top = box.getMaxY();
            boolean overlaps = box.getMaxX() > feet.getMinX() && box.getMinX() < feet.getMaxX()
                    && box.getMaxZ() > feet.getMinZ() && box.getMinZ() < feet.getMaxZ();
            if (overlaps && top <= to.getY() + 0.1D && top >= to.getY() - 0.6D) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWaterlogged(Block block) {
        BlockData data = block.getBlockData();
        return data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    /** A wall right next to the player in the knockback direction legitimately stops them. */
    private static boolean blockedAlong(Location location, double directionX, double directionZ) {
        World world = location.getWorld();
        for (double distance : new double[]{0.4D, 0.8D}) {
            double x = location.getX() + directionX * distance;
            double z = location.getZ() + directionZ * distance;
            for (double height : new double[]{0.1D, 1.0D, 1.7D}) {
                Block block = world.getBlockAt(floor(x), floor(location.getY() + height), floor(z));
                if (!block.isPassable()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean nearPiston(Location location) {
        Block center = location.getBlock();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (PISTON_PARTS.contains(center.getRelative(x, y, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isContainer(Material type) {
        String name = type.name();
        return name.endsWith("CHEST") || name.equals("BARREL") || name.endsWith("SHULKER_BOX") || name.endsWith("FURNACE")
                || name.equals("SMOKER") || name.equals("HOPPER") || name.equals("DISPENSER") || name.equals("DROPPER")
                || name.equals("CRAFTER");
    }

    /** True when the centre, the face centres and the inset corners of the block are all behind other blocks. */
    private static boolean completelyHidden(Location eye, Block block) {
        double inset = 0.05D;
        double x = block.getX();
        double y = block.getY();
        double z = block.getZ();
        double[][] points = {
                {x + 0.5, y + 0.5, z + 0.5},
                {x + 0.5, y + 1 - inset, z + 0.5}, {x + 0.5, y + inset, z + 0.5},
                {x + inset, y + 0.5, z + 0.5}, {x + 1 - inset, y + 0.5, z + 0.5},
                {x + 0.5, y + 0.5, z + inset}, {x + 0.5, y + 0.5, z + 1 - inset},
                {x + inset, y + inset, z + inset}, {x + 1 - inset, y + inset, z + inset},
                {x + inset, y + 1 - inset, z + inset}, {x + 1 - inset, y + 1 - inset, z + inset},
                {x + inset, y + inset, z + 1 - inset}, {x + 1 - inset, y + inset, z + 1 - inset},
                {x + inset, y + 1 - inset, z + 1 - inset}, {x + 1 - inset, y + 1 - inset, z + 1 - inset}
        };
        for (double[] point : points) {
            if (!rayBlockedBefore(eye, new Vector(point[0], point[1], point[2]), block)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a view-blocking block other than {@code target} lies between the eye and the point. */
    private static boolean rayBlockedBefore(Location eye, Vector point, Block target) {
        Vector direction = point.clone().subtract(eye.toVector());
        double distance = direction.length();
        if (distance < 0.05D) {
            return false;
        }
        RayTraceResult result = eye.getWorld().rayTraceBlocks(eye, direction.normalize(), distance,
                FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitBlock() == null) {
            return false;
        }
        Block hit = result.getHitBlock();
        return !hit.equals(target) && hit.getType().isOccluding()
                && result.getHitPosition().distance(eye.toVector()) < distance - 0.1D;
    }

    /** True when both the target's centre and its nearest point are hidden behind a view-blocking block. */
    private static boolean isBlockedByWall(Location eye, BoundingBox target) {
        return rayBlocked(eye, target.getCenter()) && rayBlocked(eye, CombatGeometry.closestPoint(target, eye.toVector()));
    }

    private static boolean rayBlocked(Location eye, Vector point) {
        Vector direction = point.clone().subtract(eye.toVector());
        double distance = direction.length();
        if (distance < 0.05D) {
            return false;
        }
        RayTraceResult result = eye.getWorld().rayTraceBlocks(eye, direction.normalize(), distance,
                FluidCollisionMode.NEVER, true);
        return result != null && result.getHitBlock() != null && result.getHitBlock().getType().isOccluding()
                && result.getHitPosition().distance(eye.toVector()) < distance - 0.1D;
    }

    private static String wallMaterial(Location eye, BoundingBox target) {
        Vector direction = target.getCenter().subtract(eye.toVector());
        RayTraceResult result = eye.getWorld().rayTraceBlocks(eye, direction.clone().normalize(), direction.length(),
                FluidCollisionMode.NEVER, true);
        return result == null || result.getHitBlock() == null ? "a block"
                : result.getHitBlock().getType().name().toLowerCase(Locale.ROOT);
    }

    /** A position the player can safely be put back to: loaded, standing room, and solid or liquid below. */
    private static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);
        boolean standingRoom = feet.isPassable() && head.isPassable()
                && feet.getType() != Material.LAVA && head.getType() != Material.LAVA;
        boolean ground = !below.isPassable() || below.getType() == Material.WATER || feet.getType() == Material.WATER;
        return standingRoom && ground && below.getType() != Material.LAVA;
    }

    private static Location validSetback(PlayerState state, Location current) {
        Location safe = state.lastSafe;
        if (safe == null || safe.getWorld() == null || !safe.getWorld().equals(current.getWorld()) || !isSafe(safe)) {
            return null;
        }
        return safe;
    }

    private void markSpear(Player player, PlayerState state, long now) {
        state.lastSpearMillis = now;
        manager.exempt(player, CheckCategory.MOVEMENT, manager.config().exemptionMillis("SPEAR"));
    }

    private static boolean isSpear(Material material) {
        return material != null && material.name().endsWith("_SPEAR");
    }

    private static boolean isCritical(EntityDamageByEntityEvent event, Player attacker) {
        if (IS_CRITICAL != null) {
            try {
                return Boolean.TRUE.equals(IS_CRITICAL.invoke(event));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the vanilla rule
            }
        }
        return attacker.getFallDistance() > 0F && !attacker.isOnGround() && !attacker.isSprinting()
                && !attacker.isInsideVehicle() && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS)
                && !attacker.getLocation().getBlock().isLiquid()
                && !Tag.CLIMBABLE.isTagged(attacker.getLocation().getBlock().getType());
    }

    private static Method findIsCritical() {
        try {
            return EntityDamageByEntityEvent.class.getMethod("isCritical");
        } catch (NoSuchMethodException | LinkageError error) {
            return null;
        }
    }

    private static double attribute(Player player, Attribute attribute, double fallback) {
        try {
            AttributeInstance instance = player.getAttribute(attribute);
            return instance == null ? fallback : instance.getValue();
        } catch (RuntimeException | LinkageError ignored) {
            return fallback;
        }
    }

    private static int jumpBoostLevel(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private static AntiCheatManager.FlagResult strongest(AntiCheatManager.FlagResult a, AntiCheatManager.FlagResult b) {
        return b.act() || b.level() > a.level() ? b : a;
    }

    private static String blockKey(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
