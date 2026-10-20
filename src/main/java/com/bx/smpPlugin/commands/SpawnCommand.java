package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.SpawnManager;
import com.bx.smpPlugin.menus.SpawnMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /spawn: back to spawn (with the usual teleport delay), or the spawn menu when there are several
 * spawn areas. Staff can send someone else with /spawn &lt;player&gt;, which teleports at once.
 */
public class SpawnCommand implements CommandExecutor, TabCompleter {

    public static final String OTHERS_PERMISSION = "smpplugin.staff.spawn.others";

    private final SmpPlugin plugin;

    public SpawnCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            sendOther(sender, args[0]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "SPAWN.USAGE-CONSOLE", "&7/spawn <player>");
            return true;
        }

        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getConfig()
                    .getString("COMBAT-MANAGER.BLOCK-MESSAGE", "&cYou can't use this command in combat.")));
            return true;
        }

        if (plugin.getSpawnManager().shouldOpenMenu(SpawnManager.AreaType.SPAWN)) {
            new SpawnMenu(plugin).open(player);
            return true;
        }

        Location destination = plugin.getSpawnManager().resolveCommandDestination(SpawnManager.AreaType.SPAWN);
        if (destination == null || destination.getWorld() == null) {
            notSet(player);
            return true;
        }

        plugin.getTeleportManager().queue(player, destination, "SPAWN", null);
        return true;
    }

    private void sendOther(CommandSender sender, String name) {
        if (!PermissionUtils.has(sender, OTHERS_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            Text.send(sender, "GENERAL.PLAYER-NOT-FOUND", "&cThat player is not online.");
            return;
        }
        Location destination = plugin.getSpawnManager().resolveCommandDestination(SpawnManager.AreaType.SPAWN);
        if (destination == null || destination.getWorld() == null) {
            notSet(sender);
            return;
        }
        plugin.getSpigotScheduler().teleport(target, destination);
        Text.send(sender, "SPAWN.SENT", "&aSent &f{player} &ato spawn.", "{player}", target.getName());
        if (!target.equals(sender)) {
            Text.send(target, "SPAWN.SENT-BY", "&7You were sent to spawn by &f{staff}&7.", "{staff}", sender.getName());
        }
    }

    /** Explains why there is no spawn: never set, or its world is not loaded. */
    private void notSet(CommandSender sender) {
        String raw = plugin.getConfigManager().getConfig().getString("LOCATIONS.SPAWN-LOCATION", "");
        if (raw != null && !raw.isBlank()) {
            String world = raw.split(",")[0];
            if (Bukkit.getWorld(world) == null) {
                Text.send(sender, "SPAWN.WORLD-MISSING", "&cThe spawn is in the world &f{world}&c, which is not loaded right now.",
                        "{world}", world);
                return;
            }
        }
        Text.send(sender, "SPAWN.NOT-SET", "&cThe spawn has not been set yet. An admin can set it with &f/setspawn&c.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !PermissionUtils.has(sender, OTHERS_PERMISSION)) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
