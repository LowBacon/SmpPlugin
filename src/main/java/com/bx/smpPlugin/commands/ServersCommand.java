package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.ServerStatusSnapshot;
import com.bx.smpPlugin.menus.ServersMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ServersCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.servers";

    private final SmpPlugin plugin;

    public ServersCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴜѕᴇ /servers."));
            return true;
        }

        if (!plugin.getConfigManager().isCommandEnabled("SERVERS")) {
            sender.sendMessage(ColorUtils.toComponent("&cѕᴇʀᴠᴇʀѕ ᴍᴇɴᴜ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (!plugin.getNetworkStatusManager().isEnabled()) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴇᴛᴡᴏʀᴋ ѕᴇʀᴠᴇʀ ѕᴛᴀᴛᴜѕ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (sender instanceof Player player) {
            ServersMenu menu = new ServersMenu(plugin);
            if (!menu.hasRenderableServers()) {
                player.sendMessage(ColorUtils.toComponent("&cɴᴏ ѕᴇʀᴠᴇʀѕ ᴀʀᴇ ᴄᴏɴꜰɪɢᴜʀᴇᴅ ꜰᴏʀ ᴛʜᴇ ѕᴇʀᴠᴇʀѕ ᴍᴇɴᴜ."));
                return true;
            }

            menu.open(player);
            return true;
        }

        if (!plugin.getNetworkStatusManager().hasConfiguredServers()) {
            sender.sendMessage("ɴᴏ ѕᴇʀᴠᴇʀѕ ᴀʀᴇ ᴄᴏɴꜰɪɢᴜʀᴇᴅ ꜰᴏʀ ɴᴇᴛᴡᴏʀᴋ ѕᴛᴀᴛᴜѕ.");
            return true;
        }

        sender.sendMessage("ɴᴇᴛᴡᴏʀᴋ ѕᴇʀᴠᴇʀ ѕᴛᴀᴛᴜѕ:");
        for (ServerStatusSnapshot snapshot : plugin.getNetworkStatusManager().getOrderedSnapshots()) {
            sender.sendMessage("- " + snapshot.displayName()
                    + " | " + (snapshot.online() ? "ᴏɴʟɪɴᴇ" : "ᴏꜰꜰʟɪɴᴇ")
                    + " | ᴘʟᴀʏᴇʀѕ=" + snapshot.playerCount()
                    + " | ѕᴏꜰᴛᴡᴀʀᴇ=" + snapshot.softwareLabel()
                    + " | ᴘᴇʀꜰᴏʀᴍᴀɴᴄᴇ=" + snapshot.performanceLabel());
        }
        return true;
    }
}
