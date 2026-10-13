package com.bx.smpPlugin.anticheat;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure detection logic added in 2.0.0-alpha.3: timer, face visibility, knockback, ban queue, client brands. */
class AntiCheatDetectionTest {

    @Test
    void timerIgnoresANormalClockAndLagButCatchesASpeedUp() {
        TimerBalance normal = new TimerBalance();
        long time = 0L;
        for (int i = 0; i < 400; i++) {
            time += 50L;
            assertEquals(0D, normal.update(time, 250D, 1_500D), "20 updates per second never flags");
        }

        // A 2-second freeze followed by vanilla's catch-up of at most 10 ticks: never flags.
        TimerBalance lagged = new TimerBalance();
        lagged.update(0L, 250D, 1_500D);
        lagged.update(2_000L, 250D, 1_500D);
        for (int i = 0; i < 10; i++) {
            assertEquals(0D, lagged.update(2_000L + i * 2L, 250D, 1_500D));
        }

        // A network stall delivering 40 queued updates in one tick is a burst, not gained time.
        TimerBalance stalled = new TimerBalance();
        stalled.update(0L, 250D, 1_500D);
        stalled.update(2_000L, 250D, 1_500D);
        double flagged = 0D;
        for (int i = 0; i < 40; i++) {
            flagged += stalled.update(2_000L + (i / 10), 250D, 1_500D);
        }
        assertEquals(0D, flagged);

        // 1.5x game speed: 30 updates per second.
        TimerBalance cheat = new TimerBalance();
        time = 0L;
        boolean caught = false;
        for (int i = 0; i < 60 && !caught; i++) {
            time += 33L;
            caught = cheat.update(time, 250D, 1_500D) > 0D;
        }
        assertTrue(caught, "a 1.5x timer is caught within two seconds");
    }

    @Test
    void onlyFacesTheEyeIsInFrontOfCanBeClicked() {
        // Block at (0, 64, 0). Bridging east from on top of it: the eye must lean past x = 1.
        assertFalse(FaceGeometry.canSeeFace(0.7D, 65.62D, 0.5D, 0, 64, 0, BlockFace.EAST, 0.05D));
        assertTrue(FaceGeometry.canSeeFace(1.2D, 65.62D, 0.5D, 0, 64, 0, BlockFace.EAST, 0.05D));
        assertTrue(FaceGeometry.canSeeFace(0.5D, 65.62D, 0.5D, 0, 64, 0, BlockFace.UP, 0.05D));
        assertFalse(FaceGeometry.canSeeFace(0.5D, 63.5D, 0.5D, 0, 64, 0, BlockFace.UP, 0.05D));
        assertTrue(FaceGeometry.canSeeFace(0.5D, 63.5D, 0.5D, 0, 64, 0, BlockFace.DOWN, 0.05D));
        assertFalse(FaceGeometry.canSeeFace(0.5D, 65D, 2D, 0, 64, 0, BlockFace.NORTH, 0.05D));
        assertTrue(FaceGeometry.canSeeFace(0.5D, 65D, -1D, 0, 64, 0, BlockFace.NORTH, 0.05D));
        assertTrue(FaceGeometry.canSeeFace(0.5D, 65D, 1.5D, 0, 64, 0, BlockFace.SOUTH, 0.05D));
        assertTrue(FaceGeometry.canSeeFace(-0.5D, 65D, 0.5D, 0, 64, 0, BlockFace.WEST, 0.05D));
        assertFalse(FaceGeometry.canSeeFace(0.5D, 65D, 0.5D, 0, 64, 0, BlockFace.WEST, 0.05D));
    }

    @Test
    void lookRayMustPassNearTheClickedBlock() {
        // Looking straight down at the block below.
        assertTrue(FaceGeometry.rayHitsBox(0.5D, 65.62D, 0.5D, 0D, -1D, 0D, 0D, 64D, 0D, 1D, 65D, 1D, 0D, 5D));
        // Looking straight up while placing below: misses even with tolerance.
        assertFalse(FaceGeometry.rayHitsBox(0.5D, 65.62D, 0.5D, 0D, 1D, 0D, 0D, 64D, 0D, 1D, 65D, 1D, 0.6D, 5D));
        // Looking slightly past the edge is forgiven by the tolerance.
        assertTrue(FaceGeometry.rayHitsBox(0.5D, 65.62D, 0.5D, 0.9D, -0.44D, 0D, 0D, 64D, 0D, 1D, 65D, 1D, 0.6D, 5D));
    }

    @Test
    void knockbackRatioComparesTravelAlongTheAppliedDirection() {
        KnockbackTracker vanilla = new KnockbackTracker();
        vanilla.start(0.4D, 0D);
        for (double step : new double[]{0.3D, 0.12D, 0.03D, 0D, 0D}) {
            vanilla.record(step, 0.01D);
        }
        assertTrue(vanilla.ratio() > 1D, "a vanilla client travels more than the first-tick velocity");

        KnockbackTracker antiKnockback = new KnockbackTracker();
        antiKnockback.start(0D, -0.45D);
        for (int i = 0; i < 5; i++) {
            antiKnockback.record(0.05D, 0.1D);
        }
        assertTrue(antiKnockback.ratio() < 0D, "walking against the knockback gives a negative ratio");

        KnockbackTracker none = new KnockbackTracker();
        none.start(0D, 0D);
        assertFalse(none.active(), "no horizontal knockback is never tracked");
    }

    @Test
    void banQueueSurvivesARestartAndNeverQueuesTwice(@TempDir Path folder) {
        File file = folder.resolve("ban-queue.yml").toFile();
        UUID id = UUID.randomUUID();
        BanQueue queue = new BanQueue(file, Logger.getAnonymousLogger());
        assertTrue(queue.add(new BanQueue.Entry(id, "Cheater", CheckType.FLY, "ABC123", "Fly (hovered)", 1_000L)));
        assertFalse(queue.add(new BanQueue.Entry(id, "Cheater", CheckType.SPEED, "DEF456", "Speed", 2_000L)));

        BanQueue reloaded = new BanQueue(file, Logger.getAnonymousLogger());
        assertEquals(1, reloaded.size());
        BanQueue.Entry entry = reloaded.findByName("cheater").orElseThrow();
        assertEquals(CheckType.FLY, entry.check());
        assertEquals("ABC123", entry.evidenceId());

        assertEquals(1, reloaded.drain().size());
        assertEquals(0, new BanQueue(file, Logger.getAnonymousLogger()).size(), "drained queue stays empty after restart");
    }

    @Test
    void clientSignaturesMatchCaseInsensitiveSubstrings() {
        List<String> brands = AntiCheatConfig.DEFAULT_BLOCKED_BRANDS;
        assertEquals("meteor", ClientSignature.matchBrand("Meteor-Client", brands));
        assertNull(ClientSignature.matchBrand("vanilla", brands));
        assertNull(ClientSignature.matchBrand("fabric", brands));
        assertNull(ClientSignature.matchBrand(null, brands));
        assertEquals("wurst:hello", ClientSignature.matchChannel(Set.of("minecraft:brand", "wurst:hello"),
                AntiCheatConfig.DEFAULT_BLOCKED_CHANNELS));
        assertNull(ClientSignature.matchChannel(Set.of("fabric:registry/sync"), AntiCheatConfig.DEFAULT_BLOCKED_CHANNELS));
    }
}
