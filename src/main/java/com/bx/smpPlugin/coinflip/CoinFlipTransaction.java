package com.bx.smpPlugin.coinflip;

import java.util.UUID;

/**
 * One money movement in the CoinFlip journal. The pair (match, player, kind) is unique in storage, which makes
 * a second payout, refund or stake for the same match impossible at the database level.
 *
 * @param id          transaction ID (also the idempotency key)
 * @param amountCents always positive; {@link Kind} says which direction it moves
 */
public record CoinFlipTransaction(
        String id,
        String matchId,
        UUID playerId,
        String playerName,
        Kind kind,
        long amountCents,
        State state,
        String reason,
        long createdAtMillis,
        long updatedAtMillis,
        String note
) {

    public enum Kind {
        /** Stake taken from a player. */
        STAKE,
        /** Pot paid to the winner. */
        PAYOUT,
        /** Stake returned after cancellation, expiry or reconciliation. */
        REFUND,
        /** Fee removed from the pot (sink). */
        FEE;

        public boolean isCredit() {
            return this == PAYOUT || this == REFUND;
        }
    }

    public enum State {
        /** Written before the balance changes. Found after a crash, it becomes UNKNOWN. */
        INTENT,
        /** The balance change and its record were stored together. */
        APPLIED,
        /** A credit owed to a player who is offline; paid when they are next online. */
        PENDING_CLAIM,
        /** Abandoned before any balance changed. */
        VOID,
        /** The balance may or may not have changed. Staff decide with /coinflipadmin resolve. */
        UNKNOWN
    }

    public CoinFlipTransaction withState(State newState, long atMillis, String newNote) {
        return new CoinFlipTransaction(id, matchId, playerId, playerName, kind, amountCents, newState, reason,
                createdAtMillis, atMillis, newNote == null ? note : newNote);
    }
}
