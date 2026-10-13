package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMessages;
import com.bx.smpPlugin.coinflip.CoinFlipMetrics;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.coinflip.RandomSource;
import com.bx.smpPlugin.coinflip.StakeMath;
import com.bx.smpPlugin.coinflip.storage.CoinFlipRepository;
import com.bx.smpPlugin.managers.ConfigValidator;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Starts, reloads and stops CoinFlip. Owns the configuration snapshot, the storage worker, the service, the
 * presentation layer and the few repeating tasks (expiry sweep, leaderboard refresh, history retention).
 */
public final class CoinFlipModule implements CoinFlipModuleView {

    public static final String PERM_USE = "smpplugin.coinflip.use";
    public static final String PERM_CREATE = "smpplugin.coinflip.create";
    public static final String PERM_JOIN = "smpplugin.coinflip.join";
    public static final String PERM_CHALLENGE = "smpplugin.coinflip.challenge";
    public static final String PERM_PRACTICE = "smpplugin.coinflip.practice";
    public static final String PERM_ALERTS = "smpplugin.coinflip.alerts";
    public static final String PERM_INSPECT = "smpplugin.coinflip.inspect";
    public static final String PERM_ADMIN_CANCEL = "smpplugin.coinflip.admin.cancel";
    public static final String PERM_ADMIN_RECOVER = "smpplugin.coinflip.admin.recover";
    public static final String PERM_ADMIN_RESOLVE = "smpplugin.coinflip.admin.resolve";
    public static final String PERM_ADMIN_RELOAD = "smpplugin.coinflip.admin.reload";
    public static final String PERM_ADMIN_TOGGLE = "smpplugin.coinflip.admin.toggle";
    public static final String PERM_ADMIN_RESET = "smpplugin.coinflip.admin.resetstats";

    private static final String CONFIG_FILE = "coinflip.yml";
    private static final String MESSAGES_FILE = "coinflip-messages.yml";

    private final SmpPlugin plugin;
    private final CoinFlipMetrics metrics = new CoinFlipMetrics();
    private volatile CoinFlipConfig config;
    private volatile CoinFlipMessages messages;
    private CoinFlipRepository repository;
    private CoinFlipService service;
    private CoinFlipPresenter presenter;
    private CoinFlipAnimator animator;
    private CoinFlipLeaderboards leaderboards;
    private CoinFlipWebhook webhook;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile String startupProblem;

    public CoinFlipModule(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads configuration and starts storage; wagering opens once recovery has finished. */
    public void start() {
        saveDefault(CONFIG_FILE);
        saveDefault(MESSAGES_FILE);
        loadFiles();
        boolean mySql = plugin.getDatabaseManager().isMySql();
        repository = new CoinFlipRepository(() -> plugin.getDatabaseManager().openDedicatedConnection(), mySql,
                config.queryTimeoutSeconds(), config.maxPendingStorageOperations(), plugin.getLogger());
        animator = new CoinFlipAnimator(plugin, this);
        presenter = new CoinFlipPresenter(plugin, this);
        webhook = new CoinFlipWebhook(plugin.getLogger(), () -> config);
        service = new CoinFlipService(repository, new SmpEconomyGateway(plugin), presenter, RandomSource.secure(),
                runnable -> plugin.getSpigotScheduler().runGlobal(runnable),
                (ticks, task) -> {
                    if (ticks <= 0L) {
                        plugin.getSpigotScheduler().runGlobal(task);
                    } else {
                        plugin.getSpigotScheduler().runGlobalLater(task, ticks);
                    }
                },
                System::currentTimeMillis, ZoneId.systemDefault(), () -> config, this::playtimeMinutes,
                plugin.getLogger(), metrics);
        leaderboards = new CoinFlipLeaderboards(repository, () -> config, ZoneId.systemDefault(), plugin.getLogger());

        repository.initialize().whenComplete((ignored, error) -> plugin.getSpigotScheduler().runGlobal(() -> {
            if (error != null) {
                startupProblem = "storage failed to start: " + error.getMessage();
                plugin.getLogger().log(Level.SEVERE, "[CoinFlip] Storage failed to start; wagering stays off.", error);
                return;
            }
            service.recoverAtStartup().whenComplete((report, recoveryError) -> {
                if (recoveryError != null) {
                    startupProblem = "startup recovery failed: " + recoveryError.getMessage();
                    plugin.getLogger().log(Level.SEVERE, "[CoinFlip] Startup recovery failed; wagering stays off.", recoveryError);
                    return;
                }
                plugin.getLogger().info("[CoinFlip] Ready. Recovery: " + report.refunded() + " refunded, " + report.settled()
                        + " paid out, " + report.unknownTransactions() + " unknown, " + report.heldForReview()
                        + " held for review. Wagering " + (service.wageringOpen() ? "open." : "paused."));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    service.playerJoined(online.getUniqueId());
                }
                leaderboards.refresh();
            });
        }));

        tasks.add(plugin.getSpigotScheduler().runGlobalTimer(() -> {
            if (service.recovered()) {
                service.sweepExpired();
            }
        }, 20L, 20L));
        tasks.add(plugin.getSpigotScheduler().runGlobalTimer(leaderboards::refreshIfDue, 200L, 200L));
        tasks.add(plugin.getSpigotScheduler().runGlobalTimer(this::pruneHistory, 20L * 60L * 10L, 20L * 60L * 60L));
        animator.start();
    }

    /**
     * Re-reads coinflip.yml and coinflip-messages.yml. A YAML syntax error keeps the running settings. Active matches,
     * tasks and storage are untouched; settings marked (restart) in the file apply after a restart.
     */
    public Optional<String> reload() {
        for (String file : new String[]{CONFIG_FILE, MESSAGES_FILE}) {
            Optional<String> problem = ConfigValidator.validateFile(new File(plugin.getDataFolder(), file).toPath());
            if (problem.isPresent()) {
                return Optional.of(file + ": " + problem.get());
            }
        }
        loadFiles();
        return Optional.empty();
    }

    public void shutdown() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        if (animator != null) {
            animator.stopAll();
        }
        if (repository != null) {
            repository.shutdown(10);
        }
    }

    private void loadFiles() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), CONFIG_FILE));
        CoinFlipConfig loaded = CoinFlipConfig.from(yaml);
        List<String> problems = new ArrayList<>(loaded.problems());
        for (String material : new String[]{loaded.headsMaterial(), loaded.tailsMaterial(), loaded.borderMaterial(),
                loaded.accentMaterial()}) {
            Material resolved = Material.matchMaterial(material);
            if (resolved == null || !resolved.isItem()) {
                problems.add("GUI.MATERIALS: expected an item material, found " + material + " -> a default is used");
            }
        }
        for (String problem : problems) {
            plugin.getLogger().warning("[CoinFlip] coinflip.yml " + problem);
        }
        if (loaded.wageringBlocked()) {
            plugin.getLogger().severe("[CoinFlip] Wagering is paused until the money settings above are fixed.");
        }
        this.config = loaded;
        this.messages = CoinFlipMessages.from(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), MESSAGES_FILE)));
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private void pruneHistory() {
        int days = config.historyRetentionDays();
        if (days <= 0 || !repository.isReady()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        repository.transaction(dao -> dao.pruneHistory(cutoff)).thenAccept(removed -> {
            if (removed > 0) {
                plugin.getLogger().info("[CoinFlip] Removed " + removed + " finished match(es) older than " + days + " days.");
            }
        });
    }

    private long playtimeMinutes(UUID player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return 0L;
        }
        try {
            return data.getPlaytimeSeconds() / 60L;
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    @Override
    public String money(long cents) {
        return plugin.getCurrencyManager().formatMoney(StakeMath.toEconomy(cents));
    }

    public SmpPlugin plugin() {
        return plugin;
    }

    public CoinFlipConfig config() {
        return config;
    }

    public CoinFlipMessages messages() {
        return messages;
    }

    public CoinFlipService service() {
        return service;
    }

    public CoinFlipPresenter presenter() {
        return presenter;
    }

    public CoinFlipAnimator animator() {
        return animator;
    }

    public CoinFlipLeaderboards leaderboards() {
        return leaderboards;
    }

    public CoinFlipWebhook webhook() {
        return webhook;
    }

    public CoinFlipMetrics metrics() {
        return metrics;
    }

    public CoinFlipRepository repository() {
        return repository;
    }

    public String startupProblem() {
        return startupProblem;
    }
}
