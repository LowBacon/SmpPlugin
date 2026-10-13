package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.CoinSide;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * {@code /coinflip} ({@code /cf}). Every subcommand checks its own permission, and anything that moves currency goes
 * through a review screen or the service, which re-validates everything. Commands are rate-limited per player.
 */
public final class CoinFlipCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "browse", "join", "cancel", "challenge", "accept",
            "deny", "rematch", "history", "stats", "top", "practice", "settings", "help");

    private final CoinFlipModule module;
    private final Map<UUID, Long> lastCommand = new HashMap<>();

    public CoinFlipCommand(CoinFlipModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("CoinFlip is played in game. Staff tools: /coinflipadmin");
            return true;
        }
        if (!PermissionUtils.has(player, CoinFlipModule.PERM_USE)) {
            send(player, "no-permission", Map.of());
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastCommand.get(player.getUniqueId());
        if (last != null && now - last < module.config().commandCooldownMillis()) {
            return true;
        }
        lastCommand.put(player.getUniqueId(), now);
        if (lastCommand.size() > 2_000) {
            lastCommand.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        }
        if (!module.service().preferencesLoaded(player.getUniqueId())) {
            module.service().loadPreferences(player.getUniqueId());
        }

        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        UUID id = player.getUniqueId();
        switch (sub) {
            case "" -> new MainMenu(module, id).open(player);
            case "create" -> create(player, args);
            case "browse", "list" -> new BrowserMenu(module, id).open(player);
            case "join" -> join(player, args);
            case "cancel" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " cancel <match id>");
                } else {
                    module.service().cancel(id, args[1]).thenAccept(outcome -> module.presenter().sendOutcome(id, outcome));
                }
            }
            case "challenge" -> challenge(player, args, label);
            case "accept" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " accept <match id>");
                } else if (require(player, CoinFlipModule.PERM_JOIN)) {
                    JoinConfirmMenu.open(module, player, args[1]);
                }
            }
            case "deny", "decline" -> {
                if (args.length < 2) {
                    usage(player, "/" + label + " deny <match id>");
                } else {
                    module.service().decline(id, args[1]).thenAccept(outcome -> module.presenter().sendOutcome(id, outcome));
                }
            }
            case "rematch" -> rematch(player);
            case "history" -> lookup(player, args, (uuid, name) -> new HistoryMenu(module, id, uuid, name, 0,
                    CoinFlipDao.HistoryFilter.ALL).open(player));
            case "stats", "profile" -> lookup(player, args, (uuid, name) -> new StatsMenu(module, id, uuid, name).open(player));
            case "top", "leaderboard" -> new LeaderboardMenu(module, id, metric(args)).open(player);
            case "practice" -> {
                if (require(player, CoinFlipModule.PERM_PRACTICE)) {
                    new PracticeMenu(module, id).open(player);
                }
            }
            case "settings" -> new SettingsMenu(module, id).open(player);
            case "help", "rules" -> new HelpMenu(module, id).open(player);
            default -> usage(player, "/" + label + " [" + String.join("|", SUBCOMMANDS) + "]");
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (!require(player, CoinFlipModule.PERM_CREATE)) {
            return;
        }
        CreateMenu.Draft draft = CreateMenu.Draft.initial(module.service().preferences(player.getUniqueId()), module.config());
        if (args.length >= 2) {
            StakeMath.ParseResult parsed = StakeMath.parse(args[1]);
            if (!parsed.ok()) {
                invalidAmount(player, parsed);
                return;
            }
            draft = draft.withStake(parsed.cents());
        }
        if (args.length >= 3) {
            if (args[2].equalsIgnoreCase("random")) {
                draft.side = null;
            } else {
                CoinSide side = CoinSide.parse(args[2]).orElse(null);
                if (side == null) {
                    usage(player, "/coinflip create <amount> [heads|tails|random]");
                    return;
                }
                draft.side = side;
            }
        }
        // Always show the review screen: terms are confirmed before anything is reserved.
        new ReviewMenu(module, player.getUniqueId(), draft).open(player);
    }

    private void join(Player player, String[] args) {
        if (args.length < 2) {
            usage(player, "/coinflip join <match id>");
            return;
        }
        if (!require(player, CoinFlipModule.PERM_JOIN)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (module.service().preferences(id).quickConfirm()) {
            module.service().join(id, player.getName(), args[1]).thenAccept(outcome -> {
                if (!outcome.success()) {
                    module.presenter().sendOutcome(id, outcome);
                }
            });
        } else {
            JoinConfirmMenu.open(module, player, args[1]);
        }
    }

    private void challenge(Player player, String[] args, String label) {
        if (!require(player, CoinFlipModule.PERM_CHALLENGE)) {
            return;
        }
        if (args.length < 3) {
            usage(player, "/" + label + " challenge <player> <amount> [heads|tails|random]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(player, "player-offline", CoinFlipService.vars("player", args[1]));
            return;
        }
        if (module.plugin().getIgnoreManager() != null
                && module.plugin().getIgnoreManager().isIgnoring(target.getUniqueId(), player.getUniqueId())) {
            send(player, "challenge-not-accepting", CoinFlipService.vars("player", target.getName()));
            return;
        }
        StakeMath.ParseResult parsed = StakeMath.parse(args[2]);
        if (!parsed.ok()) {
            invalidAmount(player, parsed);
            return;
        }
        CreateMenu.Draft draft = CreateMenu.Draft.challenge(module.service().preferences(player.getUniqueId()), module.config(),
                target.getUniqueId(), target.getName()).withStake(parsed.cents());
        if (args.length >= 4) {
            draft.side = args[3].equalsIgnoreCase("random") ? null : CoinSide.parse(args[3]).orElse(draft.side);
        }
        new ReviewMenu(module, player.getUniqueId(), draft).open(player);
    }

    private void rematch(Player player) {
        if (!require(player, CoinFlipModule.PERM_CHALLENGE)) {
            return;
        }
        CoinFlipPresenter.Rematch rematch = module.presenter().rematchFor(player.getUniqueId()).orElse(null);
        Player target = rematch == null ? null : Bukkit.getPlayer(rematch.opponent());
        if (rematch == null || target == null) {
            send(player, "player-offline", CoinFlipService.vars("player", rematch == null ? "your last opponent" : rematch.opponentName()));
            return;
        }
        CreateMenu.Draft draft = CreateMenu.Draft.challenge(module.service().preferences(player.getUniqueId()), module.config(),
                target.getUniqueId(), target.getName()).withStake(rematch.stakeCents());
        new ReviewMenu(module, player.getUniqueId(), draft).open(player);
    }

    /** Resolves "me", an online player, or a player with CoinFlip statistics, without blocking the server thread. */
    private void lookup(Player player, String[] args, BiConsumer<UUID, String> open) {
        if (args.length < 2 || args[1].equalsIgnoreCase(player.getName())) {
            open.accept(player.getUniqueId(), player.getName());
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            open.accept(online.getUniqueId(), online.getName());
            return;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            send(player, "player-offline", CoinFlipService.vars("player", name));
            return;
        }
        module.repository().transaction(dao -> dao.findPlayerByName(name)).whenComplete((found, error) ->
                module.plugin().getSpigotScheduler().runGlobal(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null || found == null || found.isEmpty()) {
                        send(player, "player-offline", CoinFlipService.vars("player", name));
                        return;
                    }
                    open.accept(found.get().playerId(), found.get().playerName());
                }));
    }

    private static CoinFlipDao.Metric metric(String[] args) {
        if (args.length < 2) {
            return CoinFlipDao.Metric.WINS;
        }
        try {
            return CoinFlipDao.Metric.valueOf(args[1].toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return CoinFlipDao.Metric.WINS;
        }
    }

    private void invalidAmount(Player player, StakeMath.ParseResult parsed) {
        String reason = parsed.error().name().toLowerCase(Locale.ROOT).replace("too_many_decimals", "decimals").replace("too_large", "too-large");
        module.presenter().sendOutcome(player.getUniqueId(), new CoinFlipService.Outcome(false, "invalid-amount",
                CoinFlipService.vars("reason", reason), null));
    }

    private boolean require(Player player, String permission) {
        if (PermissionUtils.has(player, permission)) {
            return true;
        }
        send(player, "no-permission", Map.of());
        return false;
    }

    private void send(Player player, String key, Map<String, String> vars) {
        module.presenter().send(player.getUniqueId(), key, vars);
    }

    private static void usage(Player player, String usage) {
        player.sendMessage(ColorUtils.colorize("&7Usage: &f" + usage));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !PermissionUtils.has(player, CoinFlipModule.PERM_USE)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                String permission = switch (sub) {
                    case "create" -> CoinFlipModule.PERM_CREATE;
                    case "join", "accept" -> CoinFlipModule.PERM_JOIN;
                    case "challenge", "rematch" -> CoinFlipModule.PERM_CHALLENGE;
                    case "practice" -> CoinFlipModule.PERM_PRACTICE;
                    default -> CoinFlipModule.PERM_USE;
                };
                if (PermissionUtils.has(player, permission)) {
                    allowed.add(sub);
                }
            }
            return filter(allowed, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "join" -> filter(module.service().publicListings().stream().map(match -> match.id()).toList(), args[1]);
                case "cancel" -> filter(module.service().matchesOf(player.getUniqueId()).stream()
                        .filter(match -> match.creatorId().equals(player.getUniqueId())).map(match -> match.id()).toList(), args[1]);
                case "accept", "deny" -> filter(module.service().matchesOf(player.getUniqueId()).stream()
                        .filter(match -> player.getUniqueId().equals(match.inviteeId())).map(match -> match.id()).toList(), args[1]);
                case "challenge", "stats", "history" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> !name.equals(player.getName()) || !sub.equals("challenge")).toList(), args[1]);
                case "top" -> filter(java.util.Arrays.stream(CoinFlipDao.Metric.values())
                        .map(metric -> metric.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList(), args[1]);
                case "create" -> filter(module.config().incrementsCents().stream().map(StakeMath::format)
                        .map(value -> value.replace(",", "")).toList(), args[1]);
                default -> List.of();
            };
        }
        if ((args.length == 3 && sub.equals("create")) || (args.length == 4 && sub.equals("challenge"))) {
            return filter(List.of("heads", "tails", "random"), args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).limit(50).toList();
    }
}
