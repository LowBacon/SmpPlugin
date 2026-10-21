package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /dice: bet money on a dice roll against the house.
 *
 * <pre>
 * /dice                       the odds and how to play
 * /dice &lt;bet&gt; [high|low|1-6]  shows the bet, its chance and payout, with a Roll button
 * /dice roll [sides]          a roll just for fun, no money (also /roll)
 * </pre>
 *
 * The dice are rolled by the server (SecureRandom) and the bet is taken before the animation
 * starts; the animation only shows a result that is already decided. The result is settled exactly
 * once: when the animation ends, when the player leaves, or when the server stops, whichever
 * comes first. Until then the bet is held in {@link GamblingEscrow}, so a crash gives it back.
 */
public final class DiceGame implements CommandExecutor, TabCompleter, Listener {

    public static final String PERMISSION = "smpplugin.dice";
    public static final String FUN_PERMISSION = "smpplugin.fun.games";
    private static final String BYPASS_COOLDOWN = "smpplugin.gambling.bypasscooldown";
    /** Ticks after the roll command before each animation frame is shown. The last frame is the real result. */
    private static final int[] FRAME_TICKS = {1, 3, 5, 7, 10, 13, 17, 22, 28, 35};

    private record Pending(long stake, DiceOdds.Bet bet, long expiresAt) {
    }

    private static final class Session {
        final String id;
        final UUID player;
        final String name;
        final long stake;
        final DiceOdds.Bet bet;
        final DiceOdds.Roll roll;
        final DiceOdds.Result result;
        final double multiplier;
        final long payout;
        boolean settled;

        Session(String id, Player player, long stake, DiceOdds.Bet bet, DiceOdds.Roll roll, DiceOdds.Result result,
                double multiplier, long payout) {
            this.id = id;
            this.player = player.getUniqueId();
            this.name = player.getName();
            this.stake = stake;
            this.bet = bet;
            this.roll = roll;
            this.result = result;
            this.multiplier = multiplier;
            this.payout = payout;
        }
    }

    private final SmpPlugin plugin;
    private final Wallet wallet;
    private final GamblingEscrow escrow;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastRoll = new HashMap<>();

    DiceGame(SmpPlugin plugin, Wallet wallet, GamblingEscrow escrow) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.escrow = escrow;
    }

    // ------------------------------------------------------------------ settings (extras.yml DICE)

    private ConfigurationSection settings() {
        ConfigurationSection section = plugin.getConfigManager().getExtras() == null ? null
                : plugin.getConfigManager().getExtras().getConfigurationSection("DICE");
        return section == null ? new YamlConfiguration() : section;
    }

    private long cents(String path, double fallback) {
        double value = settings().getDouble(path, fallback);
        return Double.isFinite(value) && value > 0D ? Math.min(StakeMath.HARD_MAX_STAKE_CENTS, StakeMath.fromEconomy(value))
                : StakeMath.fromEconomy(fallback);
    }

    private long minBet() {
        return cents("MIN-BET", 10D);
    }

    private long maxBet() {
        return Math.max(minBet(), cents("MAX-BET", 1_000_000D));
    }

    private boolean tieRefunds() {
        return settings().getBoolean("TIE-REFUNDS", true);
    }

    private double multiplier(DiceOdds.Bet bet) {
        String path = switch (bet.mode()) {
            case DUEL -> "PAYOUTS.DUEL";
            case HIGH, LOW -> "PAYOUTS.HIGH-LOW";
            case NUMBER -> "PAYOUTS.NUMBER";
        };
        double fallback = switch (bet.mode()) {
            case DUEL -> 1.9D;
            case HIGH, LOW -> 1.9D;
            case NUMBER -> 5.7D;
        };
        return DiceOdds.clampMultiplier(settings().getDouble(path, fallback), bet, tieRefunds());
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("roll") || (args.length > 0 && args[0].equalsIgnoreCase("roll"))) {
            String[] rest = label.equalsIgnoreCase("roll") ? args : java.util.Arrays.copyOfRange(args, 1, args.length);
            funRoll(sender, rest);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (first) {
            case "", "odds", "help", "info" -> showOdds(player);
            case "confirm" -> confirm(player);
            case "cancel" -> {
                if (pending.remove(player.getUniqueId()) != null) {
                    Text.send(player, "DICE.CANCELLED", "&7Bet cancelled. Nothing was taken.");
                }
            }
            default -> bet(player, args);
        }
        return true;
    }

    private void bet(Player player, String[] args) {
        StakeMath.ParseResult parsed = StakeMath.parse(args[0]);
        if (!parsed.ok()) {
            Text.send(player, "DICE.BAD-AMOUNT", "&c'{amount}' is not an amount. Try &f/dice 1000 high&c.",
                    "{amount}", args[0].replace('&', '＆'));
            return;
        }
        DiceOdds.Bet bet = DiceOdds.parse(args.length > 1 ? args[1] : null).orElse(null);
        if (bet == null) {
            Text.send(player, "DICE.BAD-BET", "&cBet on &fhigh&c, &flow&c, a number &f1-6&c, or leave it out to roll against the house.");
            return;
        }
        long stake = parsed.cents();
        if (!checkAmount(player, stake)) {
            return;
        }
        boolean now = args.length > 2 && args[2].equalsIgnoreCase("now");
        if (!now && settings().getBoolean("CONFIRM", true)) {
            long seconds = Math.max(5L, settings().getLong("CONFIRM-SECONDS", 30L));
            pending.put(player.getUniqueId(), new Pending(stake, bet, System.currentTimeMillis() + seconds * 1000L));
            preview(player, stake, bet);
            return;
        }
        start(player, stake, bet);
    }

    private boolean checkAmount(Player player, long stake) {
        if (stake < minBet() || stake > maxBet()) {
            Text.send(player, "DICE.LIMITS", "&cBets are between &f{min} &cand &f{max}&c.",
                    "{min}", wallet.money(minBet()), "{max}", wallet.money(maxBet()));
            return false;
        }
        if (wallet.balance(player) < stake) {
            Text.send(player, "DICE.TOO-POOR", "&cYou only have &f{balance}&c.", "{balance}", wallet.money(wallet.balance(player)));
            return false;
        }
        return true;
    }

    private void preview(Player player, long stake, DiceOdds.Bet bet) {
        double multiplier = multiplier(bet);
        long winPays = DiceOdds.payoutCents(stake, DiceOdds.Result.WIN, multiplier);
        String betArg = betArgument(bet);
        Text.raw(player, "");
        Text.raw(player, Text.get("DICE.PREVIEW-TITLE", "&6&l🎲 Dice &8» &fyour bet"));
        Text.raw(player, Text.get("DICE.PREVIEW-BET", "  &7Bet &f{stake} &7on &e{bet}",
                "{stake}", wallet.money(stake), "{bet}", bet.describe()));
        Text.raw(player, Text.get("DICE.PREVIEW-ODDS", "  &7Chance &f{chance} &8· &7a win pays &a{payout} &7(+{profit})",
                "{chance}", DiceOdds.percent(DiceOdds.winChance(bet)), "{payout}", wallet.money(winPays),
                "{profit}", wallet.money(winPays - stake)));
        if (bet.mode() == DiceOdds.Mode.DUEL) {
            Text.raw(player, Text.get(tieRefunds() ? "DICE.PREVIEW-TIE" : "DICE.PREVIEW-TIE-LOSES",
                    tieRefunds() ? "  &7A tie gives your bet back." : "  &7A tie counts as a loss."));
        }
        Text.line(player, "  ",
                Text.button(Text.get("DICE.ROLL-BUTTON", "&a&l[ROLL]"), "dice confirm",
                        Text.get("DICE.ROLL-HOVER", "&7Take {stake} and roll", "{stake}", wallet.money(stake))),
                "  ",
                Text.button(Text.get("DICE.CANCEL-BUTTON", "&c[Cancel]"), "dice cancel", "&7Nothing is taken"),
                Text.get("DICE.PREVIEW-RTP", "  &8(returns {rtp} on average)",
                        "{rtp}", DiceOdds.percent(DiceOdds.returnToPlayer(bet, multiplier, tieRefunds()))));
        if (!betArg.isEmpty()) {
            Text.raw(player, Text.get("DICE.PREVIEW-HINT", "&8  Tip: /dice {amount} {bet} now skips this step.",
                    "{amount}", plain(stake), "{bet}", betArg));
        }
    }

    private void confirm(Player player) {
        Pending bet = pending.remove(player.getUniqueId());
        if (bet == null || bet.expiresAt() < System.currentTimeMillis()) {
            Text.send(player, "DICE.NOTHING-TO-CONFIRM", "&7That bet expired. Place it again with &f/dice <bet>&7.");
            return;
        }
        if (checkAmount(player, bet.stake())) {
            start(player, bet.stake(), bet.bet());
        }
    }

    // ------------------------------------------------------------------ playing

    private void start(Player player, long stake, DiceOdds.Bet bet) {
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid)) {
            Text.send(player, "DICE.BUSY", "&7Your dice are still rolling!");
            return;
        }
        long cooldown = Math.max(0L, settings().getLong("COOLDOWN-SECONDS", 3L)) * 1000L;
        Long last = lastRoll.get(uuid);
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown && !PermissionUtils.has(player, BYPASS_COOLDOWN)) {
            Text.send(player, "DICE.COOLDOWN", "&7Wait &f{time} &7before rolling again.",
                    "{time}", Text.duration((cooldown - (now - last)) / 1000L + 1L));
            return;
        }
        double multiplier = multiplier(bet);
        String holdId = "dice-" + uuid + "-" + now;
        if (!wallet.take(player, new GamblingEscrow.Hold(holdId, uuid, player.getName(), stake, "dice roll"))) {
            Text.send(player, "DICE.TOO-POOR", "&cYou only have &f{balance}&c.", "{balance}", wallet.money(wallet.balance(player)));
            return;
        }
        // The result is fixed here, before anything is shown.
        DiceOdds.Roll roll = DiceOdds.roll(bet, () -> random.nextInt(6) + 1);
        DiceOdds.Result result = DiceOdds.judge(bet, roll, tieRefunds());
        long payout = DiceOdds.payoutCents(stake, result, multiplier);
        Session session = new Session(holdId, player, stake, bet, roll, result, multiplier, payout);
        sessions.put(uuid, session);
        lastRoll.put(uuid, now);
        if (lastRoll.size() > 500) {
            lastRoll.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        }
        animate(player, session);
    }

    private void animate(Player player, Session session) {
        boolean duel = session.bet.mode() == DiceOdds.Mode.DUEL;
        for (int i = 0; i < FRAME_TICKS.length; i++) {
            boolean last = i == FRAME_TICKS.length - 1;
            int frame = i;
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (session.settled || !player.isOnline()) {
                    return;
                }
                if (last) {
                    reveal(player, session);
                    return;
                }
                ThreadLocalRandom shown = ThreadLocalRandom.current();
                String faces = "&f" + DiceOdds.face(shown.nextInt(1, 7))
                        + (duel ? " &8vs &f" + DiceOdds.face(shown.nextInt(1, 7)) : "");
                Text.title(player, faces, Text.get("DICE.ROLLING", "&7rolling..."), 0, 10, 0);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, 0.8F + frame * 0.1F);
            }, FRAME_TICKS[i]);
        }
    }

    private void reveal(Player player, Session session) {
        boolean duel = session.bet.mode() == DiceOdds.Mode.DUEL;
        String faces = "&f&l" + DiceOdds.face(session.roll.player()) + " " + session.roll.player()
                + (duel ? " &8vs &f&l" + DiceOdds.face(session.roll.house()) + " " + session.roll.house() : "");
        switch (session.result) {
            case WIN -> {
                Text.title(player, Text.get("DICE.WIN-TITLE", "&a&lYOU WIN"),
                        faces + " &8· &a+" + wallet.money(session.payout - session.stake), 0, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 20, 0.5, 0.5, 0.5, 0);
            }
            case PUSH -> {
                Text.title(player, Text.get("DICE.TIE-TITLE", "&e&lTIE"), faces + " &8· &7bet returned", 0, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 1.0F);
            }
            case LOSE -> {
                Text.title(player, Text.get("DICE.LOSE-TITLE", "&c&lNO LUCK"),
                        faces + " &8· &c-" + wallet.money(session.stake), 0, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7F, 1.0F);
            }
        }
        // Give the title a moment, then pay out and print the summary.
        plugin.getSpigotScheduler().runEntityLater(player, () -> settle(session, true), 12L);
    }

    /** Pays the result (once). {@code tell}: also print the summary for the player. */
    private void settle(Session session, boolean tell) {
        if (session.settled) {
            return;
        }
        session.settled = true;
        sessions.remove(session.player);
        if (session.payout > 0L) {
            boolean push = session.result == DiceOdds.Result.PUSH;
            boolean paid = wallet.pay(session.player, session.name, session.payout,
                    push ? EconomyReason.GAMBLE_REFUND : EconomyReason.GAMBLE_PAYOUT,
                    push ? "Dice tie, bet returned:" : "Dice win:", session.id);
            if (!paid) {
                escrow.keepAsPayout(session.id, session.payout, List.of());
            }
        } else {
            escrow.closeWithoutPayment(List.of(session.id));
        }
        Player player = Bukkit.getPlayer(session.player);
        if (!tell || player == null) {
            return;
        }
        String rolled = DiceOdds.face(session.roll.player()) + " " + session.roll.player()
                + (session.bet.mode() == DiceOdds.Mode.DUEL
                ? " &7vs the house's &f" + DiceOdds.face(session.roll.house()) + " " + session.roll.house() : "");
        String summary = switch (session.result) {
            case WIN -> Text.get("DICE.WIN", "&a🎲 You rolled &f{rolled} &aand won &f{payout}&a! &7(+{profit})",
                    "{rolled}", rolled, "{payout}", wallet.money(session.payout),
                    "{profit}", wallet.money(session.payout - session.stake));
            case PUSH -> Text.get("DICE.TIE", "&e🎲 You rolled &f{rolled}&e. A tie: your &f{stake} &eis back.",
                    "{rolled}", rolled, "{stake}", wallet.money(session.stake));
            case LOSE -> Text.get("DICE.LOSE", "&c🎲 You rolled &f{rolled}&c and lost &f{stake}&c.",
                    "{rolled}", rolled, "{stake}", wallet.money(session.stake));
        };
        Text.line(player, Text.prefix() + summary + " ",
                Text.button(Text.get("DICE.AGAIN-BUTTON", "&b[Again]"),
                        "dice " + plain(session.stake) + " " + betArgument(session.bet) + " now",
                        Text.get("DICE.AGAIN-HOVER", "&7Same bet again: {stake} on {bet}",
                                "{stake}", wallet.money(session.stake), "{bet}", session.bet.describe())));
        long broadcastOver = cents("BROADCAST-WIN-OVER", 50_000D);
        if (session.result == DiceOdds.Result.WIN && session.payout - session.stake >= broadcastOver) {
            Text.broadcast(Text.prefix() + Text.get("DICE.BIG-WIN", "&6🎲 &f{player} &6just won &f{payout} &6on /dice!",
                    "{player}", session.name, "{payout}", wallet.money(session.payout)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        Session session = sessions.get(uuid);
        if (session != null) {
            // Settle now, while the player's data is still loaded, instead of when the animation would end.
            settle(session, false);
        }
    }

    /** Settles every roll still in progress. Called while the plugin shuts down; nothing is scheduled. */
    void shutdown() {
        for (Session session : new ArrayList<>(sessions.values())) {
            settle(session, false);
        }
        pending.clear();
    }

    // ------------------------------------------------------------------ odds and the free roll

    private void showOdds(Player player) {
        boolean ties = tieRefunds();
        Text.raw(player, "");
        Text.raw(player, Text.get("DICE.ODDS-TITLE", "&6&l🎲 Dice &8» &7bet against the house"));
        for (DiceOdds.Bet bet : List.of(DiceOdds.Bet.duel(), new DiceOdds.Bet(DiceOdds.Mode.HIGH, 0),
                new DiceOdds.Bet(DiceOdds.Mode.LOW, 0), new DiceOdds.Bet(DiceOdds.Mode.NUMBER, 6))) {
            double multiplier = multiplier(bet);
            String name = bet.mode() == DiceOdds.Mode.NUMBER ? "a number (1-6)" : bet.describe();
            String example = bet.mode() == DiceOdds.Mode.DUEL ? "/dice 100" : "/dice 100 " + betArgument(bet);
            Text.line(player, "  ", Text.button("&e" + name, example.substring(1) + "", "&7Click to see this bet for 100"),
                    " &8· &7" + DiceOdds.percent(DiceOdds.winChance(bet)) + " chance &8· &7pays &a"
                            + DiceOdds.multiplier(multiplier) + " &8· &7returns "
                            + DiceOdds.percent(DiceOdds.returnToPlayer(bet, multiplier, ties)));
        }
        Text.raw(player, Text.get("DICE.ODDS-RULES",
                "  &7Bets: &f{min} &7to &f{max}&7. A win pays your bet times the multiplier.{tie}",
                "{min}", wallet.money(minBet()), "{max}", wallet.money(maxBet()),
                "{tie}", ties ? " Against the house, a tie gives your bet back." : ""));
        Text.raw(player, Text.get("DICE.ODDS-USAGE", "  &7Play: &f/dice <bet> [high|low|1-6] &8· &7just for fun: &f/dice roll"));
    }

    private void funRoll(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, FUN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        int sides = 6;
        if (args.length > 0) {
            try {
                sides = Math.max(2, Math.min(1000, Integer.parseInt(args[0].toLowerCase(Locale.ROOT).replace("d", ""))));
            } catch (NumberFormatException ignored) {
                sides = 6;
            }
        }
        int result = ThreadLocalRandom.current().nextInt(1, sides + 1);
        String message = Text.get("DICE.FUN-ROLL", "&7🎲 &f{player} &7rolled a &e&l{result} &8(d{sides})",
                "{player}", sender.getName(), "{result}", Integer.toString(result), "{sides}", Integer.toString(sides));
        if (sender instanceof Player player) {
            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(player.getLocation()) <= 32D * 32D) {
                    Text.raw(nearby, message);
                }
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 1.4F);
        } else {
            Text.raw(sender, message);
        }
    }

    private static String betArgument(DiceOdds.Bet bet) {
        return switch (bet.mode()) {
            case DUEL -> "duel";
            case HIGH -> "high";
            case LOW -> "low";
            case NUMBER -> Integer.toString(bet.number());
        };
    }

    /** An amount as the player would type it: 1500 or 12.5. */
    private static String plain(long cents) {
        return java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("roll")) {
            return args.length == 1 ? filter(List.of("6", "12", "20", "100"), args[0]) : List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("roll", "odds", "100", "1000", "10000"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("roll")) {
            return filter(List.of("duel", "high", "low", "1", "2", "3", "4", "5", "6"), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).toList();
    }
}
