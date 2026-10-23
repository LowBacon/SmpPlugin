// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Settlement {

    private Settlement() {
    }

    public record Ledger(long stakesApplied, int stakeCount, long credits, boolean payoutRecorded,
                         Map<UUID, Long> stakeByPlayer, Map<UUID, Long> refundByPlayer, boolean anyUnknown) {
    }

    public static Ledger ledger(List<CoinFlipTransaction> transactions) {
        long stakes = 0L;
        int stakeCount = 0;
        long credits = 0L;
        boolean payout = false;
        boolean unknown = false;
        Map<UUID, Long> stakeBy = new LinkedHashMap<>();
        Map<UUID, Long> refundBy = new LinkedHashMap<>();
        for (CoinFlipTransaction tx : transactions) {
            boolean counts = tx.state() == CoinFlipTransaction.State.APPLIED
                    || tx.state() == CoinFlipTransaction.State.PENDING_CLAIM;
            if (tx.state() == CoinFlipTransaction.State.UNKNOWN || tx.state() == CoinFlipTransaction.State.INTENT) {
                unknown = true;
            }
            switch (tx.kind()) {
                case STAKE -> {
                    if (tx.state() == CoinFlipTransaction.State.APPLIED) {
                        stakes += tx.amountCents();
                        stakeCount++;
                        stakeBy.merge(tx.playerId(), tx.amountCents(), Long::sum);
                    }
                }
                case PAYOUT -> {
                    if (counts) {
                        credits += tx.amountCents();
                        payout = true;
                    }
                }
                case REFUND -> {
                    if (counts) {
                        credits += tx.amountCents();
                        refundBy.merge(tx.playerId(), tx.amountCents(), Long::sum);
                    }
                }
                case FEE -> {
                    // the fee is the part of the pot that is not credited
                }
            }
        }
        return new Ledger(stakes, stakeCount, credits, payout, stakeBy, refundBy, unknown);
    }

    public static Map<UUID, List<Achievement>> finish(CoinFlipDao dao, CoinFlipMatch match, long now, ZoneId zone,
                                                      boolean achievementsEnabled) throws SQLException {
        long fee = match.feeCents();
        UUID winner = match.winnerId();
        if (fee > 0L) {
            dao.insertTransaction(new CoinFlipTransaction(UUID.randomUUID().toString(), match.id(), winner,
                    match.winnerName(), CoinFlipTransaction.Kind.FEE, fee, CoinFlipTransaction.State.APPLIED,
                    "fee " + StakeMath.percent(match.feeBasisPoints()), now, now, "removed from the pot"));
        }
        dao.moveMatch(match.id(), MatchState.LOCKED, MatchState.COMPLETED, now, "completed");
        Map<UUID, List<Achievement>> unlocked = new LinkedHashMap<>();
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String day = date.toString();
        String week = "W" + date.get(IsoFields.WEEK_BASED_YEAR) + "-" + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        String month = "M" + date.getYear() + "-" + String.format("%02d", date.getMonthValue());
        long duration = match.joinedAtMillis() > 0 ? Math.max(0L, now - match.joinedAtMillis()) : 0L;

        for (boolean creator : new boolean[]{true, false}) {
            UUID player = creator ? match.creatorId() : match.opponentId();
            String name = creator ? match.creatorName() : match.opponentName();
            CoinSide pick = creator ? match.creatorSide() : match.creatorSide().opposite();
            boolean won = player.equals(winner);
            long payout = match.payoutCents();
            CoinFlipStats before = dao.loadStats(player, name);
            CoinFlipStats after = before.afterMatch(name, won, match.stakeCents(), payout, pick, match.isPrivate(),
                    !creator, duration, now, day);
            dao.saveStats(after);
            long net = won ? payout - match.stakeCents() : -match.stakeCents();
            for (String period : new String[]{week, month}) {
                dao.addToPeriod(player, name, period, 1, won ? 1 : 0, net, match.stakeCents());
            }
            if (achievementsEnabled) {
                List<Achievement> fresh = new ArrayList<>();
                for (Achievement achievement : Achievement.reachedBy(after)) {
                    if (dao.unlock(player, achievement, now)) {
                        fresh.add(achievement);
                    }
                }
                if (!fresh.isEmpty()) {
                    unlocked.put(player, fresh);
                }
            }
        }
        dao.audit("system", "SETTLED", match.id(), "winner " + match.winnerName() + " outcome " + match.outcome()
                + " payout " + StakeMath.format(match.payoutCents()) + " fee " + StakeMath.format(fee), now);
        return unlocked;
    }

    public static String weekKey(long now, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return "W" + date.get(IsoFields.WEEK_BASED_YEAR) + "-" + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public static String monthKey(long now, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return "M" + date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    public static long startOfDay(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static boolean reconcile(CoinFlipDao dao, String matchId, long now, ZoneId zone, boolean achievementsEnabled,
                                    String actor) throws SQLException {
        // Always decide from the stored row: memory may be ahead of or behind the database after a storage error.
        CoinFlipMatch match = dao.loadMatch(matchId).orElse(null);
        if (match == null || match.state().isFinal()) {
            return match != null;
        }
        List<CoinFlipTransaction> transactions = dao.transactionsForMatch(match.id());
        Ledger ledger = ledger(transactions);
        if (ledger.anyUnknown()) {
            return false;
        }
        MatchState durable = match.state().durable();
        boolean bothStaked = ledger.stakeCount() >= 2 && match.opponentId() != null
                && ledger.stakeByPlayer().containsKey(match.creatorId()) && ledger.stakeByPlayer().containsKey(match.opponentId());
        if (match.outcome() != null && bothStaked) {
            if (!ledger.payoutRecorded()) {
                dao.insertTransaction(new CoinFlipTransaction(UUID.randomUUID().toString(), match.id(), match.winnerId(),
                        match.winnerName(), CoinFlipTransaction.Kind.PAYOUT, match.payoutCents(),
                        CoinFlipTransaction.State.PENDING_CLAIM, "reconciled payout", now, now, "by " + actor));
            }
            if (durable != MatchState.LOCKED) {
                dao.moveMatch(match.id(), durable, MatchState.LOCKED, 0L, null);
            }
            finish(dao, match, now, zone, achievementsEnabled);
            return true;
        }
        for (Map.Entry<UUID, Long> stake : ledger.stakeByPlayer().entrySet()) {
            long refunded = ledger.refundByPlayer().getOrDefault(stake.getKey(), 0L);
            long owed = stake.getValue() - refunded;
            if (owed > 0L) {
                String name = stake.getKey().equals(match.creatorId()) ? match.creatorName() : match.opponentName();
                dao.insertTransaction(new CoinFlipTransaction(UUID.randomUUID().toString(), match.id(), stake.getKey(), name,
                        CoinFlipTransaction.Kind.REFUND, owed, CoinFlipTransaction.State.PENDING_CLAIM,
                        "reconciled refund", now, now, "by " + actor));
            }
        }
        dao.moveMatch(match.id(), durable, MatchState.CANCELLED, now, "reconciled by " + actor);
        dao.audit(actor, "RECONCILED", match.id(), "refunded every stake taken", now);
        return true;
    }
}
