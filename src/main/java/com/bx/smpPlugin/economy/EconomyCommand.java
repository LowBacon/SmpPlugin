package com.bx.smpPlugin.economy;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Lookup;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /economy (alias /eco): admin money tools in one place.
 *
 * <pre>
 * /economy give|take|set &lt;player&gt; &lt;amount&gt; [reason]
 * /economy reset &lt;player&gt; [reason]        then /economy reset &lt;player&gt; confirm
 * /economy history &lt;player&gt; [page]
 * /economy stats
 * </pre>
 *
 * <p>Every change names the admin and the reason in the economy ledger. Player lookups and ledger
 * reads run off the main thread.
 */
public final class EconomyCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.admin.economy";
    /** Overwriting a balance (set, reset) is a destructive action, so it has its own node. */
    public static final String SET_PERMISSION = "smpplugin.admin.economy.set";
    public static final String HISTORY_OTHERS_PERMISSION = "smpplugin.admin.economy.history";
    private static final long CONFIRM_WINDOW_MS = 30_000L;
    private static final int PAGE_SIZE = 8;

    private final SmpPlugin plugin;
    private final EconomyLedger ledger;
    /** Pending /economy reset confirmations: sender key to (target, expiry). */
    private final Map<String, PendingReset> pendingResets = new ConcurrentHashMap<>();

    private record PendingReset(UUID target, long expiresAt) {
    }

    public EconomyCommand(SmpPlugin plugin, EconomyLedger ledger) {
        this.plugin = plugin;
        this.ledger = ledger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("history")) {
            history(sender, label, args);
            return true;
        }
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to manage the economy."));
            return true;
        }
        switch (sub) {
            case "give", "add", "deposit" -> adjust(sender, label, args, EconomyReason.ADMIN_ADD);
            case "take", "remove", "withdraw" -> adjust(sender, label, args, EconomyReason.ADMIN_REMOVE);
            case "set", "reset" -> {
                // Destructive nodes default to false even for ops; the console (the server owner) may always.
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !PermissionUtils.has(sender, SET_PERMISSION)) {
                    sender.sendMessage(ColorUtils.toComponent("&cOverwriting a balance needs " + SET_PERMISSION + "."));
                } else if (sub.equals("set")) {
                    adjust(sender, label, args, EconomyReason.ADMIN_SET);
                } else {
                    reset(sender, label, args);
                }
            }
            case "stats", "info" -> stats(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.toComponent("&8&m          &r &b&lEconomy &8&m          "));
        if (PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&b/" + label + " give <player> <amount> [reason] &7- add money"));
            sender.sendMessage(ColorUtils.toComponent("&b/" + label + " take <player> <amount> [reason] &7- remove money"));
            sender.sendMessage(ColorUtils.toComponent("&b/" + label + " set <player> <amount> [reason] &7- set a balance"));
            sender.sendMessage(ColorUtils.toComponent("&b/" + label + " reset <player> &7- back to the starting balance"));
            sender.sendMessage(ColorUtils.toComponent("&b/" + label + " stats &7- money in circulation"));
        }
        sender.sendMessage(ColorUtils.toComponent("&b/" + label + " history [player] [page] &7- recent transactions"));
    }

    private void adjust(CommandSender sender, String label, String[] args, EconomyReason reason) {
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " " + args[0].toLowerCase(Locale.ROOT)
                    + " <player> <amount> [reason]"));
            return;
        }
        OptionalLong cents = reason == EconomyReason.ADMIN_SET && args[2].trim().equals("0")
                ? OptionalLong.of(0L) : Money.parsePositive(args[2]);
        if (cents.isEmpty()) {
            sender.sendMessage(ColorUtils.toComponent("&cThat is not a valid amount. Use a number such as 250, 1.5k or 2m."));
            return;
        }
        String note = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        double amount = Money.toDouble(cents.getAsLong());
        Lookup.account(plugin, sender, args[1], account -> {
            if (account == null) {
                sender.sendMessage(ColorUtils.toComponent("&cNo player called " + args[1] + " has joined this server."));
                return;
            }
            EconomyManager economy = plugin.getEconomyManager();
            EconomyTransactionResult result = EconomyLedger.asActor(sender, note, () -> switch (reason) {
                case ADMIN_ADD -> economy.deposit(account, amount, reason);
                case ADMIN_REMOVE -> economy.withdraw(account, amount, reason);
                default -> economy.setBalance(account, amount, reason);
            });
            if (!result.success()) {
                sender.sendMessage(ColorUtils.toComponent(EconomyMessages.failure(plugin, result.failureReason())));
                return;
            }
            sender.sendMessage(ColorUtils.toComponent("&a" + describe(reason) + " &f" + account.displayName()
                    + "&a: " + money(result.beforeBalance()) + " &7→ &f" + money(result.afterBalance())
                    + (note == null ? "" : " &7(" + note + ")")));
            Player online = Bukkit.getPlayer(account.uuid());
            if (online != null && online != sender) {
                online.sendMessage(ColorUtils.toComponent("&7Your balance was changed by staff: &f"
                        + money(result.afterBalance())));
            }
        });
    }

    private void reset(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " reset <player> [reason]"));
            return;
        }
        String key = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        boolean confirming = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        Lookup.account(plugin, sender, args[1], account -> {
            if (account == null) {
                sender.sendMessage(ColorUtils.toComponent("&cNo player called " + args[1] + " has joined this server."));
                return;
            }
            PendingReset pending = pendingResets.get(key);
            long now = System.currentTimeMillis();
            if (!confirming || pending == null || !pending.target().equals(account.uuid()) || pending.expiresAt() < now) {
                pendingResets.put(key, new PendingReset(account.uuid(), now + CONFIRM_WINDOW_MS));
                sender.sendMessage(ColorUtils.toComponent("&eThis sets &f" + account.displayName()
                        + "&e's balance back to the starting balance. Type &f/" + label + " reset "
                        + account.displayName() + " confirm &ewithin 30 seconds to do it."));
                return;
            }
            pendingResets.remove(key);
            double start = plugin.getConfigManager().getConfig().getDouble("SETTINGS.MONEY-PER-DEFAULT", 1000.0D);
            EconomyTransactionResult result = EconomyLedger.asActor(sender, "reset",
                    () -> plugin.getEconomyManager().setBalance(account, start, EconomyReason.ADMIN_SET));
            if (!result.success()) {
                sender.sendMessage(ColorUtils.toComponent(EconomyMessages.failure(plugin, result.failureReason())));
                return;
            }
            sender.sendMessage(ColorUtils.toComponent("&aReset &f" + account.displayName() + "&a's balance: "
                    + money(result.beforeBalance()) + " &7→ &f" + money(result.afterBalance())));
        });
    }

    private void history(CommandSender sender, String label, String[] args) {
        String targetName = null;
        int page = 1;
        for (int i = 1; i < args.length; i++) {
            try {
                page = Math.max(1, Integer.parseInt(args[i]));
            } catch (NumberFormatException notANumber) {
                targetName = args[i];
            }
        }
        boolean self = targetName == null || targetName.equalsIgnoreCase(sender.getName());
        if (self && !(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /" + label + " history <player> [page]"));
            return;
        }
        if (!self && !PermissionUtils.has(sender, HISTORY_OTHERS_PERMISSION) && !PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cYou can only see your own history."));
            return;
        }
        if (self && !PermissionUtils.has(sender, "smpplugin.balance.history")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to see your transaction history."));
            return;
        }
        int requestedPage = page;
        Lookup.account(plugin, sender, self ? sender.getName() : targetName, account -> {
            if (account == null) {
                sender.sendMessage(ColorUtils.toComponent("&cThat player was not found."));
                return;
            }
            showHistory(plugin, ledger, sender, account.uuid(), account.displayName(), requestedPage);
        });
    }

    /** Sends one page of a player's ledger rows; shared with /balance history. */
    public static void showHistory(SmpPlugin plugin, EconomyLedger ledger, CommandSender sender, UUID uuid,
                                   String name, int page) {
        if (ledger == null || !ledger.isEnabled()) {
            sender.sendMessage(ColorUtils.toComponent("&cThe economy ledger is turned off (ECONOMY.LEDGER.ENABLED)."));
            return;
        }
        ledger.history(uuid, page, PAGE_SIZE).whenComplete((rows, error) -> {
            Runnable reply = () -> {
                if (error != null) {
                    sender.sendMessage(ColorUtils.toComponent("&cCould not read the history right now."));
                    return;
                }
                sender.sendMessage(ColorUtils.toComponent("&8&m        &r &bTransactions of &f" + name
                        + " &8(page " + page + ") &8&m        "));
                if (rows.isEmpty()) {
                    sender.sendMessage(ColorUtils.toComponent("&7Nothing recorded" + (page > 1 ? " on this page." : " yet.")));
                    return;
                }
                SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);
                for (EconomyLedger.Entry row : rows) {
                    sender.sendMessage(ColorUtils.toComponent(describeRow(plugin, row, format)));
                }
            };
            if (sender instanceof Player player) {
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (player.isOnline()) {
                        reply.run();
                    }
                });
            } else {
                plugin.getSpigotScheduler().runGlobal(reply);
            }
        });
    }

    static String describeRow(SmpPlugin plugin, EconomyLedger.Entry row, SimpleDateFormat format) {
        boolean shards = row.currency() == EconomyLedger.Currency.SHARDS;
        String amount = shards ? row.amountMinor() + " shards" : plugin.getCurrencyManager().formatMoney(Money.toDouble(row.amountMinor()));
        String sign = switch (row.kind()) {
            case DEPOSIT, TRANSFER_IN -> "&a+";
            case WITHDRAW, TRANSFER_OUT -> "&c-";
            case SET -> "&e=";
        };
        String after = shards ? Long.toString(row.afterMinor()) : plugin.getCurrencyManager().formatMoney(Money.toDouble(row.afterMinor()));
        StringBuilder line = new StringBuilder("&8").append(format.format(new Date(row.createdAt()))).append(' ')
                .append(sign).append(row.kind() == EconomyLedger.Kind.SET ? after : amount)
                .append(" &7").append(readableReason(row.reason()));
        if (row.counterpartyName() != null) {
            line.append(row.kind() == EconomyLedger.Kind.TRANSFER_OUT ? " &7to &f" : " &7from &f").append(row.counterpartyName());
        }
        if (row.actorName() != null) {
            line.append(" &8by ").append(row.actorName());
        }
        if (row.note() != null && !row.note().isBlank()) {
            line.append(" &8(").append(row.note()).append(')');
        }
        line.append(" &8→ &7").append(after);
        return line.toString();
    }

    static String readableReason(String reason) {
        if (reason == null) {
            return "change";
        }
        return reason.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void stats(CommandSender sender) {
        plugin.getDatabaseManager().supplyAsync(() -> {
            try (PreparedStatement statement = plugin.getDatabaseManager().getConnection().prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(money), 0), COALESCE(SUM(shards), 0) FROM players");
                 ResultSet result = statement.executeQuery()) {
                return result.next() ? new double[]{result.getLong(1), result.getDouble(2), result.getLong(3)} : new double[3];
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }).whenComplete((totals, error) -> plugin.getSpigotScheduler().runGlobal(() -> {
            if (error != null) {
                sender.sendMessage(ColorUtils.toComponent("&cCould not read the totals right now."));
                return;
            }
            sender.sendMessage(ColorUtils.toComponent("&8&m          &r &b&lEconomy stats &8&m          "));
            sender.sendMessage(ColorUtils.toComponent("&7Accounts: &f" + (long) totals[0]));
            sender.sendMessage(ColorUtils.toComponent("&7Money in circulation: &a" + money(totals[1])));
            sender.sendMessage(ColorUtils.toComponent("&7Shards in circulation: &d" + (long) totals[2]));
            sender.sendMessage(ColorUtils.toComponent("&7Highest balance allowed: &f"
                    + money(Money.toDouble(Money.effectiveLimit(plugin.getEconomyManager().maxBalanceCents())))));
            sender.sendMessage(ColorUtils.toComponent("&8Online balances newer than the last save are not counted yet."));
        }));
    }

    private String describe(EconomyReason reason) {
        return switch (reason) {
            case ADMIN_ADD -> "Gave money to";
            case ADMIN_REMOVE -> "Took money from";
            default -> "Set the balance of";
        };
    }

    private String money(double amount) {
        return plugin.getCurrencyManager().formatMoney(amount);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("history");
            if (PermissionUtils.has(sender, PERMISSION)) {
                options.addAll(List.of("give", "take", "set", "reset", "stats"));
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("stats")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "take", "set" -> options.addAll(List.of("100", "1k", "10k", "1m"));
                case "reset" -> options.add("confirm");
                case "history" -> options.addAll(List.of("1", "2", "3"));
                default -> {
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
