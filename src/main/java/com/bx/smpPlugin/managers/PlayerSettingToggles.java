package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.PlayerPreference;
import com.bx.smpPlugin.models.ThreeChoice;
import com.bx.smpPlugin.models.TwoChoice;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NightVisionUtils;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The settings the dialog interface exposes, as a table of read-and-cycle pairs.
 *
 * <p>The chest menu in {@code PlayerSettingsMenu} drives the same {@link PlayerData} flags
 * through its own switch; this table exists because two callers need the *display* text as well
 * — the dialog buttons and the {@code %economy_setting_*%} placeholders that label
 * them — and duplicating the formatting in both would let them drift apart on screen.
 */
public final class PlayerSettingToggles {

    /** One row of the table: how to read a setting and how to advance it. */
    private record Toggle(
            String label,
            Function<Context, String> status,
            Consumer<Context> cycle
    ) {
    }

    /** Everything a toggle may need. Held together so the table entries stay one-liners. */
    public record Context(SmpPlugin plugin, Player player, PlayerData data) {
    }

    private static final String ON = "&aON";
    private static final String OFF = "&cOFF";
    private static final String ANYONE = "&aAnyone";
    private static final String FRIENDS = "&eFriends/Followed";

    private final Map<String, Toggle> toggles = new LinkedHashMap<>();
    private final Map<UUID, Boolean> auctionQuickBuy = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> auctionQuickSell = new ConcurrentHashMap<>();
    private final SmpPlugin plugin;

    public PlayerSettingToggles(SmpPlugin plugin) {
        this.plugin = plugin;
        register();
    }

    /** Ids this service answers for, used by tab completion and the placeholder expansion. */
    public Set<String> ids() {
        return toggles.keySet();
    }

    public boolean has(String id) {
        return id != null && toggles.containsKey(normalise(id));
    }

    /** Human-readable name, used in the chat confirmation after a toggle. */
    public String label(String id) {
        Toggle toggle = toggles.get(normalise(id));
        return toggle == null ? null : toggle.label();
    }

    /** Current value as a colourised string, or null when the id is unknown. */
    public String status(Player player, String id) {
        Toggle toggle = toggles.get(normalise(id));
        if (toggle == null || player == null) {
            return null;
        }
        PlayerData data = plugin == null ? null : plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return null;
        }
        return toggle.status().apply(new Context(plugin, player, data));
    }

    /**
     * Advances a setting to its next value and returns the new display string, or null when the
     * id is unknown. Two-state settings flip; the choice settings cycle through their options.
     */
    public String cycle(Player player, String id) {
        Toggle toggle = toggles.get(normalise(id));
        if (toggle == null || player == null) {
            return null;
        }
        PlayerData data = plugin == null ? null : plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return null;
        }
        Context context = new Context(plugin, player, data);
        toggle.cycle().accept(context);
        return toggle.status().apply(context);
    }

    /**
     * Loads auction quick-buy/sell flags so General can paint ON/OFF on the first open instead
     * of a frame of defaults. {@code whenReady} runs on the player's entity thread one tick
     * later when the cache was empty and a value has arrived.
     */
    public void prefetchAuction(Player player, Runnable whenReady) {
        if (plugin == null || player == null || plugin.getAuctionHouseManager() == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (auctionCacheReady(player)) {
            return;
        }
        plugin.getAuctionHouseManager().getPreferenceAsync(id).whenComplete((preference, error) -> {
            boolean buy = error == null && preference != null && preference.fastBuyEnabled();
            boolean sell = error == null && preference != null && preference.fastSellEnabled();
            auctionQuickBuy.put(id, buy);
            auctionQuickSell.put(id, sell);
            if (whenReady == null) {
                return;
            }
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline()) {
                    whenReady.run();
                }
            }, 1L);
        });
    }

    public boolean auctionCacheReady(Player player) {
        if (player == null || plugin == null || plugin.getAuctionHouseManager() == null) {
            return true;
        }
        UUID id = player.getUniqueId();
        return auctionQuickBuy.containsKey(id) && auctionQuickSell.containsKey(id);
    }

    public void dropAuctionCache(UUID playerId) {
        if (playerId == null) {
            return;
        }
        auctionQuickBuy.remove(playerId);
        auctionQuickSell.remove(playerId);
    }

    private static String normalise(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private void add(String id, String label, Function<Context, String> status, Consumer<Context> cycle) {
        toggles.put(normalise(id), new Toggle(label, status, cycle));
    }

    /** Shorthand for the common case: a boolean on PlayerData with no side effect. */
    private void addFlag(
            String id,
            String label,
            Function<PlayerData, Boolean> getter,
            BiConsumer<PlayerData, Boolean> setter
    ) {
        addFlag(id, label, getter, setter, context -> {
        });
    }

    private void addFlag(
            String id,
            String label,
            Function<PlayerData, Boolean> getter,
            BiConsumer<PlayerData, Boolean> setter,
            Consumer<Context> after
    ) {
        add(
                id,
                label,
                context -> bool(getter.apply(context.data())),
                context -> {
                    setter.accept(context.data(), !getter.apply(context.data()));
                    after.accept(context);
                }
        );
    }

    private void register() {
        // Chat — Design/Dialog API/Settings/Chat
        addFlag("publicchat", "Public Chat", PlayerData::isPublicChatEnabled, PlayerData::setPublicChatEnabled);
        add("privatemessages", "Private Messages",
                context -> three(context.data().getPrivateMessagesChoice()),
                context -> context.data().setPrivateMessagesChoice(nextThree(context.data().getPrivateMessagesChoice())));
        addFlag("servermessages", "Server Chat Messages",
                PlayerData::isServerBroadcastsEnabled, PlayerData::setServerBroadcastsEnabled);
        addFlag("hotbarmessages", "Server Hotbar Messages",
                PlayerData::isHotbarMessagesEnabled, PlayerData::setHotbarMessagesEnabled);
        add("deathmessages", "Death Messages",
                context -> two(context.data().getDeathMessagesChoice()),
                context -> context.data().setDeathMessagesChoice(nextTwo(context.data().getDeathMessagesChoice())));
        add("advancementmessages", "Advancement Messages",
                context -> three(context.data().getAdvancementMessagesChoice()),
                context -> context.data().setAdvancementMessagesChoice(nextThree(context.data().getAdvancementMessagesChoice())));
        add("joinleavemessages", "Join/Leave Messages",
                context -> three(context.data().getJoinLeaveMessagesChoice()),
                context -> context.data().setJoinLeaveMessagesChoice(nextThree(context.data().getJoinLeaveMessagesChoice())));

        // Notifications — Design/Dialog API/Settings/Notifications
        addFlag("payalerts", "Pay Alerts", PlayerData::isPayAlertsEnabled, PlayerData::setPayAlertsEnabled);
        addFlag("tpaalerts", "Teleport Alerts",
                PlayerData::isTeleportAlertsEnabled, PlayerData::setTeleportAlertsEnabled);
        addFlag("bountyalerts", "Bounty Alerts", PlayerData::isBountyAlertsEnabled, PlayerData::setBountyAlertsEnabled);
        addFlag("auctionalerts", "Auction Alerts",
                PlayerData::isAuctionNotificationsEnabled, PlayerData::setAuctionNotificationsEnabled);
        addFlag("orderalerts", "Order Alerts",
                PlayerData::isOrderNotificationsEnabled, PlayerData::setOrderNotificationsEnabled);
        addFlag("serversounds", "Server Sounds",
                PlayerData::isNotificationSoundsEnabled, PlayerData::setNotificationSoundsEnabled);
        addFlag("followalerts", "Follow Alerts",
                PlayerData::isFollowAlertsEnabled, PlayerData::setFollowAlertsEnabled);

        // PvP — Design/Dialog API/Settings/PvP
        addFlag("fastcrystals", "Fast Crystals",
                PlayerData::isFastCrystalsEnabled, PlayerData::setFastCrystalsEnabled,
                context -> {
                    if (context.plugin() != null && context.plugin().getFastCrystalManager() != null) {
                        context.plugin().getFastCrystalManager().applyCrystalCooldown(context.player());
                    }
                });
        addFlag("totemparticles", "Totem Particles",
                PlayerData::isTotemParticlesEnabled, PlayerData::setTotemParticlesEnabled);
        addFlag("explosionparticles", "Explosion Particles",
                PlayerData::isExplosionParticlesEnabled, PlayerData::setExplosionParticlesEnabled);
        addFlag("explosionsounds", "Explosion Sounds",
                PlayerData::isExplosionSoundsEnabled, PlayerData::setExplosionSoundsEnabled);
        addFlag("combattimer", "Combat Timer",
                PlayerData::isCombatTimerEnabled, PlayerData::setCombatTimerEnabled);

        // Visuals — Design/Dialog API/Settings/Visuals
        addFlag("displaydonutplus", "Display CustomSmp+",
                PlayerData::isDisplayDonutPlusEnabled, PlayerData::setDisplayDonutPlusEnabled);
        addFlag("worthdisplay", "Item Worth Lore",
                PlayerData::isWorthDisplayEnabled, PlayerData::setWorthDisplayEnabled,
                context -> {
                    if (context.plugin() == null || context.plugin().getWorthManager() == null) {
                        return;
                    }
                    if (context.data().isWorthDisplayEnabled()) {
                        context.plugin().getWorthManager().syncWorthDisplay(context.player());
                    } else {
                        context.plugin().getWorthManager().clearWorthDisplay(context.player());
                    }
                });
        addFlag("tpaconfirm", "Teleport Confirm Menus",
                PlayerData::isTpaConfirmMenuEnabled, PlayerData::setTpaConfirmMenuEnabled);

        // Privacy — Design/Dialog API/Settings/Privacy
        add("tparequests", "Teleport Requests",
                context -> three(context.data().getTpaRequestsChoice()),
                context -> context.data().setTpaRequestsChoice(nextThree(context.data().getTpaRequestsChoice())));
        add("tpahere", "Teleport-Here Requests",
                context -> three(context.data().getTpaHereRequestsChoice()),
                context -> context.data().setTpaHereRequestsChoice(nextThree(context.data().getTpaHereRequestsChoice())));
        add("payments", "Allow Payments",
                context -> three(context.data().getPaymentsChoice()),
                context -> context.data().setPaymentsChoice(nextThree(context.data().getPaymentsChoice())));
        addFlag("randomizedcoords", "Randomized Coords",
                PlayerData::isRandomizedCoords, PlayerData::setRandomizedCoords,
                PlayerSettingToggles::kickForRandomizedCoords);
        addFlag("privatetransactions", "Private Transactions",
                PlayerData::isPrivateTransactionsEnabled, PlayerData::setPrivateTransactionsEnabled);

        // Scoreboard — Design/Dialog API/Settings/Scoreboard
        addFlag("scoreboard", "Scoreboard", PlayerData::isScoreboardVisible, PlayerData::setScoreboardVisible,
                PlayerSettingToggles::refreshScoreboard);

        // General — Design/Dialog API/Settings/General
        add("auctionquickbuy", "Auction Quick Buy",
                context -> bool(auctionFlag(context.player().getUniqueId(), true)),
                context -> cycleAuction(context, true));
        add("auctionquicksell", "Auction Quick Sell",
                context -> bool(auctionFlag(context.player().getUniqueId(), false)),
                context -> cycleAuction(context, false));
        add("mobspawns", "Mob Spawns",
                context -> bool(context.data().isMobSpawnEnabled()),
                context -> cycleMobSpawn(context));
        add("phantomspawning", "Phantom Spawning",
                context -> bool(context.data().isPhantomEnabled()),
                context -> cyclePhantomSpawn(context));
        add("nightvision", "Night Vision",
                context -> bool(NightVisionUtils.isEnabled(context.plugin(), context.player())),
                context -> NightVisionUtils.toggle(context.plugin(), context.player()));
        addFlag("destroypearlondeath", "Destroy Pearl on Death",
                PlayerData::isDestroyPearlOnDeath, PlayerData::setDestroyPearlOnDeath);

        // Ids the chest menu and older placeholders still use. Display follows the stored flag,
        // not the dialog labels above, so a leftover %setting_disablemobspawn% stays truthful.
        add("disablemobspawn", "Disable Mob Spawn",
                context -> bool(!context.data().isMobSpawnEnabled()),
                context -> cycleMobSpawn(context));
        add("disablephantom", "Disable Phantom Spawn",
                context -> bool(!context.data().isPhantomEnabled()),
                context -> cyclePhantomSpawn(context));
        addFlag("payconfirm", "Pay Confirm Menus",
                PlayerData::isPayConfirmMenuEnabled, PlayerData::setPayConfirmMenuEnabled);
        add("tpauto", "TP Auto",
                context -> bool(context.data().isTpauto() && context.data().isAutoTpaHereEnabled()),
                context -> {
                    PlayerData data = context.data();
                    boolean enabled = !(data.isTpauto() && data.isAutoTpaHereEnabled());
                    data.setTpauto(enabled);
                    data.setAutoTpaHereEnabled(enabled);
                    if (enabled && context.plugin() != null && context.plugin().getTPAManager() != null) {
                        context.plugin().getTPAManager().processQueuedAutoRequests(context.player().getUniqueId());
                    }
                });
        addFlag("lunarteammates", "Lunar Teammates",
                PlayerData::isLunarTeammatesEnabled, PlayerData::setLunarTeammatesEnabled);
        addFlag("chainmailonrespawn", "Automatic Respawn Kit",
                PlayerData::isChainmailOnRespawnEnabled, PlayerData::setChainmailOnRespawnEnabled);
        add("teamchat", "Team Chat Sending Mode",
                context -> bool(context.plugin() != null && context.plugin().getTeamManager() != null
                        && context.plugin().getTeamManager().isTeamChatEnabled(context.player().getUniqueId())),
                PlayerSettingToggles::cycleTeamChat);
        addFlag("sellshulkers", "Include Shulker on Sell",
                PlayerData::isSellShulkersEnabled, PlayerData::setSellShulkersEnabled);
    }

    private boolean auctionFlag(UUID playerId, boolean buy) {
        Boolean cached = buy ? auctionQuickBuy.get(playerId) : auctionQuickSell.get(playerId);
        if (cached != null) {
            return cached;
        }
        if (plugin == null || plugin.getAuctionHouseManager() == null) {
            return false;
        }
        PlayerPreference preference = plugin.getAuctionHouseManager().getPreference(playerId);
        boolean value = buy ? preference.fastBuyEnabled() : preference.fastSellEnabled();
        (buy ? auctionQuickBuy : auctionQuickSell).put(playerId, value);
        return value;
    }

    private void cycleAuction(Context context, boolean buy) {
        if (context.plugin() == null || context.plugin().getAuctionHouseManager() == null) {
            return;
        }
        UUID playerId = context.player().getUniqueId();
        boolean next = !auctionFlag(playerId, buy);
        (buy ? auctionQuickBuy : auctionQuickSell).put(playerId, next);
        context.plugin().getAuctionHouseManager().getPreferenceAsync(playerId)
                .thenCompose(preference -> {
                    if (buy) {
                        preference.fastBuyEnabled(next);
                    } else {
                        preference.fastSellEnabled(next);
                    }
                    return context.plugin().getAuctionHouseManager().savePreference(preference);
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        (buy ? auctionQuickBuy : auctionQuickSell).put(playerId, !next);
                    }
                });
    }

    private void cycleMobSpawn(Context context) {
        PlayerData data = context.data();
        data.setMobSpawnEnabled(!data.isMobSpawnEnabled());
        data.setMobSpawnDisabledUntil(expiryFor(data.isMobSpawnEnabled(),
                "SETTINGS.DISABLE-MOB-SPAWN-LIMIT-SECONDS"));
    }

    private void cyclePhantomSpawn(Context context) {
        PlayerData data = context.data();
        data.setPhantomEnabled(!data.isPhantomEnabled());
        data.setPhantomDisabledUntil(expiryFor(data.isPhantomEnabled(),
                "SETTINGS.DISABLE-PHANTOM-SPAWN-LIMIT-SECONDS"));
    }

    /** Zero clears the deadline; a positive limit stamps one that many seconds out. */
    private long expiryFor(boolean spawningAllowed, String limitPath) {
        if (spawningAllowed || plugin == null || plugin.getConfigManager() == null) {
            return 0L;
        }
        long limitSeconds = plugin.getConfigManager().getConfig().getLong(limitPath, -1L);
        return limitSeconds > 0 ? System.currentTimeMillis() + (limitSeconds * 1000L) : 0L;
    }

    private static void kickForRandomizedCoords(Context context) {
        if (context.plugin() != null && context.plugin().getDatabaseManager() != null) {
            context.plugin().getDatabaseManager().savePlayer(context.data());
        }
        context.player().kickPlayer(ColorUtils.colorize("&cThe setting has been changed. Please rejoin."));
    }

    /** Team chat needs a team and the team chat permission, so it is not a plain flag. */
    private static void cycleTeamChat(Context context) {
        if (context.plugin() == null || context.plugin().getTeamManager() == null) {
            return;
        }
        var teamManager = context.plugin().getTeamManager();
        var team = teamManager.getTeam(context.player());
        if (team == null || !teamManager.canUseTeamChat(team, context.player().getUniqueId())) {
            return;
        }
        teamManager.toggleTeamChat(context.player().getUniqueId());
    }

    private static void refreshScoreboard(Context context) {
        if (context.plugin() == null) {
            return;
        }
        ScoreboardManager scoreboardManager = context.plugin().getScoreboardManager();
        if (scoreboardManager != null) {
            scoreboardManager.applyVisibility(context.player());
        }
    }

    private static String bool(boolean value) {
        return value ? ON : OFF;
    }

    private static String three(ThreeChoice choice) {
        return switch (choice == null ? ThreeChoice.ANYONE : choice) {
            case OFF -> OFF;
            case ANYONE -> ANYONE;
            case FRIENDS_FOLLOWED -> FRIENDS;
        };
    }

    private static String two(TwoChoice choice) {
        return (choice == null ? TwoChoice.FRIENDS_FOLLOWED : choice) == TwoChoice.OFF
                ? OFF
                : FRIENDS;
    }

    private static ThreeChoice nextThree(ThreeChoice current) {
        ThreeChoice safe = current == null ? ThreeChoice.ANYONE : current;
        return ThreeChoice.values()[(safe.ordinal() + 1) % ThreeChoice.values().length];
    }

    private static TwoChoice nextTwo(TwoChoice current) {
        TwoChoice safe = current == null ? TwoChoice.FRIENDS_FOLLOWED : current;
        return TwoChoice.values()[(safe.ordinal() + 1) % TwoChoice.values().length];
    }
}
