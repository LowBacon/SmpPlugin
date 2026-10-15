package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Staff command for building AFK zones:
 * <pre>
 * /afkzone pos1 | pos2                     mark two corners where you stand
 * /afkzone create &lt;name&gt; [radius]          new zone from the corners (or a square around you)
 * /afkzone setregion &lt;name&gt; [radius]       move an existing zone's area
 * /afkzone setspawn &lt;name&gt;                 where /afk drops players
 * /afkzone reward &lt;name&gt; &lt;shards&gt; &lt;time&gt;   e.g. reward main 1 1m
 * /afkzone rename &lt;name&gt; &lt;display name&gt;
 * /afkzone delete &lt;name&gt; | list | show | reload
 * </pre>
 */
public class AfkZoneCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.admin.afkzone";

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([hms]?)", Pattern.CASE_INSENSITIVE);
    private static final List<String> SUBCOMMANDS = List.of(
            "pos1", "pos2", "create", "setregion", "setspawn", "reward", "rename", "delete", "list", "show", "reload");

    private final SmpPlugin plugin;

    public AfkZoneCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            send(sender, "&cYou do not have permission to do that.");
            return true;
        }
        AfkManager afk = plugin.getAfkManager();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                list(sender, afk);
                return true;
            }
            case "reload" -> {
                afk.reload();
                send(sender, "&aafk.yml reloaded. &7" + afk.getZones().size() + " zone(s).");
                return true;
            }
            case "delete", "remove" -> {
                AfkZone zone = requireZone(sender, afk, args);
                if (zone != null) {
                    afk.deleteZone(zone.id());
                    send(sender, "&aDeleted AFK zone &f" + zone.id() + "&a.");
                }
                return true;
            }
            case "reward" -> {
                reward(sender, afk, args);
                return true;
            }
            case "rename" -> {
                AfkZone zone = requireZone(sender, afk, args);
                if (zone != null && args.length >= 3) {
                    String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    afk.saveZone(zone.withDisplayName(name));
                    send(sender, "&aRenamed &f" + zone.id() + " &ato " + name + "&a.");
                } else if (zone != null) {
                    send(sender, "&cUsage: /" + label + " rename <name> <display name>");
                }
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player player)) {
            if (!sub.equals("help")) {
                send(sender, "&cThat part of /" + label + " needs you in game.");
            }
            help(sender, label);
            return true;
        }

        switch (sub) {
            case "pos1", "pos2" -> {
                int index = sub.equals("pos1") ? 0 : 1;
                afk.setCorner(player, index, player.getLocation());
                send(player, "&aCorner " + (index + 1) + " set at &f" + player.getLocation().getBlockX() + ", "
                        + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + "&a.");
                AfkZone.Region selection = afk.getSelection(player);
                if (selection != null) {
                    send(player, "&7Selection: &f" + selection.describe() + "&7. Now &f/" + label + " create <name>&7.");
                }
            }
            case "create" -> create(player, afk, args, label);
            case "setregion" -> {
                AfkZone zone = requireZone(player, afk, args);
                AfkZone.Region region = zone == null ? null : regionFrom(player, afk, args, label);
                if (region != null) {
                    afk.saveZone(zone.withRegion(region));
                    send(player, "&aArea of &f" + zone.id() + " &aset to &f" + region.describe() + "&a.");
                    afk.showOutline(player, 30_000L);
                }
            }
            case "setspawn" -> {
                AfkZone zone = requireZone(player, afk, args);
                if (zone != null) {
                    afk.saveZone(zone.withSpawn(player.getLocation()));
                    send(player, "&a/afk now drops players here for &f" + zone.id() + "&a.");
                }
            }
            case "show" -> {
                afk.showOutline(player, 30_000L);
                send(player, "&aShowing nearby AFK zone edges for 30 seconds.");
            }
            default -> help(player, label);
        }
        return true;
    }

    private void create(Player player, AfkManager afk, String[] args, String label) {
        if (args.length < 2) {
            send(player, "&cUsage: /" + label + " create <name> [radius]");
            return;
        }
        String id = AfkZoneStore.normalizeId(args[1]);
        if (!AfkZoneStore.isValidId(id)) {
            send(player, "&cZone names use letters, numbers, - and _ (up to 32).");
            return;
        }
        if (afk.getZone(id) != null) {
            send(player, "&cThere is already a zone called &f" + id + "&c. Use &f/" + label + " setregion " + id + "&c to move it.");
            return;
        }
        AfkZone.Region region = regionFrom(player, afk, args, label);
        if (region == null) {
            return;
        }
        AfkZone zone = new AfkZone(id, "&d" + args[1], "", region, 1L, 60).withSpawn(player.getLocation());
        afk.saveZone(zone);
        afk.showOutline(player, 30_000L);
        send(player, "&aCreated AFK zone &f" + id + "&a: " + region.describe() + ".");
        send(player, "&7It pays &b1 shard &7every &f1m&7. Change it with &f/" + label + " reward " + id + " <shards> <time>&7.");
    }

    /** A square when a radius is given, otherwise the pos1/pos2 selection. */
    private AfkZone.Region regionFrom(Player player, AfkManager afk, String[] args, String label) {
        if (args.length >= 3) {
            int radius;
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                radius = -1;
            }
            if (radius < 1 || radius > 512) {
                send(player, "&cThe radius has to be a number from 1 to 512.");
                return null;
            }
            return AfkZone.Region.square(player.getLocation(), radius);
        }
        AfkZone.Region selection = afk.getSelection(player);
        if (selection == null) {
            send(player, "&cMark two corners with &f/" + label + " pos1 &cand &f/" + label + " pos2 &cfirst, or give a radius.");
        }
        return selection;
    }

    private void reward(CommandSender sender, AfkManager afk, String[] args) {
        AfkZone zone = requireZone(sender, afk, args);
        if (zone == null) {
            return;
        }
        if (args.length < 4) {
            send(sender, "&cUsage: /afkzone reward " + zone.id() + " <shards> <time>  &7(e.g. 1 1m, 5 2m30s)");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            amount = -1L;
        }
        int seconds = parseSeconds(args[3]);
        if (amount < 0L || seconds < 1) {
            send(sender, "&cGive a shard amount (0 or more) and a time like 60, 1m or 2m30s.");
            return;
        }
        afk.saveZone(zone.withReward(amount, seconds));
        send(sender, amount == 0L
                ? "&a" + zone.id() + " no longer pays shards."
                : "&a" + zone.id() + " now pays &b" + amount + " shard(s) &aevery &f" + NumberUtils.formatCountdown(seconds) + "&a.");
    }

    private void list(CommandSender sender, AfkManager afk) {
        if (afk.getZones().isEmpty()) {
            send(sender, "&7No AFK zones yet. Start with &f/afkzone pos1&7.");
            return;
        }
        send(sender, "&8&m----&r &dAFK zones &8&m----");
        for (AfkZone zone : afk.getZones()) {
            send(sender, "&f" + zone.id() + " &8- " + zone.displayName()
                    + " &8| &7" + (zone.hasRegion() ? zone.region().describe() : "teleport only")
                    + (zone.paysRewards() ? " &8| &b" + zone.rewardAmount() + " &7per &f" + NumberUtils.formatCountdown(zone.rewardSeconds()) : "")
                    + " &8| &7players: &f" + afk.countPlayersIn(zone)
                    + (zone.spawnLocation() == null ? " &c(no spawn)" : ""));
        }
    }

    private AfkZone requireZone(CommandSender sender, AfkManager afk, String[] args) {
        if (args.length < 2) {
            send(sender, "&cName the zone. &7/afkzone list shows them.");
            return null;
        }
        AfkZone zone = afk.getZone(args[1]);
        if (zone == null) {
            send(sender, "&cThere is no AFK zone called &f" + args[1] + "&c.");
        }
        return zone;
    }

    private static void help(CommandSender sender, String label) {
        send(sender, "&8&m----&r &dAFK zones &8&m----");
        send(sender, "&f/" + label + " pos1 &7/ &fpos2 &7- mark two corners where you stand");
        send(sender, "&f/" + label + " create <name> [radius] &7- new zone (corners, or a square around you)");
        send(sender, "&f/" + label + " setregion <name> [radius] &7- move a zone's area");
        send(sender, "&f/" + label + " setspawn <name> &7- where /afk drops players");
        send(sender, "&f/" + label + " reward <name> <shards> <time> &7- e.g. 1 1m");
        send(sender, "&f/" + label + " rename <name> <display name>");
        send(sender, "&f/" + label + " delete <name> &7| &flist &7| &fshow &7| &freload");
    }

    /** "60", "90s", "1m", "2m30s", "1h"; -1 when it does not parse. */
    static int parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = DURATION.matcher(text);
        int total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return -1;
            }
            int value = Integer.parseInt(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "h" -> value * 3600;
                case "m" -> value * 60;
                default -> value;
            };
            end = matcher.end();
        }
        return end == text.length() && end > 0 ? total : -1;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.toComponent(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && List.of("setregion", "setspawn", "reward", "rename", "delete", "remove")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            plugin.getAfkManager().getZones().forEach(zone -> options.add(zone.id()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reward")) {
            options.addAll(List.of("1", "2", "5"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("reward")) {
            options.addAll(List.of("30s", "1m", "2m", "5m"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
