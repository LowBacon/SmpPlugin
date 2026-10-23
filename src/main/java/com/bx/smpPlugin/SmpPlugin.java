// SmpPlugin by LowBacon
package com.bx.smpPlugin;

import com.bx.smpPlugin.afk.AfkCommand;
import com.bx.smpPlugin.afk.AfkListener;
import com.bx.smpPlugin.afk.AfkManager;
import com.bx.smpPlugin.afk.AfkTask;
import com.bx.smpPlugin.afk.AfkZoneCommand;
import com.bx.smpPlugin.amethyst.*;
import com.bx.smpPlugin.api.EconomyExpansion;
import com.bx.smpPlugin.api.EconomyLeaderboardExpansion;
import com.bx.smpPlugin.api.EconomyRankExpansion;
import com.bx.smpPlugin.api.HideExpansion;
import com.bx.smpPlugin.api.SmpPluginExpansion;
import com.bx.smpPlugin.api.CustomSmpExpansion;
import com.bx.smpPlugin.api.FinnishSmpExpansion;
import com.bx.smpPlugin.api.FsmpExpansion;
import com.bx.smpPlugin.commands.*;
import com.bx.smpPlugin.hooks.VaultEconomyHook;
import com.bx.smpPlugin.listeners.*;
import com.bx.smpPlugin.managers.*;
import com.bx.smpPlugin.permissions.PermissionNode;
import com.bx.smpPlugin.permissions.PermissionRegistry;
import com.bx.smpPlugin.platform.BuildInfo;
import com.bx.smpPlugin.platform.Capability;
import com.bx.smpPlugin.platform.CapabilityStatus;
import com.bx.smpPlugin.platform.CompatibilityPolicy;
import com.bx.smpPlugin.platform.MinecraftVersion;
import com.bx.smpPlugin.platform.PlatformReport;
import com.bx.smpPlugin.platform.ServerPlatform;
import com.bx.smpPlugin.tasks.*;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.utils.PlayerSettingDefaults;
import com.bx.smpPlugin.utils.SpigotScheduler;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class SmpPlugin extends JavaPlugin {

    private static final String LOADED_IN_PROCESS_PROPERTY = "smpplugin.loaded-in-process";

    private static SmpPlugin instance;
    private SpigotScheduler SpigotScheduler;
    private boolean reloadedInProcess;
    private final List<String> failedListeners = new java.util.ArrayList<>();

    // ── Managers ──────────────────────────────────────────────────────────────
    private ConfigManager configManager;
    private com.bx.smpPlugin.worlds.WorldSystem worldSystem;
    private LanguageManager languageManager;
    private CurrencyManager currencyManager;
    private FeatureManager featureManager;
    private DatabaseManager databaseManager;
    private PlayerLogsManager playerLogsManager;
    private PlayerDataManager playerDataManager;
    private PlayerVisibilityManager playerVisibilityManager;
    private ExplosionParticleFilter explosionParticleFilter;
    private EconomyManager economyManager;
    private ChatManager chatManager;
    private IgnoreManager ignoreManager;
    private FriendsManager friendsManager;
    private PrivateMessageManager privateMessageManager;
    private TeamManager teamManager;
    private HomeManager homeManager;
    private BountyManager bountyManager;
    private WarpManager warpManager;
    private CuboidManager cuboidManager;
    private SpawnManager spawnManager;
    private LobbyManager lobbyManager;
    private CombatManager combatManager;
    private FastCrystalManager fastCrystalManager;
    private TPAManager tpaManager;
    private ShardManager shardManager;
    private ClearLagManager clearLagManager;
    private CrateManager crateManager;
    private CrateVisualManager crateVisualManager;
    private KeyAllManager keyAllManager;
    private AfkManager afkManager;
    private com.bx.smpPlugin.listeners.JoinPreloadListener joinPreloadListener;
    private HoverStatsManager hoverStatsManager;
    private WorthManager worthManager;
    private ShopManager shopManager;
    private OrdersManager ordersManager;
    private OrdersBedrockManager ordersBedrockManager;
    private EnchantmentsManager enchantmentsManager;
    private FilterManager filterManager;
    private DuelManager duelManager;
    private FfaManager ffaManager;
    private AuctionHouseManager auctionHouseManager;
    private AuctionOrderBotManager auctionOrderBotManager;
    private BillfordManager billfordManager;
    private LeaderboardManager leaderboardManager;
    private ScoreboardManager scoreboardManager;
    private TablistManager tablistManager;
    private TeleportManager teleportManager;
    private RTPManager rtpManager;
    private RTPZoneManager rtpZoneManager;
    private RTPQueueManager rtpQueueManager;
    private BalancedPriceManager balancedPriceManager;
    private FarmingMetaManager farmingMetaManager;
    private QuickBuyBedrockManager quickBuyBedrockManager;
    private QuickBuySearchPrompt quickBuySearchPrompt;
    private ServerNotificationManager serverNotificationManager;
    private PlayerSettingToggles playerSettingToggles;
    private com.bx.smpPlugin.core.V3Module v3Module;
    private NametagManager nametagManager;
    private DialogGateway dialogManager;
    private PortalManager portalManager;
    private AmethystToolsManager amethystToolsManager;
    private EnderChestManager enderChestManager;
    private FreezeManager freezeManager;
    private GodModeManager godModeManager;
    private InvseeManager invseeManager;
    private ProfileViewerManager profileViewerManager;
    private PunishmentManager punishmentManager;
    private VoiceMuteManager voiceMuteManager;
    private PlayerWipeManager playerWipeManager;
    private PlayerUnwipeManager playerUnwipeManager;
    private OffenseManager offenseManager;
    private StatsWipeManager statsWipeManager;
    private ServerWipeManager serverWipeManager;
    private SpawnerManager spawnerManager;
    private AntiEspManager antiEspManager;
    private com.bx.smpPlugin.hooks.TitanGuardBridge titanGuardBridge;
    private com.bx.smpPlugin.coinflip.bukkit.CoinFlipModule coinFlipModule;
    private SpawnStashManager spawnStashManager;
    private FakePlayerManager fakePlayerManager;
    private HideManager hideManager;
    private NetworkStatusManager networkStatusManager;
    private RedisManager redisManager;
    private MaintenanceManager maintenanceManager;
    private NetworkStaffChatManager networkStaffChatManager;
    private NetworkStaffAlertManager networkStaffAlertManager;
    private StaffModeManager staffModeManager;
    private DiscordWebhookManager discordWebhookManager;
    private LunarRichPresenceManager lunarRichPresenceManager;
    private LuckPermsTablistRefreshBridge luckPermsTablistRefreshBridge;
    private SkinsRestorerTablistRefreshBridge skinsRestorerTablistRefreshBridge;
    private OptimizationManager optimizationManager;
    private CrashProtectionManager crashProtectionManager;
    private AnvilModerationManager anvilModerationManager;
    private UpdateManager updateManager;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        detectInProcessReload();
        if (!checkMinecraftVersionSupport()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        instance = this;

        printStartupBanner();

        checkPlaceholderApi();

        SpigotScheduler = new SpigotScheduler(this);

        // 1. Config & database (no dependencies)
        ColorUtils.init();

        configManager = new ConfigManager(this);
        configManager.loadAll();
        List<ConfigValidator.Problem> configProblems = ConfigValidator.validateDirectory(getDataFolder());
        if (!configProblems.isEmpty()) {
            getLogger().severe("====================================================");
            getLogger().severe(configProblems.size() + " configuration file(s) could not be parsed and were loaded as EMPTY:");
            configProblems.forEach(problem -> getLogger().severe(" - " + problem.describe()));
            getLogger().severe("Features using these files run on built-in defaults until the files are fixed.");
            getLogger().severe("====================================================");
        }
        languageManager = new LanguageManager(this);
        languageManager.load();
        // The lobby, spawn and FFA worlds load first: the spawn and lobby read locations in them.
        worldSystem = new com.bx.smpPlugin.worlds.WorldSystem(this);
        worldSystem.start();
        crashProtectionManager = new CrashProtectionManager(this);
        currencyManager = new CurrencyManager(this);
        featureManager = new FeatureManager(this);
        optimizationManager = new OptimizationManager(this);
        optimizationManager.start();

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        if (!databaseManager.isReady()) {
            // Without storage every balance, home, item and stat change would vanish at the next restart, and
            // Vault would hand other plugins an economy that saves nothing. Refuse to run instead.
            getLogger().severe("====================================================");
            getLogger().severe("The database could not be started (see the error above), so SmpPlugin is disabling");
            getLogger().severe("itself. Nothing was changed. Fix database.yml (or the database server) and restart.");
            getLogger().severe("====================================================");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        playerLogsManager = new PlayerLogsManager(this);
        serverWipeManager = new ServerWipeManager(this);
        serverWipeManager.recoverOrRecreatePendingWorlds();

        // 2. Data managers (depend on DB / config)
        playerDataManager = new PlayerDataManager(this);
        playerVisibilityManager = new PlayerVisibilityManager(this);
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            explosionParticleFilter = new ExplosionParticleFilter(this);
        } else {
            getLogger().info("ProtocolLib is not installed; packet-based explosion particle filtering is disabled.");
        }
        economyManager = new EconomyManager(this);
        
        // Register Vault economy early so other plugins checking in their onEnable won't fail
        registerVaultEconomyProvider();
        
        chatManager = new ChatManager(this);
        ignoreManager = new IgnoreManager(this);
        friendsManager = new FriendsManager(this);
        privateMessageManager = new PrivateMessageManager(this);
        teamManager = new TeamManager(this);
        teamManager.loadAll();
        homeManager = new HomeManager(this);
        bountyManager = new BountyManager(this);
        bountyManager.loadAll();
        warpManager = new WarpManager(this);
        warpManager.loadAll();
        cuboidManager = new CuboidManager(this);
        cuboidManager.loadAll();

        spawnManager = new SpawnManager(this);
        spawnManager.load();
        lobbyManager = new LobbyManager(this);

        // 3. Gameplay managers
        combatManager = new CombatManager(this);
        fastCrystalManager = new FastCrystalManager(this);
        tpaManager = new TPAManager(this);
        shardManager = new ShardManager(this);
        amethystToolsManager = new AmethystToolsManager(this);
        clearLagManager = new ClearLagManager(this);
        crateManager = new CrateManager(this);
        crateVisualManager = new CrateVisualManager(this);
        keyAllManager = new KeyAllManager(this);
        afkManager = new AfkManager(this);
        hoverStatsManager = new HoverStatsManager(this);
        worthManager = new WorthManager(this);
        balancedPriceManager = new BalancedPriceManager(this);
        farmingMetaManager = new FarmingMetaManager(this);
        farmingMetaManager.load();
        shopManager = new ShopManager(this);
        quickBuySearchPrompt = new QuickBuySearchPrompt(this);
        filterManager = new FilterManager(this);
        enchantmentsManager = new EnchantmentsManager(this);
        ordersManager = new OrdersManager(this);
        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            try {
                ordersBedrockManager = new OrdersBedrockManager(this);
            } catch (LinkageError error) {
                getLogger().warning("Floodgate is present but its API could not be loaded; Orders will use Java GUIs.");
            }
            try {
                quickBuyBedrockManager = new QuickBuyBedrockManager(this);
            } catch (LinkageError error) {
                getLogger().warning("Floodgate is present but its API could not be loaded; Quick Buy will use the chest catalogue.");
            }
        }
        duelManager = new DuelManager(this);
        ffaManager = new FfaManager(this);
        auctionHouseManager = new AuctionHouseManager(this);
        auctionOrderBotManager = new AuctionOrderBotManager(this);
        serverNotificationManager = new ServerNotificationManager(this);
        billfordManager = new BillfordManager(this);
        billfordManager.load();
        leaderboardManager = new LeaderboardManager(this);
        leaderboardManager.warmUp();
        enderChestManager = new EnderChestManager(this);
        freezeManager = new FreezeManager(this);
        godModeManager = new GodModeManager();
        staffModeManager = new StaffModeManager(this);
        invseeManager = new InvseeManager(this);
        profileViewerManager = new ProfileViewerManager(this);
        punishmentManager = new PunishmentManager(this);
        voiceMuteManager = new VoiceMuteManager(this);
        voiceMuteManager.registerHook();
        playerWipeManager = new PlayerWipeManager(this);
        playerUnwipeManager = new PlayerUnwipeManager(this);
        offenseManager = new OffenseManager(this);
        anvilModerationManager = new AnvilModerationManager(this);
        anvilModerationManager.load();
        statsWipeManager = new StatsWipeManager(this);
        spawnerManager = new SpawnerManager(this);
        antiEspManager = new AntiEspManager(this);
        try {
            coinFlipModule = new com.bx.smpPlugin.coinflip.bukkit.CoinFlipModule(this);
            coinFlipModule.start();
        } catch (RuntimeException | LinkageError error) {
            coinFlipModule = null;
            getLogger().log(Level.SEVERE, "Could not start CoinFlip; it is off for this session. No currency was moved.", error);
        }
        spawnStashManager = new SpawnStashManager(this);
        fakePlayerManager = new FakePlayerManager(this);
        redisManager = new RedisManager(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        networkStatusManager = new NetworkStatusManager(this);
        networkStaffChatManager = new NetworkStaffChatManager(this);
        networkStaffAlertManager = new NetworkStaffAlertManager(this);
        ordersManager.initializeNetworkSync();
        maintenanceManager = new MaintenanceManager(this);
        maintenanceManager.initializeRedisListener();
        duelManager.initializeCrossServer();
        discordWebhookManager = new DiscordWebhookManager(this);
        initializeLunarRichPresenceManager();

        // 4. Display managers
        scoreboardManager = new ScoreboardManager(this);
        tablistManager = new TablistManager(this);
        hideManager = new HideManager(this);
        hideManager.loadAll();
        nametagManager = new NametagManager(this);
        nametagManager.start(getServer().getOnlinePlayers());
        teleportManager = new TeleportManager(this);
        rtpManager = new RTPManager(this);
        rtpZoneManager = new RTPZoneManager(this);
        rtpQueueManager = new RTPQueueManager(this);
        portalManager = new PortalManager(this);
        portalManager.loadAll();
        PlayerSettingDefaults.initialize();
        playerSettingToggles = new PlayerSettingToggles(this);
        // Dialog screens exist on Paper and Purpur only (not in the Spigot jar); otherwise chest menus are used.
        dialogManager = DialogGateway.create(this);
        initializeLuckPermsTablistRefreshBridge();
        initializeSkinsRestorerTablistRefreshBridge();

        // 5. Listeners
        registerListeners();

        // 6. Commands
        registerCommands();

        // 7. Background tasks
        ScoreboardTask.start(this);
        TablistTask.start(this);
        getSpigotScheduler().runGlobalTimer(() -> nametagManager.refreshAll(getServer().getOnlinePlayers()),
                nametagManager.updateTicks(), nametagManager.updateTicks());
        ShardTask.start(this); // passive "everywhere" shards (per minute)
        ShardCuboidTask.start(this); // spawn cuboid countdown + reward (per second)
        RTPZoneTask.start(this);
        RTPCacheTask.start(this);
        FarmingMetaTask.start(this); // farming meta rotation check (every 30 s)
        ClearLagTask.start(this);
        KeyAllTask.start(this);
        AutoSaveTask.start(this);
        AfkTask.start(this);
        LunarTeammatesTask.start(this);
        BillfordTask.start(this); // Billford trade rotation check (every 30 s)
        OrdersExpiryTask.start(this);
        AuctionHouseExpiryTask.start(this);
        AuctionOrderBotTask.start(this);
        AmethystToolsTask.start(this);
        SpawnerGenerationTask.start(this);
        DuelMatchTask.start(this);
        FfaMatchTask.start(this);

        // 8. PlaceholderAPI expansion
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new EconomyExpansion(this).register();
            new SmpPluginExpansion(this).register();
            new CustomSmpExpansion(this).register();
            new FinnishSmpExpansion(this).register();  // Legacy alias for compatibility
            new FsmpExpansion(this).register();
            new HideExpansion(this).register();
            new EconomyLeaderboardExpansion(this).register();
            new EconomyRankExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        if (maintenanceManager != null && !maintenanceManager.isMaintenanceActive()) {
            maintenanceManager.broadcastOnline();
        }

        updateManager = new UpdateManager(this);
        updateManager.checkForUpdates();

        // Off unless SHOP-GUI.WEB-SERVER.ENABLED is true in shop.yml; binds to 127.0.0.1 by default.
        com.bx.smpPlugin.managers.SellStatsExporter.startEmbeddedHttpServer(this);

        // 9. Everything added in 3.0 (calendar, daily rewards, events, extra commands).
        v3Module = new com.bx.smpPlugin.core.V3Module(this);
        v3Module.enable();

        syncCommands();
        verifyPermissionRegistration();
        logCapabilitySummary();
        getLogger().info("SmpPlugin enabled successfully.");
    }

    private void verifyPermissionRegistration() {
        List<String> missing = PermissionRegistry.all().stream()
                .map(PermissionNode::node)
                .filter(node -> getServer().getPluginManager().getPermission(node) == null)
                .toList();
        if (!missing.isEmpty()) {
            getLogger().severe(missing.size() + " permission(s) are not declared by plugin.yml, so Bukkit treats them"
                    + " as op-only: " + String.join(", ", missing)
                    + ". The jar was built from an out-of-date plugin.yml; rebuild it.");
        }
    }

    private void logCapabilitySummary() {
        List<Capability> capabilities;
        try {
            capabilities = PlatformReport.collect(this);
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Could not build the platform capability report.", exception);
            return;
        }

        getLogger().info("Platform capabilities (full report: /smpplugin status):");
        for (Capability capability : capabilities) {
            String line = " [" + capability.status() + "] " + capability.name() + ": " + capability.detail();
            if (capability.status() == CapabilityStatus.SUPPORTED) {
                getLogger().info(line);
            } else if (capability.status() == CapabilityStatus.UNAVAILABLE
                    && PlatformReport.isOptionalDependency(capability.name())) {
                // Missing optional plugins are expected; keep them out of the warning noise.
                getLogger().info(line);
            } else {
                getLogger().warning(line);
            }
        }
    }

    @Override
    public void onDisable() {
        if (nametagManager != null) {
            // The main scoreboard is saved with the world; the name tag teams must not be.
            nametagManager.shutdown();
        }
        if (v3Module != null) {
            v3Module.disable();
        }
        com.bx.smpPlugin.managers.SellStatsExporter.stopEmbeddedHttpServer();
        boolean suppressWipeSaves = serverWipeManager != null
                && serverWipeManager.shouldSuppressShutdownSaves();
        if (teleportManager != null) {
            teleportManager.restoreAllRtpChunkThrottles();
        }
        if (rtpQueueManager != null) {
            rtpQueueManager.shutdown();
        }
        if (afkManager != null) {
            afkManager.shutdown();
        }
        if (worthManager != null) {
            getServer().getOnlinePlayers().forEach(worthManager::clearWorthDisplay);
        }

        if (enderChestManager != null && !suppressWipeSaves) {
            enderChestManager.shutdown();
        }
        if (freezeManager != null) {
            freezeManager.shutdown();
        }
        if (staffModeManager != null) {
            staffModeManager.shutdown();
        }
        if (godModeManager != null) {
            godModeManager.clearAll();
        }
        if (invseeManager != null) {
            invseeManager.shutdown();
        }
        if (antiEspManager != null) {
            antiEspManager.shutdown();
        }
        if (coinFlipModule != null) {
            coinFlipModule.shutdown();
        }
        if (spawnStashManager != null) {
            spawnStashManager.shutdown();
        }
        if (fakePlayerManager != null) {
            fakePlayerManager.shutdown();
        }
        if (hideManager != null) {
            hideManager.shutdown();
        }
        if (explosionParticleFilter != null) {
            explosionParticleFilter.shutdown();
        }
        if (playerVisibilityManager != null) {
            playerVisibilityManager.clear();
        }
        if (networkStatusManager != null) {
            networkStatusManager.shutdown();
        }
        if (networkStaffChatManager != null) {
            networkStaffChatManager.shutdown();
        }
        if (networkStaffAlertManager != null) {
            networkStaffAlertManager.shutdown();
        }
        if (luckPermsTablistRefreshBridge != null) {
            luckPermsTablistRefreshBridge.shutdown();
        }
        if (skinsRestorerTablistRefreshBridge != null) {
            skinsRestorerTablistRefreshBridge.shutdown();
        }
        if (lunarRichPresenceManager != null) {
            lunarRichPresenceManager.shutdown();
        }
        if (optimizationManager != null) {
            optimizationManager.shutdown();
        }
        if (ordersManager != null) {
            ordersManager.shutdown();
        }
        if (redisManager != null) {
            redisManager.shutdown();
        }
        if (duelManager != null) {
            duelManager.shutdown();
        }
        if (ffaManager != null) {
            ffaManager.shutdown();
        }
        if (spawnerManager != null && !suppressWipeSaves) {
            spawnerManager.shutdown();
        }
        if (crateManager != null) {
            crateManager.clearAllSessions();
        }
        if (crateVisualManager != null) {
            crateVisualManager.shutdown();
        }
        if (privateMessageManager != null) {
            privateMessageManager.clear();
        }
        if (ignoreManager != null) {
            ignoreManager.clear();
        }
        if (portalManager != null) {
            portalManager.shutdown();
        }
        if (auctionHouseManager != null) {
            auctionHouseManager.shutdown();
        }
        if (shopManager != null) {
            shopManager.shutdown();
        }

        // Save all online players and close DB
        if (playerDataManager != null && !suppressWipeSaves) {
            playerDataManager.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (worldSystem != null) {
            worldSystem.stop();
        }
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("SmpPlugin disabled.");
    }

    // ── Startup Banner ─────────────────────────────────────────────────────────

    private void printStartupBanner() {
        var console = getServer().getConsoleSender();
        console.sendMessage("");
        console.sendMessage("§b§lSmpPlugin §7v" + getDescription().getVersion());
        console.sendMessage("§aPlugin enabled successfully.");
        console.sendMessage("");
    }

    // ── Registration helpers ──────────────────────────────────────────────────

    private boolean checkPlaceholderApi() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return true;
        }

        getLogger().warning("PlaceholderAPI is unavailable; placeholder expansion and external placeholders are disabled.");
        getLogger().warning("Core SmpPlugin features will continue with built-in values and fallbacks.");
        return false;
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        registerListener("PlayerContextListener", () -> new PlayerContextListener(this));
        registerListener("WorldListener", () -> new com.bx.smpPlugin.worlds.WorldListener(this, worldSystem));
        registerListener("WorldCommandGate", () -> new com.bx.smpPlugin.worlds.WorldCommandGate(this, worldSystem));
        joinPreloadListener = new com.bx.smpPlugin.listeners.JoinPreloadListener(this);
        registerListener("JoinPreloadListener", () -> joinPreloadListener);
        registerListener("PlayerJoinQuitListener", () -> new PlayerJoinQuitListener(this));
        registerListener("PlayerDeathListener", () -> new PlayerDeathListener(this));
        registerListener("ChatListener", () -> new ChatListener(this));
        registerListener("CombatListener", () -> new CombatListener(this));
        registerListener("GodModeListener", () -> new GodModeListener(this));
        registerListener("FastCrystalListener", () -> new FastCrystalListener(this));
        registerListener("ExplosionDamageListener", () -> new ExplosionDamageListener(this));
        registerListener("PlayerRespawnListener", () -> new PlayerRespawnListener(this));
        registerListener("PlayerMoveListener", () -> new PlayerMoveListener(this));
        registerListener("AfkListener", () -> new AfkListener(this));
        registerListener("LobbyListener", () -> new LobbyListener(this));
        if (pm.getPlugin("FancyNpcs") != null) {
            registerListener("LobbyNpcListener", () -> new LobbyNpcListener(this));
        }
        registerListener("PortalListener", () -> new PortalListener(this));
        registerListener("CuboidWandListener", () -> new CuboidWandListener(this));
        registerListener("InventoryClickListener", () -> new InventoryClickListener(this));
        registerListener("CrateChestListener", () -> new CrateChestListener(this));
        registerListener("EnderChestListener", () -> new EnderChestListener(this));
        registerListener("FreezeListener", () -> new FreezeListener(this));
        registerListener("StaffModeListener", () -> new StaffModeListener(this));
        registerListener("InvseeListener", () -> new InvseeListener(this));
        registerListener("PlayerStatsListener", () -> new PlayerStatsListener(this));
        registerListener("PhantomListener", () -> new PhantomListener(this));
        registerListener("MobSpawnListener", () -> new MobSpawnListener(this));
        registerListener("PlayerSettingEffectsListener", () -> new PlayerSettingEffectsListener(this));
        if (pm.isPluginEnabled("ProtocolLib")) {
            registerListener("WorthPacketDisplay", () -> new WorthPacketDisplay(this));
        } else {
            registerListener("WorthDisplayListener", () -> new WorthDisplayListener(this));
        }
        registerListener("DuelListener", () -> new DuelListener(this));
        registerListener("FfaListener", () -> new FfaListener(this));
        registerListener("AmethystToolsListener", () -> new AmethystToolsListener(this));
        registerListener("SpawnerBlockListener", () -> new SpawnerBlockListener(this));
        registerListener("SpawnerInteractListener", () -> new SpawnerInteractListener(this));
        registerListener("SpawnerVisibilityListener", () -> new SpawnerVisibilityListener(this));
        registerListener("SpawnStashListener", () -> new SpawnStashListener(this));
        registerListener("PunishmentCommandAliasListener", () -> new PunishmentCommandAliasListener(this));
        registerListener("AnvilModerationListener", () -> new AnvilModerationListener(this));
        registerListener("PlayerAdvancementDoneListener", () -> new PlayerAdvancementDoneListener(this));
        registerListener("ItemDropListener", () -> new ItemDropListener(this));
        // The anti-cheat is TitanGuard (a soft dependency, so it is already enabled here).
        titanGuardBridge = new com.bx.smpPlugin.hooks.TitanGuardBridge(this);
        titanGuardBridge.start();
        if (coinFlipModule != null) {
            registerListener("CoinFlipListener", () -> new com.bx.smpPlugin.coinflip.bukkit.CoinFlipListener(coinFlipModule));
        }
    }

    private void registerListener(String name, java.util.function.Supplier<org.bukkit.event.Listener> factory) {
        try {
            getServer().getPluginManager().registerEvents(factory.get(), this);
        } catch (RuntimeException | LinkageError error) {
            failedListeners.add(name);
            getLogger().log(Level.SEVERE, "Could not start " + name + "; the features it handles are off. "
                    + "Please report this together with the error below.", error);
        }
    }

    public List<String> getFailedListeners() {
        return List.copyOf(failedListeners);
    }

    private void registerCommands() {
        UniversalCommandTabCompleter universalCommandTabCompleter = new UniversalCommandTabCompleter(this);
        registerFallbackTabCompleters(universalCommandTabCompleter);

        // Team
        TeamCommand teamCmd = new TeamCommand(this);
        setExecutor("team", teamCmd, FeatureManager.Feature.TEAMS);
        ChatCommand chatCommand = new ChatCommand(this);
        ChatTabCompleter chatTabCompleter = new ChatTabCompleter();
        setExecutor("chat", chatCommand, FeatureManager.Feature.CHAT);
        setTabCompleter("chat", chatTabCompleter);
        IgnoreCommand ignoreCommand = new IgnoreCommand(this);
        IgnoreTabCompleter ignoreTabCompleter = new IgnoreTabCompleter(this);
        setExecutor("ignore", ignoreCommand, FeatureManager.Feature.IGNORE);
        setTabCompleter("ignore", ignoreTabCompleter);
        setExecutor("unignore", ignoreCommand, FeatureManager.Feature.IGNORE);
        setTabCompleter("unignore", ignoreTabCompleter);
        MessageCommand messageCommand = new MessageCommand(this);
        MessageTabCompleter messageTabCompleter = new MessageTabCompleter(this);
        setExecutor("msg", messageCommand, FeatureManager.Feature.MESSAGING);
        setTabCompleter("msg", messageTabCompleter);
        setExecutor("reply", messageCommand, FeatureManager.Feature.MESSAGING);
        setTabCompleter("reply", messageTabCompleter);
        setExecutor("pm", new PrivateMessageToggleCommand(this), FeatureManager.Feature.MESSAGING);

        // Homes
        HomeCommand homeCmd = new HomeCommand(this);
        setExecutor("home", homeCmd, FeatureManager.Feature.HOMES);
        setExecutor("homes", homeCmd, FeatureManager.Feature.HOMES);
        setExecutor("sethome", homeCmd, FeatureManager.Feature.HOMES);
        setExecutor("delhome", homeCmd, FeatureManager.Feature.HOMES);
        setExecutor("renamehome", homeCmd, FeatureManager.Feature.HOMES);

        // Spawn / AFK
        setExecutor("spawn", new SpawnCommand(this), FeatureManager.Feature.SPAWN);
        setExecutor("setspawn", new SetSpawnCommand(this), FeatureManager.Feature.SPAWN);
        setExecutor("setlobby", new SetLobbyCommand(this), FeatureManager.Feature.LOBBY);
        AfkCommand afkCommand = new AfkCommand(this);
        setExecutor("afk", afkCommand, FeatureManager.Feature.AFK);
        setTabCompleter("afk", afkCommand);
        setExecutor("away", afkCommand, FeatureManager.Feature.AFK);
        AfkZoneCommand afkZoneCommand = new AfkZoneCommand(this);
        setExecutor("afkzone", afkZoneCommand, FeatureManager.Feature.AFK);
        setTabCompleter("afkzone", afkZoneCommand);

        // Teleport
        TPACommand tpaCmd = new TPACommand(this);
        setExecutor("tpa", tpaCmd, FeatureManager.Feature.TPA);
        setExecutor("tpahere", tpaCmd, FeatureManager.Feature.TPA);
        setExecutor("tpaccept", tpaCmd, FeatureManager.Feature.TPA);
        setExecutor("tpadeny", tpaCmd, FeatureManager.Feature.TPA);
        setExecutor("tpacancel", tpaCmd, FeatureManager.Feature.TPA);
        setExecutor("tpauto", new TPAutoCommand(this), FeatureManager.Feature.TPA, FeatureManager.Feature.TPA_AUTO);
        setExecutor("tpahereauto", new TPAHereAutoCommand(this), FeatureManager.Feature.TPA,
                FeatureManager.Feature.TPA_AUTO);

        // Economy
        BalanceCommand balCmd = new BalanceCommand(this);
        setExecutor("balance", balCmd);
        setExecutor("pay", new PayCommand(this));
        BaltopCommand baltopCommand = new BaltopCommand(this);
        setExecutor("baltop", baltopCommand, FeatureManager.Feature.LEADERBOARDS);
        setTabCompleter("baltop", baltopCommand);
        TransactionsCommand transactionsCommand = new TransactionsCommand(this);
        setExecutor("transactions", transactionsCommand);
        setTabCompleter("transactions", transactionsCommand);
        EcoCommand ecoCommand = new EcoCommand(this);
        setExecutor("eco", ecoCommand);
        setTabCompleter("eco", ecoCommand);
        setExecutor("addmoney", new AddMoneyCommand(this));
        setExecutor("removemoney", new RemoveMoneyCommand(this));
        setExecutor("setmoney", new SetMoneyCommand(this));
        setExecutor("logs", new LogsCommand(this));

        ShardsCommand shardsCmd = new ShardsCommand(this);
        setExecutor("shards", shardsCmd, FeatureManager.Feature.SHARDS);
        setExecutor("shardpay", new ShardPayCommand(this), FeatureManager.Feature.SHARDS);
        ShardAdminCommand shardAdminCommand = new ShardAdminCommand(this);
        setExecutor("addshards", shardAdminCommand, FeatureManager.Feature.SHARDS);
        setExecutor("removeshards", shardAdminCommand, FeatureManager.Feature.SHARDS);
        setExecutor("setshards", shardAdminCommand, FeatureManager.Feature.SHARDS);
        CrateCommand crateCmd = new CrateCommand(this);
        setExecutor("crate", crateCmd, FeatureManager.Feature.CRATES);
        setExecutor("crates", crateCmd, FeatureManager.Feature.CRATES);
        setExecutor("keys", crateCmd, FeatureManager.Feature.CRATES);
        setTabCompleter("crate", crateCmd);
        setTabCompleter("crates", crateCmd);
        setTabCompleter("keys", crateCmd);

        // Shop / Sell / Worth
        ShopCommand shopCommand = new ShopCommand(this);
        setExecutor("shop", shopCommand, FeatureManager.Feature.SHOP);
        setExecutor("shardshop", shopCommand, FeatureManager.Feature.SHOP, FeatureManager.Feature.SHARDS);
        setExecutor("orders", new OrdersCommand(this), FeatureManager.Feature.ORDERS);
        setExecutor("duel", new DuelCommand(this), FeatureManager.Feature.DUELS);
        setExecutor("create", new CreateCommand(this), FeatureManager.Feature.DUELS);
        setExecutor("queue", new QueueCommand(this), FeatureManager.Feature.DUELS);
        setExecutor("leave", new LeaveCommand(this));
        setExecutor("draw", new DrawCommand(this), FeatureManager.Feature.DUELS);
        setExecutor("arena", new ArenaCommand(this), FeatureManager.Feature.DUELS);
        setExecutor("ffa", new FfaCommand(this), FeatureManager.Feature.FFA);
        setExecutor("ffastats", new FfaStatsCommand(this), FeatureManager.Feature.FFA);
        setExecutor("ffaarena", new FfaArenaCommand(this), FeatureManager.Feature.FFA);
        AuctionHouseCommand auctionHouseCommand = new AuctionHouseCommand(this);
        setExecutor("ah", auctionHouseCommand, FeatureManager.Feature.AUCTION_HOUSE);
        setTabCompleter("ah", auctionHouseCommand);
        setExecutor("enderchest", new EnderChestCommand(this), FeatureManager.Feature.ENDER_CHEST);
        setExecutor("ecsee", new EcseeCommand(this), FeatureManager.Feature.ENDER_CHEST);
        SellCommand sellCmd = new SellCommand(this);
        setExecutor("sell", sellCmd, FeatureManager.Feature.SELL);
        setExecutor("sellhand", sellCmd, FeatureManager.Feature.SELL);
        setExecutor("sellall", sellCmd, FeatureManager.Feature.SELL);
        setExecutor("sellhistory", sellCmd, FeatureManager.Feature.SELL);
        setExecutor("sellmulti", sellCmd, FeatureManager.Feature.SELL);
        setTabCompleter("sellmulti", sellCmd);
        setExecutor("sellmultiplier", sellCmd, FeatureManager.Feature.SELL);
        setTabCompleter("sellmultiplier", sellCmd);
        setExecutor("sellprogress", sellCmd, FeatureManager.Feature.SELL);
        setTabCompleter("sellprogress", sellCmd);
        SellStatsCommand sellStatsCmd = new SellStatsCommand(this);
        setExecutor("topsell", sellStatsCmd, FeatureManager.Feature.SELL);
        setTabCompleter("topsell", sellStatsCmd);
        setExecutor("worth", new WorthCommand(this), FeatureManager.Feature.SELL, FeatureManager.Feature.WORTH);
        setExecutor("meta", new MetaCommand(this), FeatureManager.Feature.SELL, FeatureManager.Feature.WORTH);

        // RTP
        setExecutor("rtp", new RTPCommand(this), FeatureManager.Feature.RTP);
        setExecutor("rtpq", new RTPQueueCommand(this), FeatureManager.Feature.RTP);
        LobbyCommand lobbyCommand = new LobbyCommand(this);
        setExecutor("lobby", lobbyCommand, FeatureManager.Feature.LOBBY);

        // Stats / Leaderboard
        setExecutor("stats", new StatsCommand(this), FeatureManager.Feature.STATS);
        setExecutor("menu", new com.bx.smpPlugin.commands.MenuCommand(this));
        setExecutor("ping", new PingCommand(this), FeatureManager.Feature.STATS);
        setExecutor("playtime", new PlaytimeCommand(this), FeatureManager.Feature.STATS);
        AddPlaytimeCommand addPlaytimeCommand = new AddPlaytimeCommand(this);
        setExecutor("addplaytime", addPlaytimeCommand);
        setTabCompleter("addplaytime", addPlaytimeCommand);
        AddKillsCommand addKillsCommand = new AddKillsCommand(this);
        setExecutor("addkills", addKillsCommand);
        setTabCompleter("addkills", addKillsCommand);
        AddDeathsCommand addDeathsCommand = new AddDeathsCommand(this);
        setExecutor("adddeaths", addDeathsCommand);
        setTabCompleter("adddeaths", addDeathsCommand);
        SetKillsCommand setKillsCommand = new SetKillsCommand(this);
        setExecutor("setkills", setKillsCommand);
        setTabCompleter("setkills", setKillsCommand);
        SetDeathsCommand setDeathsCommand = new SetDeathsCommand(this);
        setExecutor("setdeaths", setDeathsCommand);
        setTabCompleter("setdeaths", setDeathsCommand);
        SetPlaytimeCommand setPlaytimeCommand = new SetPlaytimeCommand(this);
        setExecutor("setplaytime", setPlaytimeCommand);
        setTabCompleter("setplaytime", setPlaytimeCommand);
        LeaderboardCommand lbCmd = new LeaderboardCommand(this);
        setExecutor("leaderboard", lbCmd, FeatureManager.Feature.LEADERBOARDS);
        setExecutor("freeze", new FreezeCommand(this));
        setExecutor("fly", new FlyCommand(this));
        setExecutor("kill", new KillCommand(this));
        setExecutor("heal", new HealCommand(this));
        setExecutor("feed", new FeedCommand(this));
        registerGodModeCommand(new GodModeCommand(this));
        GamemodeCommand gamemodeCommand = new GamemodeCommand(this);
        setExecutor("gamemode", gamemodeCommand, FeatureManager.Feature.GAMEMODE);
        setTabCompleter("gamemode", gamemodeCommand);
        setExecutor("staffmode", new StaffModeCommand(this));
        setExecutor("stafflist", new StaffListCommand(this), FeatureManager.Feature.STAFF_MODE);
        setExecutor("staffchat", new StaffChatCommand(this), FeatureManager.Feature.STAFF_CHAT);
        setExecutor("helpop", new HelpopCommand(this), FeatureManager.Feature.STAFF_ALERTS);
        setExecutor("report", new ReportCommand(this), FeatureManager.Feature.STAFF_ALERTS);
        setExecutor("rename", new RenameCommand(this));
        setExecutor("randomteleport", new RandomTeleportCommand(this));
        setExecutor("teleport", new TeleportCommand(this));
        setExecutor("alts", new AltsCommand(this));
        setExecutor("vanish", new VanishCommand(this), FeatureManager.Feature.STAFF_MODE);
        setExecutor("invsee", new InvseeCommand(this));
        setExecutor("profileviewer", new ProfileViewerCommand(this), FeatureManager.Feature.PROFILE_VIEWER);
        setExecutor("punishments", new PunishmentHistoryCommand(this), FeatureManager.Feature.PUNISHMENTS);
        com.bx.smpPlugin.commands.ChatLogCommand chatLogCommand = new com.bx.smpPlugin.commands.ChatLogCommand(this);
        setExecutor("chatlog", chatLogCommand);
        setTabCompleter("chatlog", chatLogCommand);
        com.bx.smpPlugin.commands.SeeHomesCommand seeHomesCommand = new com.bx.smpPlugin.commands.SeeHomesCommand(this);
        setExecutor("seehomes", seeHomesCommand, FeatureManager.Feature.HOMES);
        setTabCompleter("seehomes", seeHomesCommand);
        PunishmentCommand punishmentCommand = new PunishmentCommand(this);
        setExecutor("ban", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("tempban", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("mute", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("tempmute", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("warn", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("kick", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("blacklist", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("unban", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("unmute", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("unblacklist", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("vcmute", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        setExecutor("vcunmute", punishmentCommand, FeatureManager.Feature.PUNISHMENTS);
        com.bx.smpPlugin.commands.PlayerWipeCommand playerWipeCommand = new com.bx.smpPlugin.commands.PlayerWipeCommand(this);
        setExecutor("playerwipe", playerWipeCommand);
        setTabCompleter("playerwipe", playerWipeCommand);
        com.bx.smpPlugin.commands.PlayerUnwipeCommand playerUnwipeCommand = new com.bx.smpPlugin.commands.PlayerUnwipeCommand(this);
        setExecutor("playerunwipe", playerUnwipeCommand);
        setTabCompleter("playerunwipe", playerUnwipeCommand);
        OffendCommand offendCommand = new OffendCommand(this);
        setExecutor("offend", offendCommand, FeatureManager.Feature.PUNISHMENTS);
        setTabCompleter("offend", offendCommand);

        // Bounty
        setExecutor("bounty", new BountyCommand(this), FeatureManager.Feature.BOUNTY);

        // Warps
        WarpCommand warpCmd = new WarpCommand(this);
        WarpManagerCommand warpManagerCmd = new WarpManagerCommand(this);
        WarpTabCompleter warpTabCompleter = new WarpTabCompleter(this);
        setExecutor("warp", warpCmd, FeatureManager.Feature.WARPS);
        setTabCompleter("warp", warpTabCompleter);
        setExecutor("warpmanager", warpManagerCmd, FeatureManager.Feature.WARPS);
        setTabCompleter("warpmanager", warpTabCompleter);
        setExecutor("setwarp", warpManagerCmd, FeatureManager.Feature.WARPS);
        setTabCompleter("setwarp", warpTabCompleter);
        setExecutor("delwarp", warpManagerCmd, FeatureManager.Feature.WARPS);
        setTabCompleter("delwarp", warpTabCompleter);

        PortalManagerCommand portalManagerCmd = new PortalManagerCommand(this);
        PortalTabCompleter portalTabCompleter = new PortalTabCompleter(this);
        setExecutor("portalmanager", portalManagerCmd, FeatureManager.Feature.PORTALS);
        setTabCompleter("portalmanager", portalTabCompleter);

        // Misc toggles
        setExecutor("nightvision", new NightVisionCommand(this), FeatureManager.Feature.NIGHT_VISION);
        setExecutor("phantom", new PhantomCommand(this), FeatureManager.Feature.PHANTOM);
        setExecutor("findplayer", new FindPlayerCommand(this), FeatureManager.Feature.FIND_PLAYER);
        setExecutor("settings", new SettingsCommand(this), FeatureManager.Feature.SETTINGS);

        // Social / info
        SocialCommand socialCmd = new SocialCommand(this);
        setExecutor("discord", socialCmd, FeatureManager.Feature.SOCIAL);
        setExecutor("twitter", socialCmd, FeatureManager.Feature.SOCIAL);
        setExecutor("store", socialCmd, FeatureManager.Feature.SOCIAL);
        setExecutor("social", socialCmd, FeatureManager.Feature.SOCIAL);

        setExecutor("rules", new RulesCommand(this), FeatureManager.Feature.RULES);
        setExecutor("safety", new SafetyCommand(this), FeatureManager.Feature.SAFETY);

        FriendsCommand friendsCommand = new FriendsCommand(this);
        setExecutor("friends", friendsCommand, FeatureManager.Feature.FRIENDS);
        setExecutor("friend", friendsCommand, FeatureManager.Feature.FRIENDS);
        setTabCompleter("friends", new FriendsTabCompleter(this));
        setTabCompleter("friend", new FriendsTabCompleter(this));
        setExecutor("help", new HelpCommand(this), FeatureManager.Feature.HELP);
        setExecutor("servers", new ServersCommand(this), FeatureManager.Feature.NETWORK_SERVERS);

        // Billford
        setExecutor("billford", new BillfordCommand(this), FeatureManager.Feature.BILLFORD);
        setExecutor("spawner", new SpawnerCommand(this), FeatureManager.Feature.SPAWNERS);
        setExecutor("spawnstash", new SpawnStashCommand(this), FeatureManager.Feature.SPAWN_STASH);
        setExecutor("fakeplayer", new FakePlayerCommand(this), FeatureManager.Feature.STAFF_MODE);
        HideCommand hideCommand = new HideCommand(this);
        setExecutor("hide", hideCommand, FeatureManager.Feature.HIDE);
        setExecutor("disguise", hideCommand, FeatureManager.Feature.HIDE);
        setTabCompleter("hide", hideCommand);
        setTabCompleter("disguise", hideCommand);

        // Admin
        setExecutor("clearlag", new ClearLagCommand(this), FeatureManager.Feature.CLEAR_LAG);
        setExecutor("cuboid", new CuboidCommand(this), FeatureManager.Feature.CUBOIDS);
        AmethystToolCommand amethystToolCommand = new AmethystToolCommand(this);
        setExecutor("amethysttool", amethystToolCommand, FeatureManager.Feature.AMETHYST_TOOLS);
        setTabCompleter("amethysttool", amethystToolCommand);
        SmpPluginCommand smpPluginCommand = new SmpPluginCommand(this);
        // csmp and customsmp are plugin.yml aliases of this command.
        setExecutor("smpplugin", smpPluginCommand);
        setTabCompleter("smpplugin", smpPluginCommand);
        MaintenanceCommand maintenanceCommand = new MaintenanceCommand(this);
        setExecutor("maintenance", maintenanceCommand, FeatureManager.Feature.MAINTENANCE);
        setTabCompleter("maintenance", maintenanceCommand);
        ServerWipeCommand serverWipeCommand = new ServerWipeCommand(this);
        setExecutor("serverwipe", serverWipeCommand);
        setTabCompleter("serverwipe", serverWipeCommand);

        AnvilModerationCommand anvilModCommand = new AnvilModerationCommand(this);
        setExecutor("amod", anvilModCommand);
        setTabCompleter("amod", anvilModCommand);

        if (coinFlipModule != null) {
            setExecutor("coinflip", new com.bx.smpPlugin.coinflip.bukkit.CoinFlipCommand(coinFlipModule), FeatureManager.Feature.COINFLIP);
            com.bx.smpPlugin.coinflip.bukkit.CoinFlipAdminCommand coinFlipAdmin = new com.bx.smpPlugin.coinflip.bukkit.CoinFlipAdminCommand(coinFlipModule);
            setExecutor("coinflipadmin", coinFlipAdmin);
            setTabCompleter("coinflipadmin", coinFlipAdmin);
        }
    }

    private void setExecutor(String commandName, CommandExecutor executor, FeatureManager.Feature... requiredFeatures) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().warning("Command missing from plugin.yml: " + commandName);
            return;
        }

        if (requiredFeatures == null || requiredFeatures.length == 0) {
            command.setExecutor(executor);
            return;
        }

        FeatureCommandExecutor featureExecutor = new FeatureCommandExecutor(this, executor, requiredFeatures);
        command.setExecutor(featureExecutor);
        if (executor instanceof TabCompleter) {
            command.setTabCompleter(featureExecutor);
        }
    }

    private void registerGodModeCommand(GodModeCommand executor) {
        PluginCommand command = getCommand("god");
        if (command != null) {
            command.setExecutor(executor);
            return;
        }

        if (!executor.registerDynamically()) {
            getLogger().warning("Command missing from plugin.yml and dynamic registration failed: god");
        }
    }

    @SuppressWarnings("unused")
    private void setTabCompleter(String commandName, TabCompleter tabCompleter) {
        PluginCommand pluginCommand = getCommand(commandName);
        if (pluginCommand == null) {
            getLogger().warning("Command missing from plugin.yml: " + commandName);
            return;
        }
        pluginCommand.setTabCompleter((sender, tabCommand, alias, args) -> {
            if (!isTabCompletionFeatureEnabled(tabCommand.getName())) {
                return java.util.Collections.emptyList();
            }
            var completions = tabCompleter.onTabComplete(sender, tabCommand, alias, args);
            return completions == null ? java.util.Collections.emptyList() : completions;
        });
    }

    private void registerFallbackTabCompleters(TabCompleter tabCompleter) {
        for (String commandName : getDescription().getCommands().keySet()) {
            PluginCommand command = getCommand(commandName);
            if (command == null) {
                getLogger().warning("Command missing from plugin.yml: " + commandName);
                continue;
            }
            command.setTabCompleter(tabCompleter);
        }
    }

    private boolean isTabCompletionFeatureEnabled(String commandName) {
        if (featureManager == null) {
            return true;
        }
        for (FeatureManager.Feature feature : FeatureManager.featuresForCommand(commandName)) {
            if (feature != null && !featureManager.isEnabled(feature)) {
                return false;
            }
        }
        return true;
    }

    private void registerVaultEconomyProvider() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        getServer().getServicesManager().register(
                net.milkbowl.vault.economy.Economy.class,
                new VaultEconomyHook(this),
                this,
                ServicePriority.Highest);
        getLogger().info("Vault economy provider registered.");
    }

    public void initializeLunarRichPresenceManager() {
        if (!featureManager.isEnabled(FeatureManager.Feature.LUNAR_RICH_PRESENCE)
                || !configManager.getConfig().getBoolean("LUNAR-CLIENT.RICH-PRESENCE.ENABLED", true)) {
            return;
        }

        if (!isClassAvailable("com.lunarclient.apollo.Apollo")) {
            getLogger().warning("Lunar Rich Presence is enabled, but the Apollo plugin/API is not installed.");
            return;
        }

        try {
            lunarRichPresenceManager = new LunarRichPresenceManager(this);
        } catch (Throwable error) {
            lunarRichPresenceManager = null;
            getLogger().log(java.util.logging.Level.WARNING, "Failed to initialize Lunar Rich Presence.", error);
        }
    }

    private void initializeLuckPermsTablistRefreshBridge() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null
                && !isClassAvailable("net.luckperms.api.LuckPermsProvider")) {
            return;
        }

        try {
            luckPermsTablistRefreshBridge = new LuckPermsTablistRefreshBridge(this);
            luckPermsTablistRefreshBridge.start();
        } catch (Throwable error) {
            luckPermsTablistRefreshBridge = null;
            getLogger().log(java.util.logging.Level.WARNING, "Failed to initialize LuckPerms tablist refresh bridge.",
                    error);
        }
    }

    private void initializeSkinsRestorerTablistRefreshBridge() {
        if (!isClassAvailable("net.skinsrestorer.api.SkinsRestorerProvider")) {
            return;
        }

        try {
            skinsRestorerTablistRefreshBridge = new SkinsRestorerTablistRefreshBridge(this);
            skinsRestorerTablistRefreshBridge.start();
        } catch (Throwable error) {
            skinsRestorerTablistRefreshBridge = null;
            getLogger().log(java.util.logging.Level.WARNING, "Failed to initialize SkinsRestorer tablist refresh bridge.",
                    error);
        }
    }

    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private boolean checkMinecraftVersionSupport() {
        ServerPlatform platform = PlatformReport.detectPlatform(this);
        MinecraftVersion version;
        try {
            version = PlatformReport.detectMinecraftVersion();
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Could not read the Minecraft version from '" + getServer().getBukkitVersion()
                    + "'. SmpPlugin is disabling itself.");
            return false;
        }

        String buildMismatch = BuildInfo.load(getClassLoader()).mismatchWarning(platform);
        if (buildMismatch != null) {
            getLogger().warning("====================================================");
            getLogger().warning("WRONG BUILD: " + buildMismatch);
            getLogger().warning("====================================================");
        }

        CompatibilityPolicy.Decision decision = CompatibilityPolicy.evaluate(platform, version);
        switch (decision.verdict()) {
            case SUPPORTED -> getLogger().info("Compatibility: " + decision.reason());
            case UNTESTED -> {
                getLogger().warning("====================================================");
                getLogger().warning("UNTESTED PLATFORM: " + decision.reason());
                getLogger().warning("SmpPlugin will start, but this combination has not been verified.");
                getLogger().warning("Back up your data and check /smpplugin status before going live.");
                getLogger().warning("====================================================");
            }
            case REFUSED -> {
                getLogger().severe("====================================================");
                getLogger().severe("UNSUPPORTED PLATFORM: " + decision.reason());
                getLogger().severe("Detected: " + platform.displayName() + " " + version
                        + " (" + getServer().getVersion() + ")");
                getLogger().severe("This build supports Paper, Purpur, Spigot and CraftBukkit "
                        + CompatibilityPolicy.MINIMUM + " on Java 25.");
                getLogger().severe("SmpPlugin is disabling itself.");
                getLogger().severe("====================================================");
            }
        }
        return decision.allowsEnable();
    }

    public boolean wasReloadedInProcess() {
        return reloadedInProcess;
    }

    private void detectInProcessReload() {
        reloadedInProcess = System.getProperty(LOADED_IN_PROCESS_PROPERTY) != null;
        System.setProperty(LOADED_IN_PROCESS_PROPERTY, "true");
        if (reloadedInProcess) {
            getLogger().severe("====================================================");
            getLogger().severe("SmpPlugin was re-enabled inside a running server (/reload or a plugin manager).");
            getLogger().severe("This is not supported: in-flight transactions, menus and tasks from the previous");
            getLogger().severe("instance may be lost or duplicated. Restart the server instead.");
            getLogger().severe("Use /smpplugin reload to reload configuration files safely.");
            getLogger().severe("====================================================");
        }
    }

    // ── Static accessor ───────────────────────────────────────────────────────

    public void reloadAllPluginConfigurations() {
        if (teleportManager != null) {
            teleportManager.restoreAllRtpChunkThrottles();
        }
        configManager.reload();
        if (worldSystem != null) {
            java.util.List<String> worldProblems = worldSystem.reload();
            worldProblems.forEach(problem -> getLogger().warning("worlds.yml not applied: " + problem));
        }
        languageManager.reload();
        crashProtectionManager.reload();
        currencyManager.reload();
        optimizationManager.reload();
        warpManager.loadAll();
        spawnManager.load();
        if (afkManager != null) {
            afkManager.reload();
        }
        if (offenseManager != null) {
            offenseManager.reload();
        }
        worthManager.reload();
        if (balancedPriceManager != null) {
            balancedPriceManager.reload();
        }
        farmingMetaManager.load();
        shopManager.reload();
        ordersManager.reload();
        duelManager.reload();
        ffaManager.reload();
        auctionHouseManager.reload();
        if (auctionOrderBotManager != null) {
            auctionOrderBotManager.reload();
        }
        billfordManager.load();
        rtpManager.reload();
        rtpQueueManager.reload();
        lobbyManager.reload();
        portalManager.loadAll();
        crateManager.reload();
        crateVisualManager.reload();
        keyAllManager.reload();
        shardManager.reloadSettings();
        rtpZoneManager.reloadSettings();
        enderChestManager.reload();
        freezeManager.reload();
        staffModeManager.reload();
        invseeManager.reload();
        configManager.reloadSpawners();
        configManager.reloadSpawnStash();
        configManager.reloadNetwork();
        configManager.reloadDatabase();
        configManager.reloadDiscord();
        spawnerManager.reload();
        antiEspManager.reload();
        antiEspManager.refreshAllPlayers();
        if (coinFlipModule != null) {
            coinFlipModule.reload().ifPresent(problem ->
                    getLogger().warning("CoinFlip configuration was not reloaded: " + problem));
        }
        if (v3Module != null) {
            v3Module.reload();
        }
        if (scoreboardManager != null) {
            scoreboardManager.markDirty();
        }
        if (playerDataManager != null) {
            playerDataManager.refreshRemovedSettingOptions();
        }
        com.bx.smpPlugin.managers.SellStatsExporter.restartEmbeddedHttpServer(this);
        spawnStashManager.reload();
        fakePlayerManager.reload();
        hideManager.reload();
        networkStatusManager.reload();
        networkStaffChatManager.reload();
        networkStaffAlertManager.reload();
        discordWebhookManager.reload();
        if (maintenanceManager != null) {
            maintenanceManager.load();
        }
        if (anvilModerationManager != null) {
            anvilModerationManager.load();
        }
        if (updateManager != null) {
            updateManager.checkForUpdates();
        }
        leaderboardManager.invalidateAll();
        scoreboardManager.updateAll();
        tablistManager.updateAll();
        nametagManager.reload(getServer().getOnlinePlayers());
        if (lunarRichPresenceManager != null) {
            lunarRichPresenceManager.reload();
        } else {
            initializeLunarRichPresenceManager();
        }

        for (Player player : getServer().getOnlinePlayers()) {
            tablistManager.updateTablistName(player);
            worthManager.clearWorthDisplay(player);
            worthManager.syncWorthDisplay(player);
        }
        syncCommands();
    }

    public static SmpPlugin getInstance() {
        return instance;
    }

    public NamespacedKey getKey(String key) {
        return new NamespacedKey(this, key);
    }

    public SpigotScheduler getSpigotScheduler() {
        return SpigotScheduler;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public com.bx.smpPlugin.worlds.WorldSystem getWorldSystem() {
        return worldSystem;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public FeatureManager getFeatureManager() {
        return featureManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerLogsManager getPlayerLogsManager() {
        return playerLogsManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public PlayerVisibilityManager getPlayerVisibilityManager() {
        return playerVisibilityManager;
    }

    public ExplosionParticleFilter getExplosionParticleFilter() {
        return explosionParticleFilter;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public FriendsManager getFriendsManager() {
        return friendsManager;
    }

    public PrivateMessageManager getPrivateMessageManager() {
        return privateMessageManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public CuboidManager getCuboidManager() {
        return cuboidManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public SpawnStashManager getSpawnStashManager() {
        return spawnStashManager;
    }

    public FakePlayerManager getFakePlayerManager() {
        return fakePlayerManager;
    }

    public com.bx.smpPlugin.core.V3Module getV3Module() {
        return v3Module;
    }

    public HideManager getHideManager() {
        return hideManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public FastCrystalManager getFastCrystalManager() {
        return fastCrystalManager;
    }

    public TPAManager getTPAManager() {
        return tpaManager;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public ClearLagManager getClearLagManager() {
        return clearLagManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public CrateVisualManager getCrateVisualManager() {
        return crateVisualManager;
    }

    public KeyAllManager getKeyAllManager() {
        return keyAllManager;
    }

    public com.bx.smpPlugin.listeners.JoinPreloadListener getJoinPreloadListener() {
        return joinPreloadListener;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public HoverStatsManager getHoverStatsManager() {
        return hoverStatsManager;
    }

    public WorthManager getWorthManager() {
        return worthManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public OrdersManager getOrdersManager() {
        return ordersManager;
    }

    public OrdersBedrockManager getOrdersBedrockManager() {
        return ordersBedrockManager;
    }

    public EnchantmentsManager getEnchantmentsManager() {
        return enchantmentsManager;
    }

    public FilterManager getFilterManager() {
        return filterManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public FfaManager getFfaManager() {
        return ffaManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public AuctionOrderBotManager getAuctionOrderBotManager() {
        return auctionOrderBotManager;
    }

    public BillfordManager getBillfordManager() {
        return billfordManager;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public RTPQueueManager getRtpQueueManager() {
        return rtpQueueManager;
    }

    public BalancedPriceManager getBalancedPriceManager() {
        return balancedPriceManager;
    }

    public FarmingMetaManager getFarmingMetaManager() {
        return farmingMetaManager;
    }

    public QuickBuyBedrockManager getQuickBuyBedrockManager() {
        return quickBuyBedrockManager;
    }

    public boolean openQuickBuyBedrockCatalogue(Player player, int targetSlot) {
        if (quickBuyBedrockManager == null || player == null) {
            return false;
        }
        return quickBuyBedrockManager.openCatalogue(player, targetSlot, "", 0);
    }

    public QuickBuySearchPrompt getQuickBuySearchPrompt() {
        return quickBuySearchPrompt;
    }

    public ServerNotificationManager getServerNotificationManager() {
        return serverNotificationManager;
    }

    public PlayerSettingToggles getPlayerSettingToggles() {
        return playerSettingToggles;
    }

    public DialogGateway getDialogManager() {
        return dialogManager;
    }

    public RTPManager getRtpManager() {
        return rtpManager;
    }

    public RTPZoneManager getRtpZoneManager() {
        return rtpZoneManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }

    public AmethystToolsManager getAmethystToolsManager() {
        return amethystToolsManager;
    }

    public EnderChestManager getEnderChestManager() {
        return enderChestManager;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    public GodModeManager getGodModeManager() {
        return godModeManager;
    }

    public StaffModeManager getStaffModeManager() {
        return staffModeManager;
    }

    public InvseeManager getInvseeManager() {
        return invseeManager;
    }

    public ProfileViewerManager getProfileViewerManager() {
        return profileViewerManager;
    }

    public PlayerWipeManager getPlayerWipeManager() {
        return playerWipeManager;
    }

    public PlayerUnwipeManager getPlayerUnwipeManager() {
        return playerUnwipeManager;
    }

    public VoiceMuteManager getVoiceMuteManager() {
        return voiceMuteManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public OffenseManager getOffenseManager() {
        return offenseManager;
    }

    public AnvilModerationManager getAnvilModerationManager() {
        return anvilModerationManager;
    }

    public StatsWipeManager getStatsWipeManager() {
        return statsWipeManager;
    }

    public ServerWipeManager getServerWipeManager() {
        return serverWipeManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public AntiEspManager getAntiEspManager() {
        return antiEspManager;
    }

    public com.bx.smpPlugin.coinflip.bukkit.CoinFlipModule getCoinFlipModule() {
        return coinFlipModule;
    }

    public com.bx.smpPlugin.hooks.TitanGuardBridge getTitanGuardBridge() {
        return titanGuardBridge;
    }

    public NetworkStatusManager getNetworkStatusManager() {
        return networkStatusManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public NetworkStaffChatManager getNetworkStaffChatManager() {
        return networkStaffChatManager;
    }

    public NetworkStaffAlertManager getNetworkStaffAlertManager() {
        return networkStaffAlertManager;
    }

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public LunarRichPresenceManager getLunarRichPresenceManager() {
        return lunarRichPresenceManager;
    }

    public OptimizationManager getOptimizationManager() {
        return optimizationManager;
    }

    public CrashProtectionManager getCrashProtectionManager() {
        return crashProtectionManager;
    }

    public void syncCommands() {
        syncCommandState("spawn", FeatureManager.Feature.SPAWN);
        syncCommandState("lobby", FeatureManager.Feature.LOBBY);
        syncCommandState("setlobby", FeatureManager.Feature.LOBBY);
        syncCommandState("afk", FeatureManager.Feature.AFK);
        syncCommandState("away", FeatureManager.Feature.AFK);
        syncCommandState("afkzone", FeatureManager.Feature.AFK);
        syncCommandState("warp", FeatureManager.Feature.WARPS);
        syncCommandState("warpmanager", FeatureManager.Feature.WARPS);
        syncCommandState("setwarp", FeatureManager.Feature.WARPS);
        syncCommandState("delwarp", FeatureManager.Feature.WARPS);
    }

    private void syncCommandState(String commandName, FeatureManager.Feature feature) {
        boolean enabled = featureManager.isEnabled(feature);
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            return;
        }

        try {
            Method method = getServer().getClass().getMethod("getCommandMap");
            CommandMap commandMap = (CommandMap) method.invoke(getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            String fallbackPrefix = getDescription().getName().toLowerCase(Locale.ROOT);
            String namespacedKey = fallbackPrefix + ":" + commandName;

            if (enabled) {
                if (!knownCommands.containsKey(commandName)) {
                    commandMap.register(fallbackPrefix, command);
                }
            } else {
                command.unregister(commandMap);
                knownCommands.remove(commandName);
                knownCommands.remove(namespacedKey);
                for (String alias : command.getAliases()) {
                    knownCommands.remove(alias);
                    knownCommands.remove(fallbackPrefix + ":" + alias);
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to sync command state for: " + commandName, e);
        }
    }
}
