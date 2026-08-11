package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Sets the protected lobby location that every player is teleported to on join. */
public class SetLobbyCommand implements CommandExecutor {

    private static final String PERMISSION = "finnishsmp.admin.lobby.setlobby";

    private final FinnishSmp plugin;

    public SetLobbyCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to set the lobby."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cOnly a player can set the lobby location."));
            return true;
        }

        Location location = player.getLocation();
        if (!plugin.getLobbyManager().setLobbyLocation(location)) {
            sender.sendMessage(ColorUtils.toComponent("&cThe lobby location could not be saved. Check the server console for details."));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent("&aLobby set to &f"
                + location.getWorld().getName() + " &7(" + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ() + ")."));
        sender.sendMessage(ColorUtils.toComponent("&7Players will now be teleported here on join, and via &f/lobby&7."));
        return true;
    }
}
