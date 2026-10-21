# Known limitations (4.0.0)

What SmpPlugin 4.0.0 does not do, or does only partly. Each item is something the code really does
today; nothing here is planned work presented as done.

## Platform

- **Folia is not supported.** The plugin declares `folia-supported: false`; its modules have not been
  checked on Folia's region threads.
- **Tested versions.** 4.0.0 was started and exercised from the console on Paper 26.3 (build 200)
  and compiled against the newest Paper and Spigot APIs. It was not started on 1.21.11, 26.1.x or
  26.2, nor on Purpur, Spigot or CraftBukkit, for this release; the 3.x code it builds on was.

## Threads and the database

- **Not every database read is off the main thread.** 4.0 moved the leaderboard table read and the
  `/balance <player>`, `/economy`, `/ranked` and `/halloween reset` name lookups to background
  threads, and every write was already in the background. Older parts still read the database on
  the main thread for some offline-player commands (for example changing an offline player's
  balance or stats). These are single-row reads by primary key.
- **One SQLite connection** is shared by all threads, serialised by a lock. That is the right choice
  for SQLite; on MySQL it means database work is not parallel.
- **Balances are stored as decimals.** The database column predates 4.0. Every change is calculated
  in whole cents and stored back exactly; balances are exact up to 90 trillion, the highest balance
  allowed.

## Economy

- **One server per database for money.** Balance changes write a player's whole row. Two servers
  changing the same player's balance in the same database at the same time can overwrite each
  other. Run the economy on one server, or keep each server's database separate.
- **No retry keys.** The ledger gives every change a transaction id, but nothing retries a change
  automatically, so no idempotency keys are used. A change either happens once on the main thread
  or fails and is reported.
- Wipes (`/playerwipe`, `/serverwipe`, stats wipe) reset balances directly and are not written to
  the ledger; they have their own backups and logs.

## Rewards and item delivery

- **Crates** take the key and hand out the reward in the same server tick, after checking for
  inventory space; a failed grant gives the key back. If the server process were killed inside that
  tick, the key could be gone without the reward. There is no separate pending-delivery record.
- **Event and calendar rewards** drop items that do not fit in the inventory on the ground at the
  player's feet. Progress is recorded before the reward is given, so a reward is never paid twice.
- The Auction House and duel loot use claim lists (items wait in the claims menu), as before.

## Modules

- **Switching a module off** stops its commands for every module. For the 4.0 modules (ranked PvP,
  seasonal events, survival rules) it also removes their listeners and timers. Older modules keep
  their listeners registered while off (some, such as staff mode, freeze and invsee, also end their
  running sessions); those listeners check the switch and do nothing.

## Ranked PvP

- Ranked matches are local to one server (no cross-server ranked queue) and use static duel arenas
  that allow queue matches; generated biome arenas are not used.
- Rating placeholders for players who are not online show the starting rating.
- The anti-boost pair limit and same-IP check cannot stop two players who really are different
  people from arranging fights; they make it slow and unrewarding.

## Seasonal events

- Easter moves every year; set the Easter hunt's dates in `events.yml` each year (no automatic
  Easter date).
- Christmas presents can only be unwrapped while the Christmas event runs.
- A player's minutes towards the next playtime reward are counted in memory and start again after a
  relog or restart; the number of rewards given each day is saved.
- Events have no NPCs or holograms of their own.

## Integrations

- WorldGuard, EssentialsX, FancyHolograms and Geyser (other than Floodgate forms) have no special
  integration. RTP does not check WorldGuard regions.
- Integrations other than PlaceholderAPI, LuckPerms, Vault and ProtocolLib were not part of the
  4.0.0 test run.

## Testing

- Unit tests (311, of which 8 are skipped because they need a specific server API) cover the money
  maths, the economy's offline handling, the ledger, migrations, Elo, matchmaking, event dates,
  weighted rewards, branding, placeholders, item bans and the bundled config files.
- The plugin was started on a real Paper 26.3 server and its 4.0 commands were run from the console.
  The 4.0 features that need players (a ranked match, unwrapping a present, finding an egg, the
  menus) were **not** tested with real players. See [VERIFICATION-4.0.md](VERIFICATION-4.0.md).
- No benchmark numbers are published: `/smp diagnostics` measures your own server.
