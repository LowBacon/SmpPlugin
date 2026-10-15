package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /afk [zone]}: go to an AFK zone (a menu when there are several).
 * {@code /away}: set or clear your AFK status where you stand.
 */
public class AfkCommand implements CommandExecutor, TabCompleter {

    private final SmpPlugin plugin;

    public AfkCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        AfkManager afk = plugin.getAfkManager();
        if (command.getName().equalsIgnoreCase("away")) {
            if (!PermissionUtils.has(player, "smpplugin.afk.away")) {
                player.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
                return true;
            }
            afk.toggleAfk(player);
            return true;
        }

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            return true;
        }

        List<AfkZone> zones = afk.destinationsFor(player);
        if (zones.isEmpty()) {
            AfkManager.send(player, afk.message("NO-ZONES", "&cThere are no AFK zones yet."));
            if (PermissionUtils.has(player, AfkZoneCommand.PERMISSION)) {
                AfkManager.send(player, "&7Build one with &f/afkzone pos1&7, &f/afkzone pos2 &7and &f/afkzone create <name>&7.");
            }
            return true;
        }

        if (args.length > 0) {
            AfkZone zone = afk.getZone(args[0]);
            if (zone == null || zone.spawnLocation() == null) {
                AfkManager.send(player, afk.message("UNKNOWN-ZONE", "&cThere is no AFK zone called &f{zone}&c.").replace("{zone}", args[0]));
                return true;
            }
            afk.teleportWithWarmup(player, zone);
            return true;
        }
        if (zones.size() == 1) {
            afk.teleportWithWarmup(player, zones.getFirst());
            return true;
        }
        new AfkZoneMenu(plugin).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("afk") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getAfkManager().getZones().stream()
                .map(AfkZone::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
