package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.MatchState;
import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** The viewer's open matches and the challenges waiting for them, with cancel, accept and decline actions. */
public final class MyMatchesMenu extends CoinFlipMenu {

    public MyMatchesMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "My matches", 54);
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        long now = System.currentTimeMillis();
        List<CoinFlipMatch> mine = module.service().matchesOf(viewer);
        set(4, Gui.item(Material.BOOK, Gui.INFO + "Active: " + Gui.VALUE + mine.size(),
                Gui.MUTED + "Waiting matches can be cancelled;", Gui.MUTED + "your stake is returned in full."));
        if (mine.isEmpty()) {
            set(22, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "No active matches",
                    Gui.MUTED + "Create one or browse open matches."));
        }
        int index = 0;
        for (CoinFlipMatch match : mine) {
            if (index >= Gui.INNER_54.length) {
                break;
            }
            int slot = Gui.INNER_54[index++];
            List<String> lore = Gui.matchLore(match, module, readable, now);
            boolean creator = match.creatorId().equals(viewer);
            boolean invited = viewer.equals(match.inviteeId());
            if (match.isPrivate()) {
                lore.add(Gui.line(readable, creator ? "Challenge to" : "Challenge from", creator ? match.inviteeName() : match.creatorName()));
            }
            if (match.state() == MatchState.WAITING && creator) {
                lore.add("");
                lore.add(Gui.BAD + "Click to cancel and get your stake back");
                button(slot, Gui.item(Material.CLOCK, Gui.GOOD + "#" + match.id() + Gui.MUTED + " waiting", lore),
                        () -> openMenu(new ConfirmMenu(module, viewer, "Cancel #" + match.id() + "?",
                                List.of(Gui.MUTED + "Your stake of " + money(match.stakeCents()) + " is returned."),
                                () -> {
                                    setBusy(true);
                                    module.service().cancel(viewer, match.id()).thenAccept(outcome -> {
                                        setBusy(false);
                                        report(outcome);
                                        openMenu(new MyMatchesMenu(module, viewer));
                                    });
                                },
                                () -> openMenu(new MyMatchesMenu(module, viewer)))));
            } else if (match.state() == MatchState.WAITING && invited) {
                lore.add("");
                lore.add(Gui.GOOD + "Click to review the challenge");
                button(slot, Gui.glow(Gui.head(match.creatorId(), Gui.GOLD + "Challenge from " + match.creatorName(), lore), true),
                        () -> openMenu(new JoinConfirmMenu(module, viewer, match.id())));
            } else {
                Material icon = match.state() == MatchState.RECOVERY_REQUIRED ? Material.REDSTONE_TORCH : Material.SUNFLOWER;
                if (match.state() == MatchState.RECOVERY_REQUIRED) {
                    lore.add(Gui.MUTED + "Your currency is safe; staff will");
                    lore.add(Gui.MUTED + "settle it. Quote #" + match.id() + ".");
                }
                set(slot, Gui.item(icon, Gui.GOLD + "#" + match.id(), lore));
            }
        }
        back(() -> openMenu(new MainMenu(module, viewer)));
        button(49, Gui.item(Material.WRITABLE_BOOK, Gui.INFO + "Match history"),
                () -> openMenu(new HistoryMenu(module, viewer, viewer, player.getName(), 0, CoinFlipDao.HistoryFilter.ALL)));
        button(53, Gui.item(Material.SPYGLASS, Gui.INFO + "Browse matches"), () -> openMenu(new BrowserMenu(module, viewer)));
    }
}
