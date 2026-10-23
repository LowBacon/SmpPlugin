// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public abstract class DialogScreen {

    protected final SmpPlugin plugin;
    protected final DialogConfig config;
    protected final DialogSession.Store sessions;

    protected DialogScreen(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        this.plugin = plugin;
        this.config = config;
        this.sessions = sessions;
    }

    public abstract boolean handle(Player player, String action, DialogResponseView response);

    protected DialogSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    protected DialogFactory.Screen screen(Player player) {
        DialogSession session = session(player);
        // NONE keeps the current overlay up so the next screen can replace it in place.
        // CLOSE drops the client back to the world and Minecraft recenters the mouse.
        return DialogFactory.screen(player, session::registerCommand)
                .afterAction(DialogBase.DialogAfterAction.NONE);
    }

    protected boolean show(Player player, Dialog dialog) {
        return DialogSupport.show(player, dialog);
    }

    protected void message(Player player, String legacy) {
        if (player != null && legacy != null && !legacy.isBlank()) {
            player.sendMessage(ColorUtils.toComponent(legacy, player));
        }
    }

    protected void click(Player player) {
        // Minecraft client dialog buttons already play UI_BUTTON_CLICK natively.
        // Playing it from the server causes an echo / double-sound artifact.
    }

    protected void runCommand(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String trimmed = command.startsWith("/") ? command.substring(1) : command;
        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (player.isOnline()) {
                plugin.getSpigotScheduler().dispatchPlayerCommand(player, trimmed);
            }
        });
    }

    protected String input(DialogResponseView response, String key) {
        if (response == null) {
            return null;
        }
        String value = response.getText(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected String backAction(Player player, String fallback) {
        String overlay = session(player).namespacedOverlayReturn();
        return overlay == null ? fallback : overlay;
    }

    protected String backActionId(Player player, String fallbackId) {
        String overlay = session(player).overlayReturnAction();
        return overlay == null ? fallbackId : overlay;
    }

    protected List<DialogConfig.ButtonSpec> withOverlayBack(
            Player player,
            List<DialogConfig.ButtonSpec> buttons,
            String defaultBackId
    ) {
        String overlay = session(player).namespacedOverlayReturn();
        if (overlay == null || buttons == null || buttons.isEmpty()) {
            return buttons;
        }
        List<DialogConfig.ButtonSpec> rewritten = new ArrayList<>(buttons.size());
        for (DialogConfig.ButtonSpec spec : buttons) {
            if (spec.action() != null && spec.action().contains(defaultBackId)) {
                rewritten.add(new DialogConfig.ButtonSpec(
                        spec.label(), spec.tooltip(), spec.width(), overlay, spec.command()));
            } else {
                rewritten.add(spec);
            }
        }
        return rewritten;
    }

    protected static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    protected OfflinePlayer resolvePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        var account = plugin.getEconomyManager().resolveAccount(name);
        if (account == null) {
            return null;
        }
        return plugin.getServer().getOfflinePlayer(account.uuid());
    }

    protected String displayName(UUID uuid, String fallback) {
        String resolved = plugin.getEconomyManager().getDisplayName(uuid);
        return resolved == null || resolved.isBlank() ? fallback : resolved;
    }

    protected static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
