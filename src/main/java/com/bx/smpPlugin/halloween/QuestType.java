package com.bx.smpPlugin.halloween;

import java.util.Locale;

/** What a Halloween quest counts. */
public enum QuestType {
    /** Hostile mobs killed by the player. */
    KILL_MONSTERS(false),
    /** One kind of mob killed by the player (TARGET: ZOMBIE). */
    KILL_MOB(true),
    /** One kind of block broken by the player (TARGET: PUMPKIN). */
    BREAK_BLOCK(true),
    /** Knocks with /trickortreat. */
    TRICK_OR_TREAT(false),
    /** Spooky Calendar doors opened. */
    OPEN_DOORS(false),
    /** Halloween Candy eaten. */
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
