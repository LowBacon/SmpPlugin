// SmpPlugin by LowBacon
package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.utils.NoticeSpec;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class Reveal {

    private Reveal() {
    }

    public static boolean dialog(SmpPlugin plugin, Player player, String title, String symbol, String icon,
                                 List<String> lines, String backLabel, String backCommand) {
        DialogGateway dialogs = plugin.getDialogManager();
        if (dialogs == null || player == null || !player.isOnline()) {
            return false;
        }
        List<NoticeSpec> frames = new ArrayList<>();
        for (int lit = 1; lit <= 4; lit++) {
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                row.append(i < lit ? "&6" + symbol : "&8·").append("  ");
            }
            frames.add(new NoticeSpec(title, icon, List.of(row.toString().trim()), List.of()));
        }
        List<NoticeSpec.Button> buttons = new ArrayList<>();
        if (backCommand != null) {
            buttons.add(new NoticeSpec.Button(backLabel, backCommand));
        }
        buttons.add(new NoticeSpec.Button(Text.get("GENERAL.CLOSE", "&7Close"), null));
        frames.add(new NoticeSpec(title, icon, lines, buttons));
        return dialogs.showNoticeAnimated(player, frames, 5L);
    }
}
