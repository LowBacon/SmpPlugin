package com.bx.smpPlugin.coinflip;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One match. Identity, stake and terms never change after creation; the opponent and outcome are set once.
 * State changes go through {@link #transition}, a compare-and-set that also enforces {@link MatchState}'s
 * legal transitions, so a join and a cancel racing for the same match can never both win.
 */
public final class CoinFlipMatch {

    private final String id;
    private final UUID creatorId;
    private final String creatorName;
    private final CoinSide creatorSide;
    private final boolean privateMatch;
    private final UUID inviteeId;
    private final String inviteeName;
    private final long stakeCents;
    private final int feeBasisPoints;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final AtomicReference<MatchState> state;

    private volatile UUID opponentId;
    private volatile String opponentName;
    private volatile CoinSide outcome;
    private volatile String outcomeId;
    private volatile long joinedAtMillis;
    private volatile long settledAtMillis;
    private volatile String endReason;

    public CoinFlipMatch(String id, UUID creatorId, String creatorName, CoinSide creatorSide, boolean privateMatch,
                         UUID inviteeId, String inviteeName, long stakeCents, int feeBasisPoints,
                         long createdAtMillis, long expiresAtMillis, MatchState state) {
        this.id = id;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.creatorSide = creatorSide;
        this.privateMatch = privateMatch;
        this.inviteeId = inviteeId;
        this.inviteeName = inviteeName;
        this.stakeCents = stakeCents;
        this.feeBasisPoints = feeBasisPoints;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.state = new AtomicReference<>(state);
    }

    /**
     * Moves from {@code expected} to {@code next} if the match is still in {@code expected} and the
     * transition is legal. Returns false (and changes nothing) otherwise.
     */
    public boolean transition(MatchState expected, MatchState next) {
        if (!expected.canMoveTo(next)) {
            return false;
        }
        return state.compareAndSet(expected, next);
    }

    /** Rebuilds a match exactly as stored. Used by storage only; live code creates matches with the constructor. */
    public static CoinFlipMatch restore(String id, UUID creatorId, String creatorName, CoinSide creatorSide,
                                        boolean privateMatch, UUID inviteeId, String inviteeName, long stakeCents,
                                        int feeBasisPoints, long createdAtMillis, long expiresAtMillis, MatchState state,
                                        UUID opponentId, String opponentName, CoinSide outcome, String outcomeId,
                                        long joinedAtMillis, long endedAtMillis, String endReason) {
        CoinFlipMatch match = new CoinFlipMatch(id, creatorId, creatorName, creatorSide, privateMatch, inviteeId,
                inviteeName, stakeCents, feeBasisPoints, createdAtMillis, expiresAtMillis, state);
        match.opponentId = opponentId;
        match.opponentName = opponentName;
        match.outcome = outcome;
        match.outcomeId = outcomeId;
        match.joinedAtMillis = joinedAtMillis;
        match.settledAtMillis = endedAtMillis;
        match.endReason = endReason;
        return match;
    }

    /** Forces the in-memory state to the reconciled one; never used for live transitions. */
    void restoreState(MatchState restored) {
        state.set(restored);
    }

    public MatchState state() {
        return state.get();
    }

    /** Sets the opponent, outcome and settlement terms once. Returns false if they were already set. */
    synchronized boolean lock(UUID opponentId, String opponentName, CoinSide outcome, String outcomeId, long joinedAtMillis) {
        if (this.outcome != null) {
            return false;
        }
        this.opponentId = opponentId;
        this.opponentName = opponentName;
        this.outcome = outcome;
        this.outcomeId = outcomeId;
        this.joinedAtMillis = joinedAtMillis;
        return true;
    }

    /** Clears a reserved-but-failed join so the match can be joined again. Only legal before an outcome exists. */
    synchronized void clearPendingOpponent() {
        if (outcome == null) {
            opponentId = null;
            opponentName = null;
        }
    }

    synchronized void setPendingOpponent(UUID opponentId, String opponentName) {
        if (outcome == null) {
            this.opponentId = opponentId;
            this.opponentName = opponentName;
        }
    }

    void markEnded(long atMillis, String reason) {
        this.settledAtMillis = atMillis;
        this.endReason = reason;
    }

    public UUID winnerId() {
        if (outcome == null || opponentId == null) {
            return null;
        }
        return outcome == creatorSide ? creatorId : opponentId;
    }

    public UUID loserId() {
        UUID winner = winnerId();
        if (winner == null) {
            return null;
        }
        return winner.equals(creatorId) ? opponentId : creatorId;
    }

    public String winnerName() {
        UUID winner = winnerId();
        if (winner == null) {
            return null;
        }
        return winner.equals(creatorId) ? creatorName : opponentName;
    }

    public long potCents() {
        return StakeMath.pot(stakeCents);
    }

    public long feeCents() {
        return StakeMath.fee(potCents(), feeBasisPoints);
    }

    public long payoutCents() {
        return StakeMath.payout(stakeCents, feeBasisPoints);
    }

    public boolean involves(UUID playerId) {
        return creatorId.equals(playerId) || playerId.equals(opponentId);
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public String id() {
        return id;
    }

    public UUID creatorId() {
        return creatorId;
    }

    public String creatorName() {
        return creatorName;
    }

    public CoinSide creatorSide() {
        return creatorSide;
    }

    public boolean isPrivate() {
        return privateMatch;
    }

    public UUID inviteeId() {
        return inviteeId;
    }

    public String inviteeName() {
        return inviteeName;
    }

    public long stakeCents() {
        return stakeCents;
    }

    public int feeBasisPoints() {
        return feeBasisPoints;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public UUID opponentId() {
        return opponentId;
    }

    public String opponentName() {
        return opponentName;
    }

    public CoinSide outcome() {
        return outcome;
    }

    public String outcomeId() {
        return outcomeId;
    }

    public long joinedAtMillis() {
        return joinedAtMillis;
    }

    public long settledAtMillis() {
        return settledAtMillis;
    }

    public String endReason() {
        return endReason;
    }
}
