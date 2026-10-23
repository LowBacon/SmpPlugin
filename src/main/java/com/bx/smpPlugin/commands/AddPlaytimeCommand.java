// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
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
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddPlaytimeCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "smpplugin.admin.addplaytime";
    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private final SmpPlugin plugin;

    public AddPlaytimeCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /" + label + " <player> <seconds|1d2h30m>"));
            return true;
        }

        long seconds = parseDurationSeconds(args[1]);
        if (seconds == Long.MIN_VALUE) {
            sender.sendMessage(ColorUtils.toComponent("&cɪɴᴠᴀʟɪᴅ ᴛɪᴍᴇ. ᴜѕᴇ ѕᴇᴄᴏɴᴅѕ (ᴇ.ɢ. 3600) ᴏʀ ᴀ ᴅᴜʀᴀᴛɪᴏɴ (ᴇ.ɢ. 1ᴅ2ʜ30ᴍ)."));
            return true;
        }

        if (seconds <= 0) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴛɪᴍᴇ ᴍᴜѕᴛ ʙᴇ ᴘᴏѕɪᴛɪᴠᴇ."));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[0]);
        PlayerData data = resolveData(targetPlayer, args[0]);
        if (data == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        // getTotalPlaytimeSeconds() folds in time elapsed since sessionStartMillis, which is only
        // meaningful while the target is online (for an offline player it's frozen at their last
        // logout, so treating it as "live" would wrongly add real-world time spent offline). It's
        // also why we always add the new seconds on top of the raw stored field, not the live total:
        // adding to the live total would double count the in-progress session on every later read.
        boolean online = targetPlayer != null;
        long oldTotal = online ? data.getTotalPlaytimeSeconds() : data.getPlaytimeSeconds();
        long rawBase = data.getPlaytimeSeconds();
        long newRawBase = rawBase + seconds;
        if (newRawBase < rawBase) { // overflow guard
            newRawBase = Long.MAX_VALUE;
        }
        data.setPlaytimeSeconds(newRawBase);
        plugin.getDatabaseManager().savePlayer(data);
        invalidateLeaderboard();

        long newTotal = online ? data.getTotalPlaytimeSeconds() : data.getPlaytimeSeconds();

        sender.sendMessage(ColorUtils.toComponent("&aᴀᴅᴅᴇᴅ &f" + NumberUtils.formatTimeLong(seconds)
                + " &aᴏꜰ ᴘʟᴀʏᴛɪᴍᴇ ᴛᴏ &e" + data.getUsername()));
        sender.sendMessage(ColorUtils.toComponent("&7ᴘʀᴇᴠɪᴏᴜѕ: &f" + NumberUtils.formatTimeLong(oldTotal)
                + " &7ɴᴇᴡ: &f" + NumberUtils.formatTimeLong(newTotal)));

        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent("&e" + sender.getName() + " &aᴀᴅᴅᴇᴅ &f"
                    + NumberUtils.formatTimeLong(seconds) + " &aᴏꜰ ᴘʟᴀʏᴛɪᴍᴇ."));
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
            plugin.getLeaderboardManager().invalidate(LeaderboardManager.LeaderboardType.PLAYTIME);
        }
    }

    private long parseDurationSeconds(String input) {
        if (input == null || input.isBlank()) {
            return Long.MIN_VALUE;
        }

        String trimmed = input.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            // not a plain number, try duration syntax below
        }

        Matcher matcher = DURATION_TOKEN.matcher(trimmed);
        long totalSeconds = 0L;
        int matchedCharacters = 0;
        while (matcher.find()) {
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return Long.MIN_VALUE;
            }

            long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> 1L;
                case "m" -> 60L;
                case "h" -> 3_600L;
                case "d" -> 86_400L;
                case "w" -> 604_800L;
                default -> -1L;
            };
            if (multiplier <= 0L) {
                return Long.MIN_VALUE;
            }

            totalSeconds += amount * multiplier;
            matchedCharacters += matcher.group(0).length();
        }

        return matchedCharacters == trimmed.length() && matchedCharacters > 0 ? totalSeconds : Long.MIN_VALUE;
    }
}
