// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.TPAManager;
import com.bx.smpPlugin.menus.TpaConfirmMenu;
import com.bx.smpPlugin.menus.TpaQueueMenu;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.CommandLabelUtils;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TPACommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public TPACommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        String sub = CommandLabelUtils.normalizeLabel(label, command);
        switch (sub) {
            case "tpa" -> handleTpa(player, args);
            case "tpahere" -> handleTpaHere(player, args);
            case "tpaccept" -> handleAccept(player, args);
            case "tpadeny" -> handleDeny(player);
            case "tpacancel" -> {
                plugin.getTPAManager().cancelRequestsByRequester(player.getUniqueId());
                send(player, plugin.getConfigManager().getMessage("TPA.CANCELLED-REQUESTS"));
            }
            default -> {
            }
        }
        return true;
    }

    private void handleTpa(Player player, String[] args) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            send(player, plugin.getCombatManager().getBlockMessage());
            return;
        }

        if (args.length == 0) {
            if (plugin.getDialogManager() != null && plugin.getDialogManager().openTpaPicker(player)) {
                return;
            }
            new TpaQueueMenu(plugin, false).open(player);
            return;
        }

        Player target = plugin.getHideManager().findOnlinePlayer(player, args[0]);
        if (target == null || target.equals(player)) {
            send(player, target == null ? "&cPlayer not online."
                    : plugin.getConfigManager().getMessage("TPA.CANNOT-INVITE-YOURSELF"));
            return;
        }

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(target.getUniqueId())) {
            send(player, "&c" + target.getName() + " is currently in combat.");
            return;
        }

        if (plugin.getFriendsManager() != null && plugin.getFriendsManager().isTeleportRequestBlocked(player.getUniqueId(), target.getUniqueId())) {
            send(player, "&c" + target.getName() + " has disabled teleport requests from you.");
            return;
        }

        if (plugin.getFriendsManager() != null && plugin.getFriendsManager().isTpaAutoAcceptEnabled(player.getUniqueId(), target.getUniqueId())) {
            plugin.getSpigotScheduler().runEntity(player, () -> {
                plugin.getTeleportManager().queue(player, target.getLocation(), "TPA", null);
                send(player, plugin.getConfigManager().getMessage("TPA.YOUR-REQUEST-ACCEPTED", "{player}", target.getName()));
                target.sendMessage(ColorUtils.toComponent("&7Auto-accepted teleport request from &b" + player.getName() + "&7."));
            });
            return;
        }

        PlayerData targetData = plugin.getPlayerDataManager().get(target);
        if (targetData != null) {
            com.bx.smpPlugin.models.ThreeChoice choice = targetData.getTpaRequestsChoice();
            if (choice == com.bx.smpPlugin.models.ThreeChoice.OFF) {
                int queuePosition = plugin.getTPAManager().queueManualTPA(player, target);
                if (queuePosition == 0) {
                    sendAlreadySent(player, target);
                    return;
                }

                send(player, plugin.getConfigManager().getMessage("TPA.INVITE-SENT", "{player}", publicName(target)));
                send(player, "&7Your /tpa request was stored in &b" + publicName(target)
                        + "&7's queue &8(#" + queuePosition + "&8).");
                playNotification(player, "TPA.REQUEST-SENT");
                return;
            } else if (choice == com.bx.smpPlugin.models.ThreeChoice.FRIENDS_FOLLOWED) {
                boolean isFriendOrFollowed = plugin.getFriendsManager() != null && plugin.getFriendsManager().isFollowing(target.getUniqueId(), player.getUniqueId());
                if (!isFriendOrFollowed) {
                    send(player, "&c" + target.getName() + " only accepts teleport requests from friends/followed.");
                    return;
                }
            }
        }

        if (targetData != null && targetData.isTpauto()) {
            int queuePosition = plugin.getTPAManager().queueAutoTPA(player, target, false);
            if (queuePosition == 0) {
                sendAlreadySent(player, target);
                return;
            }

            send(player, plugin.getConfigManager().getMessage("TPA.INVITE-SENT", "{player}", publicName(target)));
            if (queuePosition > 1) {
                send(player, "&7Your /tpa request was stored in &b" + publicName(target)
                        + "&7's auto-accept queue &8(#" + queuePosition + "&8).");
            }
            playNotification(player, "TPA.REQUEST-SENT");
            plugin.getTPAManager().processQueuedAutoRequests(target.getUniqueId());
            return;
        }

        if (!plugin.getTPAManager().sendTPA(player, target)) {
            sendAlreadySent(player, target);
            return;
        }
        send(player, plugin.getConfigManager().getMessage("TPA.INVITE-SENT", "{player}", publicName(target)));
        playNotification(player, "TPA.REQUEST-SENT");
        sendIncomingRequest(player, target, false);
    }

    private void handleTpaHere(Player player, String[] args) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            send(player, plugin.getCombatManager().getBlockMessage());
            return;
        }

        if (args.length == 0) {
            if (plugin.getDialogManager() != null && plugin.getDialogManager().openTpaPicker(player)) {
                return;
            }
            new TpaQueueMenu(plugin, true).open(player);
            return;
        }

        Player target = plugin.getHideManager().findOnlinePlayer(player, args[0]);
        if (target == null || target.equals(player)) {
            send(player, target == null ? "&cPlayer not online."
                    : plugin.getConfigManager().getMessage("TPA.CANNOT-INVITE-YOURSELF"));
            return;
        }

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(target.getUniqueId())) {
            send(player, "&c" + target.getName() + " is currently in combat.");
            return;
        }

        if (plugin.getFriendsManager() != null && plugin.getFriendsManager().isTeleportRequestBlocked(player.getUniqueId(), target.getUniqueId())) {
            send(player, "&c" + target.getName() + " has disabled teleport requests from you.");
            return;
        }

        if (plugin.getFriendsManager() != null && plugin.getFriendsManager().isTpaAutoAcceptEnabled(player.getUniqueId(), target.getUniqueId())) {
            plugin.getSpigotScheduler().runEntity(target, () -> {
                plugin.getTeleportManager().queue(target, player.getLocation(), "TPAHERE", null);
                send(player, plugin.getConfigManager().getMessage("TPA.YOUR-REQUEST-HERE-ACCEPTED", "{player}", target.getName()));
                target.sendMessage(ColorUtils.toComponent("&7Auto-accepted teleport request from &b" + player.getName() + "&7."));
            });
            return;
        }

        PlayerData targetData = plugin.getPlayerDataManager().get(target);
        if (targetData != null) {
            com.bx.smpPlugin.models.ThreeChoice choice = targetData.getTpaHereRequestsChoice();
            if (choice == com.bx.smpPlugin.models.ThreeChoice.OFF) {
                int queuePosition = plugin.getTPAManager().queueManualTPAHere(player, target);
                if (queuePosition == 0) {
                    sendAlreadySent(player, target);
                    return;
                }

                send(player, plugin.getConfigManager().getMessage("TPA.INVITE-HERE-SENT", "{player}", publicName(target)));
                send(player, "&7Your /tpahere request was added to &b" + publicName(target)
                        + "&7's queue &8(#" + queuePosition + "&8).");
                playNotification(player, "TPA.REQUEST-SENT");
                return;
            } else if (choice == com.bx.smpPlugin.models.ThreeChoice.FRIENDS_FOLLOWED) {
                boolean isFriendOrFollowed = plugin.getFriendsManager() != null && plugin.getFriendsManager().isFollowing(target.getUniqueId(), player.getUniqueId());
                if (!isFriendOrFollowed) {
                    send(player, "&c" + target.getName() + " only accepts teleport-here requests from friends/followed.");
                    return;
                }
            }
        }

        if (targetData != null && targetData.isAutoTpaHereEnabled()) {
            int queuePosition = plugin.getTPAManager().queueAutoTPAHere(player, target, false);
            if (queuePosition == 0) {
                sendAlreadySent(player, target);
                return;
            }

            send(player, plugin.getConfigManager().getMessage("TPA.INVITE-HERE-SENT", "{player}", publicName(target)));
            if (queuePosition > 1) {
                send(player, "&7Your /tpahere request was added to &b" + publicName(target)
                        + "&7's auto-accept queue &8(#" + queuePosition + "&8).");
            }
            playNotification(player, "TPA.REQUEST-SENT");
            plugin.getTPAManager().processQueuedAutoRequests(target.getUniqueId());
            return;
        }

        if (!plugin.getTPAManager().sendTPAHere(player, target)) {
            sendAlreadySent(player, target);
            return;
        }
        send(player, plugin.getConfigManager().getMessage("TPA.INVITE-HERE-SENT", "{player}", publicName(target)));
        playNotification(player, "TPA.REQUEST-SENT");
        sendIncomingRequest(player, target, true);
    }

    private void handleAccept(Player player, String[] args) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            send(player, plugin.getCombatManager().getBlockMessage());
            return;
        }

        TPAManager.TpaRequest request = plugin.getTPAManager().getRequest(player.getUniqueId());
        if (request == null) {
            send(player, plugin.getConfigManager().getMessage("TPA.NO-REQUEST",
                    "{player}", args.length > 0 ? args[0] : ""));
            return;
        }

        plugin.getTPAManager().acceptPendingRequest(player);
    }

    private void handleDeny(Player player) {
        if (!plugin.getTPAManager().hasRequest(player.getUniqueId())) {
            send(player, plugin.getConfigManager().getMessage("TPA.NO-REQUEST", "{player}", ""));
            return;
        }

        TPAManager.TpaRequest request = plugin.getTPAManager().getRequest(player.getUniqueId());
        plugin.getTPAManager().removeRequest(player.getUniqueId());
        send(player, "&7TPA request denied.");
        if (request == null) {
            return;
        }

        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            requester.sendMessage(ColorUtils.toComponent("&7Your teleport request was denied."));
        }
    }

    private void sendAlreadySent(Player player, Player target) {
        send(player, plugin.getConfigManager().getMessage("TPA.ALREADY-SENT", "{player}", publicName(target)));
    }

    private void send(Player player, String message) {
        player.sendMessage(ColorUtils.toComponent(message));
    }

    private void sendIncomingRequest(Player requester, Player target, boolean tpaHere) {
        String requesterName = plugin.getHideManager().plainPublicName(requester);
        String requesterDisplayName = publicName(requester);
        UUID requesterUuid = requester.getUniqueId();

        PlayerData targetData = plugin.getPlayerDataManager().get(target);
        boolean alertsEnabled = targetData == null || targetData.isTeleportAlertsEnabled();

        plugin.getSpigotScheduler().runEntity(target, () -> {
            if (!target.isOnline()) {
                return;
            }

            if (alertsEnabled) {
                playNotification(target, "TPA.REQUEST-RECEIVED");

                String messagePath = tpaHere ? "TPA.REQUEST-HERE-RECEIVED" : "TPA.REQUEST-RECEIVED";
                String message = plugin.getConfigManager().getMessage(messagePath, "{player}", requesterDisplayName);
                sendClickableAccept(target, message, requesterUuid, requesterName, tpaHere);

                if (shouldOpenTpaConfirmMenu(target)) {
                    plugin.getSpigotScheduler().runEntityLater(target,
                            () -> openTpaConfirmMenuIfPending(target, requesterUuid, requesterName, tpaHere),
                            1L);
                }
            }
        });
    }

    private void sendClickableAccept(
            Player target,
            String message,
            UUID requesterUuid,
            String requesterName,
            boolean tpaHere
    ) {
        boolean sent = plugin.getDialogManager() != null && plugin.getDialogManager().sendClickAction(
                target,
                message,
                "&eClick to accept",
                clicker -> acceptFromChat(clicker, requesterUuid, tpaHere)
        );
        if (sent) {
            return;
        }
        TextComponent requestMsg = ColorUtils.toBaseComponent(message);
        requestMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + requesterName));
        target.spigot().sendMessage(requestMsg);
    }

    private void acceptFromChat(Player clicker, UUID requesterUuid, boolean tpaHere) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(clicker.getUniqueId())) {
            clicker.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            return;
        }
        TPAManager.TpaRequest request = plugin.getTPAManager().getRequest(clicker.getUniqueId());
        if (request == null || !request.requester().equals(requesterUuid) || request.tpaHere() != tpaHere) {
            return;
        }
        clicker.closeInventory();
        plugin.getTPAManager().acceptPendingRequest(clicker);
    }

    private void playNotification(Player player, String path) {
        SoundUtils.play(
                plugin,
                player,
                plugin.getConfigManager().getSound(path),
                PlayerSettingUtils.SoundChannel.NOTIFICATION
        );
    }

    private boolean shouldOpenTpaConfirmMenu(Player target) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(target.getUniqueId())) {
            return false;
        }
        PlayerData currentTargetData = plugin.getPlayerDataManager().get(target);
        return currentTargetData == null || currentTargetData.isTpaConfirmMenuEnabled();
    }

    private void openTpaConfirmMenuIfPending(Player target, UUID requesterUuid, String requesterName, boolean tpaHere) {
        if (!target.isOnline() || !shouldOpenTpaConfirmMenu(target)) {
            return;
        }

        TPAManager.TpaRequest request = plugin.getTPAManager().getRequest(target.getUniqueId());
        if (request == null || !request.requester().equals(requesterUuid) || request.tpaHere() != tpaHere) {
            return;
        }

        if (plugin.getDialogManager() != null
                && plugin.getDialogManager().openTpaRequest(target, requesterUuid, requesterName, tpaHere)) {
            return;
        }
        new TpaConfirmMenu(plugin, requesterName, tpaHere).open(target);
    }

    private String publicName(Player player) {
        return plugin.getHideManager().publicName(player);
    }
}
