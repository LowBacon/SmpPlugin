package com.bx.smpPlugin.utils;

import java.util.List;

/**
 * A simple pop-up: a title, an optional item, a few lines of text and buttons that run commands.
 * Shown as a dialog on Paper and Purpur through {@link DialogGateway#showNotice}; callers fall back
 * to chat when that answers false. Plain data, so the Spigot jar can build one too.
 *
 * @param icon a material name shown above the text, or null
 */
public record NoticeSpec(String title, String icon, List<String> lines, List<Button> buttons) {

    /** A button; a null or blank command just closes the pop-up. */
    public record Button(String label, String command) {
    }

    public NoticeSpec {
        lines = lines == null ? List.of() : List.copyOf(lines);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }
}
