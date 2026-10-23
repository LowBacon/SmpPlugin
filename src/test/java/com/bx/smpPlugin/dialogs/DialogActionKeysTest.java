// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DialogActionKeysTest {

    @Test
    void playerButtonsCarryTheWholeUuid() {
        UUID uuid = UUID.randomUUID();
        for (String prefix : new String[]{DialogActions.TPA_TARGET, DialogActions.TPA_TO, DialogActions.TPA_HERE}) {
            Key key = DialogActions.key(prefix + uuid);
            assertNotNull(key, prefix);
            assertEquals(prefix + uuid, key.value(), "the uuid must survive so the click finds the player");
        }
    }

    @Test
    void auctionButtonsCarryTheListingId() {
        Key key = DialogActions.key(DialogActions.AH_BUY + 9_876_543_210L);
        assertNotNull(key);
        assertEquals(DialogActions.AH_BUY + "9876543210", key.value());
        assertNotNull(DialogActions.key(DialogActions.AH_BACK));
        assertNotNull(DialogActions.key(DialogActions.TPA_REQUESTS));
    }
}
