// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatCommandLabelTest {

    @Test
    void namespacedAndTypedCommandsReduceToTheirLabel() {
        assertEquals("spawn", CombatManager.commandLabel("/smpplugin:spawn"));
        assertEquals("spawn", CombatManager.commandLabel("/SPAWN now"));
        assertEquals("tpa", CombatManager.commandLabel("/tpa"));
        assertEquals("warp", CombatManager.commandLabel("warp shop"));
        assertEquals("", CombatManager.commandLabel("/"));
        assertEquals("", CombatManager.commandLabel(null));
    }
}
