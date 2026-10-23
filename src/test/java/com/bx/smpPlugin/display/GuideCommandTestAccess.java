// SmpPlugin by LowBacon
package com.bx.smpPlugin.display;

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
