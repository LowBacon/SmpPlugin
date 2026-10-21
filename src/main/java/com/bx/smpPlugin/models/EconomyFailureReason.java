package com.bx.smpPlugin.models;

public enum EconomyFailureReason {
    NONE,
    INVALID_AMOUNT,
    PLAYER_NOT_FOUND,
    NO_PLAYER_DATA,
    INSUFFICIENT_FUNDS,
    SAME_ACCOUNT,
    /** The change would take a balance above ECONOMY.MAX-BALANCE, or above the largest balance there is. */
    BALANCE_LIMIT,
    /** The database is not connected, so no money may move. */
    STORAGE_UNAVAILABLE,
    /** The database refused the change; nothing was changed. */
    STORAGE_ERROR
}
