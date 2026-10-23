// SmpPlugin by LowBacon
package com.bx.smpPlugin.world;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BorderCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.admin.border";

    private final SmpPlugin plugin;
    private final BorderManager borders;

    public BorderCommand(SmpPlugin plugin, BorderManager borders) {
        this.plugin = plugin;
        this.borders = borders;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            Text.raw(sender, Text.get("BORDER.OVERVIEW", "&6&lBorders &8» &7real Minecraft borders for the spawn and the lobby"));
            for (String name : BorderManager.ZONES) {
                BorderZone zone = borders.zone(name);
                Text.line(sender, "  ", Text.button("&e" + name, "border " + name, "&7Details and settings"),
                        " &8· " + status(zone) + " &8· &7" + zone.describe());
            }
            Text.raw(sender, Text.get("BORDER.OVERVIEW-HINT", "&8  Start with: /border spawn set 200"));
            return true;
        }
        String zoneName = args[0].toLowerCase(Locale.ROOT);
        BorderZone zone = BorderManager.ZONES.contains(zoneName) ? borders.zone(zoneName) : null;
        if (zone == null) {
            usage(sender);
            return true;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "info";
        switch (action) {
            case "info" -> info(sender, zone);
            case "set", "size" -> set(sender, zone, args);
            case "center", "centre" -> center(sender, zone);
            case "warning" -> {
                Integer distance = integer(args, 2);
                if (distance == null) {
                    Text.send(sender, "BORDER.WARNING-USAGE", "&7/border {zone} warning <blocks> [seconds]", "{zone}", zone.name());
                    return true;
                }
                Integer time = args.length > 3 ? integer(args, 3) : Integer.valueOf(zone.warningTime());
                save(sender, zone.withWarning(distance, time == null ? zone.warningTime() : time), "&aWarning updated.");
            }
            case "damage" -> {
                Double amount = decimal(args, 2);
                if (amount == null) {
                    Text.send(sender, "BORDER.DAMAGE-USAGE", "&7/border {zone} damage <per block> [safe blocks]", "{zone}", zone.name());
                    return true;
                }
                Double buffer = args.length > 3 ? decimal(args, 3) : Double.valueOf(zone.damageBuffer());
                save(sender, zone.withDamage(amount, buffer == null ? zone.damageBuffer() : buffer), "&aDamage updated.");
            }
            case "mode" -> mode(sender, zone, args);
            case "on", "enable" -> {
                BorderZone ready = zone.configured() ? zone : defaultArea(sender, zone, false);
                if (ready == null || !allowedOnMainWorld(sender, ready, args, 2)) {
                    return true;
                }
                save(sender, ready.withEnabled(true), "&aThe " + zone.name() + " border is on.");
            }
            case "off", "disable" -> save(sender, zone.withEnabled(false), "&7The " + zone.name() + " border is off.");
            default -> usage(sender);
        }
        return true;
    }

    private void set(CommandSender sender, BorderZone zone, String[] args) {
        Double size = decimal(args, 2);
        if (size == null || size < BorderZone.MIN_SIZE || size > BorderZone.MAX_SIZE) {
            Text.send(sender, "BORDER.SIZE-USAGE", "&7/border {zone} set <size> [here] &8(size = one side, {min} to {max} blocks)",
                    "{zone}", zone.name(), "{min}", "4", "{max}", "59,999,968");
            return;
        }
        boolean here = args.length > 3 && args[3].equalsIgnoreCase("here");
        BorderZone base;
        if (here) {
            if (!(sender instanceof Player player)) {
                Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
                return;
            }
            Location at = player.getLocation();
            base = zone.withArea(at.getWorld().getName(), at.getBlockX() + 0.5D, at.getBlockZ() + 0.5D, size);
        } else if (zone.configured()) {
            base = zone.withArea(zone.world(), zone.centerX(), zone.centerZ(), size);
        } else {
            BorderZone fresh = defaultArea(sender, zone, true);
            if (fresh == null) {
                return;
            }
            base = fresh.withArea(fresh.world(), fresh.centerX(), fresh.centerZ(), size);
        }
        if (!allowedOnMainWorld(sender, base, args, 4)) {
            return;
        }
        save(sender, base.withEnabled(true), "&aThe " + zone.name() + " border is now &f" + base.describe() + "&a.");
    }

    private void center(CommandSender sender, BorderZone zone) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return;
        }
        Location at = player.getLocation();
        BorderZone moved = zone.withArea(at.getWorld().getName(), at.getBlockX() + 0.5D, at.getBlockZ() + 0.5D, zone.size());
        if (!allowedOnMainWorld(sender, moved, new String[0], 0)) {
            return;
        }
        save(sender, moved, "&aCentre moved to where you stand.");
    }

    private void mode(CommandSender sender, BorderZone zone, String[] args) {
        BorderZone.Mode mode = args.length > 2 ? BorderZone.Mode.parse(args[2], null) : null;
        if (mode == null) {
            Text.send(sender, "BORDER.MODE-USAGE", "&7/border {zone} mode <player|world>", "{zone}", zone.name());
            Text.raw(sender, "&8  player: only players inside the area are held (safe in a survival world)");
            Text.raw(sender, "&8  world: the whole world ends at the edge (for a lobby-only world)");
            return;
        }
        BorderZone changed = zone.withMode(mode);
        if (!allowedOnMainWorld(sender, changed, args, 3)) {
            return;
        }
        save(sender, changed, "&aMode set to &f" + mode.name().toLowerCase(Locale.ROOT) + "&a.");
    }

    private boolean allowedOnMainWorld(CommandSender sender, BorderZone zone, String[] args, int confirmIndex) {
        if (zone.mode() != BorderZone.Mode.WORLD || !BorderManager.isMainWorld(zone.world())) {
            return true;
        }
        boolean confirmed = false;
        for (int i = Math.max(0, confirmIndex - 1); i < args.length; i++) {
            confirmed |= args[i].equalsIgnoreCase("confirm");
        }
        if (confirmed) {
            return true;
        }
        Text.send(sender, "BORDER.MAIN-WORLD", "&c{world} is your main world. In world mode its border would shrink to"
                + " this square for everyone, including survival. Use player mode, or add &fconfirm &cto the command.",
                "{world}", zone.world());
        return false;
    }

    private BorderZone defaultArea(CommandSender sender, BorderZone zone, boolean forSet) {
        if (zone.name().equals("lobby")) {
            if (plugin.getLobbyManager() == null || !plugin.getLobbyManager().hasLobbyLocation()) {
                Text.send(sender, "BORDER.NO-LOBBY", "&cSet the lobby first with &f/setlobby&c.");
                return null;
            }
            Location lobby = plugin.getLobbyManager().getLobbyLocation();
            double[] bounds = plugin.getLobbyManager().getBorderBounds();
            double size = 200D;
            double x = lobby.getX();
            double z = lobby.getZ();
            if (bounds != null && bounds[1] > bounds[0]) {
                x = (bounds[0] + bounds[1]) / 2D;
                z = (bounds[2] + bounds[3]) / 2D;
                size = Math.max(bounds[1] - bounds[0], bounds[3] - bounds[2]);
            }
            return zone.withArea(lobby.getWorld().getName(), x, z, size);
        }
        Location spawn = plugin.getSpawnManager() == null ? null : plugin.getSpawnManager().getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) {
            Text.send(sender, "BORDER.NO-SPAWN", forSet ? "&cSet the spawn first with &f/setspawn&c, or add &fhere &cto use where you stand."
                    : "&cSet the spawn first with &f/setspawn&c, or use &f/border spawn set <size> here&c.");
            return null;
        }
        return zone.withArea(spawn.getWorld().getName(), spawn.getX(), spawn.getZ(), 200D);
    }

    private void save(CommandSender sender, BorderZone zone, String done) {
        boolean saved = borders.update(zone);
        if (zone.name().equals("lobby") && zone.configured() && plugin.getLobbyManager() != null
                && plugin.getLobbyManager().hasLobbyLocation()
                && plugin.getLobbyManager().getLobbyLocation().getWorld().getName().equals(zone.world())) {
            // Keep the lobby area (protection, lobby scoreboard) the same square as the visible border.
            plugin.getLobbyManager().setBorderSquare(zone.centerX(), zone.centerZ(), zone.half());
        }
        Text.raw(sender, Text.prefix() + done);
        if (!saved) {
            Text.send(sender, "BORDER.NOT-SAVED", "&cThe change works now but could not be saved to borders.yml; see the console.");
        }
    }

    private void info(CommandSender sender, BorderZone zone) {
        Text.raw(sender, "");
        Text.raw(sender, "&6&lBorder &8» &f" + zone.name() + " &8· " + status(zone));
        Text.raw(sender, "  &7Area: &f" + zone.describe());
        Text.raw(sender, "  &7Mode: &f" + zone.mode().name().toLowerCase(Locale.ROOT) + (zone.mode() == BorderZone.Mode.PLAYER
                ? " &8(only players inside are held; " + borders.playersInside(zone.name()) + " right now)"
                : " &8(the whole world ends at the edge)"));
        Text.raw(sender, "  &7Warning: &f" + zone.warningDistance() + " blocks &7/ &f" + zone.warningTime() + "s"
                + " &8· &7Damage: &f" + zone.damageAmount() + " per block after " + zone.damageBuffer() + " blocks");
        if (zone.mode() == BorderZone.Mode.PLAYER) {
            Text.raw(sender, "  &8Damage only applies in world mode: in player mode nobody can get outside to be hurt.");
        }
        String name = zone.name();
        Text.line(sender, "  ",
                Text.suggest("&e[size]", "/border " + name + " set ", "&7/border " + name + " set <size> [here]"), " ",
                Text.button("&e[centre here]", "/border " + name + " center", "&7Move the centre to where you stand"), " ",
                zone.enabled() ? Text.button("&c[turn off]", "/border " + name + " off", "&7Remove the border")
                        : Text.button("&a[turn on]", "/border " + name + " on", "&7Put the border up"));
    }

    private static String status(BorderZone zone) {
        if (!zone.configured()) {
            return "&8not set up";
        }
        return zone.enabled() ? "&aon" : "&coff";
    }

    private static void usage(CommandSender sender) {
        Text.send(sender, "BORDER.USAGE", "&7/border [spawn|lobby] [set <size> [here]|center|warning <blocks> [seconds]|damage <amount> [buffer]|mode <player|world>|on|off]");
    }

    private static Integer integer(String[] args, int index) {
        try {
            return args.length > index ? Integer.parseInt(args[index]) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double decimal(String[] args, int index) {
        try {
            double value = args.length > index ? Double.parseDouble(args[index]) : Double.NaN;
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(BorderManager.ZONES);
        } else if (args.length == 2) {
            options.addAll(List.of("info", "set", "center", "warning", "damage", "mode", "on", "off"));
        } else if (args.length == 3) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "set" -> options.addAll(List.of("50", "100", "200", "500"));
                case "mode" -> options.addAll(List.of("player", "world"));
                case "warning" -> options.addAll(List.of("5", "10"));
                case "damage" -> options.addAll(List.of("0.2", "1"));
                default -> {
                }
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            options.add("here");
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
