// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import java.util.List;

public record NoticeSpec(String title, String icon, List<String> lines, List<Button> buttons) {

    public record Button(String label, String command) {
    }

    public NoticeSpec {
        lines = lines == null ? List.of() : List.copyOf(lines);
        buttons = buttons == null ? List.of() : List.copyOf(buttons);
    }
}
