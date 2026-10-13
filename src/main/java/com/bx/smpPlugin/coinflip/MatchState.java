package com.bx.smpPlugin.coinflip;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a match. Every legal transition is listed in one place; anything else is rejected.
 *
 * <pre>
 * CREATING ─▶ WAITING ─▶ JOINING ─▶ LOCKED ─▶ RESOLVING ─▶ SETTLING ─▶ COMPLETED
 *     │          │          │          └──────────────────────▶ SETTLING
 *     ▼          ▼          ▼
 * CANCELLED  CANCELLING   WAITING (join failed, stake not taken)
 *              │
 *              ▼
 *        CANCELLED / EXPIRED
 * any non-final state ─▶ RECOVERY_REQUIRED ─▶ COMPLETED / CANCELLED (after reconciliation)
 * </pre>
 *
 * <p>RESOLVING, SETTLING and CANCELLING exist only in memory while a money movement is in flight; the
 * database keeps the last durable state (LOCKED or WAITING), which is what recovery starts from.</p>
 */
public enum MatchState {
    /** The creator's stake is being reserved. */
    CREATING,
    /** Listed and joinable. */
    WAITING,
    /** An opponent's stake is being reserved. */
    JOINING,
    /** Both stakes reserved and the outcome stored; not yet paid out. */
    LOCKED,
    /** The reveal animation is playing. */
    RESOLVING,
    /** The payout is being made. */
    SETTLING,
    /** The creator's refund is being made (cancellation or expiry). */
    CANCELLING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    /** A money movement has an unknown outcome; staff must reconcile it. */
    RECOVERY_REQUIRED;

    private static final Map<MatchState, Set<MatchState>> LEGAL = new EnumMap<>(MatchState.class);

    static {
        LEGAL.put(CREATING, EnumSet.of(WAITING, CANCELLED, RECOVERY_REQUIRED));
        LEGAL.put(WAITING, EnumSet.of(JOINING, CANCELLING, RECOVERY_REQUIRED));
        LEGAL.put(JOINING, EnumSet.of(LOCKED, WAITING, RECOVERY_REQUIRED));
        LEGAL.put(LOCKED, EnumSet.of(RESOLVING, SETTLING, COMPLETED, RECOVERY_REQUIRED));
        LEGAL.put(RESOLVING, EnumSet.of(SETTLING, RECOVERY_REQUIRED));
        LEGAL.put(SETTLING, EnumSet.of(COMPLETED, LOCKED, RECOVERY_REQUIRED));
        LEGAL.put(CANCELLING, EnumSet.of(CANCELLED, EXPIRED, WAITING, RECOVERY_REQUIRED));
        LEGAL.put(RECOVERY_REQUIRED, EnumSet.of(COMPLETED, CANCELLED));
        LEGAL.put(COMPLETED, EnumSet.noneOf(MatchState.class));
        LEGAL.put(CANCELLED, EnumSet.noneOf(MatchState.class));
        LEGAL.put(EXPIRED, EnumSet.noneOf(MatchState.class));
    }

    public boolean canMoveTo(MatchState next) {
        return LEGAL.get(this).contains(next);
    }

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }

    /** Counts towards a player's active-match limit and the global cap. */
    public boolean isActive() {
        return !isFinal();
    }

    /** States the database stores; in-flight states are stored as their last durable predecessor. */
    public MatchState durable() {
        return switch (this) {
            case RESOLVING, SETTLING -> LOCKED;
            case CANCELLING -> WAITING;
            default -> this;
        };
    }
}
