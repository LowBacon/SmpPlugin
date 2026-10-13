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

/**
 * Shared plumbing for a group of related dialog screens.
 *
 * <p>Each subclass owns one area of the interface — homes, pay, friends — and answers
 * {@link #handle} for the action ids it recognises. Returning false lets
 * {@link DialogManager} try the next screen, so ids never have to be registered up front.
 */
public abstract class DialogScreen {

    protected final SmpPlugin plugin;
    protected final DialogConfig config;
    protected final DialogSession.Store sessions;

    protected DialogScreen(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        this.plugin = plugin;
        this.config = config;
        this.sessions = sessions;
    }

    /**
     * Handles one action id.
     *
     * @param action the id with the namespace already stripped
     * @return true when this screen owned the action
     */
    public abstract boolean handle(Player player, String action, DialogResponseView response);

    protected DialogSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * Starts a dialog for this player with its command buttons wired to their session, so a
     * COMMAND in the config closes the dialog before it runs.
     */
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

    /** Runs a command as the player on the right thread for this server type. */
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

    /** Reads a text input, trimmed, or null when the player left it blank. */
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

    /**
     * Back target when this screen was opened from another flow (Friends details → Pay).
     * Falls back to the screen's own list action when nothing was stacked.
     */
    protected String backAction(Player player, String fallback) {
        String overlay = session(player).namespacedOverlayReturn();
        return overlay == null ? fallback : overlay;
    }

    protected String backActionId(Player player, String fallbackId) {
        String overlay = session(player).overlayReturnAction();
        return overlay == null ? fallbackId : overlay;
    }

    /** Rewrites a config Back button so it returns to the overlay screen instead of this flow's list. */
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

    /** Resolves a name typed into a dialog against online players first, then known accounts. */
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

    /** Keys can only carry lowercase, so a name embedded in an action id comes back folded. */
    protected static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
