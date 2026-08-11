package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.models.PlayerData;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.NumberUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddPlaytimeCommand implements CommandExecutor {

    private static final String PERMISSION = "finnishsmp.admin.addplaytime";

    private final FinnishSmp plugin;

    public AddPlaytimeCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /ᴀᴅᴅᴘʟᴀʏᴛɪᴍᴇ <player> <seconds>"));
            return true;
        }

        long seconds;
        try {
            seconds = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.toComponent("&cɪɴᴠᴀʟɪᴅ ᴛɪᴍᴇ."));
            return true;
        }

        if (seconds <= 0) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴛɪᴍᴇ ᴍᴜѕᴛ ʙᴇ ᴘᴏѕɪᴛɪᴠᴇ."));
            return true;
        }

        Player targetPlayer = plugin.getServer().getPlayerExact(args[0]);
        PlayerData data;
        if (targetPlayer != null) {
            data = plugin.getPlayerDataManager().get(targetPlayer);
        } else {
            var offlinePlayer = plugin.getServer().getOfflinePlayer(args[0]);
            data = plugin.getDatabaseManager().loadPlayer(offlinePlayer.getUniqueId());
        }

        if (data == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        long oldPlaytime = data.getTotalPlaytimeSeconds();
        data.setPlaytimeSeconds(oldPlaytime + seconds);
        plugin.getDatabaseManager().savePlayer(data);

        sender.sendMessage(ColorUtils.toComponent("&aᴀᴅᴅᴇᴅ " + seconds + "ѕ ᴏꜰ ᴘʟᴀʏᴛɪᴍᴇ ᴛᴏ &e" + data.getUsername()));
        sender.sendMessage(ColorUtils.toComponent("&7ᴘʀᴇᴠɪᴏᴜѕ: &ᶠ" + formatTime(oldPlaytime) + " &7ɴᴇᴡ: &ᶠ" + formatTime(oldPlaytime + seconds)));

        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent("&a&eAdmin &aᴀᴅᴅᴇᴅ &e" + seconds + "ѕ &aᴏꜰ ᴘʟᴀʏᴛɪᴍᴇ!"));
        }
        return true;
    }

    private String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "ʜ " + minutes + "ᴍ";
    }
}
