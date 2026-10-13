package com.bx.smpPlugin.anticheat;

import org.bukkit.Location;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the checks remember about one online player. Accessed on the main thread only. */
final class PlayerState {

    final Exemptions exemptions = new Exemptions();
    final Map<CheckType, ViolationBuffer> buffers = new EnumMap<>(CheckType.class);
    final Map<CheckType, Long> lastAlertMillis = new EnumMap<>(CheckType.class);
    final PositionHistory positions = new PositionHistory();

    /** Joined through Geyser/Floodgate. */
    boolean bedrock;

    // Movement
    boolean wasSupported = true;
    long lastSupportedMillis;
    long lastAirMillis;
    long lastBounceMillis;
    long lastLiquidMillis;
    double groundY;
    double lastDy;
    int airTicks;
    int hoverTicks;
    int slowAirTicks;
    long lastIceMillis;
    long lastSlimeMillis;
    double speedBalance;
    double lastMovementSpeed;
    Location lastSafe;
    long lastMovementFlagMillis;

    // Timer, no-fall, step, water walking, boats
    final TimerBalance timer = new TimerBalance();
    double trackedFallDistance;
    boolean groundSpoofedThisFall;
    double groundYForCrits;
    double highestSinceGround;
    int waterWalkTicks;
    int boatRiseTicks;
    double lastHorizontal;
    long lastMoveMillis;
    boolean lastMoveOnSolidGround;

    // Knockback
    final KnockbackTracker knockback = new KnockbackTracker();
    long knockbackMillis;

    // Inventory
    final RateWindow inventoryClicks = new RateWindow(1_000L, 128);
    long inventoryCheckUntilMillis;
    double inventoryReferenceSpeed;
    long lastTotemPopMillis;
    String clientBrand;

    // Combat
    final RateWindow clicks = new RateWindow(1_000L, 64);
    long lastAttackTick = -1L;
    org.bukkit.util.Vector lastAttackDirection;
    UUID lastAttackTarget;
    long lastRightClickMillis;
    long lastSpearMillis;
    long attackTick = -1L;
    final Set<UUID> attackTargetsThisTick = new HashSet<>();

    // Blocks
    String diggingBlockKey;
    long diggingStartMillis;
    float diggingStartSpeed;
    final RateWindow breaks = new RateWindow(1_000L, 128);
    final RateWindow places = new RateWindow(1_000L, 128);
    final RateWindow towerPlaces = new RateWindow(1_000L, 64);
    final Map<String, XrayTracker> xray = new HashMap<>();

    // Bypass permission use is logged once per session per category
    final Set<CheckCategory> bypassLogged = new HashSet<>();

    // Checks that reached their alert level recently, for multi-check escalation
    final Map<CheckType, Long> recentAlertLevel = new EnumMap<>(CheckType.class);
    boolean escalated;

    ViolationBuffer buffer(CheckType check) {
        return buffers.computeIfAbsent(check, ignored -> new ViolationBuffer());
    }

    void resetViolations() {
        buffers.values().forEach(ViolationBuffer::reset);
        speedBalance = 0D;
        recentAlertLevel.clear();
        escalated = false;
    }

    /** Clears movement tracking after teleports, respawns and world changes. */
    void resetMovement(Location location) {
        wasSupported = true;
        airTicks = 0;
        hoverTicks = 0;
        slowAirTicks = 0;
        lastDy = 0D;
        speedBalance = 0D;
        trackedFallDistance = 0D;
        groundSpoofedThisFall = false;
        highestSinceGround = 0D;
        waterWalkTicks = 0;
        boatRiseTicks = 0;
        timer.reset();
        knockback.stop();
        if (location != null) {
            groundY = location.getY();
        }
        positions.clear();
    }
}
