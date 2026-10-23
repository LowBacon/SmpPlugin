// SmpPlugin by LowBacon
package com.bx.smpPlugin.economy;

import java.sql.Connection;
import java.sql.SQLException;

public record EconomyContext(String actor, String note, SqlStep step) {

    public static final EconomyContext NONE = new EconomyContext(null, null, null);

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
