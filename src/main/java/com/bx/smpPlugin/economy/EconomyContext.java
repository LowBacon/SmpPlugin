package com.bx.smpPlugin.economy;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Extra details for one balance change: who caused it, a note for the ledger, and an optional database step that
 * commits in the same transaction as the balance and ledger rows (for example opening or closing a game hold).
 *
 * @param actor who caused the change when it was not the player themselves (an admin, CONSOLE, a plugin), or null
 * @param note  free text stored with the ledger rows, or null
 * @param step  database work that must commit together with the change, or null
 */
public record EconomyContext(String actor, String note, SqlStep step) {

    public static final EconomyContext NONE = new EconomyContext(null, null, null);

    /** Database work that runs inside the economy's transaction. */
    @FunctionalInterface
    public interface SqlStep {
        void run(Connection connection) throws SQLException;
    }

    public static EconomyContext of(String actor, String note) {
        return new EconomyContext(actor, note, null);
    }

    public static EconomyContext note(String note) {
        return new EconomyContext(null, note, null);
    }

    public EconomyContext withStep(SqlStep newStep) {
        return new EconomyContext(actor, note, newStep);
    }
}
