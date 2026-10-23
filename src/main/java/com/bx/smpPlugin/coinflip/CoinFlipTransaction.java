// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.UUID;

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
        STAKE,
        PAYOUT,
        REFUND,
        FEE;

        public boolean isCredit() {
            return this == PAYOUT || this == REFUND;
        }
    }

    public enum State {
        INTENT,
        APPLIED,
        PENDING_CLAIM,
        VOID,
        UNKNOWN
    }

    public CoinFlipTransaction withState(State newState, long atMillis, String newNote) {
        return new CoinFlipTransaction(id, matchId, playerId, playerName, kind, amountCents, newState, reason,
                createdAtMillis, atMillis, newNote == null ? note : newNote);
    }
}
