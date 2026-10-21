package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /event: the seasonal events.
 * <pre>
 * /event                     the events menu (players) or the status list (console)
 * /event status              every event and when it runs
 * /event info &lt;event&gt;        details and your progress
 * /event open &lt;event&gt;        the event's own screen (the calendar, the egg hunt...)
 * /event enable|disable|auto &lt;event&gt;   staff: run it now, stop it, or follow its dates
 * /event reload [event]      staff: read events.yml again
 * </pre>
 */
public final class EventCommand implements CommandExecutor, TabCompleter {

    public static final String USE_PERMISSION = "smpplugin.events";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.events";

    private final SmpPlugin plugin;
    private final EventManager manager;

    public EventCommand(SmpPlugin plugin, EventManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, USE_PERMISSION)) {
            send(sender, "&cYou do not have permission to use /" + label + ".");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (sender instanceof Player player) {
                    new EventsMenu(plugin, manager).open(player);
                } else {
                    status(sender);
                }
            }
            case "status", "list" -> status(sender);
            case "info" -> withEvent(sender, label, args, event -> info(sender, event));
            case "open" -> withEvent(sender, label, args, event -> {
                if (sender instanceof Player player) {
                    if (event.isRunning()) {
                        event.open(player);
                    } else {
                        send(sender, "&7" + event.displayName() + " &7is not running right now.");
                    }
                }
            });
            case "enable", "start", "on" -> staff(sender, label, args, event -> setMode(sender, event, EventManager.Mode.ON));
            case "disable", "stop", "off" -> staff(sender, label, args, event -> setMode(sender, event, EventManager.Mode.OFF));
            case "auto", "schedule" -> staff(sender, label, args, event -> setMode(sender, event, EventManager.Mode.AUTO));
            case "reload" -> {
                if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                    send(sender, "&cOnly staff can reload events.");
                    return true;
                }
                plugin.getConfigManager().reload();
                if (args.length > 1) {
                    Optional<SeasonalEvent> event = manager.get(args[1]);
                    if (event.isEmpty()) {
                        send(sender, "&cThere is no event called " + args[1] + ".");
                        return true;
                    }
                    manager.reload(event.get());
                    manager.tick();
                    send(sender, "&aReloaded " + event.get().displayName() + "&a.");
                } else {
                    manager.reload();
                    send(sender, "&aReloaded events.yml.");
                }
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        send(sender, "&8&m        &r &e&lEvents &8&m        ");
        send(sender, "&e/" + label + " &7- the events menu");
        send(sender, "&e/" + label + " status &7- every event and when it runs");
        send(sender, "&e/" + label + " info <event> &8| &eopen <event>");
        if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            send(sender, "&e/" + label + " enable|disable|auto <event> &7- run now, stop, or follow the dates");
            send(sender, "&e/" + label + " reload [event]");
        }
    }

    private void status(CommandSender sender) {
        send(sender, "&8&m        &r &e&lEvents &8&m        ");
        for (SeasonalEvent event : manager.all()) {
            send(sender, "&f" + event.displayName() + " &8(" + event.id() + ") &7- " + manager.statusLine(event));
        }
    }

    private void info(CommandSender sender, SeasonalEvent event) {
        send(sender, "&8&m        &r " + event.displayName() + " &8&m        ");
        send(sender, "&7" + event.description());
        send(sender, "&7Status: " + manager.statusLine(event));
        event.schedule().ifPresent(window -> send(sender, "&7Dates: &f" + window));
        if (event.isManaged()) {
            send(sender, "&7Mode: &f" + manager.mode(event).name().toLowerCase(Locale.ROOT));
        }
        if (sender instanceof Player player) {
            event.progress(player).forEach(line -> send(sender, line));
        }
    }

    private void setMode(CommandSender sender, SeasonalEvent event, EventManager.Mode mode) {
        if (event instanceof HalloweenEvent halloween) {
            // Halloween keeps its own dates (halloween.yml); staff switch its feature instead.
            boolean on = mode != EventManager.Mode.OFF;
            halloween.switchFeature(on);
            send(sender, on ? "&aHalloween is switched on; it runs on the dates in halloween.yml."
                    : "&cHalloween is switched off.");
            return;
        }
        if (!event.isConfigured() && mode == EventManager.Mode.ON) {
            send(sender, "&c" + event.id() + " has ENABLED: false in events.yml. Set it to true first.");
            return;
        }
        manager.setMode(event, mode);
        send(sender, switch (mode) {
            case ON -> "&a" + event.displayName() + " &ais running now, until you disable it or set it back to auto.";
            case OFF -> "&c" + event.displayName() + " &cis off. Progress is kept; /event auto " + event.id() + " follows its dates again.";
            case AUTO -> "&e" + event.displayName() + " &efollows its dates again: " + manager.statusLine(event);
        });
    }

    private void staff(CommandSender sender, String label, String[] args, java.util.function.Consumer<SeasonalEvent> action) {
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            send(sender, "&cOnly staff can switch events on and off.");
            return;
        }
        withEvent(sender, label, args, action);
    }

    private void withEvent(CommandSender sender, String label, String[] args, java.util.function.Consumer<SeasonalEvent> action) {
        if (args.length < 2) {
            send(sender, "&cUsage: /" + label + " " + args[0].toLowerCase(Locale.ROOT) + " <event>");
            return;
        }
        Optional<SeasonalEvent> event = manager.get(args[1]);
        if (event.isEmpty()) {
            send(sender, "&cThere is no event called " + args[1] + ". &7Events: &f" + ids());
            return;
        }
        action.accept(event.get());
    }

    private String ids() {
        return String.join(", ", manager.all().stream().map(SeasonalEvent::id).toList());
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.toComponent(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("status", "info", "open"));
            if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                options.addAll(List.of("enable", "disable", "auto", "reload"));
            }
        } else if (args.length == 2) {
            manager.all().forEach(event -> options.add(event.id()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
