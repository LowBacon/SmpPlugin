package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.afk.AfkManager;
import com.bx.smpPlugin.afk.AfkZone;
import com.bx.smpPlugin.core.Branding;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Works out the value of one of SmpPlugin's own placeholders, for example {@code balance} in
 * {@code %smp_balance%} or {@code kills} in {@code %economy_kills%}.
 *
 * <p>It does not need PlaceholderAPI: the scoreboard, the tab list and messages use it directly,
 * and the PlaceholderAPI expansions ({@code smp}, {@code economy}, {@code smpplugin},
 * {@code customsmp}) hand their requests to it. Every value comes from memory (the online
 * player's cached data, the leaderboard cache); nothing here reads the database.
 */
public class PlaceholderResolver {

    private static final String SETTING_PREFIX = "setting_";

    private final SmpPlugin plugin;
    private final LeaderboardPlaceholderResolver leaderboardPlaceholderResolver;

    public PlaceholderResolver(SmpPlugin plugin) {
        this.plugin = plugin;
        this.leaderboardPlaceholderResolver = new LeaderboardPlaceholderResolver(plugin);
    }

    /** The value, or null when no placeholder of this name exists. */
    public String resolve(OfflinePlayer offlinePlayer, String params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        String v4 = resolveV4(offlinePlayer, params);
        if (v4 != null) {
            return v4;
        }
        return resolveClassic(offlinePlayer, params);
    }

    /**
     * The names added in 4.0 for {@code %smp_...%}: player_name, rank, balance, shards, kills,
     * deaths, kdr, playtime, team, online, max_players, server_name, server_address, and the ranked
     * and event values.
     */
    private String resolveV4(OfflinePlayer offlinePlayer, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "server_name", "server", "brand":
                return Branding.name();
            case "server_address", "address":
                return Branding.address();
            case "online":
                return Integer.toString(visibleOnlineCount());
            case "max_players", "max":
                return Integer.toString(Bukkit.getMaxPlayers());
            default:
                break;
        }
        if (key.startsWith("ranked_") || key.equals("elo") || key.startsWith("event")) {
            return plugin.getV4Module() == null ? null : plugin.getV4Module().placeholder(offlinePlayer, key);
        }
        if (offlinePlayer == null) {
            return switch (key) {
                case "player_name", "player", "rank", "balance", "kdr" -> "";
                default -> null;
            };
        }
        switch (key) {
            case "player_name", "player": {
                String name = offlinePlayer.getName();
                if (plugin.getHideManager() != null) {
                    return plugin.getHideManager().publicName(offlinePlayer.getUniqueId(), name);
                }
                return name == null ? "" : name;
            }
            case "rank": {
                String rank = com.bx.smpPlugin.utils.RolePrefixResolver.resolve(offlinePlayer);
                return rank == null || rank.isBlank() ? "&7Member" : rank.trim();
            }
            case "balance", "balance_short": {
                PlayerData data = plugin.getPlayerDataManager().get(offlinePlayer.getUniqueId());
                double money = data == null ? 0D : data.getMoney();
                return plugin.getCurrencyManager().formatCompactAmount(CurrencyManager.CurrencyType.MONEY, money);
            }
            case "balance_full": {
                PlayerData data = plugin.getPlayerDataManager().get(offlinePlayer.getUniqueId());
                double money = data == null ? 0D : data.getMoney();
                return plugin.getCurrencyManager().formatAmount(CurrencyManager.CurrencyType.MONEY, money);
            }
            case "kdr", "kd": {
                PlayerData data = plugin.getPlayerDataManager().get(offlinePlayer.getUniqueId());
                return data == null ? "0.00" : kdr(data.getKills(), data.getDeaths());
            }
            default:
                return null;
        }
    }

    /** Kills per death to two decimals; with no deaths it is the kill count. */
    public static String kdr(int kills, int deaths) {
        double ratio = deaths <= 0 ? kills : (double) kills / deaths;
        return String.format(Locale.ROOT, "%.2f", ratio);
    }

    /** Players online, not counting staff in vanish. */
    private int visibleOnlineCount() {
        var staffMode = plugin.getStaffModeManager();
        int count = 0;
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (staffMode == null || !staffMode.isVanished(player.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    /** The placeholders from before 4.0 (the %economy_...% list in {@link EconomyExpansion}). */
    private String resolveClassic(OfflinePlayer offlinePlayer, String params) {
        // 3.0: daily streak, Spooky Calendar and challenges, e.g. %smpplugin_daily_streak%.
        String extra = plugin.getV3Module() == null ? null : plugin.getV3Module().placeholder(offlinePlayer, params);
        if (extra != null) {
            return extra;
        }
        String leaderboardValue = leaderboardPlaceholderResolver.resolve(offlinePlayer, params);
        if (leaderboardValue != null) {
            return leaderboardValue;
        }

        // Settings the dialog buttons label themselves with, e.g. %economy_setting_publicchat%.
        if (params.regionMatches(true, 0, SETTING_PREFIX, 0, SETTING_PREFIX.length())) {
            // A setting only means anything for a player who is here to have one.
            if (offlinePlayer == null || !offlinePlayer.isOnline()
                    || plugin.getPlayerSettingToggles() == null) {
                return "";
            }
            String status = plugin.getPlayerSettingToggles()
                    .status(offlinePlayer.getPlayer(), params.substring(SETTING_PREFIX.length()));
            return status == null ? "" : status;
        }

        // Key-all and booster don't need player data
        if (params.equalsIgnoreCase("keyall_countdown")) {
            java.util.UUID uuid = offlinePlayer != null ? offlinePlayer.getUniqueId() : null;
            return plugin.getKeyAllManager().getFormattedCountdown(uuid);
        }

        // Booster countdown (needs uuid)
        if (params.equalsIgnoreCase("booster_countdown")) {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) return "inactive";
            long secs = plugin.getShardManager().getBoosterRemainingSeconds(offlinePlayer.getUniqueId());
            return secs > 0 ? NumberUtils.formatCountdown(secs) : "inactive";
        }

        if (params.equalsIgnoreCase("rtp_countdown")) {
            if (!plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.RTP_ZONE)) {
                return "disabled";
            }
            java.util.UUID uuid = offlinePlayer != null ? offlinePlayer.getUniqueId() : null;
            return plugin.getRtpZoneManager().getFormattedCountdown(uuid);
        }

        if (params.equalsIgnoreCase("billford_countdown")) {
            if (!plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.BILLFORD)) {
                return "disabled";
            }
            return plugin.getBillfordManager().getFormattedCountdown();
        }

        if (params.equalsIgnoreCase("shard_cuboid_display")) {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) return "-";
            return plugin.getShardManager().getShardCuboidDisplay(offlinePlayer.getUniqueId());
        }

        if (params.equalsIgnoreCase("shard_cuboid_status")) {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) return "outside";
            return plugin.getShardManager().getShardCuboidStatus(offlinePlayer.getUniqueId());
        }

        if (params.equalsIgnoreCase("shard_cuboid_name")) {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) return "none";
            return plugin.getShardManager().getShardCuboidName(offlinePlayer.getUniqueId());
        }

        // Ping (online only)
        if (params.equalsIgnoreCase("ping")) {
            if (offlinePlayer == null || !offlinePlayer.isOnline()) return "0";
            return String.valueOf(offlinePlayer.getPlayer().getPing());
        }

        // Username
        if (params.equalsIgnoreCase("username")) {
            if (offlinePlayer == null) return "unknown";
            return plugin.getHideManager() == null
                    ? (offlinePlayer.getName() != null ? offlinePlayer.getName() : "unknown")
                    : plugin.getHideManager().publicName(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        }

        CurrencyManager currencyManager = plugin.getCurrencyManager();
        if (params.equalsIgnoreCase("money_symbol")) {
            return currencyManager.symbol(CurrencyManager.CurrencyType.MONEY);
        }
        if (params.equalsIgnoreCase("money_symbol_color")) {
            return currencyManager.symbolColor(CurrencyManager.CurrencyType.MONEY);
        }
        if (params.equalsIgnoreCase("money_symbol_colored")) {
            return currencyManager.coloredSymbol(CurrencyManager.CurrencyType.MONEY);
        }
        if (params.equalsIgnoreCase("money_color")) {
            return currencyManager.color(CurrencyManager.CurrencyType.MONEY);
        }
        if (params.equalsIgnoreCase("money_name")) {
            return currencyManager.singular(CurrencyManager.CurrencyType.MONEY);
        }
        if (params.equalsIgnoreCase("money_name_plural")) {
            return currencyManager.plural(CurrencyManager.CurrencyType.MONEY);
        }
        if (params.equalsIgnoreCase("shards_symbol")) {
            return currencyManager.symbol(CurrencyManager.CurrencyType.SHARDS);
        }
        if (params.equalsIgnoreCase("shards_symbol_color")) {
            return currencyManager.symbolColor(CurrencyManager.CurrencyType.SHARDS);
        }
        if (params.equalsIgnoreCase("shards_symbol_colored")) {
            return currencyManager.coloredSymbol(CurrencyManager.CurrencyType.SHARDS);
        }
        if (params.equalsIgnoreCase("shards_color")) {
            return currencyManager.color(CurrencyManager.CurrencyType.SHARDS);
        }
        if (params.equalsIgnoreCase("shards_name")) {
            return currencyManager.singular(CurrencyManager.CurrencyType.SHARDS);
        }
        if (params.equalsIgnoreCase("shards_name_plural")) {
            return currencyManager.plural(CurrencyManager.CurrencyType.SHARDS);
        }

        // AFK: %economy_afk%, afk_tag, afk_time, afk_zone, afk_next, afk_earned
        String lowerParams = params.toLowerCase(java.util.Locale.ROOT);
        if (lowerParams.equals("afk") || lowerParams.startsWith("afk_")) {
            org.bukkit.entity.Player online = offlinePlayer == null ? null : offlinePlayer.getPlayer();
            AfkManager afk = plugin.getAfkManager();
            if (online == null || afk == null) {
                return lowerParams.equals("afk") ? "false" : "";
            }
            AfkZone zone = afk.getCurrentZone(online);
            return switch (lowerParams) {
                case "afk" -> String.valueOf(afk.isShownAsAfk(online));
                case "afk_tag" -> afk.getTabTag(online);
                case "afk_time" -> afk.isAfk(online.getUniqueId())
                        ? NumberUtils.formatCountdown(afk.getAfkSeconds(online.getUniqueId())) : "";
                case "afk_zone" -> zone == null ? "" : zone.displayName();
                case "afk_next" -> zone == null || !zone.paysRewards() ? ""
                        : NumberUtils.formatCountdown(afk.getSecondsToNextReward(online));
                case "afk_earned" -> String.valueOf(afk.getEarnedThisVisit(online.getUniqueId()));
                default -> "";
            };
        }

        // Team
        if (params.equalsIgnoreCase("team")) {
            if (offlinePlayer == null) return "none";
            String team = offlinePlayer.isOnline()
                    ? plugin.getTeamManager().getTeamName(offlinePlayer.getPlayer())
                    : null;
            return team != null ? team.toUpperCase() : "none";
        }

        if (offlinePlayer == null) return "";

        // All others require player data
        PlayerData data = plugin.getPlayerDataManager().get(offlinePlayer.getUniqueId());
        if (data == null && offlinePlayer.isOnline()) {
            data = plugin.getPlayerDataManager().get(offlinePlayer.getPlayer());
        }
        if (data == null) {
            return switch (params) {
                case "nicestMoney", "money_short", "money_amount_short", "nicestmoney" ->
                        currencyManager.formatCompactAmount(CurrencyManager.CurrencyType.MONEY, 0D);
                case "money_formatted", "money-formatted" -> currencyManager.formatMoney(0D);
                case "money_short_formatted", "nicestMoney_formatted", "nicestmoney_formatted" -> currencyManager.formatMoneyCompact(0D);
                case "nicestShards", "shards_short", "shards_amount_short", "nicestshards" ->
                        currencyManager.formatCompactAmount(CurrencyManager.CurrencyType.SHARDS, 0D);
                case "shards_formatted", "shards-formatted" -> currencyManager.formatShards(0L);
                case "shards_short_formatted" -> currencyManager.formatShardsCompact(0L);
                default -> "0";
            };
        }

        return switch (params) {
            case "donutplus", "display_donutplus" -> {
                if (offlinePlayer == null || !offlinePlayer.isOnline()) yield "";
                org.bukkit.entity.Player p = offlinePlayer.getPlayer();
                if (p != null && data.isDisplayDonutPlusEnabled() && (p.hasPermission("smpplugin.donutplus") || p.hasPermission("donutplus"))) {
                    yield "&d&lCustomSmp+ &r";
                }
                yield "";
            }
            case "money" -> NumberUtils.format(data.getMoney());
            case "nicestMoney", "money_short", "money_amount_short", "nicestmoney" ->
                    currencyManager.formatCompactAmount(CurrencyManager.CurrencyType.MONEY, data.getMoney());
            case "money_formatted", "money-formatted" -> currencyManager.formatMoney(data.getMoney());
            case "money_short_formatted", "nicestMoney_formatted", "nicestmoney_formatted" -> currencyManager.formatMoneyCompact(data.getMoney());
            case "shards" -> String.valueOf(data.getShards());
            case "nicestShards", "shards_short", "shards_amount_short", "nicestshards" ->
                    currencyManager.formatCompactAmount(CurrencyManager.CurrencyType.SHARDS, data.getShards());
            case "shards_formatted", "shards-formatted" -> currencyManager.formatShards(data.getShards());
            case "shards_short_formatted" -> currencyManager.formatShardsCompact(data.getShards());
            case "kills" -> String.valueOf(data.getKills());
            case "deaths" -> String.valueOf(data.getDeaths());
            case "playtime" -> NumberUtils.formatTimeLong(data.getTotalPlaytimeSeconds());
            case "killStreak", "killstreak" -> String.valueOf(data.getKillStreak());
            case "highestKillStreak", "highestkillstreak" -> String.valueOf(data.getHighestKillStreak());
            case "blocksPlaced", "blocksplaced" -> String.valueOf(data.getBlocksPlaced());
            case "blocksBroken", "blocksbroken" -> String.valueOf(data.getBlocksBroken());
            case "mobsKilled", "mobskilled" -> String.valueOf(data.getMobsKilled());
            case "moneySpent", "moneyspent" -> NumberUtils.format(data.getMoneySpent());
            case "moneyMade", "moneymade" -> NumberUtils.format(data.getMoneyMade());
            case "x", "coord_x", "coords_x" -> {
                if (offlinePlayer == null || !offlinePlayer.isOnline() || offlinePlayer.getPlayer() == null) yield "0";
                yield String.valueOf(data.getDisplayX(offlinePlayer.getPlayer().getLocation().getBlockX()));
            }
            case "y", "coord_y", "coords_y" -> {
                if (offlinePlayer == null || !offlinePlayer.isOnline() || offlinePlayer.getPlayer() == null) yield "0";
                yield String.valueOf(data.getDisplayY(offlinePlayer.getPlayer().getLocation().getBlockY()));
            }
            case "z", "coord_z", "coords_z" -> {
                if (offlinePlayer == null || !offlinePlayer.isOnline() || offlinePlayer.getPlayer() == null) yield "0";
                yield String.valueOf(data.getDisplayZ(offlinePlayer.getPlayer().getLocation().getBlockZ()));
            }
            case "coords", "location", "formatted_coords" -> {
                if (offlinePlayer == null || !offlinePlayer.isOnline() || offlinePlayer.getPlayer() == null) yield "0, 0, 0";
                yield data.getDisplayCoords(offlinePlayer.getPlayer().getLocation());
            }
            case "randomized_coords", "randomized_coords_enabled" -> String.valueOf(data.isRandomizedCoords());
            default -> null;
        };
    }
}
