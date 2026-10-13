package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.IgnoreManager;
import com.bx.smpPlugin.models.IgnoreEntry;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class IgnoreCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.ignore";

    private final SmpPlugin plugin;

    public IgnoreCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfigManager().isCommandEnabled("IGNORE")) {
            send(sender, message("DISABLED", "&cɪɢɴᴏʀᴇ ᴄᴏᴍᴍᴀɴᴅ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            send(sender, message("PLAYER-ONLY", "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ."));
            return true;
        }

        if (!PermissionUtils.has(player, PERMISSION)) {
            send(player, message("NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        boolean removeOnly = label.equalsIgnoreCase("unignore");
        if (args.length == 0) {
            send(player, removeOnly
                    ? message("UNIGNORE-USAGE", "&cᴜѕᴀɢᴇ: /unignore <player>")
                    : message("USAGE", "&cᴜѕᴀɢᴇ: /ignore <player|list>"));
            return true;
        }

        if (!removeOnly && args[0].equalsIgnoreCase("list")) {
            sendIgnoreList(player);
            return true;
        }

        ResolvedTarget target = resolveTarget(player, args[0]);
        if (target == null) {
            send(player, message("PLAYER-NOT-FOUND", "&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        if (player.getUniqueId().equals(target.uuid())) {
            send(player, message("CANNOT-IGNORE-SELF", "&cʏᴏᴜ ᴄᴀɴɴᴏᴛ ɪɢɴᴏʀᴇ ʏᴏᴜʀѕᴇʟꜰ."));
            return true;
        }

        if (removeOnly) {
            handleRemove(player, target);
            return true;
        }

        IgnoreManager.ToggleResult result = plugin.getIgnoreManager()
                .toggleIgnore(player, target.uuid(), target.name());
        if (result.action() == IgnoreManager.ToggleAction.ADDED) {
            send(player, message("ADDED", "&7%player% &cʜᴀѕ ʙᴇᴇɴ ᴀᴅᴅᴇᴅ ᴛᴏ ʏᴏᴜʀ ɪɢɴᴏʀᴇ ʟɪѕᴛ.")
                    .replace("%player%", target.name()));
            return true;
        }

        if (result.action() == IgnoreManager.ToggleAction.REMOVED) {
            String displayName = result.entry() == null ? target.name() : result.entry().ignoredNameSnapshot();
            send(player, message("REMOVED", "&7%player% &cʜᴀѕ ʙᴇᴇɴ ʀᴇᴍᴏᴠᴇᴅ ꜰʀᴏᴍ ʏᴏᴜʀ ɪɢɴᴏʀᴇ ʟɪѕᴛ.")
                    .replace("%player%", displayName));
            return true;
        }

        send(player, message("ERROR", "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴜᴘᴅᴀᴛᴇ ʏᴏᴜʀ ɪɢɴᴏʀᴇ ʟɪѕᴛ."));
        return true;
    }

    private void handleRemove(Player player, ResolvedTarget target) {
        if (!plugin.getIgnoreManager().isIgnoring(player.getUniqueId(), target.uuid())) {
            send(player, message("NOT-IGNORED", "&7%player% &cɪѕ ɴᴏᴛ ɪɴ ʏᴏᴜʀ ɪɢɴᴏʀᴇ ʟɪѕᴛ.")
                    .replace("%player%", target.name()));
            return;
        }

        if (!plugin.getIgnoreManager().removeIgnore(player.getUniqueId(), target.uuid())) {
            send(player, message("ERROR", "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴜᴘᴅᴀᴛᴇ ʏᴏᴜʀ ɪɢɴᴏʀᴇ ʟɪѕᴛ."));
            return;
        }

        send(player, message("REMOVED", "&7%player% &cʜᴀѕ ʙᴇᴇɴ ʀᴇᴍᴏᴠᴇᴅ ꜰʀᴏᴍ ʏᴏᴜʀ ɪɢɴᴏʀᴇ ʟɪѕᴛ.")
                .replace("%player%", target.name()));
    }

    private void sendIgnoreList(Player player) {
        List<IgnoreEntry> entries = plugin.getIgnoreManager().getIgnoredPlayers(player.getUniqueId());
        if (entries.isEmpty()) {
            send(player, message("LIST-EMPTY", "&7ʏᴏᴜ ᴀʀᴇ ɴᴏᴛ ɪɢɴᴏʀɪɴɢ ᴀɴʏᴏɴᴇ."));
            return;
        }

        send(player, message("LIST-HEADER", "&8&m-------- &cɪɢɴᴏʀᴇᴅ ᴘʟᴀʏᴇʀѕ &7(%count%) &8&m--------")
                .replace("%count%", String.valueOf(entries.size()))
                .replace("{count}", String.valueOf(entries.size())));
        for (IgnoreEntry entry : entries) {
            send(player, message("LIST-ENTRY", "&8- &7%player%")
                    .replace("%player%", entry.ignoredNameSnapshot())
                    .replace("{player}", entry.ignoredNameSnapshot()));
        }
    }

    private ResolvedTarget resolveTarget(Player owner, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        Player online = plugin.getHideManager().findOnlinePlayer(owner, input);
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), plugin.getHideManager().plainPublicName(online));
        }

        for (IgnoreEntry entry : plugin.getIgnoreManager().getIgnoredPlayers(owner.getUniqueId())) {
            if (entry.ignoredNameSnapshot().equalsIgnoreCase(input)
                    || entry.ignoredUuid().toString().equalsIgnoreCase(input)) {
                return new ResolvedTarget(entry.ignoredUuid(), entry.ignoredNameSnapshot());
            }
        }

        UUID uuid = plugin.getHideManager().findKnownPlayerUuid(owner, input);
        if (uuid == null) {
            return null;
        }

        String name = plugin.getDatabaseManager().getLastKnownUsername(uuid);
        String fallback = name == null || name.isBlank() ? input : name;
        return new ResolvedTarget(uuid, plugin.getHideManager().plainPublicName(uuid, fallback));
    }

    private void send(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.sendMessage(ColorUtils.toComponent(message, player));
            return;
        }
        sender.sendMessage(ColorUtils.colorize(message));
    }

    private String message(String key, String fallback) {
        return plugin.getConfigManager().getMessages().getString("IGNORE." + key, fallback);
    }

    private record ResolvedTarget(UUID uuid, String name) {
    }
}
