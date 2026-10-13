package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinFlipTransaction;
import com.bx.smpPlugin.coinflip.MatchState;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /coinflipadmin} ({@code /cfadmin}): status, inspection, audit, cancellation, recovery, reconciliation,
 * transaction resolution, statistics reset, wagering switch and reload. Every subcommand has its own permission;
 * cancellations, resolutions and resets require a reason and are written to the audit log.
 */
public final class CoinFlipAdminCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final List<String> SUBCOMMANDS = List.of("status", "inspect", "audit", "cancel", "recover", "reconcile",
            "resolve", "stats", "resetstats", "disable", "enable", "reload", "refresh", "debug");

    private final CoinFlipModule module;

    public CoinFlipAdminCommand(CoinFlipModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (require(sender, CoinFlipModule.PERM_INSPECT)) {
                    status(sender);
                }
            }
            case "inspect" -> {
                if (require(sender, CoinFlipModule.PERM_INSPECT)) {
                    inspect(sender, args);
                }
            }
            case "audit" -> {
                if (require(sender, CoinFlipModule.PERM_INSPECT)) {
                    audit(sender, args);
                }
            }
            case "cancel" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_CANCEL)) {
                    cancel(sender, args);
                }
            }
            case "recover" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_RECOVER)) {
                    module.service().recoverNow(sender.getName()).whenComplete((count, error) -> reply(sender, error == null
                            ? "&aRecovery pass done: " + count + " match(es) retried or reconciled."
                            : "&cRecovery failed: " + error.getMessage()));
                }
            }
            case "reconcile" -> {
                if (require(sender, CoinFlipModule.PERM_INSPECT)) {
                    reconcile(sender);
                }
            }
            case "resolve" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_RESOLVE)) {
                    resolve(sender, args);
                }
            }
            case "stats" -> {
                if (require(sender, CoinFlipModule.PERM_INSPECT)) {
                    stats(sender, args);
                }
            }
            case "resetstats" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_RESET)) {
                    resetStats(sender, args);
                }
            }
            case "disable", "enable" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_TOGGLE)) {
                    boolean pause = sub.equals("disable");
                    module.service().setWageringPaused(pause, sender.getName());
                    module.plugin().getLogger().info("[CoinFlip] " + sender.getName() + (pause ? " paused" : " resumed") + " wagering.");
                    send(sender, pause ? "&eWagering paused. Open matches can still be cancelled and expire normally."
                            : "&aWagering resumed.");
                }
            }
            case "reload" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_RELOAD)) {
                    Optional<String> problem = module.reload();
                    if (problem.isPresent()) {
                        send(sender, "&cReload cancelled; the current settings stay active: &7" + problem.get());
                    } else {
                        CoinFlipConfig config = module.config();
                        send(sender, "&aCoinFlip configuration reloaded. Active matches were not touched.");
                        config.problems().forEach(line -> send(sender, "&e" + line));
                        if (config.wageringBlocked()) {
                            send(sender, "&cWagering is paused until the money settings are fixed.");
                        }
                    }
                }
            }
            case "refresh" -> {
                if (require(sender, CoinFlipModule.PERM_ADMIN_RELOAD)) {
                    module.leaderboards().refresh().thenRun(() -> reply(sender, "&aLeaderboards refreshed."));
                }
            }
            case "debug" -> {
                if (require(sender, CoinFlipModule.PERM_INSPECT)) {
                    send(sender, "&8&m-----&r &5CoinFlip counters &8&m-----");
                    module.metrics().snapshot().forEach((key, value) -> send(sender, "&7" + key + ": &f" + value));
                    send(sender, "&7animations running: &f" + module.animator().running());
                }
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        send(sender, "&8&m-----&r &5CoinFlip admin &8&m-----");
        send(sender, "&f/" + label + " status &7- health, counts, storage, problems");
        send(sender, "&f/" + label + " inspect <match> &7- match, journal and audit trail");
        send(sender, "&f/" + label + " audit [match] &7- recent audit log");
        send(sender, "&f/" + label + " cancel <match> <reason> &7- refund a waiting match");
        send(sender, "&f/" + label + " recover &7- retry stuck payouts, reconcile held matches");
        send(sender, "&f/" + label + " reconcile &7- ledger consistency report");
        send(sender, "&f/" + label + " resolve <transaction> <applied|not-applied> <reason>");
        send(sender, "&f/" + label + " stats <player> &7| resetstats <player> <reason>");
        send(sender, "&f/" + label + " disable|enable &7- pause or resume wagering");
        send(sender, "&f/" + label + " reload &7| refresh &7| debug");
    }

    private void status(CommandSender sender) {
        CoinFlipService service = module.service();
        CoinFlipConfig config = module.config();
        send(sender, "&8&m-----&r &5CoinFlip status &8&m-----");
        send(sender, "&7Wagering: " + (service.wageringOpen() ? "&aopen" : "&cpaused")
                + (service.wageringPaused() ? " &7(staff)" : "") + (config.maintenance() ? " &7(maintenance)" : "")
                + (config.wageringBlocked() ? " &c(config problem)" : ""));
        send(sender, "&7Storage: " + (module.repository().isReady() ? "&aready" : "&cnot ready")
                + " &8| &7recovered: " + (service.recovered() ? "&ayes" : "&cno")
                + " &8| &7queue: &f" + module.repository().queued()
                + " &8| &7avg: &f" + String.format(Locale.ROOT, "%.1fms", module.repository().averageMillis())
                + " &8| &7slow: &f" + module.repository().slowCount()
                + " &8| &7failed: &f" + module.repository().failedCount());
        if (module.startupProblem() != null) {
            send(sender, "&cStartup: " + module.startupProblem());
        }
        List<String> counts = new ArrayList<>();
        for (MatchState state : MatchState.values()) {
            long count = service.countInState(state);
            if (count > 0) {
                counts.add(state.name().toLowerCase(Locale.ROOT) + " " + count);
            }
        }
        send(sender, "&7Active matches: &f" + (counts.isEmpty() ? "none" : String.join(", ", counts)));
        send(sender, "&7Fee: &f" + StakeMath.percent(config.feeBasisPoints()) + " &8| &7stakes: &f"
                + module.money(config.minStakeCents()) + " - " + module.money(config.maxStakeCents()));
        config.problems().forEach(problem -> send(sender, "&eConfig: " + problem));
        for (CoinFlipMatch match : service.activeMatches()) {
            if (match.state() == MatchState.RECOVERY_REQUIRED) {
                send(sender, "&cHeld for review: &f#" + match.id() + " &7(" + match.creatorName() + " vs "
                        + (match.opponentName() == null ? "-" : match.opponentName()) + ", " + module.money(match.stakeCents()) + ")");
            }
        }
        module.repository().transaction(dao -> dao.transactionsInState(CoinFlipTransaction.State.UNKNOWN, 20))
                .whenComplete((unknown, error) -> {
                    if (error == null && !unknown.isEmpty()) {
                        reply(sender, "&cUnknown transactions: &f" + unknown.size() + " &7(see /coinflipadmin reconcile)");
                    }
                });
        module.repository().transaction(dao -> dao.transactionsInState(CoinFlipTransaction.State.PENDING_CLAIM, 500))
                .whenComplete((claims, error) -> {
                    if (error == null) {
                        long total = claims.stream().mapToLong(CoinFlipTransaction::amountCents).sum();
                        reply(sender, "&7Pending claims: &f" + claims.size() + " &7worth &f" + module.money(total));
                    }
                });
    }

    private void inspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "&cUsage: /coinflipadmin inspect <match>");
            return;
        }
        String id = CoinFlipService.normalizeId(args[1]);
        record Inspection(Optional<CoinFlipMatch> match, List<CoinFlipTransaction> transactions, List<CoinFlipDao.AuditRow> audit) {
        }
        module.repository().transaction(dao -> new Inspection(dao.loadMatch(id), dao.transactionsForMatch(id), dao.auditLog(id, 20)))
                .whenComplete((inspection, error) -> module.plugin().getSpigotScheduler().runGlobal(() -> {
                    if (error != null) {
                        send(sender, "&cStorage error: " + error.getMessage());
                        return;
                    }
                    if (inspection.match().isEmpty()) {
                        send(sender, "&cNo match #" + id + " in storage.");
                        return;
                    }
                    CoinFlipMatch match = inspection.match().get();
                    MatchState memory = module.service().findActive(id).map(CoinFlipMatch::state).orElse(null);
                    send(sender, "&8&m-----&r &5Match #" + match.id() + " &8&m-----");
                    send(sender, "&7Stored state: &f" + match.state() + (memory != null && memory != match.state() ? " &7(in memory: &f" + memory + "&7)" : ""));
                    send(sender, "&7Creator: &f" + match.creatorName() + " &7(" + match.creatorSide().displayName() + ")"
                            + " &8| &7opponent: &f" + (match.opponentName() == null ? "-" : match.opponentName()));
                    if (match.isPrivate()) {
                        send(sender, "&7Challenge to: &f" + match.inviteeName());
                    }
                    send(sender, "&7Stake: &f" + module.money(match.stakeCents()) + " &8| &7pot: &f" + module.money(match.potCents())
                            + " &8| &7fee: &f" + module.money(match.feeCents()) + " &8| &7payout: &f" + module.money(match.payoutCents()));
                    send(sender, "&7Outcome: &f" + (match.outcome() == null ? "not drawn" : match.outcome().displayName()
                            + " &7(" + match.outcomeId() + "), winner &f" + match.winnerName()));
                    send(sender, "&7Created: &f" + TIME.format(Instant.ofEpochMilli(match.createdAtMillis()))
                            + (match.settledAtMillis() > 0 ? " &8| &7ended: &f" + TIME.format(Instant.ofEpochMilli(match.settledAtMillis())) : "")
                            + (match.endReason() == null ? "" : " &8| &7" + match.endReason()));
                    send(sender, "&7Journal:");
                    for (CoinFlipTransaction tx : inspection.transactions()) {
                        String colour = switch (tx.state()) {
                            case APPLIED -> "&a";
                            case PENDING_CLAIM -> "&e";
                            case UNKNOWN, INTENT -> "&c";
                            default -> "&7";
                        };
                        send(sender, " " + colour + tx.kind() + " " + tx.state() + " &f" + module.money(tx.amountCents()) + " &7"
                                + tx.playerName() + " &8" + tx.id() + (tx.note() == null ? "" : " &7(" + tx.note() + ")"));
                    }
                    send(sender, "&7Audit:");
                    for (CoinFlipDao.AuditRow row : inspection.audit()) {
                        send(sender, " &8" + TIME.format(Instant.ofEpochMilli(row.at())) + " &f" + row.action() + " &7by " + row.actor()
                                + " &8- &7" + row.detail());
                    }
                }));
    }

    private void audit(CommandSender sender, String[] args) {
        String matchId = args.length >= 2 ? CoinFlipService.normalizeId(args[1]) : null;
        module.repository().transaction(dao -> dao.auditLog(matchId, 20)).whenComplete((rows, error) ->
                module.plugin().getSpigotScheduler().runGlobal(() -> {
                    if (error != null) {
                        send(sender, "&cStorage error: " + error.getMessage());
                        return;
                    }
                    send(sender, "&8&m-----&r &5CoinFlip audit" + (matchId == null ? "" : " #" + matchId) + " &8&m-----");
                    for (CoinFlipDao.AuditRow row : rows) {
                        send(sender, "&8" + TIME.format(Instant.ofEpochMilli(row.at())) + " &f" + row.action()
                                + (row.matchId() == null ? "" : " &7#" + row.matchId()) + " &7by " + row.actor() + " &8- &7" + row.detail());
                    }
                }));
    }

    private void cancel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "&cUsage: /coinflipadmin cancel <match> <reason>");
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        module.service().adminCancel(sender.getName(), args[1], reason).thenAccept(outcome -> outcomeReply(sender, outcome));
    }

    private void reconcile(CommandSender sender) {
        module.service().reconcileReport(200).whenComplete((problems, error) -> {
            if (error != null) {
                reply(sender, "&cStorage error: " + error.getMessage());
                return;
            }
            if (problems.isEmpty()) {
                reply(sender, "&aLedger check: every recent match balances and nothing is unknown.");
                return;
            }
            reply(sender, "&cLedger check found " + problems.size() + " item(s):");
            problems.forEach(problem -> reply(sender, " &7" + problem));
            reply(sender, "&7Resolve unknown transactions with /coinflipadmin resolve after checking the balance in /logs.");
        });
    }

    private void resolve(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "&cUsage: /coinflipadmin resolve <transaction> <applied|not-applied> <reason>");
            send(sender, "&7applied = the balance did change; not-applied = it did not (a credit is then paid again as a claim).");
            return;
        }
        boolean applied;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "applied" -> applied = true;
            case "not-applied", "notapplied" -> applied = false;
            default -> {
                send(sender, "&cSay applied or not-applied.");
                return;
            }
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        module.service().resolve(sender.getName(), args[1], applied, reason).thenAccept(outcome -> outcomeReply(sender, outcome));
    }

    private void stats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "&cUsage: /coinflipadmin stats <player>");
            return;
        }
        String name = args[1];
        module.repository().transaction(dao -> dao.findPlayerByName(name).map(row -> {
            try {
                return dao.loadStats(row.playerId(), row.playerName());
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        })).whenComplete((stats, error) -> module.plugin().getSpigotScheduler().runGlobal(() -> {
            if (error != null || stats == null || stats.isEmpty()) {
                send(sender, "&cNo CoinFlip statistics for " + name + ".");
                return;
            }
            var s = stats.get();
            send(sender, "&8&m-----&r &5" + s.playerName() + " &8&m-----");
            send(sender, "&7Played &f" + s.played() + " &7wins &f" + s.wins() + " &7losses &f" + s.losses()
                    + " &7practice &f" + s.practiceFlips() + (s.hidden() ? " &8(private profile)" : ""));
            send(sender, "&7Staked &f" + module.money(s.stakedCents()) + " &7gross &f" + module.money(s.grossPayoutCents())
                    + " &7net &f" + (s.netCents() < 0 ? "-" : "+") + module.money(Math.abs(s.netCents())));
            send(sender, "&7Best streak &f" + s.bestStreak() + " &7largest payout &f" + module.money(s.largestPayoutCents()));
            if (sender instanceof Player player) {
                new StatsMenu(module, player.getUniqueId(), s.playerId(), s.playerName()).open(player);
            }
        }));
    }

    private void resetStats(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "&cUsage: /coinflipadmin resetstats <player> <reason>");
            return;
        }
        String name = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        module.repository().transaction(dao -> dao.findPlayerByName(name)).whenComplete((found, error) ->
                module.plugin().getSpigotScheduler().runGlobal(() -> {
                    if (error != null || found == null || found.isEmpty()) {
                        send(sender, "&cNo CoinFlip statistics for " + name + ".");
                        return;
                    }
                    module.service().resetStats(sender.getName(), found.get().playerId(), found.get().playerName(), reason)
                            .thenAccept(outcome -> outcomeReply(sender, outcome));
                }));
    }

    private void outcomeReply(CommandSender sender, CoinFlipService.Outcome outcome) {
        reply(sender, module.messages().chat(outcome.messageKey(), outcome.placeholders()));
    }

    /** Sends from any thread. */
    private void reply(CommandSender sender, String message) {
        module.plugin().getSpigotScheduler().runGlobal(() -> send(sender, message));
    }

    private boolean require(CommandSender sender, String permission) {
        if (PermissionUtils.has(sender, permission)) {
            return true;
        }
        send(sender, "&cYou do not have permission to do that.");
        return false;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.colorize(message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                String permission = switch (sub) {
                    case "cancel" -> CoinFlipModule.PERM_ADMIN_CANCEL;
                    case "recover" -> CoinFlipModule.PERM_ADMIN_RECOVER;
                    case "resolve" -> CoinFlipModule.PERM_ADMIN_RESOLVE;
                    case "resetstats" -> CoinFlipModule.PERM_ADMIN_RESET;
                    case "disable", "enable" -> CoinFlipModule.PERM_ADMIN_TOGGLE;
                    case "reload", "refresh" -> CoinFlipModule.PERM_ADMIN_RELOAD;
                    default -> CoinFlipModule.PERM_INSPECT;
                };
                if (PermissionUtils.has(sender, permission)) {
                    allowed.add(sub);
                }
            }
            return filter(allowed, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("inspect", "audit", "cancel").contains(sub)) {
            return filter(module.service().activeMatches().stream().map(CoinFlipMatch::id).toList(), args[1]);
        }
        if (args.length == 2 && List.of("stats", "resetstats").contains(sub)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && sub.equals("resolve")) {
            return filter(List.of("applied", "not-applied"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).limit(50).toList();
    }
}
