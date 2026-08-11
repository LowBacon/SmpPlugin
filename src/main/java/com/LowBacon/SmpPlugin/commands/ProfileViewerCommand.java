package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.menus.ProfileViewerMenu;
import com.bx.finnishSmp.models.ProfileSnapshot;
import com.bx.finnishSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ProfileViewerCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public ProfileViewerCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        if (!plugin.getProfileViewerManager().canView(player)) {
            player.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴠɪᴇᴡ ᴘʟᴀʏᴇʀ ᴘʀᴏꜰɪʟᴇѕ."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /" + label + " <player>"));
            return true;
        }

        ProfileSnapshot snapshot = plugin.getProfileViewerManager().resolveProfile(args[0]).orElse(null);
        if (snapshot == null) {
            player.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ᴘʀᴏꜰɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        new ProfileViewerMenu(plugin, snapshot.getUuid()).open(player);
        return true;
    }
}
