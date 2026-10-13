package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinSide;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The reveal screen. It only displays: the result is already stored before this menu opens, closing it stops the
 * animation but not the payout, and the same result arrives in chat either way.
 */
public final class FlipMenu extends CoinFlipMenu {

    private static final int[] SWEEP = {11, 12, 14, 15};

    private final CoinFlipMatch match;
    private final CoinSide practicePick;
    private final CoinSide outcome;
    private CoinSide showing = CoinSide.HEADS;
    private int sweep = -1;
    private boolean revealed;

    /** A real match reveal. */
    FlipMenu(CoinFlipModule module, UUID viewer, CoinFlipMatch match) {
        super(module, viewer, "Flip &8#" + match.id(), 27);
        this.match = match;
        this.practicePick = null;
        this.outcome = match.outcome();
    }

    /** A practice flip. */
    FlipMenu(CoinFlipModule module, UUID viewer, CoinSide pick, CoinSide outcome) {
        super(module, viewer, "Practice flip", 27);
        this.match = null;
        this.practicePick = pick;
        this.outcome = outcome;
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        if (match != null) {
            set(4, Gui.item(Material.SUNFLOWER, Gui.GOLD + "Match #" + match.id(), List.of(
                    Gui.line(readable, "Pot", money(match.potCents())),
                    Gui.line(readable, "Fee", match.feeCents() == 0 ? "none" : money(match.feeCents())),
                    Gui.line(readable, "Winner receives", money(match.payoutCents())),
                    Gui.MUTED + "The result was drawn and stored",
                    Gui.MUTED + "before this animation started.")));
            set(10, Gui.head(match.creatorId(), Gui.sideColour(match.creatorSide()) + match.creatorName(),
                    List.of(Gui.line(readable, "Side", match.creatorSide().displayName()))));
            set(16, Gui.head(match.opponentId(), Gui.sideColour(match.creatorSide().opposite()) + match.opponentName(),
                    List.of(Gui.line(readable, "Side", match.creatorSide().opposite().displayName()))));
        } else {
            set(4, Gui.item(Material.FEATHER, Gui.INFO + "Practice", List.of(
                    Gui.MUTED + "No currency is involved.",
                    Gui.line(readable, "Your pick", practicePick.displayName()))));
        }
        drawCoin();
        drawStatus();
        button(26, Gui.item(Material.BARRIER, Gui.BAD + "Close", Gui.MUTED + "The result still arrives in chat."), () -> close());
    }

    /** Next animation frame: flips the coin face and moves the highlight. */
    void frame(boolean reducedMotion) {
        showing = showing.opposite();
        if (!reducedMotion) {
            if (sweep >= 0) {
                set(SWEEP[sweep], Gui.item(Gui.material(config().borderMaterial(), Material.BLACK_STAINED_GLASS_PANE), " "));
            }
            sweep = (sweep + 1) % SWEEP.length;
            set(SWEEP[sweep], Gui.item(Gui.material(config().accentMaterial(), Material.PURPLE_STAINED_GLASS_PANE), " "));
        }
        drawCoin();
    }

    void reveal() {
        revealed = true;
        showing = outcome;
        if (sweep >= 0) {
            set(SWEEP[sweep], Gui.item(Gui.material(config().borderMaterial(), Material.BLACK_STAINED_GLASS_PANE), " "));
        }
        for (int slot : SWEEP) {
            set(slot, Gui.item(Material.LIME_STAINED_GLASS_PANE, " "));
        }
        drawCoin();
        drawStatus();
    }

    boolean revealed() {
        return revealed;
    }

    CoinSide practiceOutcome() {
        return outcome;
    }

    String matchId() {
        return match == null ? null : match.id();
    }

    private void drawCoin() {
        String headsMaterial = config().headsMaterial();
        String tailsMaterial = config().tailsMaterial();
        Material material = Gui.sideMaterial(showing, headsMaterial, tailsMaterial);
        String name = revealed ? Gui.sideColour(showing) + "&l" + showing.displayName().toUpperCase() + "!"
                : Gui.sideColour(showing) + showing.displayName();
        set(13, Gui.glow(Gui.item(material, name, revealed ? Gui.MUTED + "Final result" : Gui.MUTED + "Flipping..."), revealed));
    }

    private void drawStatus() {
        if (!revealed) {
            set(22, Gui.item(Material.CLOCK, Gui.INFO + "Flipping...", Gui.MUTED + "Fair 50/50 flip."));
            return;
        }
        if (match != null) {
            boolean viewerWon = viewer.equals(match.winnerId());
            set(22, Gui.glow(Gui.item(viewerWon ? Material.EMERALD : Material.PAPER,
                    viewerWon ? Gui.GOOD + "You won " + money(match.payoutCents()) : Gui.VALUE + match.winnerName() + Gui.MUTED + " wins",
                    Gui.MUTED + "Landed on " + outcome.displayName() + ".",
                    Gui.MUTED + "Details are in chat."), viewerWon));
        } else {
            boolean right = practicePick == outcome;
            set(22, Gui.item(right ? Material.LIME_DYE : Material.GRAY_DYE,
                    right ? Gui.GOOD + "You called it" : Gui.MUTED + "Not this time",
                    Gui.MUTED + "Landed on " + outcome.displayName() + ". No currency moved."));
        }
    }
}
