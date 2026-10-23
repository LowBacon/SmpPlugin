// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorldCommand {

    private final SmpPlugin plugin;
    private final WorldSystem worlds;

    public WorldCommand(SmpPlugin plugin, WorldSystem worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    public void handle(CommandSender sender, String label, String[] args) {
        if (!PermissionUtils.has(sender, WorldSystem.ADMIN)) {
            send(sender, "&cYou do not have permission to manage the worlds.");
            return;
        }
        String sub = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                send(sender, "&8&m        &r &b&lWorlds &8&m        ");
                worlds.statusLines().forEach(line -> send(sender, line));
            }
            case "info" -> info(sender, args);
            case "setspawn" -> setSpawn(sender, label, args);
            case "tp", "teleport" -> teleport(sender, label, args);
            case "reload" -> reload(sender);
            default -> send(sender, "&cUsage: /" + label + " world <status|info [lobby|spawn|ffa]|setspawn <lobby|spawn|ffa>"
                    + "|tp [player] <lobby|spawn|ffa>|reload>");
        }
    }

    private void info(CommandSender sender, String[] args) {
        WorldRules rules;
        if (args.length >= 3) {
            rules = worlds.settings().rules(args[2]);
        } else if (sender instanceof Player player) {
            rules = worlds.rulesFor(player.getWorld());
        } else {
            rules = null;
        }
        if (rules == null) {
            send(sender, "&7Not a managed world. Use &f/smp world info <lobby|spawn|ffa>&7.");
            return;
        }
        World world = worlds.world(rules.key());
        send(sender, "&8&m        &r &b&l" + rules.key() + " &8(" + rules.worldName() + ") &8&m        ");
        send(sender, "&7Loaded: " + (world == null ? "&cno" : "&ayes&7, " + world.getPlayers().size() + " player(s)"));
        send(sender, "&7Sees: &f" + WorldSystem.seesText(rules.visibility(), rules.visibilityGroup()) + " &8| &7Sidebar: " + onOff(rules.sidebar())
                + " &8| &7PvP: " + onOff(rules.pvp()));
        send(sender, "&7Protection: &f" + (rules.protectedRadius() < 0 ? "whole world"
                : rules.protectedRadius() == 0 ? "off" : rules.protectedRadius() + " blocks around the spawn"));
        send(sender, "&7Break " + onOff(rules.blockBreak()) + " &7Place " + onOff(rules.blockPlace())
                + " &7Containers " + onOff(rules.containers()) + " &7Drops " + onOff(rules.itemDrop())
                + " &7Hunger " + onOff(rules.hunger()) + " &7Fall " + onOff(rules.fallDamage())
                + " &7Portals " + onOff(rules.portals()));
        send(sender, "&7Command allowlist: " + (rules.commandAllowlist() ? "&f" + String.join(", ",
                rules.allowedCommands().stream().sorted().toList()) : "&coff"));
        Location spawn = worlds.spawnLocation(rules.key());
        if (spawn != null) {
            send(sender, String.format(Locale.ROOT, "&7Arrival point: &f%.1f %.1f %.1f", spawn.getX(), spawn.getY(), spawn.getZ()));
        }
    }

    private void setSpawn(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cOnly a player can set a spawn (it uses where you stand).");
            return;
        }
        if (args.length < 3 || worlds.settings().rules(args[2]) == null) {
            send(sender, "&cUsage: /" + label + " world setspawn <lobby|spawn|ffa>");
            return;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        if (!worlds.isWorld(player.getWorld(), key)) {
            send(sender, "&cStand in the " + key + " world to set its spawn.");
            return;
        }
        if (worlds.setSpawn(key, player.getLocation())) {
            send(sender, "&aSet the " + key + " arrival point to where you stand.");
        } else {
            send(sender, "&cCould not save worlds.yml; the arrival point did not change.");
        }
    }

    private void teleport(CommandSender sender, String label, String[] args) {
        // "/smp world tp spawn" moves yourself; "/smp world tp <player> spawn" someone else.
        boolean self = args.length == 3 && sender instanceof Player && worlds.settings().rules(args[2]) != null;
        if (!self && (args.length < 4 || worlds.settings().rules(args[3]) == null)) {
            send(sender, "&cUsage: /" + label + " world tp [player] <lobby|spawn|ffa>");
            return;
        }
        Player target = self ? (Player) sender : Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, "&cThat player is not online.");
            return;
        }
        String key = (self ? args[2] : args[3]).toLowerCase(Locale.ROOT);
        // Staff moving a player decide that themselves, so combat does not block it.
        worlds.transfer(target, key, true).thenAccept(failure -> {
            if (failure == WorldSystem.Failure.NONE) {
                send(sender, "&aSent &f" + target.getName() + " &ato the " + key + " world.");
            } else {
                send(sender, WorldSystem.describe(failure, key));
            }
        });
    }

    private void reload(CommandSender sender) {
        plugin.getConfigManager().reload();
        List<String> problems = worlds.reload();
        if (!problems.isEmpty()) {
            send(sender, "&cworlds.yml was not applied; the current settings stay:");
            problems.forEach(problem -> send(sender, "&7- &f" + problem));
            return;
        }
        if (plugin.getLobbyManager() != null) {
            plugin.getLobbyManager().reload();
        }
        send(sender, "&aReloaded worlds.yml.");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (!PermissionUtils.has(sender, WorldSystem.ADMIN)) {
            return options;
        }
        if (args.length == 2) {
            options.addAll(List.of("status", "info", "setspawn", "tp", "reload"));
        } else if (args.length == 3 && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("setspawn"))) {
            options.addAll(WorldSettings.KEYS);
        } else if (args.length == 3 && args[1].equalsIgnoreCase("tp")) {
            options.addAll(WorldSettings.KEYS);
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 4 && args[1].equalsIgnoreCase("tp")) {
            options.addAll(WorldSettings.KEYS);
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }

    private static String onOff(boolean value) {
        return value ? "&aon" : "&coff";
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.toComponent(message));
    }
}
