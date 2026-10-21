# Placeholder reference

SmpPlugin's placeholders work in three places:

- **Inside SmpPlugin** (scoreboard, tab list, menus, messages), **with or without PlaceholderAPI**.
  The plugin fills its own placeholders itself, then hands the text to PlaceholderAPI if it is
  installed.
- **In other plugins** through PlaceholderAPI. Install PlaceholderAPI; the expansions register by
  themselves (no `/papi ecloud download` needed).
- On the scoreboard and tab list, a placeholder nothing can fill (a typo, or a plugin that is not
  installed) is left out instead of being shown as code. Set `HIDE-UNRESOLVED-PLACEHOLDERS: false`
  (in `scoreboard.yml`, and under `TABLIST` in `config.yml`) to see it as written while editing.

Every name below works under every prefix: `%smp_...%` (new in 4.0), `%economy_...%`,
`%smpplugin_...%`, `%customsmp_...%`, and the older `%finnishsmp_...%` and `%fsmp_...%`. For example
`%smp_kills%` and `%economy_kills%` are the same value.

Values come from memory (the online player's data and the cached leaderboards); no placeholder reads
the database, so they are cheap on a scoreboard.

## Server and branding (4.0)

| Placeholder | Value |
|---|---|
| `%smp_server_name%` | `BRANDING.SERVER-NAME` (default CustomSMP). Also `{server_name}` in SmpPlugin's own text. |
| `%smp_server_address%` | `BRANDING.SERVER-ADDRESS`. Also `{server_address}`. |
| `{server_title}` | The server name in a bold gradient (`BRANDING.GRADIENT-FROM` / `-TO`); SmpPlugin's own text only. |
| `%smp_online%` | Players online, not counting staff in vanish |
| `%smp_max_players%` | The server's player limit |
| `%server_online%`, `%server_max_players%` | The same, filled by SmpPlugin even without PlaceholderAPI's server expansion |

## Player (4.0 names)

| Placeholder | Value |
|---|---|
| `%smp_player_name%` | The player's name (their disguise name while hidden with `/hide`) |
| `%smp_rank%` | The rank prefix from LuckPerms (or Vault chat); `Member` when there is none |
| `%smp_balance%` | Balance, short (`1.5K`) |
| `%smp_balance_full%` | Balance, full (`1,500.00`) |
| `%smp_kdr%` | Kills per death, two decimals (the kill count when there are no deaths) |

## Economy and statistics

| Placeholder | Value |
|---|---|
| `%smp_money%` | Raw balance |
| `%smp_nicestMoney%`, `%smp_money_short%` | Short balance |
| `%smp_money_formatted%`, `%smp_money_short_formatted%` | Balance with the currency symbol |
| `%smp_money_symbol%`, `%smp_money_name%`, `%smp_money_name_plural%`, `%smp_money_color%` | Currency settings from `config.yml` |
| `%smp_shards%` | Shards (short on SmpPlugin's own scoreboard) |
| `%smp_nicestShards%`, `%smp_shards_short%`, `%smp_shards_formatted%` | Shards, short / with symbol |
| `%smp_kills%`, `%smp_deaths%`, `%smp_killstreak%`, `%smp_highestkillstreak%` | PvP statistics |
| `%smp_playtime%` | Total playtime |
| `%smp_blocksplaced%`, `%smp_blocksbroken%`, `%smp_mobskilled%` | Statistics |
| `%smp_moneyspent%`, `%smp_moneymade%` | Money spent and earned |
| `%smp_team%` | Team name in capitals, or `none` |
| `%smp_ping%`, `%smp_username%` | Ping in ms, the player's public name |
| `%smp_x%`, `%smp_y%`, `%smp_z%`, `%smp_coords%` | Position (randomised when the player chose that setting) |
| `%smp_keyall_countdown%`, `%smp_booster_countdown%` | Time to the next key-all, and the shard booster's time left |
| `%smp_shard_cuboid_display%`, `%smp_shard_cuboid_status%`, `%smp_shard_cuboid_name%` | Shard area status |
| `%smp_rtp_countdown%`, `%smp_billford_countdown%` | RTP zone and Billford countdowns |
| `%smp_setting_<id>%` | A player setting's current value, e.g. `%smp_setting_publicchat%` |

## Leaderboards

`%smp_top_<type>_<position>_<field>%`, for example `%smp_top_money_1_name%`.

- `<type>`: `money`, `shards`, `kills`, `deaths`, `playtime`, `blocksPlaced`, `blocksBroken`,
  `mobsKilled`, `killStreak`, `highestKillStreak`, `moneySpent`, `moneyMade`
- `<field>`: `name`, `value`, `value_short`, `rank`, `display`

The expansions `%economylb_...%` and `%economyrank_...%` from earlier versions still work.

## AFK

| Placeholder | Value |
|---|---|
| `%smp_afk%` | `true` / `false` |
| `%smp_afk_tag%`, `%smp_afk_time%` | The AFK tag and how long the player has been AFK |
| `%smp_afk_zone%`, `%smp_afk_next%`, `%smp_afk_earned%` | The AFK zone, time to its next reward, shards earned this visit |

## Ranked PvP (4.0)

| Placeholder | Value |
|---|---|
| `%smp_elo%` | Rating (also `%smp_ranked_rating%`) |
| `%smp_ranked_tier%` | Tier name from `ranked.yml` TIERS |
| `%smp_ranked_wins%`, `%smp_ranked_losses%`, `%smp_ranked_draws%`, `%smp_ranked_games%` | Record |
| `%smp_ranked_peak%` | Highest rating reached |
| `%smp_ranked_position%` | Leaderboard position, or `-` |
| `%smp_ranked_queue%` | Players in the ranked queue |

Ratings are loaded when a player joins; for players who are not online these show the starting rating.

## Events (3.0 and 4.0)

| Placeholder | Value |
|---|---|
| `%smp_events_active%` | Names of the events running now, or `None` |
| `%smp_event_<id>%` | `running` or `off`, e.g. `%smp_event_christmas%` |
| `%smp_event_advent_opened%`, `%smp_event_advent_ready%` | Advent Calendar doors opened / ready to open |
| `%smp_event_easter_found%` | Easter eggs found this season |
| `%smp_halloween_opened%`, `%smp_halloween_ready%`, `%smp_halloween_countdown%` | Spooky Calendar |
| `%smp_daily_streak%`, `%smp_daily_ready%` | Daily rewards |
| `%smp_challenges_done%`, `%smp_challenges_total%` | Daily challenges |

## Hidden players

`%hide_active%`, `%hide_name%`, `%hide_plain_name%`, `%hide_mode%`, `%hide_alias%`, `%hide_skin%`.

## Scoreboard and tab list tokens

These work in SmpPlugin's own scoreboard and tab list only.

| Where | Tokens |
|---|---|
| Scoreboard lines | `{player}` `{money}` `{shards}` `{kills}` `{deaths}` `{playtime}`; special lines `{team}`, `{shard_booster}`, `{shard_cuboid}` |
| Tab list header and footer | `%animated_title%` `%animated_line%` `%player%` `%rank%` `%world%` `%team%` `%online%` `%max_players%` `%afk_count%` `%tip%` `%money%` `%balance%` `%shards%` `%kills%` `%deaths%` `%playtime%` `%ping%` `%ping_colored%` `%tps%` `%tps_colored%` |
| Anywhere | `{server_name}` `{server_title}` `{server_address}` |
