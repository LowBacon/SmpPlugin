package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Lookup;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /easter: your egg hunt progress and the best hunters. Staff hide eggs with
 * /easter addegg (the block you look at), and use removeegg [id], eggs and reset &lt;player&gt;.
 */
public final class EasterCommand implements CommandExecutor, TabCompleter {

    private final SmpPlugin plugin;
    private final EasterEvent easter;

    EasterCommand(SmpPlugin plugin, EasterEvent easter) {
        this.plugin = plugin;
        this.easter = easter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, EventCommand.USE_PERMISSION)) {
            send(sender, "&cYou do not have permission to use /" + label + ".");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean staff = PermissionUtils.has(sender, EventCommand.ADMIN_PERMISSION);
        switch (sub) {
            case "top" -> top(sender);
            case "addegg" -> {
                if (staff && sender instanceof Player player) {
                    Block block = player.getTargetBlockExact(6);
                    if (block == null || block.getType().isAir()) {
                        send(sender, "&cLook at the block that should be the egg (within 6 blocks).");
                        return true;
                    }
                    String id = easter.addEgg(block);
                    send(sender, id == null ? "&cThere is already an egg there."
                            : "&aHid egg &f" + id + " &aat " + block.getX() + " " + block.getY() + " " + block.getZ()
                            + "&a. &7(" + easter.eggs().size() + " eggs)");
                } else {
                    help(sender, label, staff);
                }
            }
            case "removeegg", "delegg" -> {
                if (!staff) {
                    help(sender, label, false);
                    return true;
                }
                Block block = sender instanceof Player player ? player.getTargetBlockExact(6) : null;
                String removed = easter.removeEgg(block, args.length > 1 ? args[1] : null);
                send(sender, removed == null ? "&cNo egg there. Look at an egg or give its id (/" + label + " eggs)."
                        : "&aRemoved egg &f" + removed + "&a.");
            }
            case "eggs" -> {
                if (!staff) {
                    help(sender, label, false);
                    return true;
                }
                send(sender, "&d&lEaster eggs &7(" + easter.eggs().size() + ")");
                for (EasterEvent.Egg egg : easter.eggs()) {
                    send(sender, "&f" + egg.id() + " &7" + egg.world() + " " + egg.x() + " " + egg.y() + " " + egg.z());
                }
            }
            case "reset" -> {
                if (!staff || args.length < 2) {
                    help(sender, label, staff);
                    return true;
                }
                Lookup.account(plugin, sender, args[1], account -> {
                    if (account == null) {
                        send(sender, "&cThat player was not found.");
                        return;
                    }
                    easter.resetPlayer(account.uuid());
                    send(sender, "&aCleared the eggs &f" + account.displayName() + " &afound this season.");
                });
            }
            case "" -> {
                if (!(sender instanceof Player player)) {
                    help(sender, label, staff);
                    return true;
                }
                if (!easter.isRunning()) {
                    send(sender, "&7The egg hunt is not running right now. " + "&8(/event info easter)");
                    return true;
                }
                easter.progress(player).forEach(line -> send(sender, line));
                send(sender, "&7Right-click an egg to collect it. &8(/" + label + " top)");
            }
            default -> help(sender, label, staff);
        }
        return true;
    }

    private void top(CommandSender sender) {
        easter.top(10).whenComplete((rows, error) -> plugin.getSpigotScheduler().runGlobal(() -> {
            if (error != null) {
                send(sender, "&cCould not read the egg hunt leaderboard right now.");
                return;
            }
            send(sender, "&d&lBest egg hunters");
            if (rows.isEmpty()) {
                send(sender, "&7Nobody found an egg yet.");
            }
            int position = 1;
            for (Map.Entry<String, Integer> row : rows) {
                send(sender, "&d#" + position++ + " &f" + row.getKey() + " &7- &d" + row.getValue() + " eggs");
            }
        }));
    }

    private void help(CommandSender sender, String label, boolean staff) {
        send(sender, "&d/" + label + " &7- your progress  &d/" + label + " top &7- the best hunters");
        if (staff) {
            send(sender, "&d/" + label + " addegg &8| &dremoveegg [id] &8| &deggs &8| &dreset <player>");
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.toComponent(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean staff = PermissionUtils.has(sender, EventCommand.ADMIN_PERMISSION);
        if (args.length == 1) {
            options.add("top");
            if (staff) {
                options.addAll(List.of("addegg", "removeegg", "eggs", "reset"));
            }
        } else if (args.length == 2 && staff && args[0].equalsIgnoreCase("removeegg")) {
            easter.eggs().forEach(egg -> options.add(egg.id()));
        } else if (args.length == 2 && staff && args[0].equalsIgnoreCase("reset")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
