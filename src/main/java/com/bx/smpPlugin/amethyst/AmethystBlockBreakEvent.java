// SmpPlugin by LowBacon
package com.bx.smpPlugin.amethyst;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

// A block an amethyst drill, shovel or chopper takes out next to the one the player broke. It is a
// real break event so protection plugins can stop it; anti-cheats skip it because the player did
// not mine it themselves.
public class AmethystBlockBreakEvent extends BlockBreakEvent {

    public AmethystBlockBreakEvent(Block block, Player player) {
        super(block, player);
    }
}
