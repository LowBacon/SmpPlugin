package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.models.PlayerData;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddDeathsCommand implements CommandExecutor {

    private static final String PERMISSION = "finnishsmp.admin.adddeaths";

    private final FinnishSmp plugin;

    public AddDeathsCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /ᴀᴅᴅᴅᴇᴀᴛʜѕ <player> <amount>"));
            return true;
        }

        int deaths;
        try {
            deaths = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.toComponent("&cɪɴᴠᴀʟɪᴅ ᴀᴍᴏᴜɴᴛ."));
            return true;
        }

        if (deaths <= 0) {
            sender.sendMessage(ColorUtils.toComponent("&cᴅᴇᴀᴛʜѕ ᴍᴜѕᴛ ʙᴇ ᴘᴏѕɪᴛɪᴠᴇ."));
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

        int oldDeaths = data.getDeaths();
        data.setDeaths(oldDeaths + deaths);
        plugin.getDatabaseManager().savePlayer(data);

        sender.sendMessage(ColorUtils.toComponent("&aᴀᴅᴅᴇᴅ " + deaths + " ᴅᴇᴀᴛʜѕ ᴛᴏ &e" + data.getUsername()));
        sender.sendMessage(ColorUtils.toComponent("&7ᴘʀᴇᴠɪᴏᴜѕ: &ᶠ" + oldDeaths + " &7ɴᴇᴡ: &ᶠ" + (oldDeaths + deaths)));

        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent("&a&eAdmin &aᴀᴅᴅᴇᴅ &e" + deaths + " ᴅᴇᴀᴛʜѕ!"));
        }
        return true;
    }
}
