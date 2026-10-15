package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.models.PunishmentRecord;
import com.bx.smpPlugin.models.PunishmentScope;
import com.bx.smpPlugin.models.PunishmentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMuteManagerTest {

    private static PunishmentRecord record(UUID target, PunishmentType type, Long expiresAt) {
        return new PunishmentRecord(1L, target, "Steve", type, "spam", null, "console",
                System.currentTimeMillis(), expiresAt, null, "", null, "", "local", PunishmentScope.SERVER);
    }

    @Test
    void permanentVoiceMuteHoldsUntilLifted() {
        VoiceMuteManager manager = new VoiceMuteManager(null);
        UUID steve = UUID.randomUUID();
        assertFalse(manager.isVoiceMuted(steve));

        manager.apply(steve, record(steve, PunishmentType.VOICE_MUTE, null));
        assertTrue(manager.isVoiceMuted(steve));

        manager.apply(steve, null);
        assertFalse(manager.isVoiceMuted(steve));
    }

    @Test
    void temporaryVoiceMuteRunsOutWithoutARelog() {
        VoiceMuteManager manager = new VoiceMuteManager(null);
        UUID steve = UUID.randomUUID();
        manager.apply(steve, record(steve, PunishmentType.VOICE_MUTE, System.currentTimeMillis() + 60_000L));
        assertTrue(manager.isVoiceMuted(steve));

        manager.apply(steve, record(steve, PunishmentType.VOICE_MUTE, System.currentTimeMillis() - 1L));
        assertFalse(manager.isVoiceMuted(steve));
    }

    @Test
    void aTextMuteIsNotAVoiceMute() {
        VoiceMuteManager manager = new VoiceMuteManager(null);
        UUID steve = UUID.randomUUID();
        manager.apply(steve, record(steve, PunishmentType.MUTE, null));
        assertFalse(manager.isVoiceMuted(steve));
        assertFalse(manager.isVoiceMuted(null));
    }
}
