package com.bx.smpPlugin.anticheat;

import com.bx.smpPlugin.SmpPlugin;
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

/** {@code /anticheat} (alias {@code /ac}): alerts, evidence lookup, exemptions and status for staff. */
public final class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final long MAX_EXEMPT_SECONDS = 3_600L;

    private final SmpPlugin plugin;
    private final AntiCheatManager manager;

    public AntiCheatCommand(SmpPlugin plugin, AntiCheatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "alerts" -> alerts(sender);
            case "status" -> status(sender);
            case "checks" -> checks(sender);
            case "history" -> history(sender, label, args);
            case "evidence" -> evidence(sender, label, args);
            case "exempt" -> exempt(sender, label, args);
            case "reset" -> reset(sender, label, args);
            case "reload" -> reload(sender);
            case "profile" -> profile(sender, label, args);
            case "bans" -> bans(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void profile(CommandSender sender, String label, String[] args) {
        if (!require(sender, AntiCheatManager.INSPECT_PERMISSION)) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&cUsage: /" + label + " profile <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "&cThat player is not online.");
            return;
        }
        String brand = manager.clientBrand(target);
        send(sender, "&8&m-----&r &cProfile: " + target.getName() + " &8&m-----");
        send(sender, "&7Client: &f" + (brand == null ? "unknown" : brand)
                + (manager.isBedrock(target) ? " &8(Bedrock via Floodgate)" : "")
                + " &7ping: &f" + AntiCheatManager.safePing(target) + "ms");
        double total = 0D;
        List<String> lines = new ArrayList<>();
        for (CheckType check : CheckType.values()) {
            double level = manager.violationLevel(target, check);
            total += level;
            if (level >= 0.05D) {
                CheckSettings settings = manager.config().settings(check);
                lines.add(String.format(Locale.ROOT, " &f%s &7vl &c%.1f &8(alert %.0f, kick %s, ban %s)",
                        check.displayName(), level, settings.alertAt(),
                        settings.kickAt() > 0D ? fmt(settings.kickAt()) : "-", settings.banAt() > 0D ? fmt(settings.banAt()) : "-"));
            }
        }
        if (lines.isEmpty()) {
            send(sender, "&aNo current violations.");
        } else {
            lines.forEach(line -> send(sender, line));
            send(sender, "&7Total: &f" + fmt(total));
        }
        boolean queued = manager.queuedBans().stream().anyMatch(entry -> entry.playerId().equals(target.getUniqueId()));
        if (queued) {
            send(sender, "&cThis player is on the ban queue.");
        }
    }

    private void bans(CommandSender sender, String label, String[] args) {
        String action = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            if (!require(sender, AntiCheatManager.INSPECT_PERMISSION)) {
                return;
            }
            send(sender, "&8&m-----&r &cBan queue &8(" + manager.config().banMode().name().toLowerCase(Locale.ROOT)
                    + " mode) &8&m-----");
            if (manager.queuedBans().isEmpty()) {
                send(sender, "&7Nobody is waiting.");
                return;
            }
            for (BanQueue.Entry entry : manager.queuedBans()) {
                send(sender, "&f" + entry.playerName() + " &7- &c" + entry.check().displayName() + " &8#" + entry.evidenceId()
                        + " &7" + TIME.format(Instant.ofEpochMilli(entry.queuedAtMillis())));
                send(sender, "  &8" + entry.reason());
            }
            send(sender, "&7/" + label + " bans approve <player|all> &8| &7/" + label + " bans deny <player>");
            return;
        }
        if (!require(sender, AntiCheatManager.MANAGE_PERMISSION)) {
            return;
        }
        switch (action) {
            case "approve" -> {
                if (args.length < 3) {
                    send(sender, "&cUsage: /" + label + " bans approve <player|all>");
                    return;
                }
                if (args[2].equalsIgnoreCase("all")) {
                    send(sender, "&aBanned " + manager.runBanWave(sender.getName()) + " queued player(s).");
                } else if (manager.approveBan(args[2], sender.getName())) {
                    send(sender, "&aBan approved for " + args[2] + ".");
                } else {
                    send(sender, "&c" + args[2] + " is not on the ban queue.");
                }
            }
            case "deny" -> {
                if (args.length < 3) {
                    send(sender, "&cUsage: /" + label + " bans deny <player>");
                    return;
                }
                send(sender, manager.denyBan(args[2], sender.getName())
                        ? "&a" + args[2] + " removed from the ban queue." : "&c" + args[2] + " is not on the ban queue.");
            }
            case "run" -> send(sender, "&aBan wave ran: " + manager.runBanWave(sender.getName()) + " player(s) banned.");
            default -> send(sender, "&cUsage: /" + label + " bans [list|approve <player|all>|deny <player>|run]");
        }
    }

    private void help(CommandSender sender, String label) {
        send(sender, "&8&m-----&r &cAnti-cheat &8&m-----");
        send(sender, "&f/" + label + " alerts &7- toggle alerts for yourself");
        send(sender, "&f/" + label + " status &7- module state and players with violations");
        send(sender, "&f/" + label + " checks &7- what each check looks for and its thresholds");
        send(sender, "&f/" + label + " history <player> &7- recent flags with evidence IDs");
        send(sender, "&f/" + label + " evidence <id> &7- full details of one flag");
        send(sender, "&f/" + label + " profile <player> &7- client, ping and every current violation level");
        send(sender, "&f/" + label + " bans [approve|deny|run] &7- review the ban queue");
        send(sender, "&f/" + label + " exempt <player> <check|movement|combat|world|player> <seconds>");
        send(sender, "&f/" + label + " reset <player> &7- clear a player's violation levels");
        send(sender, "&f/" + label + " reload &7- reload anticheat.yml");
    }

    private void alerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cConsole always receives alerts when ALERTS.CONSOLE is true.");
            return;
        }
        if (!require(sender, AntiCheatManager.ALERTS_PERMISSION)) {
            return;
        }
        boolean enabled = manager.toggleAlerts(player.getUniqueId());
        send(sender, enabled ? "&aAnti-cheat alerts on." : "&eAnti-cheat alerts off.");
    }

    private void status(CommandSender sender) {
        if (!require(sender, AntiCheatManager.INSPECT_PERMISSION)) {
            return;
        }
        send(sender, "&8&m-----&r &cAnti-cheat status &8&m-----");
        send(sender, "&7Active: " + (manager.isActive() ? "&ayes" : "&cno")
                + " &8| &7checks enabled: &f" + manager.enabledCheckCount() + "/" + CheckType.values().length);
        send(sender, "&7Kicks: " + (manager.config().kicksEnabled() ? "&aon" : "&coff")
                + " &8| &7bans: &f" + manager.config().banMode().name().toLowerCase(Locale.ROOT)
                + " &8| &7ban queue: &f" + manager.queuedBans().size());
        List<String> flagged = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            double total = 0D;
            CheckType worst = null;
            double worstLevel = 0D;
            for (CheckType check : CheckType.values()) {
                double level = manager.violationLevel(online, check);
                total += level;
                if (level > worstLevel) {
                    worstLevel = level;
                    worst = check;
                }
            }
            if (worst != null && total >= 1D) {
                flagged.add(String.format(Locale.ROOT, "&f%s &7- &c%s %.1f &8(total %.1f)",
                        online.getName(), worst.displayName(), worstLevel, total));
            }
        }
        if (flagged.isEmpty()) {
            send(sender, "&7No online player currently has a violation level above 1.");
        } else {
            send(sender, "&7Players with violations:");
            flagged.forEach(line -> send(sender, " " + line));
        }
        send(sender, "&7Native anti-Xray and integrations: &f/smpplugin status");
    }

    private void checks(CommandSender sender) {
        if (!require(sender, AntiCheatManager.INSPECT_PERMISSION)) {
            return;
        }
        for (CheckType check : CheckType.values()) {
            CheckSettings settings = manager.config().settings(check);
            String state = settings.enabled() ? "&aon" : "&coff";
            String action = settings.actionAt() > 0D ? fmt(settings.actionAt()) : "never";
            String kick = settings.kickAt() > 0D ? fmt(settings.kickAt()) : "never";
            String ban = settings.banAt() > 0D ? fmt(settings.banAt()) : "never";
            send(sender, "&f" + check.displayName() + " " + state + " &8| &7alert " + fmt(settings.alertAt())
                    + ", act " + action + ", kick " + kick + ", ban " + ban);
            send(sender, "  &8" + check.description());
        }
    }

    private void history(CommandSender sender, String label, String[] args) {
        if (!require(sender, AntiCheatManager.INSPECT_PERMISSION)) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&cUsage: /" + label + " history <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "&cThat player is not online. Older flags are in plugins/SmpPlugin/anticheat/.");
            return;
        }
        List<EvidenceLog.Flag> flags = manager.evidence().history(target.getUniqueId());
        if (flags.isEmpty()) {
            send(sender, "&7No flags recorded for " + target.getName() + " this session.");
            return;
        }
        send(sender, "&8&m-----&r &cFlags for " + target.getName() + " &8(newest last) &8&m-----");
        for (EvidenceLog.Flag flag : flags.subList(Math.max(0, flags.size() - 10), flags.size())) {
            send(sender, "&8#" + flag.id() + " &7" + TIME.format(Instant.ofEpochMilli(flag.timeMillis()))
                    + " &c" + flag.check().displayName() + " &7vl " + fmt(flag.violationLevel())
                    + " &8(" + flag.detail() + ")");
        }
    }

    private void evidence(CommandSender sender, String label, String[] args) {
        if (!require(sender, AntiCheatManager.INSPECT_PERMISSION)) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&cUsage: /" + label + " evidence <id>");
            return;
        }
        EvidenceLog.Flag flag = manager.evidence().find(args[1]);
        if (flag == null) {
            send(sender, "&cNo recent flag with that ID. Older evidence is in plugins/SmpPlugin/anticheat/.");
            return;
        }
        send(sender, "&8&m-----&r &cEvidence #" + flag.id() + " &8&m-----");
        send(sender, "&7Player: &f" + flag.playerName() + " &8(" + flag.playerId() + ")");
        send(sender, "&7Check: &c" + flag.check().displayName() + " &7- " + flag.check().description());
        send(sender, "&7Detail: &f" + flag.detail());
        send(sender, "&7Violation level: &f" + fmt(flag.violationLevel()) + " &7ping: &f" + flag.pingMillis() + "ms");
        send(sender, String.format(Locale.ROOT, "&7Where: &f%s %.1f %.1f %.1f", flag.world(), flag.x(), flag.y(), flag.z()));
        send(sender, "&7When: &f" + TIME.format(Instant.ofEpochMilli(flag.timeMillis())) + " &7action: &f" + flag.action());
    }

    private void exempt(CommandSender sender, String label, String[] args) {
        if (!require(sender, AntiCheatManager.MANAGE_PERMISSION)) {
            return;
        }
        if (args.length < 4) {
            send(sender, "&cUsage: /" + label + " exempt <player> <check|movement|combat|world|player> <seconds>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "&cThat player is not online.");
            return;
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[3]);
        } catch (NumberFormatException exception) {
            send(sender, "&cSeconds must be a whole number.");
            return;
        }
        if (seconds < 1L || seconds > MAX_EXEMPT_SECONDS) {
            send(sender, "&cSeconds must be between 1 and " + MAX_EXEMPT_SECONDS + ". Exemptions are always temporary.");
            return;
        }
        String scope = args[2].toUpperCase(Locale.ROOT);
        Optional<CheckType> check = CheckType.fromInput(scope);
        if (check.isPresent()) {
            manager.exempt(target, check.get(), seconds * 1_000L);
        } else {
            CheckCategory category;
            try {
                category = CheckCategory.valueOf(scope);
            } catch (IllegalArgumentException exception) {
                send(sender, "&cUnknown check or category: " + args[2]);
                return;
            }
            manager.exempt(target, category, seconds * 1_000L);
        }
        plugin.getLogger().info("[AntiCheat] " + sender.getName() + " exempted " + target.getName()
                + " from " + scope.toLowerCase(Locale.ROOT) + " for " + seconds + "s.");
        send(sender, "&aExempted " + target.getName() + " from " + scope.toLowerCase(Locale.ROOT) + " for " + seconds + "s.");
    }

    private void reset(CommandSender sender, String label, String[] args) {
        if (!require(sender, AntiCheatManager.MANAGE_PERMISSION)) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&cUsage: /" + label + " reset <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "&cThat player is not online.");
            return;
        }
        manager.resetViolations(target);
        plugin.getLogger().info("[AntiCheat] " + sender.getName() + " reset violation levels of " + target.getName() + ".");
        send(sender, "&aViolation levels of " + target.getName() + " cleared.");
    }

    private void reload(CommandSender sender) {
        if (!require(sender, AntiCheatManager.MANAGE_PERMISSION)) {
            return;
        }
        Optional<String> problem = manager.reload();
        if (problem.isPresent()) {
            send(sender, "&cReload cancelled, anticheat.yml has an error. The current settings stay active:");
            send(sender, "&7" + problem.get().replace('\n', ' '));
            return;
        }
        send(sender, "&aanticheat.yml reloaded.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (PermissionUtils.has(sender, AntiCheatManager.ALERTS_PERMISSION)) {
                options.add("alerts");
            }
            if (PermissionUtils.has(sender, AntiCheatManager.INSPECT_PERMISSION)) {
                options.addAll(List.of("status", "checks", "history", "evidence", "profile", "bans"));
            }
            if (PermissionUtils.has(sender, AntiCheatManager.MANAGE_PERMISSION)) {
                options.addAll(List.of("exempt", "reset", "reload"));
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("history", "exempt", "reset", "profile").contains(sub)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (sub.equals("bans")) {
            if (args.length == 2) {
                List<String> actions = new ArrayList<>(List.of("list"));
                if (PermissionUtils.has(sender, AntiCheatManager.MANAGE_PERMISSION)) {
                    actions.addAll(List.of("approve", "deny", "run"));
                }
                return filter(actions, args[1]);
            }
            if (args.length == 3 && List.of("approve", "deny").contains(args[1].toLowerCase(Locale.ROOT))) {
                List<String> names = new ArrayList<>(manager.queuedBans().stream().map(BanQueue.Entry::playerName).toList());
                if (args[1].equalsIgnoreCase("approve")) {
                    names.add("all");
                }
                return filter(names, args[2]);
            }
        }
        if (args.length == 3 && sub.equals("exempt")) {
            List<String> scopes = new ArrayList<>(List.of("movement", "combat", "world", "player"));
            Arrays.stream(CheckType.values()).map(check -> check.configKey().toLowerCase(Locale.ROOT)).forEach(scopes::add);
            return filter(scopes, args[2]);
        }
        if (args.length == 4 && sub.equals("exempt")) {
            return filter(List.of("30", "60", "300"), args[3]);
        }
        return List.of();
    }

    private boolean require(CommandSender sender, String permission) {
        if (PermissionUtils.has(sender, permission)) {
            return true;
        }
        send(sender, "&cYou do not have permission to do that.");
        return false;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ColorUtils.colorize(message));
    }
}
