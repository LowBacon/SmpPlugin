# Upgrading from 3.x to 4.0

Upgrading is drop-in: stop the server, back up `plugins/SmpPlugin/` (and your MySQL database if you
use one), replace the old jar with `SmpPlugin-4.0.0-paper.jar` (or `-spigot.jar`) and start. Nothing
has to be done by hand. This page lists everything that happens and every change you may notice.

## What happens on the first start

1. **Config files.** Settings that are new in 4.0 are added to your files; values you already have
   are never changed. Before a file is touched, the old copy goes to
   `plugins/SmpPlugin/config-backups/<date>/`. New files are created: `ranked.yml`, `events.yml`,
   `advent.yml` and `survival-rules.yml`.
2. **Titles.** If your tab list title (`TABLIST.ANIMATION.TEXT`) is still exactly `CustomSmp`, or
   your scoreboard title is still exactly the 3.x gradient, it is switched to the branding token, so
   it reads **CustomSMP** and follows `BRANDING.SERVER-NAME`. A title you changed is left alone.
3. **Messages repaired.** Config comments that an old tool had written into the middle of some
   messages (the ban and mute screens among them) are removed, with a backup (this is the 3.2.0 fix).
4. **Database.** New tables are created by numbered migrations: `schema_migrations`,
   `economy_ledger`, `ranked_ratings` and `ranked_matches`. Existing tables are not changed. The
   console says `Database migrated to schema version 3 (ran [1, 2, 3])`.
5. **Old CoinFlip money** (coming from 3.1 or older): the old CoinFlip was replaced in 3.2, which is
   part of 4.0. Stakes still in open old coin flips and payouts still owed are paid back once.

## Things that work differently

| Area | 3.x | 4.0 |
|---|---|---|
| Server name | Spelled out in each file | `BRANDING.SERVER-NAME` in `config.yml`, default **CustomSMP** |
| Placeholders without PlaceholderAPI | Shown as written (`%economy_kills%`) | Filled in by SmpPlugin; unknown ones left out of the scoreboard and tab list |
| Money maths | Doubles rounded to the cent | Whole cents with overflow checks; the highest balance is `ECONOMY.MAX-BALANCE` (90 trillion by default) |
| `/pay 1e5`, `/pay NaN` | Accepted or a vague error | Refused with "That is not a valid amount" |
| Shard changes | Changed directly | Go through the economy and are written to the ledger |
| Leaderboards | Re-read the players table on the main thread every 30 s | Read in the background; an admin edit to an offline player shows within about 30 s |
| `/smpplugin` | Only `/smpplugin`, `/csmp`, `/customsmp` | Also `/smp`, with `help`, `modules`, `reload <module>` and `diagnostics` |
| The words "status" / "currently" in messages | Always capitalised | Shown as written |
| Scoreboard with TAB or FeatherBoard installed | Both drew a sidebar | SmpPlugin steps aside unless `OVERRIDE-OTHER-PLUGINS: true` |
| Default tab list footer | Online count | Balance and rank, address, tips (new installs only; your footer is kept) |
| Default scoreboard lines | Money, shards, kills, deaths, key-all, playtime, ping | Player, rank, balance, shards, kills, deaths, K/D, playtime, team, online, server (new installs only) |

## Seasonal events start on their own

`events.yml` ships with Advent (1–27 December), Christmas presents (20–27 December), the Easter egg
hunt (1–22 April) and Summer Splash (21 June – 31 August), each switched on. They hand out money and
shards. To keep an event off, run `/event disable <event>` once (it stays off), set its `ENABLED:
false`, or turn the whole `SEASONAL_EVENTS` module off in `/smp modules`. Halloween works as before
and is now listed in `/event` too.

## Item bans

`survival-rules.yml` bans only the debug stick and the command block minecart by default. Add
anything else you want to block; remove those two if you want them allowed.

## Permissions

All 3.x nodes keep working. New player nodes default to true: `smpplugin.balance.history`,
`smpplugin.ranked`, `smpplugin.events`, `smpplugin.advent`. New admin nodes are in the
`smpplugin.admin` bundle (and so in the admin, manager, developer, co-owner and owner roles):
`smpplugin.admin.economy`, `smpplugin.admin.economy.history`, `smpplugin.admin.ranked`,
`smpplugin.admin.events`, `smpplugin.admin.advent`, `smpplugin.bypass.itemban`.
`smpplugin.admin.economy.set` (`/economy set` and `reset`) is a destructive action: only the owner
role has it. `smpplugin.bypass.paylimit` and `smpplugin.bypass.paycooldown` are rank perks.

## Going back to 3.x

Stop the server and put the 3.x jar back. 3.x ignores the new tables and the new settings, so your
data keeps working; the ledger and ranked tables simply stay unused. To get your old config files
back exactly, copy them from `config-backups/`.
