// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum MatchState {
    CREATING,
    WAITING,
    JOINING,
    LOCKED,
    RESOLVING,
    SETTLING,
    CANCELLING,
    COMPLETED,
    CANCELLED,
    EXPIRED,
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

    public boolean isActive() {
        return !isFinal();
    }

    public MatchState durable() {
        return switch (this) {
            case RESOLVING, SETTLING -> LOCKED;
            case CANCELLING -> WAITING;
            default -> this;
        };
    }
}
