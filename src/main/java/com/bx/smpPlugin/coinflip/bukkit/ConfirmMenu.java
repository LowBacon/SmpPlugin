package com.bx.smpPlugin.coinflip.bukkit;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** A plain yes/no screen for actions that cannot be undone. */
public final class ConfirmMenu extends CoinFlipMenu {

    private final String question;
    private final List<String> details;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean used;

    public ConfirmMenu(CoinFlipModule module, UUID viewer, String question, List<String> details, Runnable onConfirm,
                       Runnable onCancel) {
        super(module, viewer, "Confirm", 27);
        this.question = question;
        this.details = details;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void render(Player player) {
        set(13, Gui.item(Material.PAPER, Gui.GOLD + question, details));
        button(11, Gui.item(Material.RED_CONCRETE, Gui.BAD + "No, go back"), onCancel);
        button(15, Gui.item(Material.LIME_CONCRETE, Gui.GOOD + "Yes"), () -> {
            if (used) {
                return;
            }
            used = true;
            onConfirm.run();
        });
    }
}
