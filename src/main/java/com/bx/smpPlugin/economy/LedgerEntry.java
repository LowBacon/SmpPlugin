// SmpPlugin by LowBacon
package com.bx.smpPlugin.economy;

import java.util.UUID;

public record LedgerEntry(String txId, String groupId, long createdAt, UUID player, UUID counterparty, String reason,
                          long amountCents, long balanceAfterCents, long balanceVersion, String actor, String note) {

    public static final int MAX_NOTE = 255;
    public static final int MAX_ACTOR = 64;

    public LedgerEntry {
        actor = cut(actor, MAX_ACTOR);
        note = cut(note, MAX_NOTE);
    }

    private static String cut(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
