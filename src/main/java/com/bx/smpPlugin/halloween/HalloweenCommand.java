// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.seasonal.CalendarCommand;
import com.bx.smpPlugin.seasonal.CalendarMenu;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HalloweenCommand extends CalendarCommand {

    private final HalloweenHub hub;
    private final HalloweenMenus menus;

    public HalloweenCommand(SmpPlugin plugin, HalloweenManager halloween, HalloweenHub hub, HalloweenMenus menus) {
        super(plugin, halloween);
        this.hub = hub;
        this.menus = menus;
    }

    @Override
    protected boolean open(CommandSender sender) {
        if (sender instanceof Player player && PermissionUtils.has(player, calendar.usePermission())
                && calendar.isTesting(player.getUniqueId())) {
            // Staff testing a date go straight to the calendar the test applies to.
            new CalendarMenu(plugin, calendar).open(player);
            return true;
        }
        return super.open(sender);
    }

    @Override
    protected boolean handlePlayerAction(CommandSender sender, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("calendar")) {
            if (sender instanceof Player player && PermissionUtils.has(player, calendar.usePermission()) && calendar.isEnabled()) {
                new CalendarMenu(plugin, calendar).open(player);
            } else {
                super.open(sender);
            }
            return true;
        }
        if (!action.equals("candy")) {
            return false;
        }
        if (args.length >= 4) {
            adjust(sender, args);
            return true;
        }
        if (sender instanceof Player player) {
            CandyAccount candy = hub.candy(player.getUniqueId());
            Text.send(player, "HALLOWEEN.CANDY-BALANCE", "&7You have &6{balance} {candy} &8(earned {earned} this season)",
                    "{balance}", Long.toString(candy.balance()), "{candy}", hub.candyName(),
                    "{earned}", Long.toString(candy.earned()));
        } else {
            Text.send(sender, "HALLOWEEN.CANDY-USAGE", "&7/halloween candy <player> <give|take|set> <amount>");
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void adjust(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, calendar.adminPermission())) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException ignored) {
            amount = -1L;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        if (amount < 0L || amount > CandyAccount.MAX || !List.of("give", "take", "set").contains(mode)) {
            Text.send(sender, "HALLOWEEN.CANDY-USAGE", "&7/halloween candy <player> <give|take|set> <amount>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        long value = amount;
        hub.adjust(target.getUniqueId(), balance -> switch (mode) {
            case "give" -> balance + value;
            case "take" -> Math.max(0L, balance - value);
            default -> value;
        }, balance -> {
            plugin.getLogger().info("[Halloween] " + sender.getName() + " used candy " + mode + " " + value + " on "
                    + args[1] + "; balance now " + balance + ".");
            Text.send(sender, "HALLOWEEN.CANDY-SET", "&f{player} &7now has &6{balance} {candy}&7.", "{player}", args[1],
                    "{balance}", Long.toString(balance), "{candy}", hub.candyName());
        });
    }

    @Override
    protected List<String> playerActions(CommandSender sender) {
        return List.of("calendar", "candy");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("candy")) {
            if (!PermissionUtils.has(sender, calendar.adminPermission())) {
                return List.of();
            }
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
                return filter(names, args[1]);
            }
            return args.length == 3 ? filter(List.of("give", "take", "set"), args[2]) : List.of();
        }
        return super.onTabComplete(sender, command, alias, args);
    }
}
