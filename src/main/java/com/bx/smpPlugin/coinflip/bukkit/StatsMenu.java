// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipStats;
import com.bx.smpPlugin.coinflip.CoinSide;
import com.bx.smpPlugin.coinflip.Settlement;
import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class StatsMenu extends CoinFlipMenu {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private record Loaded(CoinFlipStats stats, long weekPlayed, long weekNet, long monthPlayed, long monthNet) {
    }

    private final UUID subject;
    private final String subjectName;
    private Loaded loaded;
    private boolean failed;

    public StatsMenu(CoinFlipModule module, UUID viewer, UUID subject, String subjectName) {
        super(module, viewer, "Stats: " + subjectName, 54);
        this.subject = subject;
        this.subjectName = subjectName;
    }

    @Override
    public void open(Player player) {
        super.open(player);
        long now = System.currentTimeMillis();
        String week = Settlement.weekKey(now, ZoneId.systemDefault());
        String month = Settlement.monthKey(now, ZoneId.systemDefault());
        module.repository().transaction(dao -> {
            CoinFlipStats stats = dao.loadStats(subject, subjectName);
            long[] weekRow = dao.periodTotals(subject, week);
            long[] monthRow = dao.periodTotals(subject, month);
            return new Loaded(stats, weekRow[0], weekRow[1], monthRow[0], monthRow[1]);
        }).whenComplete((result, error) -> module.plugin().getSpigotScheduler().runGlobal(() -> {
            loaded = result;
            failed = error != null;
            refresh();
        }));
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        back(() -> openMenu(new MainMenu(module, viewer)));
        if (failed) {
            set(22, Gui.item(Material.BARRIER, Gui.BAD + "Statistics are unavailable", Gui.MUTED + "Please try again later."));
            return;
        }
        if (loaded == null) {
            set(22, Gui.item(Material.CLOCK, Gui.INFO + "Loading..."));
            return;
        }
        CoinFlipStats stats = loaded.stats();
        boolean self = subject.equals(viewer);
        if (!self && stats.hidden() && !can(CoinFlipModule.PERM_INSPECT)) {
            set(22, Gui.item(Material.IRON_DOOR, Gui.MUTED + subjectName + "'s profile is private"));
            return;
        }
        String winRate = stats.played() == 0 ? "-" : String.format(Locale.ROOT, "%.1f%% (%d of %d)", stats.winRate(), stats.wins(), stats.played());
        set(4, Gui.head(subject, Gui.GOLD + subjectName, List.of(
                Gui.line(readable, "Finished matches", Long.toString(stats.played())),
                Gui.line(readable, "Win rate", winRate),
                stats.hidden() ? Gui.MUTED + "Profile: private" : Gui.MUTED + "Profile: public")));

        set(19, Gui.item(Material.EMERALD, Gui.GOOD + "Wins: " + Gui.VALUE + stats.wins(),
                Gui.line(readable, "Losses", Long.toString(stats.losses())),
                Gui.line(readable, "Current streak", Long.toString(stats.currentStreak())),
                Gui.line(readable, "Best streak", Long.toString(stats.bestStreak()))));
        set(20, Gui.item(Material.GOLD_INGOT, Gui.GOLD + "Totals",
                Gui.line(readable, "Total staked", money(stats.stakedCents())),
                Gui.line(readable, "Gross payouts", money(stats.grossPayoutCents())),
                Gui.line(readable, "Net result", signed(stats.netCents())),
                Gui.MUTED + "Gross = received from wins.",
                Gui.MUTED + "Net = gross minus everything staked."));
        set(21, Gui.item(Material.DIAMOND, Gui.GOLD + "Records",
                Gui.line(readable, "Largest payout", money(stats.largestPayoutCents())),
                Gui.line(readable, "Largest net win", money(stats.largestNetWinCents())),
                Gui.line(readable, "Average stake", money(stats.averageStakeCents()))));
        CoinSide favourite = stats.favouriteSide();
        set(22, Gui.item(Material.SUNFLOWER, Gui.INFO + "Sides",
                Gui.line(readable, "Heads picked", Long.toString(stats.headsPicks())),
                Gui.line(readable, "Tails picked", Long.toString(stats.tailsPicks())),
                Gui.line(readable, "Favourite", favourite == null ? "none" : favourite.displayName())));
        set(23, Gui.item(Material.PAPER, Gui.INFO + "Match types",
                Gui.line(readable, "Public", Long.toString(stats.publicMatches())),
                Gui.line(readable, "Challenges", Long.toString(stats.privateMatches())),
                Gui.line(readable, "Joined others' matches", Long.toString(stats.joinedMatches())),
                Gui.line(readable, "Practice flips", Long.toString(stats.practiceFlips()))));
        set(24, Gui.item(Material.CLOCK, Gui.INFO + "Time",
                Gui.line(readable, "Time in matches", Gui.duration(stats.totalDurationMillis())),
                Gui.line(readable, "Active days", Long.toString(stats.activeDays())),
                Gui.line(readable, "First match", stats.firstMatchMillis() == 0 ? "-" : DATE.format(Instant.ofEpochMilli(stats.firstMatchMillis()))),
                Gui.line(readable, "Last match", stats.lastMatchMillis() == 0 ? "-" : DATE.format(Instant.ofEpochMilli(stats.lastMatchMillis())))));
        set(25, Gui.item(Material.COMPASS, Gui.INFO + "This week / month",
                Gui.line(readable, "Matches this week", Long.toString(loaded.weekPlayed())),
                Gui.line(readable, "Net this week", signed(loaded.weekNet())),
                Gui.line(readable, "Matches this month", Long.toString(loaded.monthPlayed())),
                Gui.line(readable, "Net this month", signed(loaded.monthNet()))));

        button(40, Gui.item(Material.WRITABLE_BOOK, Gui.INFO + "Match history"), () -> openMenu(
                new HistoryMenu(module, viewer, subject, subjectName, 0, CoinFlipDao.HistoryFilter.ALL)));
        if (self) {
            button(49, Gui.item(Material.NETHER_STAR, Gui.GOLD + "Achievements"), () -> openMenu(new AchievementsMenu(module, viewer)));
        }
    }

    private String signed(long cents) {
        return (cents >= 0 ? Gui.GOOD + "+" : Gui.BAD + "-") + money(Math.abs(cents));
    }
}
