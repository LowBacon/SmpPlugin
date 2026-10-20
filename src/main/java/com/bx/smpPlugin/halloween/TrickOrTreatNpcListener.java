package com.bx.smpPlugin.halloween;

import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Clicking a FancyNpcs NPC named in TRICK-OR-TREAT.NPCS knocks on its door. Kept in its own class because it names
 * FancyNpcs classes: it is only created when FancyNpcs is installed, so servers without it never load them.
 */
public final class TrickOrTreatNpcListener implements Listener {

    private final TrickOrTreatCommand trickOrTreat;

    public TrickOrTreatNpcListener(TrickOrTreatCommand trickOrTreat) {
        this.trickOrTreat = trickOrTreat;
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        Npc npc = event.getNpc();
        Player player = event.getPlayer();
        if (npc == null || player == null || npc.getData() == null) {
            return;
        }
        String name = npc.getData().getName();
        if (trickOrTreat.isHouseNpc(name)) {
            trickOrTreat.knock(player, "npc:" + name.toLowerCase(java.util.Locale.ROOT));
        }
    }
}
