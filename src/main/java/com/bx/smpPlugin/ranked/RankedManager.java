package com.bx.smpPlugin.ranked;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.DuelManager;
import com.bx.smpPlugin.models.DuelMatch;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Ranked PvP: the ranked queue, Elo ratings and the ranked leaderboard.
 *
 * <p>Matches are ordinary duels started with the {@link DuelMatch.MatchType#RANKED} type, so the
 * duel system keeps doing what it already does (arenas, countdown, inventory restore, disconnects).
 * When a ranked duel finishes, {@link DuelManager} hands the result here and both ratings change.
 *
 * <p>Ratings of online players are kept in memory and read on the database thread when they join.
 * The queue, matchmaking and rating changes all run on the main thread; database writes are queued
 * on the database thread in order.
 */
public final class RankedManager implements Listener {

    /** Everything read from ranked.yml. */
    record Settings(boolean enabled, int startRating, Elo.Settings elo, Matchmaker.Tolerance tolerance,
                    int intervalSeconds, int maxGamesPerPair, long pairWindowMillis, boolean blockSameIp,
                    int minMatchSeconds, double winMoney, long winShards, int leaderboardSize,
                    int leaderboardRefreshSeconds, List<Map.Entry<Integer, String>> tiers) {

        static Settings read(FileConfiguration config) {
            FileConfiguration c = config == null ? new org.bukkit.configuration.file.YamlConfiguration() : config;
            int floor = Math.max(0, c.getInt("RATING.FLOOR", 100));
            Elo.Settings elo = new Elo.Settings(
                    clamp(c.getInt("RATING.K-FACTOR", 24), 1, 200),
                    clamp(c.getInt("RATING.K-PROVISIONAL", 40), 1, 200),
                    clamp(c.getInt("RATING.PROVISIONAL-GAMES", 10), 0, 1000),
                    clamp(c.getInt("RATING.K-HIGH", 16), 1, 200),
                    c.getInt("RATING.HIGH-RATING", 2000),
                    floor);
            Matchmaker.Tolerance tolerance = new Matchmaker.Tolerance(
                    clamp(c.getInt("MATCHMAKING.BASE-TOLERANCE", 100), 0, 10_000),
                    clamp(c.getInt("MATCHMAKING.GROWTH-PER-10-SECONDS", 25), 0, 10_000),
                    clamp(c.getInt("MATCHMAKING.MAX-TOLERANCE", 600), 0, 10_000));
            List<Map.Entry<Integer, String>> tiers = new ArrayList<>();
            for (Map<?, ?> tier : c.getMapList("TIERS")) {
                Object min = tier.get("MIN");
                Object name = tier.get("NAME");
                if (min instanceof Number number && name != null) {
                    tiers.add(Map.entry(number.intValue(), name.toString()));
                }
            }
            tiers.sort((a, b) -> Integer.compare(b.getKey(), a.getKey()));
            return new Settings(
                    c.getBoolean("ENABLED", true),
                    Math.max(floor, c.getInt("RATING.START", 1000)),
                    elo,
                    tolerance,
                    clamp(c.getInt("MATCHMAKING.INTERVAL-SECONDS", 2), 1, 60),
                    Math.max(0, c.getInt("ANTI-BOOST.MAX-GAMES-PER-PAIR", 3)),
                    Math.max(0L, c.getLong("ANTI-BOOST.PAIR-WINDOW-HOURS", 24L)) * 3_600_000L,
                    c.getBoolean("ANTI-BOOST.BLOCK-SAME-IP", true),
                    Math.max(0, c.getInt("ANTI-BOOST.MIN-MATCH-SECONDS", 15)),
                    Math.max(0D, c.getDouble("REWARDS.WIN-MONEY", 0D)),
                    Math.max(0L, c.getLong("REWARDS.WIN-SHARDS", 0L)),
                    clamp(c.getInt("LEADERBOARD.SIZE", 100), 1, 1000),
                    clamp(c.getInt("LEADERBOARD.REFRESH-SECONDS", 60), 10, 3600),
                    List.copyOf(tiers));
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private final SmpPlugin plugin;
    private final RankedRepository repository;
    private final Map<UUID, RankedRepository.Profile> profiles = new ConcurrentHashMap<>();
    /** Waiting players in join order. Main thread only. */
    private final Map<UUID, Matchmaker.Ticket> queue = new LinkedHashMap<>();
    private final PairHistory pairs = new PairHistory(0L);
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile List<RankedRepository.Profile> leaderboard = List.of();
    private volatile Map<UUID, Integer> positions = Map.of();
    private Settings settings;
    private boolean running;

    public RankedManager(SmpPlugin plugin) {
        this.plugin = plugin;
        this.repository = new RankedRepository(() -> plugin.getDatabaseManager().getConnection());
        this.settings = Settings.read(plugin.getConfigManager().getRanked());
    }

    public void start() {
        running = true;
        settings = Settings.read(plugin.getConfigManager().getRanked());
        pairs.setWindowMillis(settings.pairWindowMillis());
        plugin.getDuelManager().setMatchFinishListener(this::onDuelFinished);
        for (Player online : Bukkit.getOnlinePlayers()) {
            loadProfile(online);
        }
        long since = System.currentTimeMillis() - settings.pairWindowMillis();
        plugin.getDatabaseManager().supplyAsync(() -> repository.pairsSince(since))
                .whenComplete((played, error) -> plugin.getSpigotScheduler().runGlobal(() -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not read recent ranked matches", error);
                        return;
                    }
                    played.forEach(pair -> pairs.record(pair.first(), pair.second(), pair.endedAt()));
                }));
        scheduleTasks();
        refreshLeaderboard();
    }

    private void scheduleTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        long matchmaking = settings.intervalSeconds() * 20L;
        tasks.add(plugin.getSpigotScheduler().runGlobalTimer(this::matchmake, matchmaking, matchmaking));
        long refresh = settings.leaderboardRefreshSeconds() * 20L;
        tasks.add(plugin.getSpigotScheduler().runGlobalTimer(this::refreshLeaderboard, refresh, refresh));
    }

    public void reload() {
        settings = Settings.read(plugin.getConfigManager().getRanked());
        pairs.setWindowMillis(settings.pairWindowMillis());
        if (running) {
            scheduleTasks();
            if (!settings.enabled()) {
                clearQueue("&eRanked PvP was turned off; you left the ranked queue.");
            }
        }
    }

    public void stop() {
        running = false;
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (plugin.getDuelManager() != null) {
            plugin.getDuelManager().setMatchFinishListener(null);
        }
        queue.clear();
        profiles.clear();
        pairs.clear();
    }

    public boolean isEnabled() {
        return running && settings.enabled() && plugin.getDuelManager() != null && plugin.getDuelManager().isEnabled();
    }

    // ------------------------------------------------------------------ profiles

    private void loadProfile(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        plugin.getDatabaseManager().supplyAsync(() -> repository.load(uuid))
                .whenComplete((loaded, error) -> plugin.getSpigotScheduler().runGlobal(() -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not load the ranked rating of " + name, error);
                        return;
                    }
                    profiles.put(uuid, loaded.orElseGet(() ->
                            RankedRepository.Profile.fresh(uuid, name, settings.startRating())));
                }));
    }

    /** The player's ranked record, or null while it is still loading (or for offline players). */
    public RankedRepository.Profile profile(UUID uuid) {
        return uuid == null ? null : profiles.get(uuid);
    }

    /** Any player's record, online or not, read on the database thread when needed. */
    public CompletableFuture<RankedRepository.Profile> profileAnywhere(UUID uuid, String name) {
        RankedRepository.Profile cached = profile(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return plugin.getDatabaseManager().supplyAsync(() -> repository.load(uuid)
                .orElse(RankedRepository.Profile.fresh(uuid, name, settings.startRating())));
    }

    public CompletableFuture<List<RankedRepository.MatchRow>> history(UUID uuid, int limit) {
        return plugin.getDatabaseManager().supplyAsync(() -> repository.history(uuid, limit));
    }

    public List<RankedRepository.Profile> leaderboard() {
        return leaderboard;
    }

    /** 1-based leaderboard position, or 0 when not on it. */
    public int position(UUID uuid) {
        Integer position = positions.get(uuid);
        return position == null ? 0 : position;
    }

    public String tier(int rating) {
        for (Map.Entry<Integer, String> tier : settings.tiers()) {
            if (rating >= tier.getKey()) {
                return tier.getValue();
            }
        }
        return "&7Unranked";
    }

    public int startRating() {
        return settings.startRating();
    }

    private void refreshLeaderboard() {
        int size = settings.leaderboardSize();
        plugin.getDatabaseManager().supplyAsync(() -> repository.top(size)).whenComplete((top, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not refresh the ranked leaderboard", error);
                return;
            }
            Map<UUID, Integer> newPositions = new HashMap<>();
            for (int i = 0; i < top.size(); i++) {
                newPositions.put(top.get(i).player(), i + 1);
            }
            leaderboard = List.copyOf(top);
            positions = Collections.unmodifiableMap(newPositions);
        });
    }

    /** Sets a rating (admin). Saved at once; the player keeps their win and loss counts. */
    public void setRating(UUID uuid, String name, int rating, Runnable done) {
        int value = Math.max(settings.elo().floor(), rating);
        profileAnywhere(uuid, name).thenAccept(profile -> {
            RankedRepository.Profile updated = profile.withRating(value, System.currentTimeMillis());
            plugin.getDatabaseManager().executeAsync(() -> repository.save(updated));
            plugin.getSpigotScheduler().runGlobal(() -> {
                if (profiles.containsKey(uuid)) {
                    profiles.put(uuid, updated);
                }
                refreshLeaderboard();
                done.run();
            });
        });
    }

    /** Deletes a player's ranked record (admin): they start over at the starting rating. */
    public void reset(UUID uuid, String name, Runnable done) {
        plugin.getDatabaseManager().executeAsync(() -> repository.delete(uuid));
        plugin.getSpigotScheduler().runGlobal(() -> {
            if (profiles.containsKey(uuid)) {
                profiles.put(uuid, RankedRepository.Profile.fresh(uuid, name, settings.startRating()));
            }
            refreshLeaderboard();
            done.run();
        });
    }

    // ------------------------------------------------------------------ queue

    public boolean isQueued(UUID uuid) {
        return queue.containsKey(uuid);
    }

    public int queueSize() {
        return queue.size();
    }

    /** Puts a player in the ranked queue. Returns false with a message when they cannot join. */
    public boolean join(Player player) {
        if (!isEnabled()) {
            send(player, "&cRanked PvP is not available right now.");
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (queue.containsKey(uuid)) {
            send(player, "&eYou are already in the ranked queue.");
            return false;
        }
        RankedRepository.Profile profile = profiles.get(uuid);
        if (profile == null) {
            send(player, "&cYour rating is still loading. Try again in a moment.");
            return false;
        }
        if (!plugin.getDuelManager().canStartDuel(player, true)) {
            return false;
        }
        if (!plugin.getDuelManager().hasFreeQueueArena()) {
            send(player, "&cNo duel arena is free for ranked matches right now.");
            return false;
        }
        queue.put(uuid, new Matchmaker.Ticket(uuid, profile.rating(), System.currentTimeMillis()));
        send(player, "&aYou joined the ranked queue &7(rating &f" + profile.rating() + "&7, "
                + queue.size() + " waiting).");
        matchmake();
        return true;
    }

    public boolean leave(Player player, boolean quiet) {
        boolean removed = queue.remove(player.getUniqueId()) != null;
        if (!quiet) {
            send(player, removed ? "&eYou left the ranked queue." : "&cYou are not in the ranked queue.");
        }
        return removed;
    }

    private void clearQueue(String message) {
        for (UUID uuid : List.copyOf(queue.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                send(player, message);
            }
        }
        queue.clear();
    }

    private void matchmake() {
        if (!isEnabled() || queue.size() < 2) {
            return;
        }
        // Players who went somewhere else (a casual duel, FFA, offline) drop out of the queue.
        queue.keySet().removeIf(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            return player == null || !player.isOnline() || !plugin.getDuelManager().canStartDuel(player, false);
        });
        long now = System.currentTimeMillis();
        List<Matchmaker.Pair> pairsToStart = Matchmaker.pair(new ArrayList<>(queue.values()), settings.tolerance(), now,
                (first, second) -> mayMeet(first, second, now));
        for (Matchmaker.Pair pair : pairsToStart) {
            if (!plugin.getDuelManager().hasFreeQueueArena()) {
                return;
            }
            Player first = Bukkit.getPlayer(pair.first().player());
            Player second = Bukkit.getPlayer(pair.second().player());
            if (first == null || second == null) {
                continue;
            }
            queue.remove(first.getUniqueId());
            queue.remove(second.getUniqueId());
            if (!plugin.getDuelManager().startRankedMatch(first, second)) {
                // Not started (a player became busy): both go back to the front of the queue.
                queue.put(first.getUniqueId(), pair.first());
                queue.put(second.getUniqueId(), pair.second());
                continue;
            }
            send(first, "&6&lRanked &7match found against &f" + second.getName() + " &7(" + pair.second().rating() + ").");
            send(second, "&6&lRanked &7match found against &f" + first.getName() + " &7(" + pair.first().rating() + ").");
        }
    }

    private boolean mayMeet(UUID first, UUID second, long now) {
        if (settings.maxGamesPerPair() > 0 && pairs.count(first, second, now) >= settings.maxGamesPerPair()) {
            return false;
        }
        if (settings.blockSameIp()) {
            String a = address(Bukkit.getPlayer(first));
            String b = address(Bukkit.getPlayer(second));
            return a == null || !a.equals(b);
        }
        return true;
    }

    private static String address(Player player) {
        InetSocketAddress socket = player == null ? null : player.getAddress();
        return socket == null || socket.getAddress() == null ? null : socket.getAddress().getHostAddress();
    }

    // ------------------------------------------------------------------ results

    private void onDuelFinished(DuelManager.FinishedMatch match) {
        if (match.type() != DuelMatch.MatchType.RANKED || match.cancelled()) {
            if (match.type() == DuelMatch.MatchType.RANKED) {
                recordUnrated(match, "cancelled");
            }
            return;
        }
        RankedRepository.Profile one = profiles.get(match.playerOne());
        RankedRepository.Profile two = profiles.get(match.playerTwo());
        if (one == null || two == null) {
            recordUnrated(match, "rating not loaded");
            return;
        }
        long now = System.currentTimeMillis();
        String unrated = null;
        if (match.durationSeconds() < settings.minMatchSeconds()) {
            unrated = "shorter than " + settings.minMatchSeconds() + "s";
        } else if (settings.maxGamesPerPair() > 0
                && pairs.count(match.playerOne(), match.playerTwo(), now) >= settings.maxGamesPerPair()) {
            unrated = "too many games against each other";
        }
        pairs.record(match.playerOne(), match.playerTwo(), now);
        if (unrated != null) {
            recordUnrated(match, unrated);
            tell(match.playerOne(), "&7This ranked match did not count: " + unrated + ".");
            tell(match.playerTwo(), "&7This ranked match did not count: " + unrated + ".");
            return;
        }

        Elo.Outcome outcome = match.winner() == null ? Elo.Outcome.DRAW
                : match.winner().equals(match.playerOne()) ? Elo.Outcome.PLAYER_ONE_WINS : Elo.Outcome.PLAYER_TWO_WINS;
        Elo.Result result = Elo.rate(one.rating(), one.games(), two.rating(), two.games(), outcome, settings.elo());
        Elo.Outcome twoOutcome = outcome == Elo.Outcome.DRAW ? Elo.Outcome.DRAW
                : outcome == Elo.Outcome.PLAYER_ONE_WINS ? Elo.Outcome.PLAYER_TWO_WINS : Elo.Outcome.PLAYER_ONE_WINS;
        RankedRepository.Profile newOne = one.afterMatch(match.playerOneName(), result.playerOneAfter(), outcome, now);
        RankedRepository.Profile newTwo = two.afterMatch(match.playerTwoName(), result.playerTwoAfter(), twoOutcome, now);
        profiles.put(newOne.player(), newOne);
        profiles.put(newTwo.player(), newTwo);
        RankedRepository.MatchRow row = new RankedRepository.MatchRow(match.id(), match.playerOne(), match.playerOneName(),
                match.playerTwo(), match.playerTwoName(), match.winner(), match.endReason(), true, null,
                one.rating(), newOne.rating(), two.rating(), newTwo.rating(), match.durationSeconds(), now);
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                repository.save(newOne);
                repository.save(newTwo);
                repository.insertMatch(row);
            } catch (IllegalStateException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save ranked match " + match.id(), exception);
            }
        });

        tell(match.playerOne(), resultLine(one.rating(), newOne.rating()));
        tell(match.playerTwo(), resultLine(two.rating(), newTwo.rating()));
        if (match.winner() != null) {
            reward(match.winner());
        }
        // Players who left during the match are not kept in memory.
        forgetIfOffline(match.playerOne());
        forgetIfOffline(match.playerTwo());
    }

    private void recordUnrated(DuelManager.FinishedMatch match, String reason) {
        RankedRepository.Profile one = profiles.get(match.playerOne());
        RankedRepository.Profile two = profiles.get(match.playerTwo());
        int ratingOne = one == null ? settings.startRating() : one.rating();
        int ratingTwo = two == null ? settings.startRating() : two.rating();
        RankedRepository.MatchRow row = new RankedRepository.MatchRow(match.id(), match.playerOne(), match.playerOneName(),
                match.playerTwo(), match.playerTwoName(), match.winner(), match.endReason(), false, reason,
                ratingOne, ratingOne, ratingTwo, ratingTwo, match.durationSeconds(), System.currentTimeMillis());
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                repository.insertMatch(row);
            } catch (IllegalStateException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not record ranked match " + match.id(), exception);
            }
        });
        forgetIfOffline(match.playerOne());
        forgetIfOffline(match.playerTwo());
    }

    private void reward(UUID winner) {
        Player player = Bukkit.getPlayer(winner);
        if (player == null) {
            return;
        }
        if (settings.winMoney() > 0D) {
            plugin.getEconomyManager().deposit(player, settings.winMoney(), EconomyReason.RANKED_REWARD);
        }
        if (settings.winShards() > 0L) {
            plugin.getEconomyManager().depositShards(winner, player.getName(), settings.winShards(), EconomyReason.RANKED_REWARD);
        }
    }

    static String resultLine(int before, int after) {
        int change = after - before;
        String sign = change > 0 ? "&a+" + change : change < 0 ? "&c" + change : "&7±0";
        return "&6&lRanked &7rating: &f" + after + " &8(" + sign + "&8)";
    }

    private void forgetIfOffline(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) {
            profiles.remove(uuid);
            queue.remove(uuid);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        loadProfile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        queue.remove(uuid);
        // A player in a ranked duel keeps their record until the duel's result arrives.
        if (plugin.getDuelManager() == null || !plugin.getDuelManager().isInDuel(uuid)) {
            profiles.remove(uuid);
        }
    }

    // ------------------------------------------------------------------ placeholders

    /** Values for %smp_elo%, %smp_ranked_wins% and the rest; null when the name is not a ranked one. */
    public String placeholder(UUID uuid, String key) {
        RankedRepository.Profile profile = uuid == null ? null : profiles.get(uuid);
        int rating = profile == null ? settings.startRating() : profile.rating();
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "elo", "ranked_rating", "ranked_elo" -> Integer.toString(rating);
            case "ranked_wins" -> Integer.toString(profile == null ? 0 : profile.wins());
            case "ranked_losses" -> Integer.toString(profile == null ? 0 : profile.losses());
            case "ranked_draws" -> Integer.toString(profile == null ? 0 : profile.draws());
            case "ranked_games" -> Integer.toString(profile == null ? 0 : profile.games());
            case "ranked_peak" -> Integer.toString(profile == null ? rating : profile.peak());
            case "ranked_tier" -> tier(rating);
            case "ranked_position" -> {
                int position = position(uuid);
                yield position == 0 ? "-" : Integer.toString(position);
            }
            case "ranked_queue" -> Integer.toString(queueSize());
            default -> null;
        };
    }

    private void tell(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            send(player, message);
        }
    }

    private void send(Player player, String message) {
        if (player != null) {
            player.sendMessage(ColorUtils.toComponent(Objects.requireNonNullElse(message, "")));
        }
    }

    /** For the tiers list in the menu. */
    List<Map.Entry<Integer, String>> tiers() {
        return settings.tiers();
    }

    ConfigurationSection config() {
        return plugin.getConfigManager().getRanked();
    }
}
