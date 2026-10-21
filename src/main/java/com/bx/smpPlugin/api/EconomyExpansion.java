package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides %economy_*% placeholders for scoreboards, chat, holograms, etc.
 *
 * Supported:
 *   %economy_money%                raw money
 *   %economy_nicestMoney%          compact money amount (1,5K, 2,3M, ...)
 *   %economy_money_short%          compact money amount
 *   %economy_money_formatted%      configured money display
 *   %economy_money_symbol%         configured money symbol
 *   %economy_money_symbol_color%   configured money symbol color
 *   %economy_money_color%          configured money amount/display color
 *   %economy_money_name%           configured singular money name
 *   %economy_top_money_1_name%     leaderboard name for rank 1
 *   %economy_top_money_1_value%    full leaderboard value for rank 1
 *   %economy_top_money_1_value_short% compact leaderboard value for rank 1
 *   %economy_top_money_1_display%  ready-to-render leaderboard line for rank 1
 *   %economy_shards%               shard count
 *   %economy_nicestShards%         compact shard count
 *   %economy_shards_short%         compact shard count
 *   %economy_shards_formatted%     configured shard display
 *   %economy_shards_symbol%        configured shard symbol
 *   %economy_shards_symbol_color%  configured shard symbol color
 *   %economy_shards_color%         configured shard amount/display color
 *   %economy_shards_name%          configured singular shard name
 *   %economy_kills%                kill count
 *   %economy_deaths%               death count
 *   %economy_playtime%             formatted playtime
 *   %economy_team%                 team name (or "none")
 *   %economy_ping%                 player ping in ms
 *   %economy_username%             player name
 *   %economy_keyall_countdown%     time until next key-all
 *   %economy_booster_countdown%    time until booster expires (or "inactive")
 *   %economy_shard_cuboid_display% shard cuboid HUD text for scoreboard/action info
 *   %economy_shard_cuboid_status%  current shard cuboid state
 *   %economy_shard_cuboid_name%    active shard cuboid name
 *   %economy_x%                    player display X coordinate (randomized if setting enabled)
 *   %economy_y%                    player display Y coordinate
 *   %economy_z%                    player display Z coordinate (randomized if setting enabled)
 *   %economy_coords%               player display formatted coordinates (X, Y, Z)
 *   %economy_randomized_coords%    boolean setting state
 *   (4.0) %smp_balance% %smp_rank% %smp_kdr% %smp_team% %smp_online% %smp_max_players%
 *   %smp_server_name% %smp_server_address% %smp_elo% %smp_ranked_wins% %smp_events_active%
 *   and every name above with the smp prefix. All of them work under every prefix.
 *   %economy_setting_<id>%         a player setting's current value, coloured for display
 *                                  (publicchat, payalerts, scoreboard, sellshulkers, ...)
 */
public class EconomyExpansion extends PlaceholderExpansion {

    private final PlaceholderResolver resolver;

    public EconomyExpansion(SmpPlugin plugin) {
        this.resolver = new PlaceholderResolver(plugin);
    }

    @Override
    public @NotNull String getIdentifier() { return "economy"; }

    @Override
    public @NotNull String getAuthor() { return "SmpPlugin"; }

    @Override
    public @NotNull String getVersion() { return "4.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        return resolver.resolve(offlinePlayer, params);
    }
}
