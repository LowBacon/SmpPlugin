// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.UUID;

public record CoinFlipStats(
        UUID playerId,
        String playerName,
        long played,
        long wins,
        long losses,
        long stakedCents,
        long grossPayoutCents,
        long netCents,
        long largestPayoutCents,
        long largestNetWinCents,
        long currentStreak,
        long bestStreak,
        long headsPicks,
        long tailsPicks,
        long publicMatches,
        long privateMatches,
        long joinedMatches,
        long practiceFlips,
        long totalDurationMillis,
        long firstMatchMillis,
        long lastMatchMillis,
        long activeDays,
        String lastActiveDay,
        boolean hidden
) {

    public static CoinFlipStats empty(UUID playerId, String playerName) {
        return new CoinFlipStats(playerId, playerName, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", false);
    }

    public double winRate() {
        return played == 0 ? -1D : wins * 100D / played;
    }

    public long averageStakeCents() {
        return played == 0 ? 0L : stakedCents / played;
    }

    public CoinSide favouriteSide() {
        if (headsPicks == tailsPicks) {
            return null;
        }
        return headsPicks > tailsPicks ? CoinSide.HEADS : CoinSide.TAILS;
    }

    public CoinFlipStats afterMatch(String name, boolean won, long stake, long payout, CoinSide pick,
                                    boolean privateMatch, boolean joined, long durationMillis, long nowMillis, String day) {
        long net = won ? payout - stake : -stake;
        long streak = won ? currentStreak + 1 : 0;
        boolean newDay = !day.equals(lastActiveDay);
        return new CoinFlipStats(
                playerId,
                name == null ? playerName : name,
                played + 1,
                wins + (won ? 1 : 0),
                losses + (won ? 0 : 1),
                stakedCents + stake,
                grossPayoutCents + (won ? payout : 0),
                netCents + net,
                won ? Math.max(largestPayoutCents, payout) : largestPayoutCents,
                won ? Math.max(largestNetWinCents, net) : largestNetWinCents,
                streak,
                Math.max(bestStreak, streak),
                headsPicks + (pick == CoinSide.HEADS ? 1 : 0),
                tailsPicks + (pick == CoinSide.TAILS ? 1 : 0),
                publicMatches + (privateMatch ? 0 : 1),
                privateMatches + (privateMatch ? 1 : 0),
                joinedMatches + (joined ? 1 : 0),
                practiceFlips,
                totalDurationMillis + Math.max(0L, durationMillis),
                firstMatchMillis == 0 ? nowMillis : firstMatchMillis,
                nowMillis,
                activeDays + (newDay ? 1 : 0),
                day,
                hidden);
    }

    public CoinFlipStats afterPractice() {
        return new CoinFlipStats(playerId, playerName, played, wins, losses, stakedCents, grossPayoutCents, netCents,
                largestPayoutCents, largestNetWinCents, currentStreak, bestStreak, headsPicks, tailsPicks, publicMatches,
                privateMatches, joinedMatches, practiceFlips + 1, totalDurationMillis, firstMatchMillis, lastMatchMillis,
                activeDays, lastActiveDay, hidden);
    }

    public CoinFlipStats withHidden(boolean newHidden) {
        return new CoinFlipStats(playerId, playerName, played, wins, losses, stakedCents, grossPayoutCents, netCents,
                largestPayoutCents, largestNetWinCents, currentStreak, bestStreak, headsPicks, tailsPicks, publicMatches,
                privateMatches, joinedMatches, practiceFlips, totalDurationMillis, firstMatchMillis, lastMatchMillis,
                activeDays, lastActiveDay, newHidden);
    }
}
