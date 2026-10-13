package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shows the exact terms of one match before joining; also used to accept a private challenge. */
public final class JoinConfirmMenu extends CoinFlipMenu {

    private final String matchId;

    public JoinConfirmMenu(CoinFlipModule module, UUID viewer, String matchId) {
        super(module, viewer, "Join #" + matchId, 27);
        this.matchId = matchId;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        CoinFlipMatch match = module.service().findActive(matchId).orElse(null);
        if (match == null || match.state() != MatchState.WAITING) {
            set(13, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "This match is no longer open",
                    Gui.MUTED + "Someone else joined, it was cancelled", Gui.MUTED + "or it expired. Nothing was reserved."));
            back(() -> openMenu(new BrowserMenu(module, viewer)));
            return;
        }
        List<String> lore = Gui.matchLore(match, module, readable, System.currentTimeMillis());
        lore.add(Gui.line(readable, "Your side", match.creatorSide().opposite().displayName()));
        lore.add("");
        lore.add(Gui.MUTED + "Joining reserves " + money(match.stakeCents()) + " from");
        lore.add(Gui.MUTED + "your balance. The result is drawn");
        lore.add(Gui.MUTED + "fairly by the server, 50/50.");
        set(4, Gui.head(match.creatorId(), Gui.VALUE + match.creatorName() + Gui.MUTED + "'s match", lore));

        button(11, Gui.item(Material.RED_CONCRETE, match.isPrivate() ? Gui.BAD + "Decline" : Gui.BAD + "Go back",
                        match.isPrivate() ? Gui.MUTED + "Their stake is returned." : Gui.MUTED + "Nothing is reserved."),
                () -> {
                    if (match.isPrivate() && viewer.equals(match.inviteeId())) {
                        setBusy(true);
                        module.service().decline(viewer, matchId).thenAccept(outcome -> {
                            setBusy(false);
                            report(outcome);
                            close();
                        });
                    } else {
                        openMenu(new BrowserMenu(module, viewer));
                    }
                });
        if (busy()) {
            set(15, Gui.item(Material.CLOCK, Gui.INFO + "Joining...", Gui.MUTED + "Reserving your stake."));
        } else {
            button(15, Gui.glow(Gui.item(Material.LIME_CONCRETE, Gui.GOOD + "Join for " + money(match.stakeCents()),
                    Gui.MUTED + "You play " + match.creatorSide().opposite().displayName() + "."), true), this::join);
        }
    }

    private void join() {
        Player player = Bukkit.getPlayer(viewer);
        if (player == null) {
            return;
        }
        if (!can(CoinFlipModule.PERM_JOIN)) {
            message("no-permission", Map.of());
            return;
        }
        setBusy(true);
        refresh();
        module.service().join(viewer, player.getName(), matchId).thenAccept(outcome -> {
            setBusy(false);
            if (!outcome.success()) {
                report(outcome);
                refresh();
            }
            // On success the reveal screen opens for both players.
        });
    }

    static void open(CoinFlipModule module, Player player, String rawId) {
        String id = CoinFlipService.normalizeId(rawId);
        new JoinConfirmMenu(module, player.getUniqueId(), id).open(player);
    }
}
