package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SetKillsCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "smpplugin.admin.setkills";

    private final SmpPlugin plugin;

    public SetKillsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /" + label + " <player> <amount>"));
            return true;
        }

        int kills;
        try {
            kills = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.toComponent("&cɪɴᴠᴀʟɪᴅ ᴀᴍᴏᴜɴᴛ."));
            return true;
        }

        if (kills < 0) {
            sender.sendMessage(ColorUtils.toComponent("&cᴋɪʟʟѕ ᴍᴜѕᴛ ʙᴇ ᴢᴇʀᴏ ᴏʀ ᴀʙᴏᴠᴇ."));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[0]);
        PlayerData data = resolveData(targetPlayer, args[0]);
        if (data == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        int oldKills = data.getKills();
        data.setKills(kills);
        plugin.getDatabaseManager().savePlayer(data);
        invalidateLeaderboard();

        sender.sendMessage(ColorUtils.toComponent("&aѕᴇᴛ &e" + data.getUsername() + "&a'ѕ ᴋɪʟʟѕ ᴛᴏ &f" + kills + "&a."));
        sender.sendMessage(ColorUtils.toComponent("&7ᴘʀᴇᴠɪᴏᴜѕ: &f" + oldKills + " &7ɴᴇᴡ: &f" + kills));

        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent("&e" + sender.getName() + " &aѕᴇᴛ ʏᴏᴜʀ ᴋɪʟʟѕ ᴛᴏ &f" + kills + "&a."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !PermissionUtils.has(sender, PERMISSION)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], names, matches);
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return matches;
    }

    private PlayerData resolveData(Player targetPlayer, String rawName) {
        if (targetPlayer != null) {
            return plugin.getPlayerDataManager().get(targetPlayer);
        }
        UUID uuid = plugin.getDatabaseManager().findPlayerUuidByUsername(rawName);
        return uuid == null ? null : plugin.getDatabaseManager().loadPlayer(uuid);
    }

    private void invalidateLeaderboard() {
        if (plugin.getLeaderboardManager() != null) {
            plugin.getLeaderboardManager().invalidate(LeaderboardManager.LeaderboardType.KILLS);
        }
    }
}
