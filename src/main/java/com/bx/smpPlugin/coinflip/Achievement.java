package com.bx.smpPlugin.coinflip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Cosmetic milestones. None rewards losing, chasing losses or staking more; most reward taking part.
 * Progress is read from {@link CoinFlipStats}, so an achievement unlocks exactly when its statistic does.
 */
public enum Achievement {
    FIRST_PRACTICE("Warm-up", "Flip a practice coin.", "FEATHER", CoinFlipStats::practiceFlips, 1),
    FIRST_MATCH("Heads or Tails", "Finish your first match.", "GOLD_NUGGET", CoinFlipStats::played, 1),
    FIRST_WIN("Lucky Break", "Win your first match.", "EMERALD", CoinFlipStats::wins, 1),
    PLAYED_10("Regular", "Finish 10 matches.", "IRON_INGOT", CoinFlipStats::played, 10),
    PLAYED_50("Veteran", "Finish 50 matches.", "GOLD_INGOT", CoinFlipStats::played, 50),
    PLAYED_100("Centurion", "Finish 100 matches.", "DIAMOND", CoinFlipStats::played, 100),
    PLAYED_500("Legend of the Coin", "Finish 500 matches.", "NETHERITE_INGOT", CoinFlipStats::played, 500),
    WINS_10("Ten Up", "Win 10 matches.", "EMERALD_BLOCK", CoinFlipStats::wins, 10),
    WINS_50("Fortune Favours", "Win 50 matches.", "GOLD_BLOCK", CoinFlipStats::wins, 50),
    WINS_100("Hundred Wins", "Win 100 matches.", "DIAMOND_BLOCK", CoinFlipStats::wins, 100),
    STREAK_3("Hat Trick", "Win 3 matches in a row.", "BLAZE_POWDER", CoinFlipStats::bestStreak, 3),
    STREAK_5("On a Roll", "Win 5 matches in a row.", "BLAZE_ROD", CoinFlipStats::bestStreak, 5),
    ACTIVE_7_DAYS("Familiar Face", "Play on 7 different days.", "CLOCK", CoinFlipStats::activeDays, 7),
    ACTIVE_30_DAYS("Fixture", "Play on 30 different days.", "RECOVERY_COMPASS", CoinFlipStats::activeDays, 30),
    CHALLENGER("Challenger", "Finish a private challenge.", "PAPER", CoinFlipStats::privateMatches, 1),
    CHALLENGER_10("Rivalry", "Finish 10 private challenges.", "WRITABLE_BOOK", CoinFlipStats::privateMatches, 10),
    GOOD_SPORT("Good Sport", "Join 10 matches other players created.", "TOTEM_OF_UNDYING", CoinFlipStats::joinedMatches, 10);

    private final String displayName;
    private final String description;
    private final String icon;
    private final ToLongFunction<CoinFlipStats> metric;
    private final long target;

    Achievement(String displayName, String description, String icon, ToLongFunction<CoinFlipStats> metric, long target) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.metric = metric;
        this.target = target;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String icon() {
        return icon;
    }

    public long target() {
        return target;
    }

    public long progress(CoinFlipStats stats) {
        return Math.min(target, metric.applyAsLong(stats));
    }

    public boolean reached(CoinFlipStats stats) {
        return metric.applyAsLong(stats) >= target;
    }

    /** Every achievement the stats have reached. Callers insert them idempotently; duplicates are ignored. */
    public static List<Achievement> reachedBy(CoinFlipStats stats) {
        List<Achievement> reached = new ArrayList<>();
        for (Achievement achievement : values()) {
            if (achievement.reached(stats)) {
                reached.add(achievement);
            }
        }
        return reached;
    }
}
