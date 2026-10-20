package com.bx.smpPlugin.display;

/** Lets tests in other packages read how guide.yml lines are parsed. */
public final class GuideCommandTestAccess {

    private GuideCommandTestAccess() {
    }

    public static String command(Object raw) {
        GuideCommand.Line line = GuideCommand.parse(raw);
        return line == null ? null : line.command();
    }

    public static boolean suggest(Object raw) {
        GuideCommand.Line line = GuideCommand.parse(raw);
        return line != null && line.suggest();
    }
}
