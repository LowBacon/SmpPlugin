package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinSide;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** Practice flips: the same fair coin and animation, no currency, no match record. */
public final class PracticeMenu extends CoinFlipMenu {

    private static final long PRACTICE_COOLDOWN_MILLIS = 1_500L;
    private static final Map<UUID, Long> LAST_FLIP = new java.util.HashMap<>();

    public PracticeMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "Practice", 27);
    }

    @Override
    protected void render(Player player) {
        set(4, Gui.item(Material.FEATHER, Gui.INFO + "Practice mode", Gui.MUTED + "No currency is involved and nothing",
                Gui.MUTED + "counts as a match. Pick a side."));
        for (CoinSide side : CoinSide.values()) {
            int slot = side == CoinSide.HEADS ? 11 : 15;
            button(slot, Gui.item(Gui.sideMaterial(side, config().headsMaterial(), config().tailsMaterial()),
                    Gui.sideColour(side) + side.displayName(), Gui.MUTED + "Flip and see if it lands on " + side.displayName() + "."),
                    () -> flip(side));
        }
        back(() -> openMenu(new MainMenu(module, viewer)));
    }

    private void flip(CoinSide pick) {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) {
            return;
        }
        if (!can(CoinFlipModule.PERM_PRACTICE)) {
            message("no-permission", Map.of());
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_FLIP.get(viewer);
        if (last != null && now - last < PRACTICE_COOLDOWN_MILLIS) {
            return;
        }
        LAST_FLIP.put(viewer, now);
        if (LAST_FLIP.size() > 1_000) {
            LAST_FLIP.entrySet().removeIf(entry -> now - entry.getValue() > PRACTICE_COOLDOWN_MILLIS);
        }
        CoinSide outcome = module.service().practiceFlip(viewer, player.getName());
        module.animator().startPractice(player, pick, outcome);
    }
}
