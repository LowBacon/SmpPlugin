// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.economy.LedgerText;
import com.bx.smpPlugin.economy.Money;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EcoCommand implements CommandExecutor, TabCompleter {

    static final String HISTORY_PERMISSION = "smpplugin.admin.economy.history";
    static final String STATS_PERMISSION = "smpplugin.admin.economy.stats";
    private static final String GIVE_PERMISSION = "smpplugin.admin.addmoney";
    private static final String TAKE_PERMISSION = "smpplugin.admin.removemoney";
    private static final String SET_PERMISSION = "smpplugin.admin.setmoney";

    private final SmpPlugin plugin;
    private final EconomyAdmin admin;

    public EcoCommand(SmpPlugin plugin) {
        this.plugin = plugin;
        this.admin = new EconomyAdmin(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give", "add" -> change(sender, label, args, EconomyAdmin.Action.GIVE, GIVE_PERMISSION);
            case "take", "remove" -> change(sender, label, args, EconomyAdmin.Action.TAKE, TAKE_PERMISSION);
            case "set" -> change(sender, label, args, EconomyAdmin.Action.SET, SET_PERMISSION);
            case "reset" -> reset(sender, label, args);
            case "history" -> history(sender, label, args);
            case "stats" -> stats(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void change(CommandSender sender, String label, String[] args, EconomyAdmin.Action action, String node) {
        if (!allowed(sender, node)) {
            return;
        }
        if (args.length < 3) {
            Text.send(sender, "ECONOMY.ECO-USAGE-CHANGE", "&cUsage: /{label} {sub} <player> <amount> [note]",
                    "{label}", label, "{sub}", args[0].toLowerCase(Locale.ROOT));
            return;
        }
        admin.run(sender, action, args[1], args[2], note(args, 3));
    }

    private void reset(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, SET_PERMISSION)) {
            return;
        }
        if (args.length < 2) {
            Text.send(sender, "ECONOMY.ECO-USAGE-RESET", "&cUsage: /{label} reset <player> [note]", "{label}", label);
            return;
        }
        admin.run(sender, EconomyAdmin.Action.RESET, args[1], null, note(args, 2));
    }

    private void history(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, HISTORY_PERMISSION)) {
            return;
        }
        if (args.length < 2) {
            Text.send(sender, "ECONOMY.ECO-USAGE-HISTORY", "&cUsage: /{label} history <player> [page]", "{label}", label);
            return;
        }
        EconomyManager.AccountReference account = plugin.getEconomyManager().resolveAccount(args[1]);
        if (account == null) {
            sender.sendMessage(Text.color(plugin.getConfigManager().getMessage("BALANCE.ADMIN.PLAYER-NOT-FOUND")));
            return;
        }
        int page = args.length >= 3 ? TransactionsCommand.parsePage(args[2]) : 1;
        plugin.getEconomyManager().history(account.uuid(), page, TransactionsCommand.PAGE_SIZE,
                result -> TransactionsCommand.render(plugin, sender, result,
                        Text.get("ECONOMY.ECO-HISTORY-HEADER", "&6&lBalance history of {player}", "{player}",
                                account.displayName()),
                        label + " history " + account.displayName()));
    }

    private void stats(CommandSender sender, String[] args) {
        if (!allowed(sender, STATS_PERMISSION)) {
            return;
        }
        int hours = 24;
        if (args.length >= 2) {
            try {
                hours = Math.max(1, Math.min(24 * 365, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                hours = 24;
            }
        }
        int window = hours;
        EconomyManager economy = plugin.getEconomyManager();
        economy.stats(System.currentTimeMillis() - window * 3_600_000L, stats -> {
            Text.raw(sender, Text.get("ECONOMY.STATS-HEADER", "&6&lEconomy &8» &7last {hours}h", "{hours}",
                    Integer.toString(window)));
            Text.raw(sender, Text.get("ECONOMY.STATS-SUPPLY", "&7Money supply: &f{total} &7across &f{accounts} &7accounts",
                    "{total}", money(stats.supply().total()), "{accounts}", Long.toString(stats.supply().accounts())));
            long created = 0L;
            long destroyed = 0L;
            for (EconomyStore.ReasonTotal total : stats.totals()) {
                created += total.creditedCents();
                destroyed += total.debitedCents();
                Text.raw(sender, Text.get("ECONOMY.STATS-LINE",
                        "&8• &f{reason} &8({count}) &a+{in} &c-{out}",
                        "{reason}", LedgerText.reason(total.reason()),
                        "{count}", Long.toString(total.count()),
                        "{in}", money(Money.toUnits(total.creditedCents())),
                        "{out}", money(Money.toUnits(total.debitedCents()))));
            }
            if (stats.totals().isEmpty()) {
                Text.raw(sender, Text.get("ECONOMY.STATS-NONE", "&7No balance changes in this window."));
            }
            // Payments move money between players: their credits and debits cancel out except for tax.
            Text.raw(sender, Text.get("ECONOMY.STATS-NET", "&7All credits &a+{in} &7· all debits &c-{out} &7· net &f{net}",
                    "{in}", money(Money.toUnits(created)), "{out}", money(Money.toUnits(destroyed)),
                    "{net}", (created >= destroyed ? "+" : "-") + money(Money.toUnits(Math.abs(created - destroyed)))));
            Text.raw(sender, Text.get("ECONOMY.STATS-WRITER",
                    "&7Balance writer: &f{pending} &7queued, &f{failed} &7failed, &f{conflicts} &7conflicts since start",
                    "{pending}", Long.toString(economy.pendingWrites()),
                    "{failed}", Long.toString(economy.failedWrites()),
                    "{conflicts}", Long.toString(economy.versionConflicts())));
        });
    }

    private void help(CommandSender sender, String label) {
        Text.raw(sender, Text.get("ECONOMY.ECO-HELP-HEADER", "&6&lEconomy tools"));
        if (PermissionUtils.has(sender, GIVE_PERMISSION)) {
            Text.raw(sender, "&e/" + label + " give <player> <amount> [note] &8- &7add money");
        }
        if (PermissionUtils.has(sender, TAKE_PERMISSION)) {
            Text.raw(sender, "&e/" + label + " take <player> <amount> [note] &8- &7remove money");
        }
        if (PermissionUtils.has(sender, SET_PERMISSION)) {
            Text.raw(sender, "&e/" + label + " set <player> <amount> [note] &8- &7overwrite a balance");
            Text.raw(sender, "&e/" + label + " reset <player> [note] &8- &7back to the starting balance");
        }
        if (PermissionUtils.has(sender, HISTORY_PERMISSION)) {
            Text.raw(sender, "&e/" + label + " history <player> [page] &8- &7every balance change");
        }
        if (PermissionUtils.has(sender, STATS_PERMISSION)) {
            Text.raw(sender, "&e/" + label + " stats [hours] &8- &7money supply and flows");
        }
    }

    private boolean allowed(CommandSender sender, String node) {
        if (PermissionUtils.has(sender, node)) {
            return true;
        }
        Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
        return false;
    }

    private String money(double amount) {
        return plugin.getCurrencyManager().formatMoney(amount);
    }

    private static String note(String[] args, int from) {
        return args.length <= from ? null : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (PermissionUtils.has(sender, GIVE_PERMISSION)) {
                options.add("give");
            }
            if (PermissionUtils.has(sender, TAKE_PERMISSION)) {
                options.add("take");
            }
            if (PermissionUtils.has(sender, SET_PERMISSION)) {
                options.add("set");
                options.add("reset");
            }
            if (PermissionUtils.has(sender, HISTORY_PERMISSION)) {
                options.add("history");
            }
            if (PermissionUtils.has(sender, STATS_PERMISSION)) {
                options.add("stats");
            }
            return startsWith(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("give", "add", "take", "remove", "set", "reset", "history").contains(sub)) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!(sender instanceof Player viewer) || viewer.canSee(online)) {
                    names.add(online.getName());
                }
            }
            return startsWith(names, args[1]);
        }
        if (args.length == 3 && List.of("give", "add", "take", "remove", "set").contains(sub)) {
            return startsWith(List.of("100", "1000", "10k", "100k", "1m"), args[2]);
        }
        if (args.length == 2 && sub.equals("stats")) {
            return startsWith(List.of("1", "24", "168", "720"), args[1]);
        }
        return List.of();
    }

    private static List<String> startsWith(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
