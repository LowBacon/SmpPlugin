package com.bx.smpPlugin.economy;

import java.util.UUID;

/**
 * One row of the economy ledger: a single balance change of one player.
 *
 * @param txId              unique id of this row
 * @param groupId           shared by the rows of one transaction (both sides of a payment); may equal {@code txId}
 * @param createdAt         epoch millis
 * @param player            whose balance changed
 * @param counterparty      the other player of a transfer, or null
 * @param reason            {@link com.bx.smpPlugin.models.EconomyReason} name
 * @param amountCents       signed change in cents (negative for a debit)
 * @param balanceAfterCents balance after the change
 * @param balanceVersion    the balance version written with the change
 * @param actor             who caused it when it was not the player (an admin, the console, a plugin), or null
 * @param note              free text, or null
 */
public record LedgerEntry(String txId, String groupId, long createdAt, UUID player, UUID counterparty, String reason,
                          long amountCents, long balanceAfterCents, long balanceVersion, String actor, String note) {

    /** Longest note stored; longer notes are cut. */
    public static final int MAX_NOTE = 255;
    /** Longest actor name stored. */
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
