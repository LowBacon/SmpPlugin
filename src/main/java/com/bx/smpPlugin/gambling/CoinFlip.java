package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /cf (/coinflip): a player puts up a bet; anyone can join it by matching the bet, and the flip
 * starts at once. Both heads spin past a marker and the head that stops in the middle takes the pot.
 *
 * <pre>
 * /cf                 the open games: click one to join, or a quick bet to start your own
 * /cf &lt;amount&gt;        start a game
 * /cf join &lt;player&gt;   join that player's game
 * /cf cancel          take back your open games
 * </pre>
 *
 * The bets are equal, so each side wins exactly half the time. The server draws the winner
 * (SecureRandom) the moment the game is joined; the spin only shows it. Bets sit in
 * {@link GamblingEscrow} until paid: an open game nobody joins is refunded after a while, a server
 * stop refunds open games and pays games that were already spinning, and a crash refunds everything
 * at the next start.
 */
public final class CoinFlip implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.coinflip.use";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.coinflip";

    enum State {
        OPEN, SPINNING, DONE
    }

    /** One game. Only touched on the server thread. */
    static final class Game {
        final int id;
        final UUID creator;
        final String creatorName;
        final long stake;
        final long createdAt = System.currentTimeMillis();
        final String creatorHold;
        UUID joiner;
        String joinerName;
        String joinerHold;
        State state = State.OPEN;
        /** 0 = the creator wins, 1 = the joiner wins. */
        int winner = -1;
        int[] reel;
        List<Integer> delays;
        int position;
        int wait;
        int lingerTicks;
        long payout;
        FlipScreen screen;

        Game(int id, Player creator, long stake) {
            this.id = id;
            this.creator = creator.getUniqueId();
            this.creatorName = creator.getName();
            this.stake = stake;
            this.creatorHold = "coinflip-" + id + "-" + creator.getUniqueId();
        }

        UUID winnerId() {
            return winner == 0 ? creator : joiner;
        }

        String winnerName() {
            return winner == 0 ? creatorName : joinerName;
        }
    }

    private final SmpPlugin plugin;
    private final Wallet wallet;
    private final GamblingEscrow escrow;
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, Game> open = new LinkedHashMap<>();
    private final List<Game> spinning = new ArrayList<>();
    private final Map<UUID, Long> lastAction = new HashMap<>();
    private int nextId = 1;
    private BukkitTask ticker;
    private int ticks;

    CoinFlip(SmpPlugin plugin, Wallet wallet, GamblingEscrow escrow) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.escrow = escrow;
    }

    // ------------------------------------------------------------------ settings (extras.yml COINFLIP)

    private ConfigurationSection settings() {
        ConfigurationSection section = plugin.getConfigManager().getExtras() == null ? null
                : plugin.getConfigManager().getExtras().getConfigurationSection("COINFLIP");
        return section == null ? new YamlConfiguration() : section;
    }

    long minBet() {
        double value = settings().getDouble("MIN-BET", 1D);
        return Double.isFinite(value) && value > 0D ? Math.max(1L, StakeMath.fromEconomy(value)) : 100L;
    }

    /** The highest bet: MAX-BET, or no limit (only the largest amount the economy handles) when it is 0. */
    long maxBet() {
        double value = settings().getDouble("MAX-BET", 0D);
        if (!Double.isFinite(value) || value <= 0D) {
            return StakeMath.HARD_MAX_STAKE_CENTS;
        }
        return Math.max(minBet(), Math.min(StakeMath.HARD_MAX_STAKE_CENTS, StakeMath.fromEconomy(value)));
    }

    boolean noLimit() {
        return maxBet() >= StakeMath.HARD_MAX_STAKE_CENTS;
    }

    int feeBasisPoints() {
        double percent = settings().getDouble("FEE-PERCENT", 0D);
        int bp = (int) Math.round((Double.isFinite(percent) ? percent : 0D) * 100D);
        return Math.max(0, Math.min(SpinMath.MAX_FEE_BASIS_POINTS, bp));
    }

    private int maxOpenPerPlayer() {
        return Math.max(1, Math.min(50, settings().getInt("MAX-OPEN-PER-PLAYER", 3)));
    }

    private long expireMillis() {
        return Math.max(1L, settings().getLong("EXPIRE-MINUTES", 30L)) * 60_000L;
    }

    List<Long> quickBets() {
        List<Long> bets = new ArrayList<>();
        for (Object raw : settings().getList("QUICK-BETS", List.of(1_000, 10_000, 100_000, 1_000_000, 10_000_000))) {
            StakeMath.ParseResult parsed = StakeMath.parse(String.valueOf(raw));
            if (parsed.ok() && bets.size() < 5) {
                bets.add(parsed.cents());
            }
        }
        return bets;
    }

    Wallet wallet() {
        return wallet;
    }

    long payoutFor(long stake) {
        long pot = stake * 2L;
        return pot - SpinMath.fee(pot, feeBasisPoints());
    }

    /** Open games, the viewer's own first, then the biggest bets. */
    List<Game> openGames(UUID viewer) {
        List<Game> games = new ArrayList<>(open.values());
        games.sort(Comparator.comparing((Game game) -> !game.creator.equals(viewer))
                .thenComparing(Comparator.comparingLong((Game game) -> game.stake).reversed())
                .thenComparingInt(game -> game.id));
        return games;
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("cancelall")) {
            if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
                return true;
            }
            int count = open.size();
            for (Game game : new ArrayList<>(open.values())) {
                refundOpen(game, Text.get("COINFLIP.CANCELLED-BY-STAFF", "a staff member cancelled every open game"));
            }
            Text.send(sender, "COINFLIP.CANCELLED-ALL", "&7Cancelled {count} open game(s); every bet was given back.",
                    "{count}", Integer.toString(count));
            refreshMenus();
            return true;
        }
        if (!(sender instanceof Player player)) {
            help(sender);
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        switch (action) {
            case "" -> new CoinFlipMenu(plugin, this, player.getUniqueId()).open(player);
            case "help", "info" -> help(player);
            case "cancel" -> cancelOwn(player);
            case "join" -> {
                if (args.length < 2) {
                    Text.send(player, "COINFLIP.JOIN-USAGE", "&7/cf join <player>");
                    return true;
                }
                Game game = null;
                for (Game candidate : openGames(player.getUniqueId())) {
                    if (candidate.creatorName.equalsIgnoreCase(args[1])) {
                        game = candidate;
                        break;
                    }
                }
                if (game == null) {
                    Text.send(player, "COINFLIP.NO-GAME", "&7{player} has no open game.", "{player}", args[1].replace('&', '＆'));
                } else {
                    join(player, game.id);
                }
            }
            default -> {
                StakeMath.ParseResult parsed = StakeMath.parse(args[0]);
                if (!parsed.ok()) {
                    Text.send(player, "COINFLIP.BAD-AMOUNT", "&c'{amount}' is not an amount. Try &f/cf 1000&c.",
                            "{amount}", args[0].replace('&', '＆'));
                } else {
                    create(player, parsed.cents());
                }
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        Text.raw(sender, "");
        Text.raw(sender, Text.get("COINFLIP.HELP-TITLE", "&6&lCoinFlip &8» &7how it works"));
        Text.raw(sender, Text.get("COINFLIP.HELP-1", "  &f/cf <amount> &7starts a game. Others see it in &f/cf&7."));
        Text.raw(sender, Text.get("COINFLIP.HELP-2", "  &7Click someone's game to bet the same amount; the flip starts at once."));
        Text.raw(sender, Text.get("COINFLIP.HELP-3", "  &7Both heads spin; the one that stops in the middle wins &f{pot}&7. It is a fair 50/50.",
                "{pot}", feeBasisPoints() == 0 ? "both bets" : "both bets minus a " + SpinMath.percent(feeBasisPoints()) + " fee"));
        Text.raw(sender, Text.get("COINFLIP.HELP-4", "  &f/cf cancel &7takes back your open games. Games nobody joins come back after {minutes} minutes.",
                "{minutes}", Long.toString(expireMillis() / 60_000L)));
        Text.raw(sender, Text.get("COINFLIP.HELP-5", "  &7Bets: from &f{min}{max}&7.", "{min}", wallet.money(minBet()),
                "{max}", noLimit() ? " &7(no maximum)" : " &7to &f" + wallet.money(maxBet())));
    }

    // ------------------------------------------------------------------ playing

    private boolean spam(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastAction.put(player.getUniqueId(), now);
        if (lastAction.size() > 500) {
            lastAction.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        }
        return previous != null && now - previous < 400L;
    }

    void create(Player player, long stake) {
        if (!PermissionUtils.has(player, PERMISSION) || spam(player)) {
            return;
        }
        if (stake < minBet() || stake > maxBet()) {
            Text.send(player, "COINFLIP.LIMITS", noLimit() ? "&cThe smallest bet is &f{min}&c." : "&cBets are between &f{min} &cand &f{max}&c.",
                    "{min}", wallet.money(minBet()), "{max}", wallet.money(maxBet()));
            return;
        }
        long mine = open.values().stream().filter(game -> game.creator.equals(player.getUniqueId())).count();
        if (mine >= maxOpenPerPlayer()) {
            Text.send(player, "COINFLIP.TOO-MANY", "&7You already have {count} open game(s). &f/cf cancel &7takes them back.",
                    "{count}", Long.toString(mine));
            return;
        }
        if (!wallet.take(player, stake, "a coin flip")) {
            Text.send(player, "COINFLIP.TOO-POOR", "&cYou only have &f{balance}&c.", "{balance}", wallet.money(wallet.balance(player)));
            return;
        }
        Game game = new Game(nextId++, player, stake);
        open.put(game.id, game);
        escrow.open(new GamblingEscrow.Hold(game.creatorHold, player.getUniqueId(), player.getName(), stake, "coin flip"));
        startTicker();
        Text.line(player, Text.prefix() + Text.get("COINFLIP.CREATED", "&aYour &f{stake} &aflip is open. ",
                        "{stake}", wallet.money(stake)),
                Text.button(Text.get("COINFLIP.CANCEL-BUTTON", "&c[Cancel]"), "cf cancel", "&7Take your bet back"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7F, 1.4F);
        if (settings().getBoolean("ANNOUNCE-NEW", true)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && PermissionUtils.has(online, PERMISSION)) {
                    Text.line(online, Text.prefix() + Text.get("COINFLIP.ANNOUNCE", "&6🪙 &f{player} &7wants to flip for &6{stake}&7. ",
                                    "{player}", player.getName(), "{stake}", wallet.money(stake)),
                            Text.button(Text.get("COINFLIP.JOIN-BUTTON", "&a[Flip]"), "cf join " + player.getName(),
                                    Text.get("COINFLIP.JOIN-HOVER", "&7Bet {stake} and flip now", "{stake}", wallet.money(stake))));
                }
            }
        }
        refreshMenus();
    }

    void cancelOwn(Player player) {
        int count = 0;
        for (Game game : new ArrayList<>(open.values())) {
            if (game.creator.equals(player.getUniqueId())) {
                refundOpen(game, null);
                count++;
            }
        }
        if (count == 0) {
            Text.send(player, "COINFLIP.NOTHING-OPEN", "&7You have no open games.");
        } else {
            Text.send(player, "COINFLIP.CANCELLED", "&7Took back {count} game(s); your money is back.", "{count}", Integer.toString(count));
        }
        refreshMenus();
    }

    void cancel(Player player, int gameId) {
        Game game = open.get(gameId);
        if (game != null && game.creator.equals(player.getUniqueId())) {
            refundOpen(game, null);
            Text.send(player, "COINFLIP.CANCELLED-ONE", "&7Game cancelled; your &f{stake} &7is back.", "{stake}", wallet.money(game.stake));
            refreshMenus();
        }
    }

    void join(Player player, int gameId) {
        if (!PermissionUtils.has(player, PERMISSION) || spam(player)) {
            return;
        }
        Game game = open.get(gameId);
        if (game == null) {
            Text.send(player, "COINFLIP.GONE", "&7That game was just taken or cancelled.");
            refreshMenus();
            return;
        }
        if (game.creator.equals(player.getUniqueId())) {
            Text.send(player, "COINFLIP.OWN", "&7That's your own game. Click it to cancel.");
            return;
        }
        if (!wallet.take(player, game.stake, "a coin flip")) {
            Text.send(player, "COINFLIP.TOO-POOR", "&cYou only have &f{balance}&c.", "{balance}", wallet.money(wallet.balance(player)));
            return;
        }
        open.remove(game.id);
        game.joiner = player.getUniqueId();
        game.joinerName = player.getName();
        game.joinerHold = "coinflip-" + game.id + "-" + player.getUniqueId();
        escrow.open(new GamblingEscrow.Hold(game.joinerHold, player.getUniqueId(), player.getName(), game.stake, "coin flip"));

        // The draw: equal bets, so an exact 50/50.
        game.winner = random.nextBoolean() ? 0 : 1;
        game.payout = payoutFor(game.stake);
        int steps = 30 + random.nextInt(8);
        game.reel = SpinMath.reel(new long[]{1L, 1L}, game.winner, steps + FlipScreen.WINDOW, FlipScreen.WINDOW, steps,
                random::nextLong);
        game.delays = SpinMath.delays(steps, 1, 6);
        game.position = 0;
        game.wait = game.delays.getFirst();
        game.state = State.SPINNING;
        game.screen = new FlipScreen(plugin, this, game);
        spinning.add(game);
        startTicker();

        game.screen.open(player);
        Player creator = Bukkit.getPlayer(game.creator);
        if (creator != null) {
            boolean free = creator.getOpenInventory().getType() == InventoryType.CRAFTING
                    || creator.getOpenInventory().getTopInventory().getHolder() instanceof CoinFlipMenu;
            if (free) {
                game.screen.open(creator);
            }
            Text.line(creator, Text.prefix() + Text.get("COINFLIP.JOINED-YOURS", "&f{player} &ajoined your &f{stake} &aflip! ",
                            "{player}", player.getName(), "{stake}", wallet.money(game.stake)),
                    free ? "" : Text.button(Text.get("COINFLIP.WATCH-BUTTON", "&e[Watch]"), "cf", "&7Open CoinFlip"));
        }
        refreshMenus();
    }

    // ------------------------------------------------------------------ clock

    private void startTicker() {
        if (ticker == null) {
            ticks = 0;
            ticker = plugin.getSpigotScheduler().runGlobalTimer(this::tick, 1L, 1L);
        }
    }

    private void tick() {
        ticks++;
        Iterator<Game> iterator = spinning.iterator();
        while (iterator.hasNext()) {
            Game game = iterator.next();
            if (game.state == State.DONE) {
                if (--game.lingerTicks <= 0) {
                    iterator.remove();
                }
                continue;
            }
            if (--game.wait > 0) {
                continue;
            }
            game.position++;
            game.screen.step(game.position, game.delays.size());
            if (game.position >= game.delays.size()) {
                settle(game);
                continue;
            }
            game.wait = game.delays.get(game.position);
        }
        if (ticks % 20 == 0 && !open.isEmpty()) {
            long now = System.currentTimeMillis();
            boolean changed = false;
            for (Game game : new ArrayList<>(open.values())) {
                if (now - game.createdAt > expireMillis()) {
                    refundOpen(game, Text.get("COINFLIP.EXPIRED", "nobody joined in time"));
                    changed = true;
                }
            }
            if (changed) {
                refreshMenus();
            }
        }
        if (spinning.isEmpty() && open.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    // ------------------------------------------------------------------ endings

    /** Pays the winner of a spun game. Runs once per game. */
    private void settle(Game game) {
        if (game.state == State.DONE) {
            return;
        }
        game.state = State.DONE;
        game.lingerTicks = 60;
        UUID winner = game.winnerId();
        String winnerName = game.winnerName();
        String winnerHold = game.winner == 0 ? game.creatorHold : game.joinerHold;
        boolean paid = wallet.pay(winner, winnerName, game.payout, EconomyReason.GAMBLE_PAYOUT, "CoinFlip win:");
        for (String hold : new String[]{game.creatorHold, game.joinerHold}) {
            if (hold.equals(winnerHold) && !paid) {
                escrow.update(hold, game.payout);
            } else {
                escrow.close(hold);
            }
        }
        game.screen.showWinner();
        UUID loser = game.winner == 0 ? game.joiner : game.creator;
        Player winnerPlayer = Bukkit.getPlayer(winner);
        Player loserPlayer = Bukkit.getPlayer(loser);
        if (winnerPlayer != null) {
            Text.title(winnerPlayer, Text.get("COINFLIP.WIN-TITLE", "&a&lYOU WON!"), "&f+" + wallet.money(game.payout - game.stake), 5, 50, 15);
            winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
            winnerPlayer.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, winnerPlayer.getLocation().add(0, 1.2, 0), 25, 0.5, 0.5, 0.5, 0);
            Text.send(winnerPlayer, "COINFLIP.WON", "&aYou won the flip against &f{other} &aand got &f{payout}&a!",
                    "{other}", game.winner == 0 ? game.joinerName : game.creatorName, "{payout}", wallet.money(game.payout));
        }
        if (loserPlayer != null) {
            Text.title(loserPlayer, "&c" + winnerName, Text.get("COINFLIP.LOSE-SUBTITLE", "&7won the flip"), 5, 40, 10);
            loserPlayer.playSound(loserPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.8F);
            Text.send(loserPlayer, "COINFLIP.LOST", "&c{other} won the flip; you lost &f{stake}&c.",
                    "{other}", winnerName, "{stake}", wallet.money(game.stake));
        }
        long broadcastOver = StakeMath.fromEconomy(Math.max(0D, settings().getDouble("BROADCAST-OVER", 100_000D)));
        if (broadcastOver > 0L && game.stake * 2L >= broadcastOver) {
            Text.broadcast(Text.prefix() + Text.get("COINFLIP.BIG-WIN", "&6🪙 &f{winner} &6won a &f{pot} &6coin flip against &f{loser}&6!",
                    "{winner}", winnerName, "{pot}", wallet.money(game.stake * 2L),
                    "{loser}", game.winner == 0 ? game.joinerName : game.creatorName));
        }
    }

    /** Gives an open game's bet back and removes it. */
    private void refundOpen(Game game, String reason) {
        if (open.remove(game.id) == null) {
            return;
        }
        if (wallet.pay(game.creator, game.creatorName, game.stake, EconomyReason.GAMBLE_REFUND, "CoinFlip bet returned:")) {
            escrow.close(game.creatorHold);
        }
        Player creator = Bukkit.getPlayer(game.creator);
        if (creator != null && reason != null) {
            Text.send(creator, "COINFLIP.REFUNDED", "&7Your &f{stake} &7flip ended ({reason}); the money is back.",
                    "{stake}", wallet.money(game.stake), "{reason}", reason);
        }
    }

    void refreshMenus() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof CoinFlipMenu menu) {
                menu.refresh(online);
            }
        }
    }

    /** Server stopping: pay games already spinning, give open bets back. Nothing is scheduled. */
    void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (Game game : new ArrayList<>(spinning)) {
            settle(game);
        }
        spinning.clear();
        for (Game game : new ArrayList<>(open.values())) {
            refundOpen(game, null);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("join", "cancel", "help"));
            for (long bet : quickBets()) {
                options.add(java.math.BigDecimal.valueOf(bet, 2).stripTrailingZeros().toPlainString());
            }
            if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                options.add("cancelall");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (Game game : open.values()) {
                options.add(game.creatorName);
            }
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).distinct().toList();
    }
}
