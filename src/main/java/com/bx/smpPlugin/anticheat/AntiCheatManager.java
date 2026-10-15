package com.bx.smpPlugin.anticheat;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.ConfigValidator;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Runs the anti-cheat: decides whether a check applies to a player, turns flags into violation
 * levels, and handles alerts, evidence, corrections, kicks and the ban queue.
 */
public final class AntiCheatManager {

    public static final String ALERTS_PERMISSION = "smpplugin.security.alerts";
    public static final String INSPECT_PERMISSION = "smpplugin.security.inspect";
    public static final String MANAGE_PERMISSION = "smpplugin.security.manage";

    private static final String CONFIG_FILE = "anticheat.yml";
    /** A gap between two server ticks longer than this counts as server lag. */
    private static final long LAG_TICK_MILLIS = 150L;

    /**
     * Result of a flag.
     *
     * @param level   violation level after the flag
     * @param act     whether the caller should correct the player now (setback or cancel)
     * @param id      evidence ID
     */
    public record FlagResult(double level, boolean act, String id) {
        static final FlagResult NONE = new FlagResult(0D, false, "");
    }

    private final SmpPlugin plugin;
    private final File configFile;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<UUID> alertsMuted = ConcurrentHashMap.newKeySet();
    private final EvidenceLog evidence;
    private final BanQueue banQueue;
    private volatile AntiCheatConfig config;
    private BukkitTask tickMonitor;
    private BukkitTask banWaveTask;
    private long lastTickMillis;
    private long lastServerLagMillis;
    private volatile boolean deferToTitanGuard;
    private volatile boolean titanGuardPresent;
    private volatile long titanGuardCheckedAt;

    public AntiCheatManager(SmpPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.exists()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        YamlConfiguration initialYaml = YamlConfiguration.loadConfiguration(configFile);
        this.config = AntiCheatConfig.from(initialYaml);
        this.deferToTitanGuard = initialYaml.getBoolean("DEFER-TO-TITANGUARD", true);
        File folder = new File(plugin.getDataFolder(), "anticheat");
        this.evidence = new EvidenceLog(folder.toPath(), plugin.getLogger(),
                config.evidenceFileLogging(), config.evidenceMemoryPerPlayer());
        this.evidence.pruneOldFiles(config.evidenceRetentionDays());
        this.banQueue = new BanQueue(new File(folder, "ban-queue.yml"), plugin.getLogger());
    }

    /** Starts the server-lag monitor and the ban-wave timer. Call once after the plugin is enabled. */
    public void start() {
        lastTickMillis = System.currentTimeMillis();
        tickMonitor = plugin.getSpigotScheduler().runGlobalTimer(this::monitorTick, 1L, 1L);
        scheduleBanWave();
    }

    /**
     * Re-reads anticheat.yml. Keeps the current settings and returns the parse error when the file is invalid.
     */
    public Optional<String> reload() {
        Optional<String> problem = ConfigValidator.validateFile(configFile.toPath());
        if (problem.isPresent()) {
            return problem;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        AntiCheatConfig loaded = AntiCheatConfig.from(yaml);
        this.deferToTitanGuard = yaml.getBoolean("DEFER-TO-TITANGUARD", true);
        this.titanGuardCheckedAt = 0L;
        this.config = loaded;
        evidence.configure(loaded.evidenceFileLogging(), loaded.evidenceMemoryPerPlayer());
        scheduleBanWave();
        return Optional.empty();
    }

    public void shutdown() {
        if (tickMonitor != null) {
            tickMonitor.cancel();
        }
        if (banWaveTask != null) {
            banWaveTask.cancel();
        }
        evidence.shutdown();
        states.clear();
    }

    public AntiCheatConfig config() {
        return config;
    }

    public EvidenceLog evidence() {
        return evidence;
    }

    public boolean isActive() {
        FeatureManager features = plugin.getFeatureManager();
        return config.enabled() && (features == null || features.isEnabled(FeatureManager.Feature.ANTICHEAT))
                && !isDeferringToTitanGuard();
    }

    /**
     * True while the TitanGuard anti-cheat plugin is running and anticheat.yml DEFER-TO-TITANGUARD is
     * on, so the two anti-cheats never set back or punish the same player twice. The plugin lookup is
     * cached for a few seconds because this runs on every move event.
     */
    public boolean isDeferringToTitanGuard() {
        if (!deferToTitanGuard) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - titanGuardCheckedAt > 5_000L) {
            titanGuardCheckedAt = now;
            titanGuardPresent = plugin.getServer().getPluginManager().isPluginEnabled("TitanGuard");
        }
        return titanGuardPresent;
    }

    PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> {
            PlayerState state = new PlayerState();
            state.bedrock = detectBedrock(player.getUniqueId());
            state.lastSafe = player.getLocation();
            state.groundY = player.getLocation().getY();
            return state;
        });
    }

    void forget(Player player) {
        states.remove(player.getUniqueId());
    }

    /** True while the server itself recently stalled; timing-based checks pause so lag is never blamed on players. */
    boolean recentServerLag(long nowMillis) {
        return nowMillis - lastServerLagMillis < config.serverLagGraceMillis();
    }

    private void monitorTick() {
        long now = System.currentTimeMillis();
        if (now - lastTickMillis > LAG_TICK_MILLIS) {
            lastServerLagMillis = now;
        }
        lastTickMillis = now;
    }

    /**
     * Whether {@code check} should run for {@code player} right now. Creative and spectator players, players
     * inside an exemption window, Bedrock players for movement/aim checks, and holders of the narrow bypass
     * permission are skipped. Bypass use is logged once per session.
     */
    boolean shouldCheck(Player player, CheckType check, long nowMillis) {
        if (!isActive()) {
            return false;
        }
        CheckSettings settings = config.settings(check);
        if (settings == null || !settings.enabled()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        PlayerState state = state(player);
        if (state.exemptions.isExempt(check, nowMillis)) {
            return false;
        }
        if (state.bedrock && config.skipForBedrock(check)) {
            return false;
        }
        String bypass = check.category().bypassPermission();
        if (PermissionUtils.hasExact(player, bypass)) {
            if (state.bypassLogged.add(check.category())) {
                plugin.getLogger().info("[AntiCheat] " + player.getName() + " is exempt from "
                        + check.category().name().toLowerCase(Locale.ROOT) + " checks via " + bypass + ".");
            }
            return false;
        }
        return true;
    }

    /**
     * Records a failed check.
     *
     * @param weight how strong this piece of evidence is (added to the violation level)
     * @param detail short human-readable explanation with the measured values
     */
    FlagResult flag(Player player, CheckType check, double weight, String detail, long nowMillis) {
        CheckSettings settings = config.settings(check);
        PlayerState state = state(player);
        double level = state.buffer(check).add(weight, settings.decayPerSecond(), nowMillis);
        boolean act = settings.shouldAct(level);
        boolean ban = config.banMode() != AntiCheatConfig.BanMode.OFF && settings.shouldBan(level);
        boolean kick = (config.kicksEnabled() && settings.shouldKick(level)) || (ban && config.kickOnQueue());

        String id = evidence.nextId();
        Location location = player.getLocation();
        String action = ban ? "ban-queue" : kick ? "kick" : act ? "corrected" : "none";
        int ping = safePing(player);
        evidence.record(new EvidenceLog.Flag(id, player.getUniqueId(), player.getName(), check, detail, level, ping,
                location.getWorld() == null ? "?" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), nowMillis, action));

        if (level >= settings.alertAt()) {
            Long last = state.lastAlertMillis.get(check);
            if (last == null || nowMillis - last >= config.alertCooldownMillis() || kick || ban) {
                state.lastAlertMillis.put(check, nowMillis);
                alert(player, check, detail, level, ping, id, location);
            }
            escalateIfManyChecks(player, state, check, id, nowMillis);
        }

        if (ban) {
            queueBan(player, check, id, check.displayName() + " (" + detail + ")");
            state.buffer(check).reset();
        }
        if (kick) {
            kick(player, id);
            state.buffer(check).reset();
        }
        return new FlagResult(level, act, id);
    }

    /** Several different checks at alert level in a short window is much stronger evidence than any one alone. */
    private void escalateIfManyChecks(Player player, PlayerState state, CheckType check, String id, long nowMillis) {
        if (!config.multiCheckEnabled()) {
            return;
        }
        state.recentAlertLevel.put(check, nowMillis);
        state.recentAlertLevel.values().removeIf(time -> nowMillis - time > config.multiCheckWindowMillis());
        int distinct = state.recentAlertLevel.size();
        if (distinct < config.multiCheckDistinct() || state.escalated) {
            return;
        }
        state.escalated = true;
        String summary = distinct + " different checks within "
                + config.multiCheckWindowMillis() / 60_000L + " min: " + String.join(", ",
                state.recentAlertLevel.keySet().stream().map(CheckType::displayName).toList());
        broadcastToStaff(ColorUtils.colorize("&8[&cAC&8] &4&lMULTI-CHECK &f" + player.getName() + " &7" + summary + " &8#" + id));
        if (config.banMode() != AntiCheatConfig.BanMode.OFF) {
            queueBan(player, check, id, summary);
            if (config.kickOnQueue()) {
                kick(player, id);
            }
        }
    }

    private void kick(Player player, String id) {
        String message = ColorUtils.colorize(config.kickMessage().replace("{id}", id));
        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (player.isOnline()) {
                player.kickPlayer(message);
            }
        });
    }

    private void queueBan(Player player, CheckType check, String id, String reason) {
        BanQueue.Entry entry = new BanQueue.Entry(player.getUniqueId(), player.getName(), check, id, reason,
                System.currentTimeMillis());
        if (!banQueue.add(entry)) {
            return;
        }
        String mode = config.banMode() == AntiCheatConfig.BanMode.AUTO
                ? "will be banned in the next ban wave" : "is waiting for staff approval (/anticheat bans)";
        broadcastToStaff(ColorUtils.colorize("&8[&cAC&8] &c" + player.getName() + " &7reached the ban level for &c"
                + check.displayName() + " &7and " + mode + ". &8#" + id));
        plugin.getLogger().warning("[AntiCheat] Ban queued for " + player.getName() + ": " + reason + " #" + id);
    }

    // ── Ban queue (used by /anticheat bans) ─────────────────────────────────────────

    public Collection<BanQueue.Entry> queuedBans() {
        return banQueue.entries();
    }

    /** Executes the queued ban for this player name. Returns false when nobody by that name is queued. */
    public boolean approveBan(String playerName, String staffName) {
        Optional<BanQueue.Entry> entry = banQueue.findByName(playerName);
        if (entry.isEmpty()) {
            return false;
        }
        banQueue.remove(entry.get().playerId());
        executeBan(entry.get(), staffName);
        return true;
    }

    /** Removes a player from the queue without banning. */
    public boolean denyBan(String playerName, String staffName) {
        Optional<BanQueue.Entry> entry = banQueue.findByName(playerName);
        if (entry.isEmpty()) {
            return false;
        }
        banQueue.remove(entry.get().playerId());
        plugin.getLogger().info("[AntiCheat] " + staffName + " removed " + entry.get().playerName() + " from the ban queue.");
        return true;
    }

    /** Executes every queued ban now. Returns how many ran. */
    public int runBanWave(String actor) {
        List<BanQueue.Entry> entries = banQueue.drain();
        for (BanQueue.Entry entry : entries) {
            executeBan(entry, actor);
        }
        if (!entries.isEmpty()) {
            broadcastToStaff(ColorUtils.colorize("&8[&cAC&8] &cBan wave: &f" + entries.size() + " &7player(s) banned."));
        }
        return entries.size();
    }

    private void executeBan(BanQueue.Entry entry, String actor) {
        String command = config.banCommand()
                .replace("{player}", entry.playerName())
                .replace("{uuid}", entry.playerId().toString())
                .replace("{check}", entry.check().displayName())
                .replace("{id}", entry.evidenceId());
        plugin.getLogger().warning("[AntiCheat] Ban for " + entry.playerName() + " (" + entry.reason()
                + ", #" + entry.evidenceId() + ") executed by " + actor + ": /" + command);
        plugin.getSpigotScheduler().runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private void scheduleBanWave() {
        if (banWaveTask != null) {
            banWaveTask.cancel();
            banWaveTask = null;
        }
        if (config.banMode() != AntiCheatConfig.BanMode.AUTO) {
            return;
        }
        long ticks = Math.max(20L * 60L, config.banWaveMillis() / 50L);
        banWaveTask = plugin.getSpigotScheduler().runGlobalTimer(() -> runBanWave("ban wave"), ticks, ticks);
    }

    // ── Queries ───────────────────────────────────────────────────────────────────

    public double violationLevel(Player player, CheckType check) {
        return state(player).buffer(check).current(config.settings(check).decayPerSecond(), System.currentTimeMillis());
    }

    /** The client brand reported at join, or null when the server API or client did not provide one. */
    public String clientBrand(Player player) {
        return state(player).clientBrand;
    }

    public boolean isBedrock(Player player) {
        return state(player).bedrock;
    }

    /** Short automatic or staff-granted grace period for one category. */
    public void exempt(Player player, CheckCategory category, long millis) {
        state(player).exemptions.exempt(category, millis, System.currentTimeMillis());
    }

    public void exempt(Player player, CheckType check, long millis) {
        state(player).exemptions.exempt(check, millis, System.currentTimeMillis());
    }

    public void resetViolations(Player player) {
        state(player).resetViolations();
    }

    public boolean toggleAlerts(UUID staff) {
        if (!alertsMuted.remove(staff)) {
            alertsMuted.add(staff);
            return false;
        }
        return true;
    }

    public boolean alertsEnabled(UUID staff) {
        return !alertsMuted.contains(staff);
    }

    /** Number of currently enabled checks. */
    public int enabledCheckCount() {
        int count = 0;
        for (CheckType type : CheckType.values()) {
            if (config.settings(type).enabled()) {
                count++;
            }
        }
        return count;
    }

    private void alert(Player player, CheckType check, String detail, double level, int ping, String id, Location location) {
        String message = config.alertFormat()
                .replace("{player}", player.getName())
                .replace("{check}", check.displayName())
                .replace("{detail}", detail)
                .replace("{vl}", String.format(Locale.ROOT, "%.1f", level))
                .replace("{ping}", String.valueOf(ping))
                .replace("{id}", id)
                .replace("{world}", location.getWorld() == null ? "?" : location.getWorld().getName())
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()));
        broadcastToStaff(ColorUtils.colorize(message));
    }

    private void broadcastToStaff(String colored) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (alertsEnabled(online.getUniqueId()) && PermissionUtils.has(online, ALERTS_PERMISSION)) {
                online.sendMessage(colored);
            }
        }
        if (config.consoleAlerts()) {
            plugin.getLogger().info(ChatColor.stripColor(colored));
        }
    }

    static int safePing(Player player) {
        try {
            return Math.max(0, player.getPing());
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    /** Floodgate is optional; look it up by name so the plugin loads without it. */
    private boolean detectBedrock(UUID playerId) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(instance, playerId));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            plugin.getLogger().log(Level.FINE, "Floodgate lookup failed", error);
            return false;
        }
    }
}
