package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.MatchState;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The CoinFlip hub. Reads only in-memory state, so opening it never touches storage. */
public final class MainMenu extends CoinFlipMenu {

    public MainMenu(CoinFlipModule module, UUID viewer) {
        super(module, viewer, "Home", 54);
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void render(Player player) {
        CoinFlipConfig config = config();
        boolean readable = readable();
        boolean open = module.service().wageringOpen();
        long balance = StakeMath.fromEconomy(module.plugin().getEconomyManager().getBalance(player));
        int listed = module.service().publicListings().size();
        List<CoinFlipMatch> mine = module.service().matchesOf(viewer);
        long invites = mine.stream().filter(match -> viewer.equals(match.inviteeId()) && match.state() == MatchState.WAITING).count();
        String pausedKey = config.wageringBlocked() ? "wagering-blocked" : "maintenance";

        List<String> balanceLore = new ArrayList<>();
        balanceLore.add(Gui.line(readable, "Balance", money(balance)));
        balanceLore.add(Gui.line(readable, "Stakes", money(config.minStakeCents()) + " to " + money(config.maxStakeCents())));
        balanceLore.add(Gui.line(readable, "Fee", config.feeBasisPoints() == 0 ? "none" : StakeMath.percent(config.feeBasisPoints()) + " of the pot"));
        balanceLore.add("");
        balanceLore.add(Gui.MUTED + "In-game currency only. It has no");
        balanceLore.add(Gui.MUTED + "real-world value and cannot be bought.");
        if (!open) {
            balanceLore.add("");
            balanceLore.add(Gui.BAD + "Wagering is paused right now.");
        }
        set(config.mainSlot("BALANCE"), Gui.head(viewer, Gui.GOLD + player.getName(), balanceLore));

        gated(config.mainSlot("CREATE"), open, can(CoinFlipModule.PERM_CREATE), pausedKey,
                Gui.item(Gui.material(config.headsMaterial(), Material.GOLD_BLOCK), Gui.GOOD + "Create match",
                        Gui.MUTED + "Pick a stake and a side, then", Gui.MUTED + "wait for someone to join.", "",
                        Gui.INFO + "Click to start"),
                () -> openMenu(new CreateMenu(module, viewer, CreateMenu.Draft.initial(prefs(), config))));

        button(config.mainSlot("BROWSE"), Gui.item(Material.SPYGLASS, Gui.INFO + "Browse matches",
                        Gui.line(readable, "Open now", Integer.toString(listed)), "", Gui.MUTED + "Join a waiting match."),
                () -> openMenu(new BrowserMenu(module, viewer)));

        gated(config.mainSlot("CHALLENGE"), open && config.allowPrivate(), can(CoinFlipModule.PERM_CHALLENGE),
                config.allowPrivate() ? pausedKey : "challenges-disabled",
                Gui.item(Material.PAPER, Gui.GOLD + "Challenge a player",
                        Gui.MUTED + "Send a private challenge to", Gui.MUTED + "someone who is online."),
                () -> openMenu(new ChallengeMenu(module, viewer, 0)));

        ItemStack myMatches = Gui.item(Material.BOOK, Gui.INFO + "My matches",
                Gui.line(readable, "Active", Integer.toString(mine.size())),
                Gui.line(readable, "Challenges for you", Long.toString(invites)));
        button(config.mainSlot("MY-MATCHES"), Gui.glow(myMatches, invites > 0), () -> openMenu(new MyMatchesMenu(module, viewer)));

        button(config.mainSlot("HISTORY"), Gui.item(Material.WRITABLE_BOOK, Gui.INFO + "Match history",
                        Gui.MUTED + "Your finished, cancelled and", Gui.MUTED + "expired matches."),
                () -> openMenu(new HistoryMenu(module, viewer, viewer, player.getName(), 0, CoinFlipDao.HistoryFilter.ALL)));
        button(config.mainSlot("STATS"), Gui.item(Material.COMPASS, Gui.INFO + "My statistics",
                        Gui.MUTED + "Wins, losses, totals and streaks."),
                () -> openMenu(new StatsMenu(module, viewer, viewer, player.getName())));
        button(config.mainSlot("LEADERBOARDS"), Gui.item(Material.GOLDEN_HELMET, Gui.GOLD + "Leaderboards",
                        Gui.MUTED + "Top players by wins, matches", Gui.MUTED + "played and more."),
                () -> openMenu(new LeaderboardMenu(module, viewer, CoinFlipDao.Metric.WINS)));
        button(config.mainSlot("ACHIEVEMENTS"), Gui.item(Material.NETHER_STAR, Gui.GOLD + "Achievements",
                        Gui.MUTED + "Cosmetic milestones."),
                () -> openMenu(new AchievementsMenu(module, viewer)));
        gated(config.mainSlot("PRACTICE"), true, can(CoinFlipModule.PERM_PRACTICE), "no-permission",
                Gui.item(Material.FEATHER, Gui.INFO + "Practice",
                        Gui.MUTED + "Flip without any currency.", Gui.MUTED + "Nothing is recorded as a match."),
                () -> openMenu(new PracticeMenu(module, viewer)));
        button(config.mainSlot("SETTINGS"), Gui.item(Material.COMPARATOR, Gui.INFO + "Settings",
                        Gui.MUTED + "Animation, sounds, privacy", Gui.MUTED + "and wagering limits."),
                () -> openMenu(new SettingsMenu(module, viewer)));
        button(config.mainSlot("HELP"), Gui.item(Material.KNOWLEDGE_BOOK, Gui.INFO + "Help and rules",
                        Gui.MUTED + "How matches, odds and fees work."),
                () -> openMenu(new HelpMenu(module, viewer)));
        button(config.mainSlot("CLOSE"), Gui.item(Material.BARRIER, Gui.BAD + "Close"), () -> close());
    }

}
