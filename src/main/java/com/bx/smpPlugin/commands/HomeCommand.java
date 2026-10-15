package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.HomeManager;
import com.bx.smpPlugin.menus.HomeMenu;
import com.bx.smpPlugin.models.Home;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.CommandLabelUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class HomeCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public HomeCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }

        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getConfig()
                    .getString("COMBAT-MANAGER.BLOCK-MESSAGE", "&cʏᴏᴜ ᴄᴀɴ'ᴛ ᴜѕᴇ ᴛʜɪѕ ɪɴ ᴄᴏᴍʙᴀᴛ.")));
            return true;
        }

        // Works for /smpplugin:sethome too, not just the plain label.
        String sub = CommandLabelUtils.normalizeLabel(label, command);
        HomeManager homes = plugin.getHomeManager();

        if (sub.equals("homes")) {
            new HomeMenu(plugin).open(player);
            return true;
        }

        if (sub.equals("sethome")) {
            String name = args.length > 0 ? args[0] : "home";
            if (!HomeManager.isValidHomeName(name)) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.INVALID-NAME")));
                return true;
            }
            if (isInArena(player)) {
                player.sendMessage(ColorUtils.toComponent("&cYou can't set a home inside a duel or FFA arena."));
                return true;
            }
            boolean exists = homes.getHome(player.getUniqueId(), name) != null;
            if (!exists && !homes.canSetHome(player)) {
                player.sendMessage(ColorUtils.toComponent("&cYou've used all your homes &7("
                        + homes.getHomeCount(player.getUniqueId()) + "/" + homes.getMaxHomes(player)
                        + ")&c. Delete one with &f/delhome <name>&c."));
                return true;
            }
            if (homes.setHome(player, name)) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.SET")));
            }
            return true;
        }

        if (sub.equals("delhome")) {
            if (args.length == 0) { player.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /delhome <name>")); return true; }
            boolean removed = homes.deleteHome(player.getUniqueId(), args[0]);
            player.sendMessage(ColorUtils.toComponent(removed
                    ? plugin.getConfigManager().getMessage("HOME.DELETED")
                    : "&cʜᴏᴍᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        if (sub.equals("renamehome")) {
            if (args.length < 2) { player.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /renamehome <old> <new>")); return true; }
            if (!HomeManager.isValidHomeName(args[1])) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage("HOME.INVALID-NAME")));
                return true;
            }
            boolean ok = homes.renameHome(player.getUniqueId(), args[0], args[1]);
            player.sendMessage(ColorUtils.toComponent(ok
                    ? plugin.getConfigManager().getMessage("HOME.RENAME-SUCCESS", "{name}", args[1])
                    : "&cꜰᴀɪʟᴇᴅ ᴛᴏ ʀᴇɴᴀᴍᴇ ʜᴏᴍᴇ."));
            return true;
        }

        // /home [name]. With no name: the only home, or the one called "home", or the menu.
        Home home;
        if (args.length > 0) {
            home = homes.getHome(player.getUniqueId(), args[0]);
            if (home == null) {
                player.sendMessage(ColorUtils.toComponent("&cʜᴏᴍᴇ '&e" + args[0] + "&c' ɴᴏᴛ ꜰᴏᴜɴᴅ."));
                return true;
            }
        } else {
            List<Home> owned = homes.getHomes(player.getUniqueId());
            home = owned.size() == 1 ? owned.get(0) : homes.getHome(player.getUniqueId(), "home");
            if (home == null) {
                new HomeMenu(plugin).open(player);
                return true;
            }
        }

        if (home.getLocation() == null || home.getLocation().getWorld() == null) {
            player.sendMessage(ColorUtils.toComponent("&cThat home's world is not loaded right now."));
            return true;
        }
        plugin.getTeleportManager().queue(player, home.getLocation(), "HOME", null);
        return true;
    }

    private boolean isInArena(Player player) {
        return (plugin.getDuelManager() != null && (plugin.getDuelManager().isInDuel(player.getUniqueId())
                || plugin.getDuelManager().isTransitioning(player.getUniqueId())))
                || (plugin.getFfaManager() != null && (plugin.getFfaManager().isInSession(player.getUniqueId())
                || plugin.getFfaManager().isInMatch(player.getUniqueId())));
    }
}
