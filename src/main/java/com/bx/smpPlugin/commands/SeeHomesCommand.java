package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.ProfileViewerManager;
import com.bx.smpPlugin.menus.ProfileViewerHomesMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /seehomes &lt;player&gt;: staff view of someone's homes, with click-to-teleport. */
public class SeeHomesCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.staff.seehomes";

    private final SmpPlugin plugin;

    public SeeHomesCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** The profile viewer already opens this menu, so its permission works here too. */
    public static boolean canSeeHomes(Permissible permissible) {
        return PermissionUtils.hasAny(permissible, PERMISSION, ProfileViewerManager.VIEW_PERMISSION);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (!canSeeHomes(player)) {
            player.sendMessage(ColorUtils.toComponent("&cYou do not have permission to view other players' homes."));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " <player>"));
            return true;
        }

        String input = args[0];
        Player online = plugin.getHideManager().findOnlinePlayer(player, input);
        if (online != null) {
            new ProfileViewerHomesMenu(plugin, online.getUniqueId(), false).open(player);
            return true;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            UUID uuid = plugin.getDatabaseManager().findPlayerUuidByUsername(input);
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (uuid == null) {
                    player.sendMessage(ColorUtils.toComponent("&cNo player named &f" + input + " &chas joined this server."));
                    return;
                }
                new ProfileViewerHomesMenu(plugin, uuid, false).open(player);
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !canSeeHomes(sender)) {
            return Collections.emptyList();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                names.add(online.getName());
            }
        }
        return names;
    }
}
