// SmpPlugin by LowBacon
package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.afk.AfkManager;
import com.bx.smpPlugin.afk.AfkZone;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.NumberUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EconomyExpansion extends PlaceholderExpansion {

    private static final String SETTING_PREFIX = "setting_";

    private final SmpPlugin plugin;
    private final LeaderboardPlaceholderResolver leaderboardPlaceholderResolver;

    public EconomyExpansion(SmpPlugin plugin) {
        this.plugin = plugin;
        this.leaderboardPlaceholderResolver = new LeaderboardPlaceholderResolver(plugin);
    }

    @Override
    public @NotNull String getIdentifier() { return "economy"; }

    @Override
    public @NotNull String getAuthor() { return "SmpPlugin"; }

    @Override
    public @NotNull String getVersion() { return "1.1"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
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
