package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Sets the server's single primary spawn point, or deletes it (/setspawn delete, /delspawn). */
public class SetSpawnCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.admin.setspawn";

    private final SmpPlugin plugin;

    public SetSpawnCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to set the spawn."));
            return true;
        }

        boolean deleteLabel = "delspawn".equalsIgnoreCase(label);
        if (deleteLabel || args.length > 0 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("remove"))) {
            boolean confirmed = args.length > (deleteLabel ? 0 : 1) && args[deleteLabel ? 0 : 1].equalsIgnoreCase("confirm");
            delete(sender, confirmed);
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
        com.bx.smpPlugin.core.Text.line(sender, "&7Want a real world border around it? ",
                com.bx.smpPlugin.core.Text.suggest("&e[/border spawn set 200]", "/border spawn set 200",
                        "&7A 200 x 200 Minecraft border around the spawn"));
        return true;
    }

    private void delete(CommandSender sender, boolean confirmed) {
        String raw = plugin.getConfigManager().getConfig().getString("LOCATIONS.SPAWN-LOCATION", "");
        if (raw == null || raw.isBlank()) {
            sender.sendMessage(ColorUtils.toComponent("&7There is no spawn set with /setspawn to delete."));
            return;
        }
        if (!confirmed) {
            sender.sendMessage(ColorUtils.toComponent("&eDelete the spawn? &7/spawn and respawning stop using it, and the spawn"
                    + " border is taken down."));
            com.bx.smpPlugin.core.Text.line(sender, "  ",
                    com.bx.smpPlugin.core.Text.button("&c&l[Yes, delete it]", "setspawn delete confirm", "&7Delete the spawn"));
            return;
        }
        if (plugin.getV3Module() != null) {
            plugin.getV3Module().turnOffBorder("spawn");
        }
        if (!plugin.getSpawnManager().deletePrimarySpawnLocation()) {
            sender.sendMessage(ColorUtils.toComponent("&cThe spawn could not be deleted. Check the server console for details."));
            return;
        }
        sender.sendMessage(ColorUtils.toComponent("&aThe spawn was deleted. &7Set a new one any time with &f/setspawn&7."));
        if (plugin.getSpawnManager().hasSpawn()) {
            sender.sendMessage(ColorUtils.toComponent("&7Your spawn-menu areas still give players a spawn; change those in the spawn menu."));
        }
    }
}
