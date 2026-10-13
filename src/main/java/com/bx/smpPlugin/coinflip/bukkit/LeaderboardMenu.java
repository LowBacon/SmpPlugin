package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Cached rankings; opening this menu never queries storage. Players who made their profile private are left out. */
public final class LeaderboardMenu extends CoinFlipMenu {

    private static final Map<CoinFlipDao.Metric, String[]> INFO = Map.ofEntries(
            Map.entry(CoinFlipDao.Metric.WINS, new String[]{"Most wins", "Finished matches won."}),
            Map.entry(CoinFlipDao.Metric.PLAYED, new String[]{"Most matches", "Finished matches played."}),
            Map.entry(CoinFlipDao.Metric.WIN_RATE, new String[]{"Best win rate", "Wins divided by finished matches; needs a minimum number of matches."}),
            Map.entry(CoinFlipDao.Metric.GROSS_PAYOUT, new String[]{"Highest gross payouts", "Everything received from wins, before subtracting stakes."}),
            Map.entry(CoinFlipDao.Metric.NET_PROFIT, new String[]{"Highest net result", "Payouts received minus everything staked."}),
            Map.entry(CoinFlipDao.Metric.LARGEST_PAYOUT, new String[]{"Largest single payout", "The biggest pot won in one match."}),
            Map.entry(CoinFlipDao.Metric.BEST_STREAK, new String[]{"Longest win streak", "Most wins in a row."}),
            Map.entry(CoinFlipDao.Metric.WEEKLY_PLAYED, new String[]{"Most active this week", "Finished matches this week."}),
            Map.entry(CoinFlipDao.Metric.MONTHLY_PLAYED, new String[]{"Most active this month", "Finished matches this month."}),
            Map.entry(CoinFlipDao.Metric.WEEKLY_NET, new String[]{"Best net this week", "Net result this week."}),
            Map.entry(CoinFlipDao.Metric.MONTHLY_NET, new String[]{"Best net this month", "Net result this month."}));
    private static final Material[] MEDALS = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};

    private final CoinFlipDao.Metric metric;

    public LeaderboardMenu(CoinFlipModule module, UUID viewer, CoinFlipDao.Metric metric) {
        super(module, viewer, "Top: " + INFO.get(metric)[0], 54);
        this.metric = metric;
    }

    @Override
    protected void render(Player player) {
        boolean readable = readable();
        List<CoinFlipDao.RankRow> rows = module.leaderboards().top(metric);
        long age = System.currentTimeMillis() - module.leaderboards().refreshedAtMillis();
        set(4, Gui.item(Material.GOLDEN_HELMET, Gui.GOLD + INFO.get(metric)[0], List.of(
                Gui.MUTED + INFO.get(metric)[1],
                Gui.line(readable, "Updated", module.leaderboards().refreshedAtMillis() == 0 ? "not yet" : Gui.duration(age) + " ago"),
                Gui.MUTED + "Ties: fewer matches played ranks higher.")));
        if (rows.isEmpty()) {
            set(22, Gui.item(Material.STRUCTURE_VOID, Gui.MUTED + "No entries yet"));
        }
        for (int i = 0; i < rows.size() && i < Gui.INNER_54.length; i++) {
            CoinFlipDao.RankRow row = rows.get(i);
            String rank = "#" + (i + 1) + " ";
            List<String> lore = List.of(
                    Gui.line(readable, INFO.get(metric)[0], format(row.value())),
                    Gui.line(readable, "Finished matches", Long.toString(row.played())));
            if (i < MEDALS.length && !readable) {
                set(Gui.INNER_54[i], Gui.glow(Gui.head(row.playerId(), Gui.GOLD + rank + Gui.VALUE + row.playerName(), lore), true));
            } else {
                set(Gui.INNER_54[i], Gui.head(row.playerId(), Gui.MUTED + rank + Gui.VALUE + row.playerName(), lore));
            }
        }
        back(() -> openMenu(new MainMenu(module, viewer)));
        CoinFlipDao.Metric[] metrics = CoinFlipDao.Metric.values();
        button(49, Gui.item(Material.HOPPER, Gui.INFO + "Category: " + Gui.VALUE + INFO.get(metric)[0],
                Gui.MUTED + "Left-click: next", Gui.MUTED + "Right-click: previous"), click -> {
            int step = click.isRightClick() ? metrics.length - 1 : 1;
            openMenu(new LeaderboardMenu(module, viewer, metrics[(metric.ordinal() + step) % metrics.length]));
        });
    }

    private String format(long value) {
        return switch (metric) {
            case GROSS_PAYOUT, NET_PROFIT, LARGEST_PAYOUT, WEEKLY_NET, MONTHLY_NET -> money(value);
            case WIN_RATE -> String.format(Locale.ROOT, "%.2f%%", value / 100D);
            default -> Long.toString(value);
        };
    }
}
