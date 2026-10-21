package com.bx.smpPlugin.ranked;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Lookup;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /ranked: the menu, the queue, stats, the leaderboard and match history. */
public final class RankedCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.ranked";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.ranked";
    private static final int PAGE_SIZE = 10;

    private final SmpPlugin plugin;
    private final RankedManager ranked;

    public RankedCommand(SmpPlugin plugin, RankedManager ranked) {
        this.plugin = plugin;
        this.ranked = ranked;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            send(sender, "&cYou do not have permission to play ranked.");
            return true;
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui" -> {
                if (sender instanceof Player player) {
                    new RankedMenu(plugin, ranked).open(player);
                } else {
                    help(sender, label);
                }
            }
            case "join", "queue", "play" -> {
                if (sender instanceof Player player) {
                    ranked.join(player);
                } else {
                    send(sender, "&cOnly players can queue.");
                }
            }
            case "leave", "quit" -> {
                if (sender instanceof Player player) {
                    ranked.leave(player, false);
                }
            }
            case "stats", "info" -> stats(sender, args.length > 1 ? args[1] : null);
            case "top", "leaderboard", "lb" -> top(sender, args.length > 1 ? parsePage(args[1]) : 1);
            case "history" -> history(sender, args.length > 1 ? args[1] : null);
            case "setrating" -> setRating(sender, label, args);
            case "reset" -> reset(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        send(sender, "&8&m        &r &6&lRanked PvP &8&m        ");
        send(sender, "&6/" + label + " &7- the ranked menu");
        send(sender, "&6/" + label + " join &8| &6leave &7- the ranked queue");
        send(sender, "&6/" + label + " stats [player] &7- rating and record");
        send(sender, "&6/" + label + " top [page] &7- the leaderboard");
        send(sender, "&6/" + label + " history [player] &7- recent matches");
        if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            send(sender, "&6/" + label + " setrating <player> <rating> &8| &6reset <player>");
        }
    }

    private void stats(CommandSender sender, String name) {
        String target = name == null ? sender.getName() : name;
        if (name == null && !(sender instanceof Player)) {
            send(sender, "&cUsage: /ranked stats <player>");
            return;
        }
        Lookup.account(plugin, sender, target, account -> {
            if (account == null) {
                send(sender, "&cThat player was not found.");
                return;
            }
            ranked.profileAnywhere(account.uuid(), account.displayName()).whenComplete((profile, error) ->
                    reply(sender, () -> {
                        if (error != null) {
                            send(sender, "&cCould not read that rating right now.");
                            return;
                        }
                        int position = ranked.position(account.uuid());
                        send(sender, "&8&m        &r &6&l" + account.displayName() + " &8&m        ");
                        send(sender, "&7Rating: &f" + profile.rating() + " " + ranked.tier(profile.rating())
                                + " &8(peak " + profile.peak() + ")");
                        send(sender, "&7Record: &a" + profile.wins() + "W &c" + profile.losses() + "L &7"
                                + profile.draws() + "D &8(" + profile.games() + " games)");
                        send(sender, "&7Position: &f" + (position == 0 ? "not ranked yet" : "#" + position));
                    }));
        });
    }

    private void top(CommandSender sender, int page) {
        List<RankedRepository.Profile> board = ranked.leaderboard();
        int pages = Math.max(1, (board.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(1, Math.min(page, pages));
        send(sender, "&8&m        &r &6&lRanked top &8(page " + current + "/" + pages + ") &8&m        ");
        if (board.isEmpty()) {
            send(sender, "&7Nobody has played a ranked match yet.");
            return;
        }
        for (int i = (current - 1) * PAGE_SIZE; i < Math.min(board.size(), current * PAGE_SIZE); i++) {
            RankedRepository.Profile profile = board.get(i);
            send(sender, "&6#" + (i + 1) + " &f" + profile.name() + " &7- &f" + profile.rating() + " "
                    + ranked.tier(profile.rating()) + " &8(" + profile.wins() + "W/" + profile.losses() + "L)");
        }
    }

    private void history(CommandSender sender, String name) {
        String target = name == null ? sender.getName() : name;
        if (name == null && !(sender instanceof Player)) {
            send(sender, "&cUsage: /ranked history <player>");
            return;
        }
        Lookup.account(plugin, sender, target, account -> {
            if (account == null) {
                send(sender, "&cThat player was not found.");
                return;
            }
            ranked.history(account.uuid(), 10).whenComplete((rows, error) -> reply(sender, () -> {
                if (error != null) {
                    send(sender, "&cCould not read the match history right now.");
                    return;
                }
                send(sender, "&8&m        &r &6&lRanked history of " + account.displayName() + " &8&m        ");
                if (rows.isEmpty()) {
                    send(sender, "&7No ranked matches yet.");
                    return;
                }
                SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);
                for (RankedRepository.MatchRow row : rows) {
                    send(sender, describe(account.uuid(), row, format));
                }
            }));
        });
    }

    static String describe(UUID viewer, RankedRepository.MatchRow row, SimpleDateFormat format) {
        boolean first = row.playerOne().equals(viewer);
        String opponent = first ? row.playerTwoName() : row.playerOneName();
        int before = first ? row.playerOneBefore() : row.playerTwoBefore();
        int after = first ? row.playerOneAfter() : row.playerTwoAfter();
        String result = row.winner() == null ? "&7Draw" : row.winner().equals(viewer) ? "&aWin" : "&cLoss";
        String change = !row.rated() ? "&8unrated (" + row.unratedReason() + ")"
                : (after >= before ? "&a+" : "&c") + (after - before) + " &8→ &f" + after;
        return "&8" + format.format(new Date(row.endedAt())) + " " + result + " &7vs &f" + opponent + " " + change;
    }

    private void setRating(CommandSender sender, String label, String[] args) {
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            send(sender, "&cYou do not have permission to change ratings.");
            return;
        }
        if (args.length < 3) {
            send(sender, "&cUsage: /" + label + " setrating <player> <rating>");
            return;
        }
        int rating;
        try {
            rating = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            send(sender, "&cThe rating must be a whole number.");
            return;
        }
        if (rating < 0 || rating > 10_000) {
            send(sender, "&cThe rating must be between 0 and 10000.");
            return;
        }
        Lookup.account(plugin, sender, args[1], account -> {
            if (account == null) {
                send(sender, "&cThat player was not found.");
                return;
            }
            ranked.setRating(account.uuid(), account.displayName(), rating,
                    () -> send(sender, "&aSet the ranked rating of &f" + account.displayName() + " &ato &f" + rating + "&a."));
        });
    }

    private void reset(CommandSender sender, String label, String[] args) {
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            send(sender, "&cYou do not have permission to reset ratings.");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            send(sender, "&eThis deletes the player's ranked record. Type &f/" + label + " reset "
                    + (args.length > 1 ? args[1] : "<player>") + " confirm &eto do it.");
            return;
        }
        Lookup.account(plugin, sender, args[1], account -> {
            if (account == null) {
                send(sender, "&cThat player was not found.");
                return;
            }
            ranked.reset(account.uuid(), account.displayName(),
                    () -> send(sender, "&aReset the ranked record of &f" + account.displayName() + "&a."));
        });
    }

    private void reply(CommandSender sender, Runnable reply) {
        if (sender instanceof Player player) {
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (player.isOnline()) {
                    reply.run();
                }
            });
        } else {
            plugin.getSpigotScheduler().runGlobal(reply);
        }
    }

    private static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.toComponent(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("join", "leave", "stats", "top", "history"));
            if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                options.addAll(List.of("setrating", "reset"));
            }
        } else if (args.length == 2 && List.of("stats", "history", "setrating", "reset")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            options.add("confirm");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
