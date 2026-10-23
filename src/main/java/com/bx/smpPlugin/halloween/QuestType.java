// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import java.util.Locale;

public enum QuestType {
    KILL_MONSTERS(false),
    KILL_MOB(true),
    BREAK_BLOCK(true),
    TRICK_OR_TREAT(false),
    OPEN_DOORS(false),
    EAT_CANDY(false);

    private final boolean needsTarget;

    QuestType(boolean needsTarget) {
        this.needsTarget = needsTarget;
    }

    public boolean needsTarget() {
        return needsTarget;
    }

    public static QuestType parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
