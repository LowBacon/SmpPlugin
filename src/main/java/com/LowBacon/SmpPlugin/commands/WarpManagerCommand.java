package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.utils.PermissionUtils;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class WarpManagerCommand implements CommandExecutor {

    private static final String WARP_MANAGER_PERMISSION = "finnishsmp.admin.warpmanager";
    private static final String SET_WARP_PERMISSION = "finnishsmp.admin.setwarp";
    private static final String DELETE_WARP_PERMISSION = "finnishsmp.admin.delwarp";

    private final FinnishSmp plugin;

    public WarpManagerCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String loweredLabel = label.toLowerCase(Locale.ROOT);

        if (loweredLabel.equals("setwarp")) {
            handleCreateAlias(sender, args);
            return true;
        }

        if (loweredLabel.equals("delwarp")) {
            handleDeleteAlias(sender, args);
            return true;
        }

        if (args.length == 0) {
            sendMessage(sender, message("WARPMANAGER.USAGE", "&cᴜѕᴀɢᴇ: /warpmanager <create|delete|list> [ɴᴀᴍᴇ]"));
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "create" -> handleCreate(sender, args, true);
            case "delete" -> handleDelete(sender, args, true);
            case "list" -> handleList(sender);
            default -> sendMessage(sender, message("WARPMANAGER.USAGE", "&cᴜѕᴀɢᴇ: /warpmanager <create|delete|list> [ɴᴀᴍᴇ]"));
        }
        return true;
    }

    private void handleCreateAlias(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, message("WARPMANAGER.CREATE-USAGE-ALIAS", "&cᴜѕᴀɢᴇ: /setwarp <name>"));
            return;
        }

        handleCreate(sender, new String[]{"create", args[0]}, false);
    }

    private void handleDeleteAlias(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sendMessage(sender, message("WARPMANAGER.DELETE-USAGE-ALIAS", "&cᴜѕᴀɢᴇ: /delwarp <name>"));
            return;
        }

        handleDelete(sender, new String[]{"delete", args[0]}, false);
    }

    private void handleCreate(CommandSender sender, String[] args, boolean managerCommand) {
        if (!hasCreatePermission(sender)) {
            sendMessage(sender, message("WARPMANAGER.NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴍᴀɴᴀɢᴇ ᴡᴀʀᴘѕ."));
            return;
        }

        if (!(sender instanceof Player player)) {
            sendMessage(sender, message("WARPMANAGER.CREATE-PLAYER-ONLY", "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴄʀᴇᴀᴛᴇ ᴡᴀʀᴘѕ."));
            return;
        }

        if (args.length != 2) {
            sendMessage(sender, managerCommand
                    ? message("WARPMANAGER.CREATE-USAGE", "&cᴜѕᴀɢᴇ: /warpmanager ᴄʀᴇᴀᴛᴇ <name>")
                    : message("WARPMANAGER.CREATE-USAGE-ALIAS", "&cᴜѕᴀɢᴇ: /setwarp <name>"));
            return;
        }

        String requestedName = args[1];
        if (!plugin.getWarpManager().isValidWarpName(requestedName)) {
            sendMessage(sender, message("WARP.INVALID-NAME",
                    "&cɪɴᴠᴀʟɪᴅ ᴡᴀʀᴘ ɴᴀᴍᴇ. ᴜѕᴇ ᴏɴʟʏ ʟᴇᴛᴛᴇʀѕ, ɴᴜᴍʙᴇʀѕ, ᴅᴀѕʜᴇѕ, ᴀɴᴅ ᴜɴᴅᴇʀѕᴄᴏʀᴇѕ."));
            return;
        }

        String normalizedName = plugin.getWarpManager().normalizeName(requestedName);
        if (plugin.getWarpManager().warpExists(normalizedName)) {
            sendMessage(sender, message("WARP.ALREADY-EXISTS",
                    "&cᴡᴀʀᴘ '&e{name}&c' ᴀʟʀᴇᴀᴅʏ ᴇxɪѕᴛѕ.",
                    "{name}", normalizedName));
            return;
        }

        boolean created = plugin.getWarpManager().createWarp(normalizedName, player.getLocation());
        if (!created) {
            sendMessage(sender, message("WARPMANAGER.CREATE-FAILED",
                    "&cꜰᴀɪʟᴇᴅ ᴛᴏ ᴄʀᴇᴀᴛᴇ ᴡᴀʀᴘ '&e{name}&c'.",
                    "{name}", normalizedName));
            return;
        }

        sendMessage(sender, message("WARP.CREATED",
                "&aᴡᴀʀᴘ &b{name} &aʜᴀѕ ʙᴇᴇɴ ᴄʀᴇᴀᴛᴇᴅ.",
                "{name}", normalizedName));
    }

    private void handleDelete(CommandSender sender, String[] args, boolean managerCommand) {
        if (!hasDeletePermission(sender)) {
            sendMessage(sender, message("WARPMANAGER.NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴍᴀɴᴀɢᴇ ᴡᴀʀᴘѕ."));
            return;
        }

        if (args.length != 2) {
            sendMessage(sender, managerCommand
                    ? message("WARPMANAGER.DELETE-USAGE", "&cᴜѕᴀɢᴇ: /warpmanager ᴅᴇʟᴇᴛᴇ <name>")
                    : message("WARPMANAGER.DELETE-USAGE-ALIAS", "&cᴜѕᴀɢᴇ: /delwarp <name>"));
            return;
        }

        String requestedName = args[1];
        String normalizedName = plugin.getWarpManager().normalizeName(requestedName);
        if (normalizedName == null || !plugin.getWarpManager().deleteWarp(normalizedName)) {
            sendWarpNotFound(sender, requestedName);
            return;
        }

        sendMessage(sender, message("WARP.DELETED",
                "&aᴡᴀʀᴘ &b{name} &aʜᴀѕ ʙᴇᴇɴ ᴅᴇʟᴇᴛᴇᴅ.",
                "{name}", normalizedName));
    }

    private void handleList(CommandSender sender) {
        if (!hasListPermission(sender)) {
            sendMessage(sender, message("WARPMANAGER.NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴍᴀɴᴀɢᴇ ᴡᴀʀᴘѕ."));
            return;
        }

        List<String> names = plugin.getWarpManager().getSortedWarpNames();
        if (names.isEmpty()) {
            sendMessage(sender, message("WARP.LIST-EMPTY", "&cɴᴏ ᴡᴀʀᴘѕ ᴀᴠᴀɪʟᴀʙʟᴇ."));
            return;
        }

        sendMessage(sender, message("WARP.LIST-HEADER",
                "&8&m---------------- &bᴡᴀʀᴘѕ &7({count}) &8&m----------------",
                "{count}", String.valueOf(names.size())));
        for (String name : names) {
            sendMessage(sender, message("WARP.LIST-ENTRY", "&7- &b{name}", "{name}", name));
        }
    }

    private void sendWarpNotFound(CommandSender sender, String requestedName) {
        sendMessage(sender, message("WARP.NOT-FOUND", "&cᴡᴀʀᴘ '&e{name}&c' ɴᴏᴛ ꜰᴏᴜɴᴅ.", "{name}", requestedName));

        List<String> suggestions = plugin.getWarpManager().findWarpSuggestions(requestedName);
        if (!suggestions.isEmpty()) {
            sendMessage(sender, message("WARP.NOT-FOUND-SUGGESTION",
                    "&7ᴅɪᴅ ʏᴏᴜ ᴍᴇᴀɴ: &b{suggestions}",
                    "{suggestions}", String.join("&7, &b", suggestions)));
        }
    }

    private boolean hasCreatePermission(CommandSender sender) {
        return PermissionUtils.has(sender, WARP_MANAGER_PERMISSION) || PermissionUtils.has(sender, SET_WARP_PERMISSION);
    }

    private boolean hasDeletePermission(CommandSender sender) {
        return PermissionUtils.has(sender, WARP_MANAGER_PERMISSION) || PermissionUtils.has(sender, DELETE_WARP_PERMISSION);
    }

    private boolean hasListPermission(CommandSender sender) {
        return PermissionUtils.has(sender, WARP_MANAGER_PERMISSION)
                || PermissionUtils.has(sender, SET_WARP_PERMISSION)
                || PermissionUtils.has(sender, DELETE_WARP_PERMISSION);
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.colorize(message));
    }

    private String message(String path, String fallback, String... placeholders) {
        return plugin.getConfigManager().getMessageOrDefault(path, fallback, placeholders);
    }
}
