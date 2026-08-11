package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Sets the server's single primary spawn point. */
public class SetSpawnCommand implements CommandExecutor {

    private static final String PERMISSION = "finnishsmp.admin.setspawn";

    private final FinnishSmp plugin;

    public SetSpawnCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to set the spawn."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cOnly a player can set the spawn location."));
            return true;
        }

        Location location = player.getLocation();
        if (!plugin.getSpawnManager().setPrimarySpawnLocation(location)) {
            sender.sendMessage(ColorUtils.toComponent("&cThe spawn could not be saved. Check the server console for details."));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent("&aSpawn set to &f"
                + location.getWorld().getName() + " &7(" + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ() + ")."));
        sender.sendMessage(ColorUtils.toComponent("&7Players can now use &f/spawn&7 to teleport here."));
        return true;
    }
}
