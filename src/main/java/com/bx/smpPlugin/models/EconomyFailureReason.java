package com.bx.smpPlugin.models;

public enum EconomyFailureReason {
    NONE,
    INVALID_AMOUNT,
    PLAYER_NOT_FOUND,
    NO_PLAYER_DATA,
    INSUFFICIENT_FUNDS,
    SAME_ACCOUNT,
    /** The change would take the balance past ECONOMY.MAX-BALANCE (or the 90 trillion precision limit). */
    BALANCE_LIMIT
}
