package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AddKillsCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.admin.addkills";

    private final SmpPlugin plugin;

    public AddKillsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /ᴀᴅᴅᴋɪʟʟѕ <player> <amount>"));
            return true;
        }

        int kills;
        try {
            kills = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.toComponent("&cɪɴᴠᴀʟɪᴅ ᴀᴍᴏᴜɴᴛ."));
            return true;
        }

        if (kills <= 0) {
            sender.sendMessage(ColorUtils.toComponent("&cᴋɪʟʟѕ ᴍᴜѕᴛ ʙᴇ ᴘᴏѕɪᴛɪᴠᴇ."));
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

        int oldKills = data.getKills();
        data.setKills(oldKills + kills);
        plugin.getDatabaseManager().savePlayer(data);

        sender.sendMessage(ColorUtils.toComponent("&aᴀᴅᴅᴇᴅ " + kills + " ᴋɪʟʟs ᴛᴏ &e" + data.getUsername()));
        sender.sendMessage(ColorUtils.toComponent("&7ᴘʀᴇᴠɪᴏᴜѕ: &ᶠ" + oldKills + " &7ɴᴇᴡ: &ᶠ" + (oldKills + kills)));

        if (targetPlayer != null && !targetPlayer.equals(sender)) {
            targetPlayer.sendMessage(ColorUtils.toComponent("&a&eAdmin &aᴀᴅᴅᴇᴅ &e" + kills + " ᴋɪʟʟѕ!"));
        }
        return true;
    }
}
