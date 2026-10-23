// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Reward;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.halloween.CalendarRules;
import com.bx.smpPlugin.models.EconomyReason;
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

public class CalendarCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_ACTIONS = List.of("edit", "give", "reset", "test", "stats", "reload");

    protected final SmpPlugin plugin;
    protected final SeasonCalendar calendar;

    public CalendarCommand(SmpPlugin plugin, SeasonCalendar calendar) {
        this.plugin = plugin;
        this.calendar = calendar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && handlePlayerAction(sender, args)) {
            return true;
        }
        if (args.length == 0 || !PermissionUtils.has(sender, calendar.adminPermission())) {
            return open(sender);
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "edit" -> {
                if (sender instanceof Player player) {
                    new CalendarEditorMenu(plugin, calendar).open(player);
                } else {
                    Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
                }
            }
            case "give" -> give(sender, args);
            case "reset" -> reset(sender, args);
            case "test", "preview" -> test(sender, args);
            case "stats" -> stats(sender);
            case "reload" -> {
                plugin.getConfigManager().reload();
                calendar.loadOverrides();
                Text.send(sender, "CALENDAR.RELOADED", "&aReloaded {file} and the in-game prizes.",
                        "{file}", calendar.fileName());
                for (String problem : calendar.validate()) {
                    Text.raw(sender, "&e ⚠ " + problem);
                }
            }
            default -> Text.send(sender, "CALENDAR.ADMIN-USAGE",
                    "&7/{command} &8[&fedit&8|&fgive <player> <door>&8|&freset <player> [door]&8|&ftest <yyyy-MM-dd|off>&8|&fstats&8|&freload&8]",
                    "{command}", calendar.command());
        }
        return true;
    }

    protected boolean handlePlayerAction(CommandSender sender, String[] args) {
        return false;
    }

    protected boolean open(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, calendar.usePermission())) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (!calendar.isEnabled() && !calendar.isTesting(player.getUniqueId())) {
            Text.send(player, "CALENDAR.DISABLED", "&7The {name} is closed on this server right now.",
                    "{name}", calendar.theme().name());
            return true;
        }
        if (!calendar.isTesting(player.getUniqueId()) && !plugin.getV3Module().store().isLoaded(player.getUniqueId())) {
            Text.send(player, "GENERAL.LOADING", "&7Your data is still loading. Try again in a moment.");
            return true;
        }
        calendar.open(player);
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.send(sender, "CALENDAR.GIVE-USAGE", "&7/{command} give <player> <door>", "{command}", calendar.command());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        int door = parseDoor(args[2]);
        if (target == null || door < 0) {
            Text.send(sender, "CALENDAR.GIVE-USAGE", "&7/{command} give <player> <door>", "{command}", calendar.command());
            return;
        }
        Reward prize = calendar.prize(door);
        prize.give(plugin, target, EconomyReason.EVENT_REWARD,
                EconomyContext.of(sender.getName(), calendar.key() + " door " + door + " given by staff"));
        plugin.getLogger().info("[" + calendar.id().displayName() + "] " + sender.getName() + " gave " + target.getName()
                + " the prize of door " + door + ".");
        Text.send(sender, "CALENDAR.GIVEN", "&aGave &f{player} &athe prize of door {door}.",
                "{player}", target.getName(), "{door}", Integer.toString(door));
    }

    @SuppressWarnings("deprecation")
    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "CALENDAR.RESET-USAGE", "&7/{command} reset <player> [door]", "{command}", calendar.command());
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int door = args.length >= 3 ? parseDoor(args[2]) : 0;
        if (door < 0) {
            Text.send(sender, "CALENDAR.RESET-USAGE", "&7/{command} reset <player> [door]", "{command}", calendar.command());
            return;
        }
        calendar.reset(target.getUniqueId(), door, changed -> {
            if (changed) {
                plugin.getLogger().info("[" + calendar.id().displayName() + "] " + sender.getName() + " closed "
                        + (door <= 0 ? "every door" : "door " + door) + " for " + args[1] + " again.");
            }
            Text.send(sender, "CALENDAR.RESET-DONE",
                    changed ? "&aClosed {what} &afor &f{player}&a again." : "&7Nothing to reset for &f{player}&7.",
                    "{what}", door <= 0 ? "every door" : "door " + door, "{player}", args[1]);
        });
    }

    private void test(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("off")) {
            calendar.setTestDate(player.getUniqueId(), null);
            Text.send(player, "CALENDAR.TEST-OFF", "&7Test mode off. The calendar shows your real doors again.");
            return;
        }
        LocalDate date = args[1].equalsIgnoreCase("today") ? LocalDate.now(calendar.zone()) : SeasonCalendar.parseDate(args[1]);
        if (date == null) {
            Text.send(player, "CALENDAR.TEST-USAGE", "&7/{command} test <yyyy-MM-dd|today|off>, e.g. &f{example}",
                    "{command}", calendar.command(), "{example}",
                    LocalDate.of(calendar.seasonYear(), calendar.rules().month(), calendar.rules().firstDay()).toString());
            return;
        }
        calendar.setTestDate(player.getUniqueId(), date);
        Text.send(player, "CALENDAR.TEST-ON",
                "&dTest mode: the calendar thinks it is &f{date}&d for you only. Opening doors gives nothing and saves"
                        + " nothing. &7/{command} test off &dto stop.",
                "{date}", date.toString(), "{command}", calendar.command());
    }

    private void stats(CommandSender sender) {
        calendar.doorStatistics(counts -> {
            Text.send(sender, "CALENDAR.STATS-HEADER", "&f{name} {year}: &7players per door",
                    "{name}", calendar.theme().name(), "{year}", Integer.toString(calendar.seasonYear()));
            if (counts.isEmpty()) {
                Text.raw(sender, "&8  No doors opened yet.");
                return;
            }
            StringBuilder line = new StringBuilder("&7");
            for (Map.Entry<Integer, Integer> entry : new TreeMap<>(counts).entrySet()) {
                line.append(calendar.theme().accent()).append(entry.getKey()).append("&8:&f").append(entry.getValue()).append("  ");
            }
            Text.raw(sender, line.toString());
        });
    }

    protected int parseDoor(String raw) {
        try {
            int door = Integer.parseInt(raw);
            return door >= 1 && door <= 31 ? door : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    protected List<String> playerActions(CommandSender sender) {
        return List.of();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean staff = PermissionUtils.has(sender, calendar.adminPermission());
        if (args.length == 1) {
            List<String> options = new ArrayList<>(playerActions(sender));
            if (staff) {
                options.addAll(ADMIN_ACTIONS);
            }
            return filter(options, args[0]);
        }
        if (!staff) {
            return List.of();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("test") || args[0].equalsIgnoreCase("preview"))) {
            int year = calendar.seasonYear();
            CalendarRules rules = calendar.rules();
            return filter(List.of("off", "today",
                    LocalDate.of(year, rules.month(), rules.firstDay()).toString(),
                    LocalDate.of(year, rules.month(), rules.lastDayIn(year)).toString()), args[1]);
        }
        return List.of();
    }

    protected static List<String> filter(List<String> options, String typed) {
        String lower = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
