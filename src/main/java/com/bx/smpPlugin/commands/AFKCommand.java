package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.AfkPointManager;
import com.bx.smpPlugin.managers.SpawnManager;
import com.bx.smpPlugin.menus.AfkMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class AFKCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public AFKCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        if (!plugin.getConfigManager().isCommandEnabled("AFK")) {
            player.sendMessage(ColorUtils.toComponent("&cᴀꜰᴋ ᴄᴏᴍᴍᴀɴᴅ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getConfig()
                    .getString("COMBAT-MANAGER.BLOCK-MESSAGE", "&cʏᴏᴜ ᴄᴀɴ'ᴛ ᴜѕᴇ ᴛʜɪѕ ɪɴ ᴄᴏᴍʙᴀᴛ.")));
            return true;
        }

        List<Location> destinations = collectDestinations();
        if (destinations.isEmpty()) {
            player.sendMessage(ColorUtils.toComponent("&cᴀꜰᴋ ʟᴏᴄᴀᴛɪᴏɴ ɪѕ ɴᴏᴛ ѕᴇᴛ."));
            if (PermissionUtils.has(player, AfkPointCommand.PERMISSION)) {
                player.sendMessage(ColorUtils.toComponent(
                        "&7ѕᴛᴀɴᴅ ᴡʜᴇʀᴇ ʏᴏᴜ ᴡᴀɴᴛ ᴛʜᴇ ᴀꜰᴋ ᴢᴏɴᴇ ᴀɴᴅ ʀᴜɴ &f/setafk&7."));
            }
            return true;
        }

        boolean menuEnabled = plugin.getSpawnManager().isMenuEnabled(SpawnManager.AreaType.AFK);
        if (destinations.size() > 1 && menuEnabled) {
            new AfkMenu(plugin).open(player);
            return true;
        }

        Location destination = destinations.size() == 1
                ? destinations.get(0)
                : destinations.get(ThreadLocalRandom.current().nextInt(destinations.size()));
        plugin.getTeleportManager().queue(player, destination, "AFK", null);
        return true;
    }

    /** Named {@code /setafk} points first, then legacy cuboid areas, deduplicated by block position. */
    private List<Location> collectDestinations() {
        Set<String> seen = new LinkedHashSet<>();
        List<Location> destinations = new ArrayList<>();

        for (AfkPointManager.AfkPoint point : plugin.getAfkPointManager().getResolvablePoints()) {
            Location location = point.location();
            if (seen.add(locationKey(location))) {
                destinations.add(location);
            }
        }

        for (SpawnManager.TeleportArea area : plugin.getSpawnManager().getValidAreas(SpawnManager.AreaType.AFK)) {
            Location location = plugin.getSpawnManager().resolveDestination(area);
            if (location != null && seen.add(locationKey(location))) {
                destinations.add(location);
            }
        }

        if (destinations.isEmpty()) {
            Location fallback = plugin.getSpawnManager().resolveCommandDestination(SpawnManager.AreaType.AFK);
            if (fallback != null) {
                destinations.add(fallback);
            }
        }

        return destinations;
    }

    private String locationKey(Location location) {
        return (location.getWorld() == null ? "?" : location.getWorld().getName())
                + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
