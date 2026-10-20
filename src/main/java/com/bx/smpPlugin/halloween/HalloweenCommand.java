package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * /halloween [edit|give|reset|preview|stats|reload]. With no arguments it opens the calendar.
 */
public final class HalloweenCommand implements CommandExecutor, TabCompleter {

    public static final String USE_PERMISSION = "smpplugin.halloween";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.halloween";

    private static final List<String> ADMIN_ACTIONS = List.of("edit", "give", "reset", "preview", "stats", "reload");

    private final SmpPlugin plugin;
    private final HalloweenManager halloween;

    public HalloweenCommand(SmpPlugin plugin, HalloweenManager halloween) {
        this.plugin = plugin;
        this.halloween = halloween;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            return openCalendar(sender);
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "edit" -> {
                if (sender instanceof Player player) {
                    new CalendarEditorMenu(plugin, halloween).open(player);
                } else {
                    Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
                }
            }
            case "give" -> give(sender, args);
            case "reset" -> reset(sender, args);
            case "preview" -> preview(sender, args);
            case "stats" -> stats(sender);
            case "reload" -> {
                plugin.getConfigManager().reload();
                halloween.loadOverrides();
                Text.send(sender, "HALLOWEEN.RELOADED", "&aReloaded halloween.yml and the in-game prizes.");
            }
            default -> {
                Text.send(sender, "HALLOWEEN.ADMIN-USAGE",
                        "&7/halloween &8[&fedit&8|&fgive <player> <door>&8|&freset <player> [door]&8|&fpreview <yyyy-MM-dd|off>&8|&fstats&8|&freload&8]");
            }
        }
        return true;
    }

    private boolean openCalendar(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, USE_PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (!halloween.isEnabled()) {
            Text.send(player, "HALLOWEEN.DISABLED", "&7The Spooky Calendar is closed on this server.");
            return true;
        }
        new CalendarMenu(plugin, halloween).open(player);
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.send(sender, "HALLOWEEN.GIVE-USAGE", "&7/halloween give <player> <door>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        int door = parseDoor(args[2]);
        if (target == null || door < 0) {
            Text.send(sender, "HALLOWEEN.GIVE-USAGE", "&7/halloween give <player> <door>");
            return;
        }
        halloween.prize(door).give(plugin, target);
        Text.send(sender, "HALLOWEEN.GIVEN", "&aGave &f{player} &athe prize of door &6{door}&a.",
                "{player}", target.getName(), "{door}", Integer.toString(door));
    }

    @SuppressWarnings("deprecation")
    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "HALLOWEEN.RESET-USAGE", "&7/halloween reset <player> [door]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int door = args.length >= 3 ? parseDoor(args[2]) : 0;
        if (door < 0) {
            Text.send(sender, "HALLOWEEN.RESET-USAGE", "&7/halloween reset <player> [door]");
            return;
        }
        halloween.reset(target.getUniqueId(), door, changed -> Text.send(sender, "HALLOWEEN.RESET-DONE",
                changed ? "&aClosed {what} &afor &f{player}&a again." : "&7Nothing to reset for &f{player}&7.",
                "{what}", door <= 0 ? "every door" : "door &6" + door, "{player}", args[1]));
    }

    private void preview(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("off")) {
            halloween.setPreviewDate(null);
            Text.send(sender, "HALLOWEEN.PREVIEW-OFF", "&7Preview off. The calendar uses today's date again.");
            return;
        }
        LocalDate date = HalloweenManager.parseDate(args[1]);
        if (date == null) {
            Text.send(sender, "HALLOWEEN.PREVIEW-USAGE", "&7/halloween preview <yyyy-MM-dd|off>, e.g. &f2026-10-13");
            return;
        }
        halloween.setPreviewDate(date);
        Text.send(sender, "HALLOWEEN.PREVIEW-ON",
                "&dPreview: the calendar now thinks it is &f{date}&d for everyone, until a restart or /halloween preview off.",
                "{date}", date.toString());
    }

    private void stats(CommandSender sender) {
        halloween.doorStatistics(counts -> {
            Text.send(sender, "HALLOWEEN.STATS-HEADER", "&6Spooky Calendar {year}: &7players per door",
                    "{year}", Integer.toString(halloween.seasonYear()));
            if (counts.isEmpty()) {
                Text.raw(sender, "&8  No doors opened yet.");
                return;
            }
            StringBuilder line = new StringBuilder("&7");
            for (Map.Entry<Integer, Integer> entry : new TreeMap<>(counts).entrySet()) {
                line.append("&6").append(entry.getKey()).append("&8:&f").append(entry.getValue()).append("  ");
            }
            Text.raw(sender, line.toString());
        });
    }

    private int parseDoor(String raw) {
        try {
            int door = Integer.parseInt(raw);
            return door >= 1 && door <= 31 ? door : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(ADMIN_ACTIONS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            int year = halloween.seasonYear();
            CalendarRules rules = halloween.rules();
            return filter(List.of("off",
                    LocalDate.of(year, rules.month(), rules.firstDay()).toString(),
                    LocalDate.of(year, rules.month(), rules.lastDayIn(year)).toString()), args[1]);
        }
        return List.of();
    }

    static List<String> filter(List<String> options, String typed) {
        String lower = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
