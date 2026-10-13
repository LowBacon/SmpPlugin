package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.coinflip.StakeMath;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Last look before the stake is reserved: stake, pot, fee, payout, side, visibility and expiry. Large stakes need a
 * second click. The confirm button locks while the request is in flight, so it cannot be submitted twice.
 */
public final class ReviewMenu extends CoinFlipMenu {

    private final CreateMenu.Draft draft;
    private boolean armed;

    public ReviewMenu(CoinFlipModule module, UUID viewer, CreateMenu.Draft draft) {
        super(module, viewer, "Review", 27);
        this.draft = draft;
    }

    @Override
    protected void render(Player player) {
        CoinFlipConfig config = config();
        boolean readable = readable();
        long fee = StakeMath.fee(StakeMath.pot(draft.stakeCents), config.feeBasisPoints());
        set(4, Gui.item(Material.SUNFLOWER, Gui.GOLD + "Your match", List.of(
                Gui.line(readable, "Stake (each player)", money(draft.stakeCents)),
                Gui.line(readable, "Pot", money(StakeMath.pot(draft.stakeCents))),
                Gui.line(readable, "Fee", fee == 0 ? "none" : money(fee) + " (" + StakeMath.percent(config.feeBasisPoints()) + ")"),
                Gui.line(readable, "Winner receives", money(StakeMath.payout(draft.stakeCents, config.feeBasisPoints()))),
                Gui.line(readable, "Your side", draft.side == null ? "random" : draft.side.displayName()),
                Gui.line(readable, "Visibility", draft.targetId == null ? "public" : "challenge to " + draft.targetName),
                Gui.line(readable, "Expires after", Gui.duration(draft.targetId == null ? config.expiryMillis() : config.challengeExpiryMillis())),
                "",
                Gui.MUTED + "Your stake is reserved now and returned",
                Gui.MUTED + "in full if nobody joins or you cancel.",
                Gui.MUTED + "In-game currency only.")));
        set(13, Gui.item(Material.BOOK, Gui.INFO + "Rules", List.of(
                Gui.MUTED + "Each side has exactly a 50% chance.",
                Gui.MUTED + "The result is drawn by the server after",
                Gui.MUTED + "both stakes are reserved, then shown.")));

        button(11, Gui.item(Material.RED_CONCRETE, Gui.BAD + "Go back", Gui.MUTED + "Nothing has been reserved."),
                () -> openMenu(new CreateMenu(module, viewer, draft)));
        boolean needsSecondClick = config.highStakeConfirmCents() > 0 && draft.stakeCents >= config.highStakeConfirmCents();
        if (busy()) {
            set(15, Gui.item(Material.CLOCK, Gui.INFO + "Creating...", Gui.MUTED + "Reserving your stake."));
        } else if (needsSecondClick && !armed) {
            button(15, Gui.item(Material.ORANGE_CONCRETE, Gui.GOLD + "Confirm stake of " + money(draft.stakeCents),
                    Gui.MUTED + "This is a large stake.", Gui.MUTED + "Click once more to confirm."), () -> {
                armed = true;
                refresh();
            });
        } else {
            button(15, Gui.glow(Gui.item(Material.LIME_CONCRETE, Gui.GOOD + (armed ? "Yes, create it" : "Create match"),
                    Gui.MUTED + "Reserves " + money(draft.stakeCents) + " now."), true), this::submit);
        }
    }

    private void submit() {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) {
            return;
        }
        boolean challenge = draft.targetId != null;
        if (!can(CoinFlipModule.PERM_CREATE) || (challenge && !can(CoinFlipModule.PERM_CHALLENGE))) {
            message("no-permission", java.util.Map.of());
            return;
        }
        CoinSide side = draft.side != null ? draft.side : ThreadLocalRandom.current().nextBoolean() ? CoinSide.HEADS : CoinSide.TAILS;
        setBusy(true);
        refresh();
        module.service().create(new CoinFlipService.CreateRequest(viewer, player.getName(), draft.stakeCents, side,
                draft.targetId, draft.targetName)).thenAccept(outcome -> {
            setBusy(false);
            report(outcome);
            if (outcome.success()) {
                openMenu(new MyMatchesMenu(module, viewer));
            } else {
                armed = false;
                refresh();
            }
        });
    }
}
