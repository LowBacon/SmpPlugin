// SmpPlugin by LowBacon
package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class EventsCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.events";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.events";

    private final SmpPlugin plugin;
    private final SeasonalEvents events;

    public EventsCommand(SmpPlugin plugin, SeasonalEvents events) {
        this.plugin = plugin;
        this.events = events;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION) && !PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new EventsMenu(plugin, events).open(player);
            } else {
                status(sender);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("list")) {
            status(sender);
            return true;
        }
        Optional<EventId> id = EventId.parse(args[0]);
        if (id.isEmpty()) {
            usage(sender);
            return true;
        }
        if (args.length == 1) {
            detail(sender, id.get());
            return true;
        }
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        Optional<EventMode> mode = EventMode.parse(args[1]);
        if (mode.isEmpty()) {
            usage(sender);
            return true;
        }
        apply(plugin, events, sender, id.get(), mode.get());
        return true;
    }

    static void apply(SmpPlugin plugin, SeasonalEvents events, CommandSender sender, EventId id, EventMode mode) {
        SeasonalEvents.Change change = events.setMode(id, mode, sender.getName());
        switch (change) {
            case CHANGED -> Text.send(sender, "EVENTS.SWITCHED",
                    "&a{event} &7is now &f{mode}&7: {status}&7.", "{event}", id.displayName(),
                    "{mode}", modeName(mode), "{status}", statusText(events, id));
            case UNCHANGED -> Text.send(sender, "EVENTS.UNCHANGED", "&7{event} is already &f{mode}&7: {status}&7.",
                    "{event}", id.displayName(), "{mode}", modeName(mode), "{status}", statusText(events, id));
            case NOT_AVAILABLE -> Text.send(sender, "EVENTS.NOT-AVAILABLE",
                    "&c{event} did not start on this server (see the console), so it cannot be switched.",
                    "{event}", id.displayName());
            case STORAGE_LOCKED -> Text.send(sender, "EVENTS.STORAGE-LOCKED",
                    "&cdata/events.yml could not be read at start-up, so it is not changed. Fix or remove it and"
                            + " restart.");
        }
    }

    private void status(CommandSender sender) {
        Text.send(sender, "EVENTS.STATUS-HEADER", "&6&lSeasonal events");
        for (EventId id : EventId.values()) {
            Text.raw(sender, Text.get("EVENTS.STATUS-LINE", "&8 » &f{event}&8: {status} &8({mode})",
                    "{event}", id.displayName(), "{status}", statusText(events, id),
                    "{mode}", modeName(events.mode(id))));
        }
        if (!events.isStorageReadable()) {
            Text.raw(sender, Text.get("EVENTS.STORAGE-WARNING",
                    "&c data/events.yml is damaged; every event follows its dates until it is fixed."));
        }
    }

    private void detail(CommandSender sender, EventId id) {
        Text.send(sender, "EVENTS.DETAIL-HEADER", "&6&l{event}", "{event}", id.displayName());
        for (String line : describe(events, id, PermissionUtils.has(sender, ADMIN_PERMISSION))) {
            Text.raw(sender, "&8 » " + line);
        }
        if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            Text.raw(sender, Text.get("EVENTS.DETAIL-SWITCH", "&7 Switch it with &f/events {key} <on|off|auto>",
                    "{key}", id.key()));
        }
    }

    private void usage(CommandSender sender) {
        Text.send(sender, "EVENTS.USAGE", PermissionUtils.has(sender, ADMIN_PERMISSION)
                ? "&7/events &8[&fstatus&8|&f<advent|halloween|trickortreat> [on|off|auto]&8]"
                : "&7/events &8[&fstatus&8|&f<advent|halloween|trickortreat>&8]");
    }

    // ------------------------------------------------------------------ shared wording

    static String modeName(EventMode mode) {
        return switch (mode) {
            case AUTO -> Text.get("EVENTS.MODE-AUTO", "follows its dates");
            case ON -> Text.get("EVENTS.MODE-ON", "forced on");
            case OFF -> Text.get("EVENTS.MODE-OFF", "switched off");
        };
    }

    static String statusText(SeasonalEvents events, EventId id) {
        EventStatus status = events.status(id);
        return switch (status) {
            case ACTIVE -> Text.get("EVENTS.STATUS-ACTIVE", "&a● Running");
            case SCHEDULED -> {
                long wait = events.secondsUntilStart(id);
                yield Text.get("EVENTS.STATUS-SCHEDULED", "&e◷ Starts in {time}", "{time}", Text.duration(wait));
            }
            case COMPLETED -> {
                long wait = events.secondsUntilStart(id);
                yield Text.get("EVENTS.STATUS-COMPLETED", "&7✔ Over for this year &8(next in {time})",
                        "{time}", Text.duration(wait));
            }
            case DISABLED -> Text.get("EVENTS.STATUS-DISABLED", "&c✖ Off");
        };
    }

    static List<String> describe(SeasonalEvents events, EventId id, boolean staff) {
        List<String> lines = new ArrayList<>();
        lines.add(Text.get("EVENTS.LINE-STATUS", "&7Status: {status}", "{status}", statusText(events, id)));
        events.source(id).ifPresent(source -> {
            CalendarRules rules = source.rules();
            int year = rules.seasonYear(source.now().toLocalDate());
            lines.add(Text.get("EVENTS.LINE-DATES", "&7Dates: &f{from} &7to &f{to}{grace}",
                    "{from}", day(rules.firstDay(), rules.month()),
                    "{to}", day(rules.lastDayIn(year), rules.month()),
                    "{grace}", rules.graceDays() > 0 ? " &8(+" + rules.graceDays() + " grace days)" : ""));
            lines.add(Text.get("EVENTS.LINE-ZONE", "&7Time zone: &f{zone}", "{zone}", source.now().getZone().getId()));
            if (!source.configEnabled()) {
                lines.add(Text.get("EVENTS.LINE-CONFIG-OFF", "&cENABLED is false in its config file."));
            }
            if (!source.featureEnabled()) {
                lines.add(Text.get("EVENTS.LINE-FEATURE-OFF", "&cIts feature is turned off (/smpplugin features)."));
            }
        });
        if (staff) {
            EventControl.Setting setting = events.setting(id);
            lines.add(Text.get("EVENTS.LINE-MODE", "&7Switch: &f{mode}", "{mode}", modeName(setting.mode())));
            if (setting.changedBy() != null && setting.changedAtMillis() > 0L) {
                long ago = Math.max(0L, (System.currentTimeMillis() - setting.changedAtMillis()) / 1000L);
                lines.add(Text.get("EVENTS.LINE-CHANGED", "&8Changed by {actor} {time} ago",
                        "{actor}", setting.changedBy(), "{time}", Text.duration(ago)));
            }
        }
        return lines;
    }

    private static String day(int day, int month) {
        return day + " " + Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION) && !PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("status");
            for (EventId id : EventId.values()) {
                options.add(id.key());
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && PermissionUtils.has(sender, ADMIN_PERMISSION) && EventId.parse(args[0]).isPresent()) {
            return filter(List.of("on", "off", "auto"), args[1]);
        }
        return List.of();
    }

    static List<String> filter(List<String> options, String typed) {
        String lower = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).toList();
    }
}
