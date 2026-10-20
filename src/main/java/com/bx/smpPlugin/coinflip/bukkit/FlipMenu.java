package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinSide;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The reveal screen. It only displays: the result is already stored before this menu opens, closing it stops the
 * animation but not the payout, and the same result arrives in chat either way.
 *
 * <p>The coin is tossed: it jumps from the middle into the top row, spins there while the frame flashes the colour
 * of the face that is up and the head of the player on that side lights up, then drops back and lands on the stored
 * result. The winner's head stays lit; the loser's turns into a skull.
 */
public final class FlipMenu extends CoinFlipMenu {

    private static final int COIN_LOW = 13;
    private static final int COIN_HIGH = 4;
    private static final int CREATOR_SLOT = 10;
    private static final int OPPONENT_SLOT = 16;
    private static final int STATUS_SLOT = 22;
    private static final int CLOSE_SLOT = 26;
    private static final int INFO_SLOT = 18;

    private final CoinFlipMatch match;
    private final CoinSide practicePick;
    private final CoinSide outcome;
    private CoinSide showing = CoinSide.HEADS;
    private int coinSlot = COIN_LOW;
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
            set(INFO_SLOT, Gui.item(Material.SUNFLOWER, Gui.GOLD + "Match #" + match.id(), List.of(
                    Gui.line(readable, "Pot", money(match.potCents())),
                    Gui.line(readable, "Fee", match.feeCents() == 0 ? "none" : money(match.feeCents())),
                    Gui.line(readable, "Winner receives", money(match.payoutCents())),
                    Gui.MUTED + "The result was drawn and stored",
                    Gui.MUTED + "before this animation started.")));
        } else {
            set(INFO_SLOT, Gui.item(Material.FEATHER, Gui.INFO + "Practice", List.of(
                    Gui.MUTED + "No currency is involved.",
                    Gui.line(readable, "Your pick", practicePick.displayName()))));
        }
        drawPlayers();
        drawCoin();
        drawStatus();
        button(CLOSE_SLOT, Gui.item(Material.BARRIER, Gui.BAD + "Close", Gui.MUTED + "The result still arrives in chat."), () -> close());
    }

    /** Next animation frame: flips the coin face and moves it along its toss. */
    void frame(boolean reducedMotion, int frame, int frames) {
        showing = showing.opposite();
        if (!reducedMotion) {
            double progress = frames <= 1 ? 1D : (double) frame / (frames - 1);
            int nextSlot = progress < 0.12D || progress > 0.85D ? COIN_LOW : COIN_HIGH;
            if (nextSlot != coinSlot) {
                set(coinSlot, frameItem(coinSlot));
                coinSlot = nextSlot;
            }
            paintFrame(showing == CoinSide.HEADS ? Material.YELLOW_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE);
        }
        drawPlayers();
        drawCoin();
    }

    /** Older callers without progress information: flip the face only. */
    void frame(boolean reducedMotion) {
        frame(reducedMotion, 0, 1);
    }

    void reveal() {
        revealed = true;
        showing = outcome;
        if (coinSlot != COIN_LOW) {
            set(coinSlot, frameItem(coinSlot));
            coinSlot = COIN_LOW;
        }
        boolean viewerWon = match != null ? viewer.equals(match.winnerId()) : practicePick == outcome;
        paintFrame(match == null || match.involves(viewer)
                ? (viewerWon ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                : Material.YELLOW_STAINED_GLASS_PANE);
        drawPlayers();
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

    private void paintFrame(Material pane) {
        ItemStack item = Gui.item(pane, " ");
        for (int slot = 0; slot < 27; slot++) {
            if (isFrame(slot) && slot != coinSlot) {
                set(slot, item);
            }
        }
    }

    private boolean isFrame(int slot) {
        return slot != COIN_LOW && slot != CREATOR_SLOT && slot != OPPONENT_SLOT && slot != STATUS_SLOT
                && slot != CLOSE_SLOT && slot != INFO_SLOT;
    }

    private ItemStack frameItem(int slot) {
        return Gui.item(Gui.material(config().borderMaterial(), Material.BLACK_STAINED_GLASS_PANE), " ");
    }

    private void drawPlayers() {
        if (match == null) {
            return;
        }
        boolean readable = readable();
        CoinSide creatorSide = match.creatorSide();
        set(CREATOR_SLOT, playerItem(match.creatorId(), match.creatorName(), creatorSide, readable));
        set(OPPONENT_SLOT, playerItem(match.opponentId(), match.opponentName(), creatorSide.opposite(), readable));
    }

    private ItemStack playerItem(UUID player, String name, CoinSide side, boolean readable) {
        List<String> lore = List.of(Gui.line(readable, "Side", side.displayName()));
        if (revealed) {
            if (side == outcome) {
                return Gui.glow(Gui.head(player, Gui.GOOD + "&l" + name + " wins!", lore), true);
            }
            return Gui.item(Material.SKELETON_SKULL, Gui.MUTED + name, lore);
        }
        return Gui.glow(Gui.head(player, Gui.sideColour(side) + name, lore), side == showing);
    }

    private void drawCoin() {
        Material material = Gui.sideMaterial(showing, config().headsMaterial(), config().tailsMaterial());
        String name = revealed ? Gui.sideColour(showing) + "&l" + showing.displayName().toUpperCase() + "!"
                : Gui.sideColour(showing) + showing.displayName();
        set(coinSlot, Gui.glow(Gui.item(material, name, revealed ? Gui.MUTED + "Final result" : Gui.MUTED + "Flipping..."), revealed));
        if (coinSlot != COIN_LOW) {
            set(COIN_LOW, Gui.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Gui.MUTED + "In the air..."));
        }
    }

    private void drawStatus() {
        if (!revealed) {
            set(STATUS_SLOT, Gui.item(Material.CLOCK, Gui.INFO + "Flipping...", Gui.MUTED + "Fair 50/50 flip."));
            return;
        }
        if (match != null) {
            boolean viewerWon = viewer.equals(match.winnerId());
            set(STATUS_SLOT, Gui.glow(Gui.item(viewerWon ? Material.EMERALD : Material.PAPER,
                    viewerWon ? Gui.GOOD + "You won " + money(match.payoutCents()) : Gui.VALUE + match.winnerName() + Gui.MUTED + " wins",
                    Gui.MUTED + "Landed on " + outcome.displayName() + ".",
                    Gui.MUTED + "Details are in chat."), viewerWon));
        } else {
            boolean right = practicePick == outcome;
            set(STATUS_SLOT, Gui.item(right ? Material.LIME_DYE : Material.GRAY_DYE,
                    right ? Gui.GOOD + "You called it" : Gui.MUTED + "Not this time",
                    Gui.MUTED + "Landed on " + outcome.displayName() + ". No currency moved."));
        }
    }
}
