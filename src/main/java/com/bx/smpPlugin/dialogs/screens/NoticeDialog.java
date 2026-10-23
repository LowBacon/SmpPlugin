// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.utils.NoticeSpec;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NoticeDialog extends DialogScreen {

    private final Map<UUID, Integer> animationIds = new ConcurrentHashMap<>();

    public NoticeDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    public boolean show(Player player, NoticeSpec notice) {
        if (notice == null) {
            return false;
        }
        DialogFactory.Screen screen = screen(player)
                .title(notice.title() == null || notice.title().isBlank() ? " " : notice.title())
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .columns(Math.max(1, Math.min(2, notice.buttons().size())));
        if (notice.icon() != null && !notice.icon().isBlank()) {
            screen.item(notice.icon());
        }
        for (String line : notice.lines()) {
            screen.text(line == null || line.isEmpty() ? " " : line, 260);
        }
        for (NoticeSpec.Button button : notice.buttons()) {
            if (button.command() == null || button.command().isBlank()) {
                screen.button(button.label(), null, 130, null);
            } else {
                screen.commandButton(button.label(), null, 130, button.command());
            }
        }
        return show(player, screen.build());
    }

    public boolean animate(Player player, List<NoticeSpec> frames, long periodTicks) {
        if (frames == null || frames.isEmpty()) {
            return false;
        }
        int id = animationIds.merge(player.getUniqueId(), 1, Integer::sum);
        if (!show(player, frames.get(0))) {
            return false;
        }
        long period = Math.max(1L, periodTicks);
        for (int i = 1; i < frames.size(); i++) {
            NoticeSpec frame = frames.get(i);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline() && animationIds.getOrDefault(player.getUniqueId(), 0) == id) {
                    show(player, frame);
                }
            }, period * i);
        }
        return true;
    }

    public void forget(UUID playerId) {
        animationIds.remove(playerId);
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        return false;
    }
}
