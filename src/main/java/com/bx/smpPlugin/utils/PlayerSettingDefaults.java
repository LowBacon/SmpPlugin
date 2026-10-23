// SmpPlugin by LowBacon
package com.bx.smpPlugin.utils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.ThreeChoice;
import com.bx.smpPlugin.models.TwoChoice;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class PlayerSettingDefaults {

    public static final String BUTTONS_PATH = "SETTINGS-MENU.BUTTONS";

    private static final String DEFAULT_KEY = "DEFAULT";
    private static final String ENABLED_KEY = "ENABLED";

    private static final Map<String, Setting> BINDINGS = createBindings();

    private PlayerSettingDefaults() {
    }

    public static void initialize() {
        if (BINDINGS.isEmpty()) {
            throw new IllegalStateException("PlayerSettingDefaults failed to register settings");
        }
    }

    public static boolean isOptionEnabled(ConfigurationSection buttonSection) {
        return buttonSection == null || buttonSection.getBoolean(ENABLED_KEY, true);
    }

    public static boolean isOptionEnabled(ConfigurationSection buttons, String key) {
        return buttons == null || isOptionEnabled(buttons.getConfigurationSection(key));
    }

    public static boolean isOptionEnabled(SmpPlugin plugin, String key) {
        return isOptionEnabled(buttons(plugin), key);
    }

    public static void applyDefaults(SmpPlugin plugin, PlayerData data) {
        applyDefaults(buttons(plugin), data, warningSink(plugin));
    }

    public static void applyDefaults(ConfigurationSection buttons, PlayerData data) {
        applyDefaults(buttons, data, null);
    }

    public static void applyDefaults(ConfigurationSection buttons, PlayerData data, Consumer<String> warnings) {
        if (buttons == null || data == null) {
            return;
        }
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection section = buttons.getConfigurationSection(key);
            if (section == null || !section.contains(DEFAULT_KEY)) {
                continue;
            }
            applyConfiguredDefault(key, section, data, warnings);
        }
    }

    public static void applyDisabledOptions(SmpPlugin plugin, PlayerData data) {
        applyDisabledOptions(buttons(plugin), data, warningSink(plugin));
    }

    public static void applyDisabledOptions(ConfigurationSection buttons, PlayerData data) {
        applyDisabledOptions(buttons, data, null);
    }

    public static void applyDisabledOptions(ConfigurationSection buttons, PlayerData data, Consumer<String> warnings) {
        if (buttons == null || data == null) {
            return;
        }

        boolean wasDirty = data.isDirty();
        PlayerData pristine = null;
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection section = buttons.getConfigurationSection(key);
            if (section == null || isOptionEnabled(section)) {
                continue;
            }
            if (section.contains(DEFAULT_KEY)) {
                applyConfiguredDefault(key, section, data, warnings);
                continue;
            }
            Setting setting = BINDINGS.get(key);
            if (setting == null) {
                continue;
            }
            if (pristine == null) {
                pristine = new PlayerData(data.getUuid(), data.getUsername());
            }
            copySetting(setting, data, pristine);
        }
        data.setDirty(wasDirty);
    }

    private static void applyConfiguredDefault(
            String key,
            ConfigurationSection section,
            PlayerData data,
            Consumer<String> warnings
    ) {
        Setting setting = BINDINGS.get(key);
        Object configured = section.get(DEFAULT_KEY);
        if (setting == null) {
            warn(warnings, "Ignoring " + BUTTONS_PATH + "." + key + "." + DEFAULT_KEY
                    + ": this setting does not support a configurable default.");
            return;
        }
        String raw = configured == null ? "" : String.valueOf(configured).trim().toLowerCase(Locale.ROOT);
        if (!applySetting(setting, data, raw)) {
            warn(warnings, "Ignoring " + BUTTONS_PATH + "." + key + "." + DEFAULT_KEY
                    + ": '" + configured + "' is not a valid value for this setting.");
        }
    }

    private static ConfigurationSection buttons(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null) {
            return null;
        }
        return plugin.getConfigManager().getMenus().getConfigurationSection(BUTTONS_PATH);
    }

    private static Consumer<String> warningSink(SmpPlugin plugin) {
        return plugin == null ? null : message -> plugin.getLogger().warning(message);
    }

    private static void warn(Consumer<String> warnings, String message) {
        if (warnings != null) {
            warnings.accept(message);
        }
    }

    private static Map<String, Setting> createBindings() {
        Map<String, Setting> bindings = new LinkedHashMap<>();
        for (Setting setting : Setting.values()) {
            bindings.put(setting.name(), setting);
        }
        return Map.copyOf(bindings);
    }

    private static boolean applySetting(Setting setting, PlayerData data, String raw) {
        return switch (setting) {
            case AUTO_CONFIRM_TPAS -> applyAutoConfirmTpas(data, raw);
            case DISABLE_MOB_SPAWN -> applyDisableMobSpawn(data, raw);
            case DISABLE_PHANTOM_SPAWN -> applyDisablePhantomSpawn(data, raw);
            case PRIVATE_MESSAGES, TPA_REQUESTS, TPA_HERE_REQUESTS, PAYMENTS, JOIN_LEAVE_MESSAGES,
                 ADVANCEMENT_MESSAGES
                    -> applyThreeChoice(setting, data, raw);
            case DEATH_MESSAGES
                    -> applyTwoChoice(setting, data, raw);
            case PUBLIC_CHAT, SERVER_BROADCASTS, TEAM_CHAT_VISIBILITY, TPA_CONFIRM_MENUS,
                 DESTROY_PEARL_ON_DEATH, PAY_CONFIRM_MENUS, HOTBAR_MESSAGES, NOTIFICATION_SOUNDS,
                 FOLLOW_ALERT_SETTINGS, DISPLAY_DONUT_PLUS, EXPLOSION_PARTICLES,
                 EXPLOSION_SOUNDS, TELEPORT_ALERTS, RTP_COORDINATES, FAST_CRYSTALS, TOTEM_PARTICLES,
                 HIDE_ALL_PLAYERS, QUIET_SPAWN, DUEL_MUSIC, TEAM_INVITES, CLEAR_ENTITIES_MESSAGES,
                 RANDOMIZED_COORDS, WORTH_DISPLAY, PAY_ALERTS, AUCTION_NOTIFICATIONS,
                 AMETHYST_BREAK_MESSAGES, DUEL_REQUESTS, KEY_ALL_NOTIFICATIONS, ORDER_NOTIFICATIONS,
                 NIGHT_VISION, BOUNTY_ALERTS, SCOREBOARD_VISIBILITY, COMBAT_TIMER, LUNAR_TEAMMATES,
                 CHAINMAIL_ON_RESPAWN, SELL_SHULKERS, PRIVATE_TRANSACTIONS
                    -> applyBoolean(setting, data, raw);
        };
    }

    private static void copySetting(Setting setting, PlayerData target, PlayerData source) {
        switch (setting) {
            case PUBLIC_CHAT -> target.setPublicChatEnabled(source.isPublicChatEnabled());
            case PRIVATE_MESSAGES -> target.setPrivateMessagesChoice(source.getPrivateMessagesChoice());
            case SERVER_BROADCASTS -> target.setServerBroadcastsEnabled(source.isServerBroadcastsEnabled());
            case TEAM_CHAT_VISIBILITY -> target.setTeamChatVisible(source.isTeamChatVisible());
            case TPA_CONFIRM_MENUS -> target.setTpaConfirmMenuEnabled(source.isTpaConfirmMenuEnabled());
            case DESTROY_PEARL_ON_DEATH -> target.setDestroyPearlOnDeath(source.isDestroyPearlOnDeath());
            case PAY_CONFIRM_MENUS -> target.setPayConfirmMenuEnabled(source.isPayConfirmMenuEnabled());
            case AUTO_CONFIRM_TPAS -> {
                target.setTpauto(source.isTpauto());
                target.setAutoTpaHereEnabled(source.isAutoTpaHereEnabled());
            }
            case HOTBAR_MESSAGES -> target.setHotbarMessagesEnabled(source.isHotbarMessagesEnabled());
            case NOTIFICATION_SOUNDS -> target.setNotificationSoundsEnabled(source.isNotificationSoundsEnabled());
            case FOLLOW_ALERT_SETTINGS -> target.setFollowAlertsEnabled(source.isFollowAlertsEnabled());
            case DISPLAY_DONUT_PLUS -> target.setDisplayDonutPlusEnabled(source.isDisplayDonutPlusEnabled());
            case EXPLOSION_PARTICLES -> target.setExplosionParticlesEnabled(source.isExplosionParticlesEnabled());
            case EXPLOSION_SOUNDS -> target.setExplosionSoundsEnabled(source.isExplosionSoundsEnabled());
            case TELEPORT_ALERTS -> target.setTeleportAlertsEnabled(source.isTeleportAlertsEnabled());
            case RTP_COORDINATES -> target.setRtpCoordinatesEnabled(source.isRtpCoordinatesEnabled());
            case FAST_CRYSTALS -> target.setFastCrystalsEnabled(source.isFastCrystalsEnabled());
            case TOTEM_PARTICLES -> target.setTotemParticlesEnabled(source.isTotemParticlesEnabled());
            case HIDE_ALL_PLAYERS -> target.setHideAllPlayersEnabled(source.isHideAllPlayersEnabled());
            case QUIET_SPAWN -> target.setQuietSpawnEnabled(source.isQuietSpawnEnabled());
            case DUEL_MUSIC -> target.setDuelMusicEnabled(source.isDuelMusicEnabled());
            case TEAM_INVITES -> target.setTeamInvitesEnabled(source.isTeamInvitesEnabled());
            case CLEAR_ENTITIES_MESSAGES ->
                    target.setClearEntitiesMessagesEnabled(source.isClearEntitiesMessagesEnabled());
            case RANDOMIZED_COORDS -> target.setRandomizedCoords(source.isRandomizedCoords());
            case TPA_REQUESTS -> target.setTpaRequestsChoice(source.getTpaRequestsChoice());
            case TPA_HERE_REQUESTS -> target.setTpaHereRequestsChoice(source.getTpaHereRequestsChoice());
            case PAYMENTS -> target.setPaymentsChoice(source.getPaymentsChoice());
            case WORTH_DISPLAY -> target.setWorthDisplayEnabled(source.isWorthDisplayEnabled());
            case JOIN_LEAVE_MESSAGES -> target.setJoinLeaveMessagesChoice(source.getJoinLeaveMessagesChoice());
            case PAY_ALERTS -> target.setPayAlertsEnabled(source.isPayAlertsEnabled());
            case ADVANCEMENT_MESSAGES -> target.setAdvancementMessagesChoice(source.getAdvancementMessagesChoice());
            case AUCTION_NOTIFICATIONS -> target.setAuctionNotificationsEnabled(source.isAuctionNotificationsEnabled());
            case AMETHYST_BREAK_MESSAGES ->
                    target.setAmethystBreakMessagesEnabled(source.isAmethystBreakMessagesEnabled());
            case DUEL_REQUESTS -> target.setDuelRequestsEnabled(source.isDuelRequestsEnabled());
            case DEATH_MESSAGES -> target.setDeathMessagesChoice(source.getDeathMessagesChoice());
            case KEY_ALL_NOTIFICATIONS -> target.setKeyAllNotificationsEnabled(source.isKeyAllNotificationsEnabled());
            case ORDER_NOTIFICATIONS -> target.setOrderNotificationsEnabled(source.isOrderNotificationsEnabled());
            case DISABLE_MOB_SPAWN -> {
                target.setMobSpawnEnabled(source.isMobSpawnEnabled());
                target.setMobSpawnDisabledUntil(source.getMobSpawnDisabledUntil());
            }
            case DISABLE_PHANTOM_SPAWN -> {
                target.setPhantomEnabled(source.isPhantomEnabled());
                target.setPhantomDisabledUntil(source.getPhantomDisabledUntil());
            }
            case NIGHT_VISION -> target.setNightVisionEnabled(source.isNightVisionEnabled());
            case BOUNTY_ALERTS -> target.setBountyAlertsEnabled(source.isBountyAlertsEnabled());
            case SCOREBOARD_VISIBILITY -> target.setScoreboardVisible(source.isScoreboardVisible());
            case COMBAT_TIMER -> target.setCombatTimerEnabled(source.isCombatTimerEnabled());
            case LUNAR_TEAMMATES -> target.setLunarTeammatesEnabled(source.isLunarTeammatesEnabled());
            case CHAINMAIL_ON_RESPAWN -> target.setChainmailOnRespawnEnabled(source.isChainmailOnRespawnEnabled());
            case SELL_SHULKERS -> target.setSellShulkersEnabled(source.isSellShulkersEnabled());
            case PRIVATE_TRANSACTIONS -> target.setPrivateTransactionsEnabled(source.isPrivateTransactionsEnabled());
        }
    }

    private static boolean applyBoolean(Setting setting, PlayerData data, String raw) {
        Boolean value = parseBoolean(raw);
        if (value == null) {
            return false;
        }
        switch (setting) {
            case PUBLIC_CHAT -> data.setPublicChatEnabled(value);
            case SERVER_BROADCASTS -> data.setServerBroadcastsEnabled(value);
            case TEAM_CHAT_VISIBILITY -> data.setTeamChatVisible(value);
            case TPA_CONFIRM_MENUS -> data.setTpaConfirmMenuEnabled(value);
            case DESTROY_PEARL_ON_DEATH -> data.setDestroyPearlOnDeath(value);
            case PAY_CONFIRM_MENUS -> data.setPayConfirmMenuEnabled(value);
            case HOTBAR_MESSAGES -> data.setHotbarMessagesEnabled(value);
            case NOTIFICATION_SOUNDS -> data.setNotificationSoundsEnabled(value);
            case FOLLOW_ALERT_SETTINGS -> data.setFollowAlertsEnabled(value);
            case DISPLAY_DONUT_PLUS -> data.setDisplayDonutPlusEnabled(value);
            case EXPLOSION_PARTICLES -> data.setExplosionParticlesEnabled(value);
            case EXPLOSION_SOUNDS -> data.setExplosionSoundsEnabled(value);
            case TELEPORT_ALERTS -> data.setTeleportAlertsEnabled(value);
            case RTP_COORDINATES -> data.setRtpCoordinatesEnabled(value);
            case FAST_CRYSTALS -> data.setFastCrystalsEnabled(value);
            case TOTEM_PARTICLES -> data.setTotemParticlesEnabled(value);
            case HIDE_ALL_PLAYERS -> data.setHideAllPlayersEnabled(value);
            case QUIET_SPAWN -> data.setQuietSpawnEnabled(value);
            case DUEL_MUSIC -> data.setDuelMusicEnabled(value);
            case TEAM_INVITES -> data.setTeamInvitesEnabled(value);
            case CLEAR_ENTITIES_MESSAGES -> data.setClearEntitiesMessagesEnabled(value);
            case RANDOMIZED_COORDS -> data.setRandomizedCoords(value);
            case WORTH_DISPLAY -> data.setWorthDisplayEnabled(value);
            case PAY_ALERTS -> data.setPayAlertsEnabled(value);
            case AUCTION_NOTIFICATIONS -> data.setAuctionNotificationsEnabled(value);
            case AMETHYST_BREAK_MESSAGES -> data.setAmethystBreakMessagesEnabled(value);
            case DUEL_REQUESTS -> data.setDuelRequestsEnabled(value);
            case KEY_ALL_NOTIFICATIONS -> data.setKeyAllNotificationsEnabled(value);
            case ORDER_NOTIFICATIONS -> data.setOrderNotificationsEnabled(value);
            case NIGHT_VISION -> data.setNightVisionEnabled(value);
            case BOUNTY_ALERTS -> data.setBountyAlertsEnabled(value);
            case SCOREBOARD_VISIBILITY -> data.setScoreboardVisible(value);
            case COMBAT_TIMER -> data.setCombatTimerEnabled(value);
            case LUNAR_TEAMMATES -> data.setLunarTeammatesEnabled(value);
            case CHAINMAIL_ON_RESPAWN -> data.setChainmailOnRespawnEnabled(value);
            case SELL_SHULKERS -> data.setSellShulkersEnabled(value);
            case PRIVATE_TRANSACTIONS -> data.setPrivateTransactionsEnabled(value);
            default -> throw new IllegalStateException("Not a boolean setting: " + setting);
        }
        return true;
    }

    private static boolean applyThreeChoice(Setting setting, PlayerData data, String raw) {
        ThreeChoice value = parseThreeChoice(raw);
        if (value == null) {
            return false;
        }
        switch (setting) {
            case PRIVATE_MESSAGES -> data.setPrivateMessagesChoice(value);
            case TPA_REQUESTS -> data.setTpaRequestsChoice(value);
            case TPA_HERE_REQUESTS -> data.setTpaHereRequestsChoice(value);
            case PAYMENTS -> data.setPaymentsChoice(value);
            case JOIN_LEAVE_MESSAGES -> data.setJoinLeaveMessagesChoice(value);
            case ADVANCEMENT_MESSAGES -> data.setAdvancementMessagesChoice(value);
            default -> throw new IllegalStateException("Not a three-choice setting: " + setting);
        }
        return true;
    }

    private static boolean applyTwoChoice(Setting setting, PlayerData data, String raw) {
        TwoChoice value = parseTwoChoice(raw);
        if (value == null) {
            return false;
        }
        switch (setting) {
            case DEATH_MESSAGES -> data.setDeathMessagesChoice(value);
            default -> throw new IllegalStateException("Not a two-choice setting: " + setting);
        }
        return true;
    }

    private static boolean applyAutoConfirmTpas(PlayerData data, String raw) {
        Boolean value = parseBoolean(raw);
        if (value == null) {
            return false;
        }
        data.setTpauto(value);
        data.setAutoTpaHereEnabled(value);
        return true;
    }

    private static boolean applyDisableMobSpawn(PlayerData data, String raw) {
        Boolean value = parseBoolean(raw);
        if (value == null) {
            return false;
        }
        data.setMobSpawnEnabled(!value);
        data.setMobSpawnDisabledUntil(0L);
        return true;
    }

    private static boolean applyDisablePhantomSpawn(PlayerData data, String raw) {
        Boolean value = parseBoolean(raw);
        if (value == null) {
            return false;
        }
        data.setPhantomEnabled(!value);
        data.setPhantomDisabledUntil(0L);
        return true;
    }

    private static Boolean parseBoolean(String raw) {
        return switch (raw) {
            case "true", "yes", "on", "enable", "enabled", "1" -> Boolean.TRUE;
            case "false", "no", "off", "disable", "disabled", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static ThreeChoice parseThreeChoice(String raw) {
        return switch (raw) {
            case "anyone", "everyone", "all", "true", "yes", "on", "enable", "enabled", "1" -> ThreeChoice.ANYONE;
            case "friends_followed", "friends-followed", "friends/followed", "friends", "followed"
                    -> ThreeChoice.FRIENDS_FOLLOWED;
            case "off", "none", "false", "no", "disable", "disabled", "0" -> ThreeChoice.OFF;
            default -> null;
        };
    }

    private static TwoChoice parseTwoChoice(String raw) {
        return switch (raw) {
            case "friends_followed", "friends-followed", "friends/followed", "friends", "followed",
                 "true", "yes", "on", "enable", "enabled", "1" -> TwoChoice.FRIENDS_FOLLOWED;
            case "off", "none", "false", "no", "disable", "disabled", "0" -> TwoChoice.OFF;
            default -> null;
        };
    }

    private enum Setting {
        PUBLIC_CHAT,
        PRIVATE_MESSAGES,
        SERVER_BROADCASTS,
        TEAM_CHAT_VISIBILITY,
        TPA_CONFIRM_MENUS,
        DESTROY_PEARL_ON_DEATH,
        PAY_CONFIRM_MENUS,
        AUTO_CONFIRM_TPAS,
        HOTBAR_MESSAGES,
        NOTIFICATION_SOUNDS,
        FOLLOW_ALERT_SETTINGS,
        DISPLAY_DONUT_PLUS,
        EXPLOSION_PARTICLES,
        EXPLOSION_SOUNDS,
        TELEPORT_ALERTS,
        RTP_COORDINATES,
        FAST_CRYSTALS,
        TOTEM_PARTICLES,
        HIDE_ALL_PLAYERS,
        QUIET_SPAWN,
        DUEL_MUSIC,
        TEAM_INVITES,
        CLEAR_ENTITIES_MESSAGES,
        RANDOMIZED_COORDS,
        TPA_REQUESTS,
        TPA_HERE_REQUESTS,
        PAYMENTS,
        WORTH_DISPLAY,
        JOIN_LEAVE_MESSAGES,
        PAY_ALERTS,
        ADVANCEMENT_MESSAGES,
        AUCTION_NOTIFICATIONS,
        AMETHYST_BREAK_MESSAGES,
        DUEL_REQUESTS,
        DEATH_MESSAGES,
        KEY_ALL_NOTIFICATIONS,
        ORDER_NOTIFICATIONS,
        DISABLE_MOB_SPAWN,
        DISABLE_PHANTOM_SPAWN,
        NIGHT_VISION,
        BOUNTY_ALERTS,
        SCOREBOARD_VISIBILITY,
        COMBAT_TIMER,
        LUNAR_TEAMMATES,
        CHAINMAIL_ON_RESPAWN,
        SELL_SHULKERS,
        PRIVATE_TRANSACTIONS
    }
}
