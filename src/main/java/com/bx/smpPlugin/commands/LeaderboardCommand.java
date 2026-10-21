package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.menus.LeaderboardMenu;
import com.bx.smpPlugin.menus.LeaderboardTypeMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class LeaderboardCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public LeaderboardCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // The console gets the top ten as text: /baltop, or /leaderboard <type>.
            var type = args.length > 0 ? plugin.getLeaderboardManager().parseType(args[0]).orElse(null)
                    : LeaderboardManager.LeaderboardType.MONEY;
            if (type == null) {
                sender.sendMessage("Unknown leaderboard. Types: " + plugin.getLeaderboardManager().getTypes().stream()
                        .map(LeaderboardManager.LeaderboardType::getConfigKey).collect(Collectors.joining(", ")));
                return true;
            }
            sender.sendMessage(ColorUtils.toComponent("&b" + plugin.getLeaderboardManager().getDisplayName(type) + " top 10"));
            for (var entry : plugin.getLeaderboardManager().getEntries(type, 0, 10)) {
                sender.sendMessage(ColorUtils.toComponent("&7#" + entry.position() + " &f" + entry.playerData().getUsername()
                        + " &7- &f" + plugin.getLeaderboardManager().formatValue(type, entry.playerData())));
            }
            return true;
        }
        if (!plugin.getConfigManager().isCommandEnabled("LEADERBOARDS")) {
            player.sendMessage(ColorUtils.toComponent("&cʟᴇᴀᴅᴇʀʙᴏᴀʀᴅѕ ᴀʀᴇ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (args.length == 0 && label.equalsIgnoreCase("baltop")) {
            new LeaderboardTypeMenu(plugin, LeaderboardManager.LeaderboardType.MONEY).open(player);
            return true;
        }

        if (args.length == 0) {
            new LeaderboardMenu(plugin).open(player);
            return true;
        }

        var type = plugin.getLeaderboardManager().parseType(args[0]).orElse(null);
        if (type == null) {
            String available = plugin.getLeaderboardManager().getTypes().stream()
                    .map(leaderboardType -> leaderboardType.getConfigKey())
                    .collect(Collectors.joining(", "));
            player.sendMessage(ColorUtils.toComponent("&cᴜɴᴋɴᴏᴡɴ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ. &7ᴀᴠᴀɪʟᴀʙʟᴇ: &f" + available));
            return true;
        }

        new LeaderboardTypeMenu(plugin, type).open(player);
        return true;
    }
}
