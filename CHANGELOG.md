# Changelog

## 2.0.0-alpha.4 — 13 October 2026

Merges the settings, Auction House, selling and RTP systems from UltimateDonutSMP-V2 into SmpPlugin.
Everything else is unchanged, including homes, the scoreboard, spawn and AFK. Not boot-tested yet.

### Settings (`/settings`)

- On Paper and Purpur, `/settings` opens a pop-up **dialog** with categories: Chat, Notifications, PvP,
  Visuals, Privacy and General (`dialog.yml`). Spigot, or `SCREENS.SETTINGS: false`, keeps the chest menu.
  Dialog labels work without PlaceholderAPI.
- 12 more options in the chest menu, for 50 in total. Nine existed in code but never showed up: team chat
  mode, show scoreboard, totem particles, RTP coordinates, hide all players, quiet spawn, duel music,
  team invites and clear-entities messages. New ones: combat timer, sell shulker contents and private
  transactions.
- Per-server control in `menus.yml SETTINGS-MENU.BUTTONS`:
  - `ENABLED: false` removes an option and pins every player to its default.
  - `DEFAULT:` sets what new players start with.
  - `COMMAND:` adds a button that runs a command.
  - `STATUS-PLACEHOLDER:` shows a PlaceholderAPI value as the button's status.
- `%economy_setting_<id>%` placeholder for every setting.
- Kept from SmpPlugin: three-choice join/leave and advancement messages, Lunar teammates, respawn kit and
  team chat.
- **Combat timer** setting: hides the combat countdown. The player is still in combat.
- **Private transactions** setting: hides your trades from followers and from auction announcements.
- The scoreboard is not changed. UltimateDonutSMP-V2's per-line scoreboard toggles were left out.

### Auction House

- Quick Buy:
  - Pin items and buy the cheapest matching listing in one click.
  - Falls back to the worth.yml price when nothing is listed (`shop.yml QUICK-BUY.PRICING`).
  - Item picker with search and enchantments: a dialog on Paper/Purpur, Bedrock forms through Floodgate,
    a chest menu elsewhere.
- **Your Items** page with listing, confirm and transaction-history screens. Donut+ and Donut++ ranks
  unlock more listing slots (`smpplugin.donutplus`, `smpplugin.donutplusplus`).
- Smarter search: partial names (`neth`) and enchant shortcuts (`prot 4`).
- `/ah search <item>`.
- Optional server announcements for new listings and sales (`config.yml SERVER-NOTIFICATIONS`, off by
  default).
- New sounds in `sounds.yml AUCTION_HOUSE` and `QUICK_BUY`.

### Selling

- `/sell` has a **Sell** button showing the live total. Closing the menu still returns unsold items.
- `/sellmulti`, `/sellmultiplier` and `/sellprogress` open the sell multipliers. `menus.yml
  PROGRESS-MENU.ENABLED: false` turns multipliers off.
- Shulker box contents are sold when the player's *Sell Shulker Contents* setting is on.
- `/meta`: the **farming meta**, one item from `worth.yml META.ITEMS` that sells for a bonus multiplier and
  rotates on a timer. Off by default (`META.ENABLED`).
- Prices:
  - Items missing from worth.yml can get an automatic balanced price, worked out from recipes and similar
    items.
  - worth.yml now blocks unobtainable items (reinforced deepslate, spawn eggs and others).
- `/topsell` (`/sellstats`, `smpplugin.admin.sellstats`):
  - Shows top items by revenue and volume, top sellers and buyers, and activity charts.
  - Exports to text and HTML.
  - Totals are kept in new `sell_summary_*` tables, back-filled from your existing sell history on first
    start.
- Sell-statistics web dashboard:
  - `shop.yml SHOP-GUI.WEB-SERVER`, off by default.
  - Unlike UltimateDonutSMP-V2, it listens on `127.0.0.1` unless `BIND-ADDRESS` is changed, because it
    has no login.
  - Resetting statistics needs `POST` and `smpplugin.admin.statswipe`.
- Sale records are written in the background on one database writer thread instead of on the main thread.
- Stats wipes and server wipes also clear the new sell summary tables. UltimateDonutSMP-V2 left them
  behind.

### RTP

- Rewritten search with a cache of ready safe spots per world (refilled every 5 seconds), so most `/rtp`
  teleports are instant.
- Per-rank cooldowns (`rtp.yml RANK-COOLDOWNS`, `smpplugin.rtp.cooldown.<seconds>`) and a priority
  waiting queue when too many players are searching (`PRIORITY-QUEUE`, `smpplugin.rtp.priority.<n>`).
- `/rtpq` (`/rtpqueue`): wait with other players and be dropped together at one shared random location.
  It can also be joined by standing in a cuboid (`rtp.yml QUEUE.CUBOID`).
- Entering combat cancels an RTP search and removes the player from the RTP queue.
- Not taken over: UltimateDonutSMP-V2's respawn RTP and first-join RTP, because they change spawn
  behaviour.

### Chat format

- New default chat line: `OWNER >> Itz_Pekoni: message`.
- `config.yml CHAT.PREFIX-FORMAT` (default `{prefix}&8>> `) controls how the rank prefix is written, and
  is only used when the player has a prefix, so players without a rank show `Itz_Pekoni: message`.

### Hidden staff commands

- 24 staff and admin commands now declare their permission in plugin.yml, so players without access no
  longer see them in tab-complete or the command list, and the server blocks them before SmpPlugin runs:
  - `/chat`, `/freeze`, `/staffmode`, `/stafflist`, `/vanish`, `/invsee`, `/profileviewer`
  - `/punishments`, `/randomteleport`, `/fakeplayer`, `/spawnstash`
  - `/warpmanager`, `/setwarp`, `/delwarp`, `/portalmanager`, `/maintenance`, `/smpplugin`
  - `/anticheat`, `/arena`, `/ffaarena`, `/spawner`, `/fly`, `/hide`, `/disguise`
- Commands with several possible nodes list them with `;`; any one of them is enough.
- If you rename staff nodes in `staff-mode.yml`, `freeze.yml` or `invsee.yml`, the plugin.yml entry still
  uses the default node.

### Fixed (found on the first real boot on Purpur 26.3)

- `menus.yml` was reset to defaults on every start, because the old-layout check treated the new Totem
  Particles settings button as a sign of an outdated file.
- Paper and Purpur were detected as Folia, which turned off random-biome duel worlds and switched some
  scoreboard, portal, spawner and crate code to Folia mode. Detection now looks for a class only Folia has.

### Technical

- Dialog code (`com.bx.smpPlugin.dialogs`) is only in the Paper/Purpur jar. The Spigot build excludes it,
  and other code reaches it through `utils.DialogGateway`, so the Spigot jar never loads a Paper class.
- `ShopManager`, `ShopPreferenceRepository`, `AuctionHouseRepository` and the RTP, worth and sell classes
  were replaced with the UltimateDonutSMP-V2 versions. SmpPlugin's 26.3 fixes, permission registry and
  legacy permission names are kept.
- `/shop` keeps SmpPlugin's categories. Only the new Quick Buy sections were added to `shop.yml`.
- New permissions (191 total):
  - `smpplugin.admin.sellstats`
  - `smpplugin.donutplusplus` and `smpplugin.donutplusplusplus`
  - the example rank nodes `smpplugin.rtp.priority.*` and `smpplugin.rtp.cooldown.*`
- Translations:
  - English has all the new text.
  - The other seven languages drop 99 auction, RTP and worth strings whose placeholders changed; those
    show in English until translated.

## 2.0.0-alpha.3 — 12 October 2026

### Added: CoinFlip Nexus (`/coinflip`, `/cf`)

- 1v1 coin flips for the server's in-game currency: public matches, private challenges with clickable
  accept/decline, rematches, practice mode, history, statistics, 11 leaderboards, 17 cosmetic achievements and
  personal settings (animation preset, sounds, particles, readable menus, privacy, quick confirm, take-a-break).
  See [docs/coinflip.md](docs/coinflip.md).
- Money safety: amounts in whole cents; every stake, payout and refund goes through a journal
  (intent, balance change, then the balance and journal row committed together). A unique key prevents a second
  payout or refund. The result is drawn once, after both stakes, and stored before the reveal. Offline players
  get claims. Startup recovery is idempotent. Unknown outcomes are held for staff, never guessed.
- Server-side limits: min/max stake, cooldowns, open-match caps, optional daily staking, net-loss and match
  caps, minimum playtime, self-exclusion. Fee defaults to 0% and is always shown before confirming.
- 16 menus with a shared component framework, and one animation driver with Fast/Subtle/Classic/Cinematic/
  Reduced/Off presets. Closing the menu or disconnecting never affects the result.
- `/coinflipadmin`: status, inspect, audit, cancel (with reason), recover, reconcile, resolve, stats, resetstats,
  disable/enable, reload, refresh, debug. Optional Discord webhook (off by default).
- `coinflip.yml` (validated; invalid money settings pause wagering) and `coinflip-messages.yml`.
- 13 new permission nodes under `smpplugin.coinflip.*`; resolve and resetstats are never bundled.

### Anti-cheat: 12 new checks and real enforcement

- New checks: **Timer**, **No fall** (applies the skipped fall damage), **Step**, **Water walk**, **Boat fly**,
  **Anti-knockback**, **Criticals**, **Scaffold** (hidden faces, aim, tower rate), **Through-wall interaction**,
  **Inventory** (chest stealer rates, inventory walk), **Auto totem**, **Cheat client** (brand and mod channels).
  Kill aura also flags attacking while using an item and switching between distant targets in one tick.
- Enforcement: every check has `KICK-AT` and `BAN-AT`. Bans go to a **ban queue** that staff approve
  (`/anticheat bans`), or run automatically in **ban waves** with `ENFORCEMENT.BANS: AUTO`. Four different
  checks at alert level within ten minutes also queue a ban.
- Server-lag guard: timing checks pause after the server itself stalls.
- `/anticheat profile <player>` and `/anticheat bans [approve|deny|run]`; new bypass node
  `smpplugin.bypass.anticheat.player`.
- **Behaviour change:** kicks are now on by default at high, sustained violation levels. Bans wait for staff
  approval unless you switch to AUTO.

### Tests

- 184 tests (was 151): CoinFlip end-to-end money flows on SQLite (races, expiry, offline claims, crash recovery,
  failure rollback, daily caps, challenges, practice), model and config validation, leaderboard ordering, and
  the new anti-cheat logic (timer balance, face geometry, knockback ratio, ban queue persistence, client signatures).

## 2.0.0-alpha.2 — 6 October 2026

### Added: built-in anti-cheat

- New server-side anti-cheat with 11 checks: Fly, Speed, Reach, Wall hit, Kill aura, Autoclicker, Fast break,
  Nuker, Fast place, Block reach and X-ray pattern. See [docs/anticheat.md](docs/anticheat.md).
- Limits come from players' live attributes (movement speed, jump strength, gravity, interaction ranges), ground is
  detected server-side from real collision shapes, and reach is lag-compensated with recent hitbox history.
- Allows for 26.3 mechanics: spears, mace wind burst, wind charges, happy ghasts, nautiluses, slime/bed bounces,
  bubble columns. Bedrock (Floodgate) players skip movement/aim checks.
- Violation levels decay over time; staff alerts with evidence IDs; daily JSON evidence files; setback to a verified
  safe position or cancelled action for clear violations. Kicks are off by default; nothing bans automatically.
- `/anticheat` (`/ac`): alerts, status, checks, history, evidence, temporary exemptions, reset, reload.
- `anticheat.yml` with per-check thresholds; reload keeps the old settings if the file is invalid.
- Permissions: `smpplugin.security.alerts`, `.inspect`, `.manage`; exact-node bypasses
  `smpplugin.bypass.anticheat.movement|combat|world` (logged when used).
- Can be switched off in `/smpplugin features` (ANTICHEAT) and appears in `/smpplugin status`.

### Fixed

- **Plugin failed to enable on Purpur/Paper 26.3** with `NoSuchFieldError: SHOW_ADVANCEMENT_MESSAGES`, which
  left every command answering "An unexpected error occurred". 2.0.0-alpha.1 was compiled against the Spigot
  API, where the rule is `GameRule.SHOW_ADVANCEMENT_MESSAGES`; Paper and Purpur call it
  `GameRule.ANNOUNCE_ADVANCEMENTS`, and their advancement display API is also different. Both are now resolved
  at runtime (`ServerApiCompat`), so the listener works on Spigot, Paper and Purpur.
- One listener failing to start no longer disables the whole plugin. It is logged by name, its feature is
  switched off, and `/smpplugin status` lists it.

### Changed

- **Two jars.** Paper hard-forked from Spigot and the 26.3 APIs are not binary compatible, so each server family
  gets a jar compiled against its own API:
  - `SmpPlugin-<version>-paper.jar` — Paper and Purpur (default `mvn clean package`)
  - `SmpPlugin-<version>-spigot.jar` — Spigot and CraftBukkit (`mvn clean package -Pspigot`)

  Using the wrong one logs a `WRONG BUILD` warning at startup and in `/smpplugin status`.
- Tests: 133 on the Spigot profile; on the Paper profile 8 item-based tests are skipped with a stated reason,
  because Paper's `ItemStack` needs a running server.

## 2.0.0-alpha.1 — 6 October 2026

First stage of the Minecraft 26.3 upgrade. Gameplay features are unchanged. **This build has not
been run on a server yet**; treat it as a test build. See [COMPATIBILITY.md](COMPATIBILITY.md) and
[docs/UPGRADE-26.3-STATUS.md](docs/UPGRADE-26.3-STATUS.md).

### Platform

- Targets Minecraft **26.3** on Paper, Purpur, Spigot and CraftBukkit. **Requires Java 25.**
- `api-version` is now `26.3`; servers older than 26.3 refuse the jar. Use 1.5 on 1.21.x – 26.2.
- **Folia is no longer supported** (`folia-supported: false`): no Folia 26.3 build exists to test against.
- Fixed the 26.3 API break: advancement announcements now use `GameRule.SHOW_ADVANCEMENT_MESSAGES`.
- Build: maven-shade-plugin 3.6.2 with ASM 9.10.1 so Java 25 classes can be shaded.

### Administration

- New `/smpplugin status` (`smpplugin.admin.status`): platform verdict, Java version, storage guarantees,
  Redis, every optional plugin, and Paper native anti-Xray per world. The same summary is logged at startup.
- `/smpplugin reload` now checks every YAML file first. If any file has a syntax error, the reload is
  cancelled, the current settings stay active, and the file and line are shown. Previously a typo made
  that file load as empty, resetting e.g. prices to defaults.
- Configuration files that fail to parse at startup are listed in the console.
- Re-enabling the plugin inside a running server (`/reload`) is detected and reported as unsupported.
- `/smpplugin` tab completion only shows subcommands you have permission for.

### Permissions (breaking — read docs/permissions-migration-and-luckperms.md)

- One registry now defines every permission; plugin.yml and `docs/permissions-reference.md` are generated
  from it and tests keep them in sync with the code.
- Staff, admin, perk, bypass and destructive nodes default to nobody. `smpplugin.admin` (default op) still
  gives ops staff tools and administration, but **no longer** destructive actions (`/serverwipe`,
  stats wipe, `/setmoney`, `/setkills`, `/setdeaths`, `/setplaytime`, punishment deletion, inventory and
  Ender Chest editing) or **security bypasses**.
- `smpplugin.staff.mode` only opens staff mode; grant `smpplugin.legacy.staff-mode-bundle` for the 1.5 tool set.
- New per-action punishment nodes: `…tempban`, `…tempmute`, `…warn`, `…kick`. 1.5 grants keep working.
- Declared 36 nodes that were checked but never declared (they were silently op-only). Player features among
  them now default to everyone: `/lobby`, `/friends`, `/findplayer`, Auction House actions, `/safety` reading.
- Renamed `anvilmod.admin`, `safety.*`, `donutfriends.admin`, `donutauction.*` into `smpplugin.*`; old names
  still work.
- Removed the `isOp()` check for the update notice (`smpplugin.admin.updatenotify`).
- `/findplayer` has a permission (`smpplugin.findplayer`, default everyone).

### plugin.yml

- Removed the duplicated `smpplugin` command entry; added `finnishsmp` as an alias of `/smpplugin`.
- Declared `/god`; added descriptions and usage to `/logs`, `/addshards`, `/removeshards`, `/setshards`,
  `/shardshop`, `/create`, `/kill`, `/safety`, `/friends`, `/friend`, `/spawnstash`, `/fakeplayer`, `/hide`,
  `/disguise`, `/maintenance`, `/serverwipe` and `/amod`.

### Known issues

- Not boot-tested on any server. Reflection-based display features (sidebar, tablist heads, hidden scoreboard
  numbers, fake players, `/hide`) are the most likely to need fixes on 26.3.
- Money still uses floating point, online balances reach the database only on autosave, and concurrent
  offline payments can overwrite each other. These are the next stage of work.
