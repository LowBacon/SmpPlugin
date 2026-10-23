// SmpPlugin by LowBacon
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitTask;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Roulette implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.roulette";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.roulette";
    static final int WINDOW = 9;

    enum State {
        WAITING, COUNTDOWN, SPINNING, SETTLED
    }

    record Entry(UUID player, String name, long stake, String holdId) {
    }

    static final class Round {
        final int number;
        final long openedAt = System.currentTimeMillis();
        final List<Entry> entries = new ArrayList<>();
        State state = State.WAITING;
        long countdownEndsAt;
        int winner = -1;
        int[] reel;
        List<Integer> delays;
        int position;
        int wait;
        int settleIn;
        long payout;
        long fee;

        Round(int number) {
            this.number = number;
        }

        long pot() {
            long pot = 0L;
            for (Entry entry : entries) {
                pot += entry.stake();
            }
            return pot;
        }

        long[] stakes() {
            long[] stakes = new long[entries.size()];
            for (int i = 0; i < stakes.length; i++) {
                stakes[i] = entries.get(i).stake();
            }
            return stakes;
        }

        Entry entryOf(UUID player) {
            for (Entry entry : entries) {
                if (entry.player().equals(player)) {
                    return entry;
                }
            }
            return null;
        }
    }

    private final SmpPlugin plugin;
    private final Wallet wallet;
    private final GamblingEscrow escrow;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Long> lastAction = new HashMap<>();
    private final RouletteMenu menu;
    private Round round;
    private Round last;
    private int rounds;
    private BukkitTask ticker;
    private int ticks;

    Roulette(SmpPlugin plugin, Wallet wallet, GamblingEscrow escrow) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.escrow = escrow;
        this.menu = new RouletteMenu(plugin, this);
    }

    // ------------------------------------------------------------------ settings (extras.yml ROULETTE)

    private ConfigurationSection settings() {
        ConfigurationSection section = plugin.getConfigManager().getExtras() == null ? null
                : plugin.getConfigManager().getExtras().getConfigurationSection("ROULETTE");
        return section == null ? new YamlConfiguration() : section;
    }

    private long cents(String path, double fallback) {
        double value = settings().getDouble(path, fallback);
        return Double.isFinite(value) && value > 0D ? Math.min(StakeMath.HARD_MAX_STAKE_CENTS, StakeMath.fromEconomy(value))
                : StakeMath.fromEconomy(fallback);
    }

    long minBet() {
        return cents("MIN-BET", 100D);
    }

    long maxBet() {
        return Math.max(minBet(), cents("MAX-BET", 10_000_000D));
    }

    int feeBasisPoints() {
        double percent = settings().getDouble("FEE-PERCENT", 0D);
        int bp = (int) Math.round((Double.isFinite(percent) ? percent : 0D) * 100D);
        return Math.max(0, Math.min(RouletteMath.MAX_FEE_BASIS_POINTS, bp));
    }

    int minPlayers() {
        return Math.max(2, settings().getInt("MIN-PLAYERS", 2));
    }

    int maxPlayers() {
        return Math.max(minPlayers(), Math.min(100, settings().getInt("MAX-PLAYERS", 20)));
    }

    int countdownSeconds() {
        return Math.max(5, Math.min(600, settings().getInt("COUNTDOWN-SECONDS", 30)));
    }

    int lockSeconds() {
        return Math.max(0, Math.min(countdownSeconds() - 1, settings().getInt("LOCK-SECONDS", 3)));
    }

    List<Long> quickBets() {
        List<Long> bets = new ArrayList<>();
        for (Object raw : settings().getList("QUICK-BETS", List.of(1_000, 10_000, 100_000, 1_000_000))) {
            StakeMath.ParseResult parsed = StakeMath.parse(String.valueOf(raw));
            if (parsed.ok() && bets.size() < 4) {
                bets.add(parsed.cents());
            }
        }
        return bets;
    }

    // ------------------------------------------------------------------ read by the menu

    Round round() {
        return round;
    }

    Round lastRound() {
        return last;
    }

    Wallet wallet() {
        return wallet;
    }

    long secondsLeft() {
        return round == null || round.state != State.COUNTDOWN ? 0L
                : Math.max(0L, (round.countdownEndsAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    boolean locked() {
        return round != null && (round.state == State.SPINNING || round.state == State.SETTLED
                || (round.state == State.COUNTDOWN && secondsLeft() <= lockSeconds()));
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("cancel")) {
            if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            } else if (round == null || round.state == State.SPINNING || round.state == State.SETTLED) {
                Text.send(sender, "ROULETTE.NOTHING-TO-CANCEL", "&7There is no round waiting to start.");
            } else {
                cancel(Text.get("ROULETTE.CANCELLED-BY-STAFF", "a staff member cancelled it"));
                Text.send(sender, "ROULETTE.CANCELLED", "&7Round cancelled; every bet was given back.");
            }
            return true;
        }
        if (action.equals("info") || action.equals("rules") || !(sender instanceof Player)) {
            rules(sender);
            return true;
        }
        Player player = (Player) sender;
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        switch (action) {
            case "join", "bet" -> {
                if (args.length < 2) {
                    Text.send(player, "ROULETTE.USAGE", "&7/roulette join <amount>");
                    return true;
                }
                StakeMath.ParseResult parsed = StakeMath.parse(args[1]);
                if (!parsed.ok()) {
                    Text.send(player, "ROULETTE.BAD-AMOUNT", "&c'{amount}' is not an amount.", "{amount}", args[1].replace('&', '＆'));
                    return true;
                }
                join(player, parsed.cents());
            }
            case "leave" -> leave(player);
            default -> menu.open(player);
        }
        return true;
    }

    void join(Player player, long stake) {
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastAction.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 750L) {
            return;
        }
        if (stake < minBet() || stake > maxBet()) {
            Text.send(player, "ROULETTE.LIMITS", "&cBets are between &f{min} &cand &f{max}&c.",
                    "{min}", wallet.money(minBet()), "{max}", wallet.money(maxBet()));
            return;
        }
        if (round != null && (round.state == State.SPINNING || round.state == State.SETTLED)) {
            Text.send(player, "ROULETTE.SPINNING", "&7The wheel is spinning. The next round opens in a moment.");
            return;
        }
        if (locked()) {
            Text.send(player, "ROULETTE.LOCKED", "&7Bets are locked; the spin is about to start.");
            return;
        }
        if (round != null && round.entryOf(player.getUniqueId()) != null) {
            Text.send(player, "ROULETTE.ALREADY-IN", "&7You're already in this round with &f{stake}&7.",
                    "{stake}", wallet.money(round.entryOf(player.getUniqueId()).stake()));
            return;
        }
        if (round != null && round.entries.size() >= maxPlayers()) {
            Text.send(player, "ROULETTE.FULL", "&7This round is full ({max} players).", "{max}", Integer.toString(maxPlayers()));
            return;
        }
        int number = round == null ? rounds + 1 : round.number;
        // Unique across restarts too (the round counter starts again at 1): a held payout may outlive a run.
        String holdId = "roulette-" + number + "-" + player.getUniqueId() + "-" + Long.toString(now, 36);
        if (!wallet.take(player, new GamblingEscrow.Hold(holdId, player.getUniqueId(), player.getName(), stake,
                "roulette round"))) {
            Text.send(player, "ROULETTE.TOO-POOR", "&cYou only have &f{balance}&c.", "{balance}", wallet.money(wallet.balance(player)));
            return;
        }
        if (round == null) {
            round = new Round(++rounds);
        }
        Entry entry = new Entry(player.getUniqueId(), player.getName(), stake, holdId);
        round.entries.add(entry);
        startTicker();

        long pot = round.pot();
        Text.send(player, "ROULETTE.JOINED", "&aYou're in with &f{stake}&a. Your chance: &f{chance}&a of a &f{pot} &apot.",
                "{stake}", wallet.money(stake), "{chance}", RouletteMath.chance(stake, pot), "{pot}", wallet.money(pot));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7F, 1.4F);
        if (round.state == State.WAITING && round.entries.size() >= minPlayers()) {
            round.state = State.COUNTDOWN;
            round.countdownEndsAt = now + countdownSeconds() * 1000L;
        }
        if (settings().getBoolean("ANNOUNCE-JOINS", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player) || !PermissionUtils.has(online, PERMISSION)) {
                    continue;
                }
                Text.line(online, Text.prefix() + Text.get("ROULETTE.JOIN-ANNOUNCE",
                                "&6🎰 &f{player} &7put &f{stake} &7in the roulette. Pot: &6{pot}&7. ",
                                "{player}", player.getName(), "{stake}", wallet.money(stake), "{pot}", wallet.money(pot)),
                        Text.button(Text.get("ROULETTE.WATCH-BUTTON", "&e[Open]"), "roulette",
                                Text.get("ROULETTE.WATCH-HOVER", "&7See the round and join")));
            }
        }
        menu.refresh();
    }

    void leave(Player player) {
        Entry entry = round == null ? null : round.entryOf(player.getUniqueId());
        if (entry == null) {
            Text.send(player, "ROULETTE.NOT-IN", "&7You're not in this round.");
            return;
        }
        if (round.state != State.WAITING) {
            Text.send(player, "ROULETTE.CANT-LEAVE", "&7Once a second player has joined, bets are final.");
            return;
        }
        round.entries.remove(entry);
        refund(entry, "left the roulette");
        Text.send(player, "ROULETTE.LEFT", "&7You left the round and got &f{stake} &7back.", "{stake}", wallet.money(entry.stake()));
        if (round.entries.isEmpty()) {
            round = null;
            stopTicker();
        }
        menu.refresh();
    }

    private void rules(CommandSender sender) {
        Text.raw(sender, "");
        Text.raw(sender, Text.get("ROULETTE.RULES-TITLE", "&6&l🎰 Head Roulette &8» &7how it works"));
        Text.raw(sender, Text.get("ROULETTE.RULE-1", "  &7Put money in with &f/roulette join <amount> &7(&f{min} &7to &f{max}&7).",
                "{min}", wallet.money(minBet()), "{max}", wallet.money(maxBet())));
        Text.raw(sender, Text.get("ROULETTE.RULE-2", "  &7When {players} players are in, a &f{seconds}s &7countdown starts; bets lock {lock}s before the end.",
                "{players}", Integer.toString(minPlayers()), "{seconds}", Integer.toString(countdownSeconds()),
                "{lock}", Integer.toString(lockSeconds())));
        Text.raw(sender, Text.get("ROULETTE.RULE-3", "  &7Your chance is &fyour bet ÷ the pot&7: bet more, win more often. The server draws the winner first; the heads then spin to it."));
        Text.raw(sender, Text.get("ROULETTE.RULE-4", "  &7The winner gets the whole pot{fee}. If nobody else joins in time, your bet comes back.",
                "{fee}", feeBasisPoints() == 0 ? "" : " minus a " + RouletteMath.percent(feeBasisPoints()) + " fee"));
    }

    // ------------------------------------------------------------------ the round clock

    private void startTicker() {
        if (ticker == null) {
            ticks = 0;
            ticker = plugin.getSpigotScheduler().runGlobalTimer(this::tick, 1L, 1L);
        }
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void tick() {
        Round current = round;
        if (current == null) {
            stopTicker();
            return;
        }
        ticks++;
        if (current.state == State.SPINNING) {
            spinStep(current);
            return;
        }
        if (current.state == State.SETTLED) {
            if (--current.settleIn <= 0) {
                round = null;
                stopTicker();
                menu.refresh();
            }
            return;
        }
        if (ticks % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (current.state == State.WAITING) {
            long timeout = Math.max(30L, settings().getLong("WAIT-TIMEOUT-SECONDS", 300L)) * 1000L;
            if (now - current.openedAt > timeout) {
                cancel(Text.get("ROULETTE.TIMEOUT", "nobody else joined in time"));
            } else {
                menu.renderStatus();
            }
            return;
        }
        long left = secondsLeft();
        for (Entry entry : current.entries) {
            Player player = Bukkit.getPlayer(entry.player());
            if (player != null) {
                Text.actionBar(player, Text.get("ROULETTE.ACTIONBAR", "&6🎰 Spin in &f{seconds}s &8· &7pot &6{pot} &8· &7your chance &f{chance}",
                        "{seconds}", Long.toString(left), "{pot}", wallet.money(current.pot()),
                        "{chance}", RouletteMath.chance(entry.stake(), current.pot())));
                if (left <= 3 && left > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.0F + (3 - left) * 0.25F);
                }
            }
        }
        if (left <= 0) {
            spin(current);
        } else {
            menu.renderStatus();
        }
    }

    private void spin(Round current) {
        if (current.entries.size() < minPlayers()) {
            cancel(Text.get("ROULETTE.TOO-FEW", "not enough players"));
            return;
        }
        long[] stakes = current.stakes();
        long pot = RouletteMath.total(stakes);
        // The draw: one ticket out of every cent in the pot.
        current.winner = RouletteMath.pick(stakes, random.nextLong(pot));
        current.fee = RouletteMath.fee(pot, feeBasisPoints());
        current.payout = pot - current.fee;
        int steps = 48 + random.nextInt(12);
        current.reel = RouletteMath.reel(stakes, current.winner, steps + WINDOW, WINDOW, steps, random::nextLong);
        current.delays = RouletteMath.delays(steps, 1, 7);
        current.position = 0;
        current.wait = current.delays.getFirst();
        current.state = State.SPINNING;
        menu.prepareSpin(current);
        for (Entry entry : current.entries) {
            Player player = Bukkit.getPlayer(entry.player());
            if (player != null && player.getOpenInventory().getType() == InventoryType.CRAFTING) {
                // Only players who are not using another screen are taken to the wheel.
                menu.open(player);
            }
        }
        menu.refresh();
    }

    private void spinStep(Round current) {
        if (--current.wait > 0) {
            return;
        }
        current.position++;
        menu.renderReel(current, current.position);
        menu.tick(current.position, current.delays.size());
        if (current.position >= current.delays.size()) {
            settle(current);
            return;
        }
        current.wait = current.delays.get(current.position);
    }

    // ------------------------------------------------------------------ ending a round

    private void settle(Round current) {
        if (current.state == State.SETTLED || current.winner < 0) {
            return;
        }
        current.state = State.SETTLED;
        current.settleIn = 100;
        last = current;
        Entry winner = current.entries.get(current.winner);
        // The payout and the closing of every bet of the round are one transaction.
        List<String> holdIds = current.entries.stream().map(Entry::holdId).toList();
        boolean paid = wallet.pay(winner.player(), winner.name(), current.payout, EconomyReason.GAMBLE_PAYOUT,
                "Roulette win:", holdIds);
        if (!paid) {
            // Keep the whole payout held so it is paid at the next start; the other bets are in it.
            escrow.keepAsPayout(winner.holdId(), current.payout,
                    holdIds.stream().filter(id -> !id.equals(winner.holdId())).toList());
        }
        String chance = RouletteMath.chance(winner.stake(), current.pot());
        Text.broadcast(Text.prefix() + Text.get("ROULETTE.WINNER", "&6🎰 &f{player} &6won &f{payout} &6in the roulette with a &f{chance} &6chance!",
                "{player}", winner.name(), "{payout}", wallet.money(current.payout), "{chance}", chance));
        for (Entry entry : current.entries) {
            Player player = Bukkit.getPlayer(entry.player());
            if (player == null) {
                continue;
            }
            if (entry == winner) {
                Text.title(player, Text.get("ROULETTE.WIN-TITLE", "&6&lYOU WON!"), "&f+" + wallet.money(current.payout - entry.stake()), 5, 60, 15);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            } else {
                Text.title(player, "&7" + winner.name(), Text.get("ROULETTE.LOSE-SUBTITLE", "&7won the pot this time"), 5, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.8F);
            }
        }
        menu.showWinner(current);
    }

    private void cancel(String reason) {
        Round current = round;
        if (current == null) {
            return;
        }
        round = null;
        stopTicker();
        for (Entry entry : current.entries) {
            refund(entry, "a cancelled roulette round");
            Player player = Bukkit.getPlayer(entry.player());
            if (player != null) {
                Text.send(player, "ROULETTE.REFUNDED", "&7The roulette round ended ({reason}); your &f{stake} &7is back.",
                        "{reason}", reason, "{stake}", wallet.money(entry.stake()));
            }
        }
        menu.refresh();
    }

    private void refund(Entry entry, String why) {
        wallet.pay(entry.player(), entry.name(), entry.stake(), EconomyReason.GAMBLE_REFUND, "Refund from " + why + ":",
                entry.holdId());
    }

    void shutdown() {
        stopTicker();
        Round current = round;
        if (current == null) {
            return;
        }
        if (current.state == State.SPINNING) {
            settle(current);
            round = null;
            return;
        }
        if (current.state == State.SETTLED) {
            round = null;
            return;
        }
        round = null;
        for (Entry entry : current.entries) {
            refund(entry, "a roulette round stopped by a restart");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("join", "leave", "info"));
            if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                options.add("cancel");
            }
            String typed = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(typed)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            List<String> amounts = new ArrayList<>();
            for (long cents : quickBets()) {
                amounts.add(java.math.BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString());
            }
            return amounts;
        }
        return Collections.emptyList();
    }
}
