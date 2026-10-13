package com.bx.smpPlugin.anticheat;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiCheatCoreTest {

    // ── Violation levels ─────────────────────────────────────────────────────────

    @Test
    void violationLevelsGrowAndDecayLinearlyButNeverBelowZero() {
        ViolationBuffer buffer = new ViolationBuffer();
        assertEquals(2D, buffer.add(2D, 1D, 1_000L), 1e-9);
        assertEquals(3.5D, buffer.add(1.5D, 1D, 1_000L), 1e-9);
        assertEquals(1.5D, buffer.current(1D, 3_000L), 1e-9, "two seconds forgive 2 VL");
        assertEquals(0D, buffer.current(1D, 60_000L), 1e-9);
        assertEquals(0D, buffer.add(-5D, 1D, 61_000L), 1e-9, "negative weights are ignored");
    }

    @Test
    void exemptionsExpireAndAreScopedToTheirCategoryOrCheck() {
        Exemptions exemptions = new Exemptions();
        exemptions.exempt(CheckCategory.MOVEMENT, 2_000L, 1_000L);
        assertTrue(exemptions.isExempt(CheckType.FLY, 2_500L));
        assertTrue(exemptions.isExempt(CheckType.SPEED, 2_999L));
        assertFalse(exemptions.isExempt(CheckType.FLY, 3_000L), "exemptions always expire");
        assertFalse(exemptions.isExempt(CheckType.REACH, 2_000L), "movement exemption does not cover combat");

        exemptions.exempt(CheckType.REACH, 500L, 10_000L);
        assertTrue(exemptions.isExempt(CheckType.REACH, 10_400L));
        assertFalse(exemptions.isExempt(CheckType.KILLAURA, 10_400L));

        exemptions.exempt(CheckCategory.MOVEMENT, 100L, 20_000L);
        exemptions.exempt(CheckCategory.MOVEMENT, 5_000L, 20_000L);
        exemptions.exempt(CheckCategory.MOVEMENT, 100L, 20_000L);
        assertTrue(exemptions.isExempt(CheckType.FLY, 24_000L), "a shorter grant never shortens a longer one");
    }

    // ── Movement physics ─────────────────────────────────────────────────────────

    @Test
    void vanillaJumpHeightMatchesTheGame() {
        // A vanilla jump peaks at about 1.2522 blocks.
        assertEquals(1.2522D, MovementLimits.maxJumpHeight(0.42D, 0, 0.08D), 0.01D);
        double jumpBoostTwo = MovementLimits.maxJumpHeight(0.42D, 2, 0.08D);
        assertTrue(jumpBoostTwo > 2.0D && jumpBoostTwo < 3.0D, "Jump Boost II is roughly 2.5 blocks: " + jumpBoostTwo);
        assertTrue(MovementLimits.maxJumpHeight(0.42D, 0, 0.04D) > MovementLimits.maxJumpHeight(0.42D, 0, 0.08D),
                "lower gravity attribute allows higher jumps");
    }

    @Test
    void legitimateVanillaSpeedsStayUnderTheLimits() {
        double walking = 0.1D;
        double sprinting = 0.13D;
        assertTrue(0.2158D < MovementLimits.maxHorizontalPerTick(walking, false, false, false, false, 0D));
        assertTrue(0.2806D < MovementLimits.maxHorizontalPerTick(sprinting, false, false, false, false, 0D));
        // Peak horizontal speed right after a sprint-jump is about 0.613 blocks per tick.
        assertTrue(0.613D < MovementLimits.maxHorizontalPerTick(sprinting, true, false, false, false, 0D));
        // Speed II raises the attribute by 40% (0.13 → about 0.182 while sprinting).
        assertTrue(0.40D < MovementLimits.maxHorizontalPerTick(0.182D, false, false, false, false, 0D));
        // Ice sprint-jumping can exceed 1 block per tick.
        assertTrue(1.2D < MovementLimits.maxHorizontalPerTick(sprinting, true, true, false, false, 0D));
    }

    @Test
    void typicalSpeedHacksExceedTheLimits() {
        double groundLimit = MovementLimits.maxHorizontalPerTick(0.13D, false, false, false, false, 0.06D);
        assertTrue(0.6D > groundLimit, "a 2x ground speed hack must be over the limit: " + groundLimit);
        double airLimit = MovementLimits.maxHorizontalPerTick(0.13D, true, false, false, false, 0.06D);
        assertTrue(1.0D > airLimit, "1 block/tick in air without ice is impossible: " + airLimit);
    }

    @Test
    void gravityNeverAcceleratesAnUpwardMotionInMidAir() {
        assertTrue(MovementLimits.isAirJump(-0.3D, 0.42D, 0.03D), "falling then jumping again in mid-air");
        assertTrue(MovementLimits.isAirJump(0.10D, 0.20D, 0.03D), "upward speed increasing in mid-air");
        assertFalse(MovementLimits.isAirJump(0.42D, 0.3332D, 0.03D), "normal jump arc slows down");
        assertFalse(MovementLimits.isAirJump(-0.1D, -0.08D, 0.03D), "still falling");
        assertFalse(MovementLimits.isAirJump(0.10D, 0.12D, 0.03D), "within tolerance");
    }

    // ── Combat geometry ──────────────────────────────────────────────────────────

    @Test
    void reachIsMeasuredToTheNearestPointOfTheHitbox() {
        BoundingBox target = new BoundingBox(4.7D, 64D, -0.3D, 5.3D, 65.8D, 0.3D);
        Vector eye = new Vector(0D, 65.62D, 0D);
        assertEquals(4.7D, CombatGeometry.distanceToBox(eye, target), 1e-9);
        assertEquals(0D, CombatGeometry.distanceToBox(new Vector(5D, 65D, 0D), target), 1e-9, "inside the box");
    }

    @Test
    void lagCompensationUsesTheClosestRecentPosition() {
        Vector eye = new Vector(0D, 65.62D, 0D);
        BoundingBox now = new BoundingBox(5.7D, 64D, -0.3D, 6.3D, 65.8D, 0.3D);
        BoundingBox earlier = new BoundingBox(3.2D, 64D, -0.3D, 3.8D, 65.8D, 0.3D);
        assertEquals(3.2D, CombatGeometry.lagCompensatedDistance(eye, List.of(now, earlier)), 1e-9);
    }

    @Test
    void attackAngleIsZeroStraightAheadAndLargeBehind() {
        Vector eye = new Vector(0D, 0D, 0D);
        Vector look = new Vector(0D, 0D, 1D);
        assertEquals(0D, CombatGeometry.angleTo(eye, look, new Vector(0D, 0D, 3D)), 1e-6);
        assertEquals(90D, CombatGeometry.angleTo(eye, look, new Vector(3D, 0D, 0D)), 1e-6);
        assertEquals(180D, CombatGeometry.angleTo(eye, look, new Vector(0D, 0D, -3D)), 1e-6);
    }

    // ── Rates ────────────────────────────────────────────────────────────────────

    @Test
    void rateWindowCountsOnlyTheLastSecondAndDetectsSameTickBursts() {
        RateWindow window = new RateWindow(1_000L, 100);
        for (int i = 0; i < 10; i++) {
            window.record(10_000L + i * 50L);
        }
        assertEquals(10, window.count(10_450L));
        assertEquals(10, window.count(10_950L), "all ten are still inside the last second");
        assertEquals(5, window.count(11_200L), "events at or before 10200 fell out of the window");
        assertEquals(1, window.countInSameTick(10_450L));

        window.record(20_000L);
        window.record(20_010L);
        window.record(20_020L);
        assertEquals(3, window.countInSameTick(20_020L));
    }

    // ── X-ray pattern ────────────────────────────────────────────────────────────

    @Test
    void neighbouringOreBlocksFormOneVeinAndCaveExposedOreDoesNotCount() {
        XrayTracker tracker = new XrayTracker(60L * 60_000L, 3);
        long now = 1_000_000L;
        assertTrue(tracker.recordOre(now, 0, -50, 0, false), "first block of a hidden vein");
        assertFalse(tracker.recordOre(now + 100, 1, -50, 0, false), "same vein");
        assertFalse(tracker.recordOre(now + 200, 1, -49, 1, false), "same vein");
        assertFalse(tracker.recordOre(now + 300, 40, -50, 40, true), "visible from a cave");
        assertTrue(tracker.recordOre(now + 400, 80, -50, 80, false), "a second hidden vein far away");
        assertEquals(2, tracker.hiddenVeins(now + 500));
    }

    @Test
    void xRayRateSeparatesTunnellingStraightToOreFromBranchMining() {
        long now = 1_000_000L;
        XrayTracker xray = new XrayTracker(60L * 60_000L, 3);
        // Tunnelling: 8 hidden veins while mining only 250 blocks.
        for (int i = 0; i < 250; i++) {
            xray.recordFiller(now + i);
        }
        for (int vein = 0; vein < 8; vein++) {
            xray.recordOre(now + 1_000 + vein, vein * 20, -55, 0, false);
        }
        assertTrue(xray.veinsPerThousand(now + 2_000, 200) > 12D);

        XrayTracker branchMiner = new XrayTracker(60L * 60_000L, 3);
        // Branch mining: 3 hidden veins in 1500 blocks.
        for (int i = 0; i < 1_500; i++) {
            branchMiner.recordFiller(now + i);
        }
        for (int vein = 0; vein < 3; vein++) {
            branchMiner.recordOre(now + 2_000 + vein, vein * 20, -55, 0, false);
        }
        assertTrue(branchMiner.veinsPerThousand(now + 3_000, 200) < 12D);
    }

    @Test
    void xRayHistoryExpiresAfterTheWindow() {
        XrayTracker tracker = new XrayTracker(60_000L, 3);
        tracker.recordOre(0L, 0, -50, 0, false);
        tracker.recordFiller(0L);
        assertEquals(1, tracker.hiddenVeins(30_000L));
        assertEquals(0, tracker.hiddenVeins(61_000L));
        assertEquals(0, tracker.fillerBlocks(61_000L));
    }
}
