// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.dialogs.DialogSupport;
import com.bx.smpPlugin.dialogs.DialogText;
import com.bx.smpPlugin.managers.TPAManager;
import com.bx.smpPlugin.menus.TpaQueueMenu;
import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// /tpa and /tpahere without a name, and the pop-up when someone sends a request. The picker lists
// who is online with their heads, a name box for anyone else, and the requests waiting for you.
public final class TpaDialog extends DialogScreen {

    private static final String PICKER = "TPA_DIALOG";
    private static final String TARGET = "TPA_TARGET_DIALOG";
    private static final String REQUEST = "TPA_REQUEST_DIALOG";

    public TpaDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    public boolean openPicker(Player player) {
        Map<String, String> tokens = DialogConfig.tokens("player", player.getName());
        int width = config.buttonWidth(PICKER, 120);
        DialogFactory.Screen screen = screen(player)
                .title(config.string(PICKER + ".TITLE", "&5&lTeleport", tokens))
                .externalTitle(config.string(PICKER + ".EXTERNAL-TITLE", "Teleport", tokens))
                .columns(config.columns(PICKER, 3))
                .item(config.string(PICKER + ".ITEM", "ENDER_PEARL"))
                .text(config.string(PICKER + ".TEXT", "&7Pick a player, or type a name below.", tokens),
                        config.integer(PICKER + ".TEXT-WIDTH", 300))
                .input(new DialogConfig.InputSpec("player",
                        config.string(PICKER + ".INPUT-LABEL", "Player name", tokens),
                        200, 16, "", List.of()))
                .commandButton(config.string(PICKER + ".TYPED-TO-LABEL", "&aTeleport to them", tokens),
                        config.string(PICKER + ".TYPED-TO-TOOLTIP", "Ask the player in the box to let you come to them", tokens),
                        width, "tpa $(player)")
                .commandButton(config.string(PICKER + ".TYPED-HERE-LABEL", "&bBring them here", tokens),
                        config.string(PICKER + ".TYPED-HERE-TOOLTIP", "Ask the player in the box to come to you", tokens),
                        width, "tpahere $(player)");

        int waiting = waitingRequests(player);
        if (waiting > 0) {
            screen.button(config.string(PICKER + ".REQUESTS-LABEL", "&eRequests (%count%)",
                            DialogConfig.tokens("count", String.valueOf(waiting))),
                    config.string(PICKER + ".REQUESTS-TOOLTIP", "Requests waiting for your answer", tokens),
                    width, DialogActions.TPA_REQUESTS);
        }

        int limit = Math.max(0, config.integer(PICKER + ".MAX-PLAYERS", 30));
        List<Player> online = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.equals(player) && player.canSee(other))
                .sorted(Comparator.comparing(other -> publicName(other).toLowerCase(java.util.Locale.ROOT)))
                .limit(limit)
                .map(other -> (Player) other)
                .toList();
        String tooltip = config.string(PICKER + ".PLAYER-TOOLTIP", "Teleport to or bring %name%");
        for (Player other : online) {
            String name = publicName(other);
            screen.button(
                    DialogPlayerHeads.withLabel(plugin, other.getUniqueId(), DialogText.of("&f" + name, player)),
                    DialogText.tooltip(DialogConfig.apply(tooltip, DialogConfig.tokens("name", name)), player),
                    width,
                    DialogActions.TPA_TARGET + other.getUniqueId());
        }
        if (online.isEmpty()) {
            screen.text(config.string(PICKER + ".EMPTY", "&8Nobody else is online right now.", tokens));
        }
        screen.exit(config.string(PICKER + ".CLOSE-LABEL", "&7Close", tokens), DialogActions.CLOSE, width);
        return show(player, screen.build());
    }

    private boolean openTarget(Player player, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !player.canSee(target)) {
            message(player, "&cThat player is no longer online.");
            return openPicker(player);
        }
        String name = publicName(target);
        Map<String, String> tokens = DialogConfig.tokens("name", name);
        int width = config.buttonWidth(TARGET, 140);
        DialogFactory.Screen screen = screen(player)
                .title(config.string(TARGET + ".TITLE", "&5&lTeleport with %name%", tokens))
                .columns(config.columns(TARGET, 2))
                .text(DialogPlayerHeads.withLabel(plugin, targetId, DialogText.of("&f" + name, player)))
                .text(config.string(TARGET + ".TEXT", "&7Send %name% a request.", tokens))
                .button(config.string(TARGET + ".TO-LABEL", "&aTeleport to %name%", tokens),
                        config.string(TARGET + ".TO-TOOLTIP", "You go to them once they accept", tokens),
                        width, DialogActions.TPA_TO + targetId)
                .button(config.string(TARGET + ".HERE-LABEL", "&bBring %name% here", tokens),
                        config.string(TARGET + ".HERE-TOOLTIP", "They come to you once they accept", tokens),
                        width, DialogActions.TPA_HERE + targetId);
        screen.exit(config.string(TARGET + ".BACK-LABEL", "&7Back", tokens), DialogActions.TPA, width);
        return show(player, screen.build());
    }

    // Replaces the chest confirm menu when a request arrives. Answering runs /tpaccept or /tpadeny,
    // so every rule of the TPA system (combat, queues, settings) still applies.
    public boolean openRequest(Player target, UUID requesterId, String requesterName, boolean tpaHere) {
        Map<String, String> tokens = DialogConfig.tokens("name", requesterName);
        int width = config.buttonWidth(REQUEST, 120);
        String question = tpaHere
                ? config.string(REQUEST + ".HERE-TEXT", "&f%name% &7wants you to teleport to them.", tokens)
                : config.string(REQUEST + ".TO-TEXT", "&f%name% &7wants to teleport to you.", tokens);
        int seconds = config.integer(REQUEST + ".EXPIRES-SECONDS", 0);
        DialogFactory.Screen screen = screen(target)
                .title(config.string(REQUEST + ".TITLE", "&5&lTeleport request", tokens))
                .externalTitle(config.string(REQUEST + ".EXTERNAL-TITLE", "Teleport request", tokens))
                .columns(2)
                .text(DialogPlayerHeads.withLabel(plugin, requesterId, DialogText.of("&f" + requesterName, target)))
                .text(question, config.integer(REQUEST + ".TEXT-WIDTH", 250));
        if (seconds > 0) {
            screen.text("&8Expires in " + seconds + " seconds.");
        }
        screen.commandButton(config.string(REQUEST + ".DENY-LABEL", "&cDeny", tokens), null, width,
                        "tpadeny " + requesterName)
                .commandButton(config.string(REQUEST + ".ACCEPT-LABEL", "&aAccept", tokens), null, width,
                        "tpaccept " + requesterName);
        return show(target, screen.build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.TPA.equals(action)) {
            return openPicker(player);
        }
        if (DialogActions.TPA_REQUESTS.equals(action)) {
            DialogSupport.close(player);
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline()) {
                    new TpaQueueMenu(plugin, false).open(player);
                }
            }, 1L);
            return true;
        }
        if (action.startsWith(DialogActions.TPA_TARGET)) {
            UUID id = parseUuid(action.substring(DialogActions.TPA_TARGET.length()));
            return id == null || openTarget(player, id);
        }
        if (action.startsWith(DialogActions.TPA_TO) || action.startsWith(DialogActions.TPA_HERE)) {
            boolean here = action.startsWith(DialogActions.TPA_HERE);
            UUID id = parseUuid(action.substring((here ? DialogActions.TPA_HERE : DialogActions.TPA_TO).length()));
            Player target = id == null ? null : Bukkit.getPlayer(id);
            DialogSupport.close(player);
            if (target == null) {
                message(player, "&cThat player is no longer online.");
                return true;
            }
            runCommand(player, (here ? "tpahere " : "tpa ") + publicName(target));
            return true;
        }
        return false;
    }

    private int waitingRequests(Player player) {
        TPAManager tpa = plugin.getTPAManager();
        if (tpa == null) {
            return 0;
        }
        int count = tpa.hasRequest(player.getUniqueId()) ? 1 : 0;
        count += tpa.getQueuedRequests(player.getUniqueId(), false).size();
        count += tpa.getQueuedRequests(player.getUniqueId(), true).size();
        return count;
    }

    private String publicName(Player player) {
        return plugin.getHideManager() != null ? plugin.getHideManager().publicName(player) : player.getName();
    }
}
