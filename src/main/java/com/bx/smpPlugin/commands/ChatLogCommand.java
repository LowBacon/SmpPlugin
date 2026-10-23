// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.ChatLogsMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatLogCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.admin.chatlog";

    private final SmpPlugin plugin;

    public ChatLogCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (!PermissionUtils.has(viewer, PERMISSION)) {
            viewer.sendMessage(ColorUtils.toComponent("&cYou do not have permission to use this command."));
            return true;
        }
        if (args.length > 1) {
            viewer.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " [player]"));
            return true;
        }
        if (args.length == 0) {
            new ChatLogsMenu(plugin, null, null).open(viewer);
            return true;
        }

        String input = args[0].trim();
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            new ChatLogsMenu(plugin, online.getUniqueId(), online.getName()).open(viewer);
            return true;
        }
        // Offline names go through the database, which is never read on the main thread.
        plugin.getDatabaseManager().executeAsync(() -> {
            UUID uuid = plugin.getDatabaseManager().findPlayerUuidByUsername(input);
            String name = uuid == null ? null : plugin.getDatabaseManager().getLastKnownUsername(uuid);
            plugin.getSpigotScheduler().runEntity(viewer, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                if (uuid == null) {
                    viewer.sendMessage(ColorUtils.toComponent("&cNo player named &f" + input + " &chas joined this server."));
                    return;
                }
                new ChatLogsMenu(plugin, uuid, name == null || name.isBlank() ? input : name).open(viewer);
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !PermissionUtils.has(sender, PERMISSION)) {
            return Collections.emptyList();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                suggestions.add(player.getName());
            }
        }
        return suggestions;
    }
}
