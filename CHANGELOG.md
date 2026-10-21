# Changelog

## 3.2.0 — unreleased

**SmpPlugin 3.2: money you can trust, and seasonal events you can switch live.** Every balance change is exact,
saved at once and recorded in a ledger. `/events` controls a new Advent Calendar, a Halloween hub and a reworked
Trick or Treat; ranks get capped, fair perks from `perks.yml`. Runs on the same versions as 3.1 (Minecraft 1.21.11
to 26.3). See [docs/economy.md](docs/economy.md) and [docs/seasonal-events.md](docs/seasonal-events.md).

### Added
- **`/transactions [page]`**, **`/baltop [page]`** and **`/eco give|take|set|reset|history|stats`** (`/smpeco`),
  each `/eco` action with its own permission. Every balance change is a row in the new `economy_ledger` table
  with who did it and why.
- **Pay rules** in `config.yml` `ECONOMY`: a balance limit, minimum and maximum payment, a pay tax and a pay
  cooldown; `ECONOMY.LEDGER.RETENTION-DAYS` and `ECONOMY.BALTOP.PAGE-SIZE`.
- **`/events`:** the status of every seasonal event (running, starts in…, over, off) and, for staff, a live
  on / off / back-to-its-dates switch that survives restarts (`data/events.yml`) and never changes dates or progress.
- **Advent Calendar** (`/advent`, `advent.yml`): a door a day from 1 to 24 December, streak bonuses, a prize for
  opening every door, gifts chosen in game, and `/advent test <date>` to try it without giving anything.
- **Halloween hub** (`/halloween`): the Spooky Calendar, Trick or Treat, a daily treat bag, season quests, a candy
  shop, candy milestones and a candy leaderboard. Candy is the new Halloween currency.
- **Trick or Treat houses:** spots set with `/trickortreat house add`, or FancyNpcs NPCs, for going door to door;
  a daily knock limit; `/trickortreat odds`.
- **Rank perks** (`perks.yml`, `/perks`): ranks by permission and priority, with capped perks for calendars,
  `/daily`, Trick or Treat and candy. Bundled ranks: `smpplugin.rank.vip`, `smpplugin.rank.vipplus`.
- **Reward vault** (`/claimitems`): reward items that do not fit go here instead of the ground.
- New permissions: `smpplugin.baltop`, `smpplugin.transactions`, `smpplugin.admin.economy.history`,
  `smpplugin.admin.economy.stats`, `smpplugin.events`, `smpplugin.admin.events`, `smpplugin.advent`,
  `smpplugin.admin.advent`, `smpplugin.perks`, `smpplugin.admin.perks`, `smpplugin.rank.vip`,
  `smpplugin.rank.vipplus`, `smpplugin.claimitems`.
- Placeholders: `events_<event>`, `advent_opened`, `advent_ready`, `advent_streak`, `advent_countdown`.
- New features in `/smpplugin features`: `ADVENT` and `PERKS`.

### Changed
- **Money is whole cents**, rounded half-up once when it comes in, and **written at once** on every change instead
  of waiting for the autosave. `DATABASE.AUTOSAVE-SECONDS` (default 60) saves the rest of the player row.
- **Balances are versioned:** an older copy can no longer overwrite a newer one.
- **Prizes are handed over at the click**, before any animation: calendar doors, `/daily`, Trick or Treat. Leaving
  during a spin no longer undoes a claim.
- **`/halloween` opens the hub**; `/halloween calendar` opens the calendar. `/halloween preview` became
  `/halloween test` and now only changes what the staff member sees, with nothing given.
- Dice and roulette stakes are held in the new `economy_holds` table, in the same transaction that takes the money.
- Vault payments by other plugins are recorded as `VAULT_DEPOSIT` / `VAULT_WITHDRAW` (they were filed as Auction
  House and sell payouts).
- Leaderboards reload stored players in the background instead of on the main thread.
- `database.yml` says plainly that MongoDB mode is a mirror of a local SQLite file, synced every
  `MONGODB.SYNC-INTERVAL-SECONDS` and at shutdown.
- SkinsRestorer is a soft dependency, so it loads before SmpPlugin.

### Fixed
- A balance change could be lost in a crash before the next autosave, or overwritten by a full player save, a
  stale login copy or CoinFlip's own write.
- `/pay` amounts above the stored precision lost cents, and very large inputs could overflow.
- The full player save reset columns it did not list to their defaults.
- The shared database connection could let another thread's statements into a running transaction.
- A database that failed to start left the plugin running every feature without storage.
- A crash during a dice or roulette round could give a stake back on top of a payout.
- A crash during the `/daily` or calendar spin lost the prize while keeping the claim.
- The Halloween preview date applied to everyone, with real prizes.
- A failed load of 3.0 player data made the player look new, so every calendar door could be opened again; a crash
  between the two statements of a player-data write could lose the value.
- Reward items that did not fit an inventory were dropped on the ground.
- Connection strings with a user name and password could be written to the log when the database failed to start.
- The crate editor and the feature toggle menu did not check the permission again on later clicks.

### Upgrading
- New tables (`economy_ledger`, `economy_holds`) and a `money_version` column are added; nothing is deleted or reset.
- `data/gambling-escrow.yml` is paid back once and renamed `.recovered`.
- `/eco` clashes with EssentialsX; use `/smpeco` when EssentialsX owns `/eco`.

## 3.1.0 — 20 October 2026

**CustomSmp 3.1: dice and a head roulette, a one-screen CoinFlip, a getting-started guide, an
in-game scoreboard editor, a cleaner tab list and real borders for the spawn and the lobby.** Runs
on the same versions as 3.0 (Minecraft 1.21.11 to 26.3).

### Added
- **`/dice <bet> [high|low|1-6]`:** bet against the house. The chance, payout and average return
  are shown with a [ROLL] button before any money moves. Odds and payouts are documented in
  `extras.yml`; a payout can never be set above the fair odds.
- **`/roulette`** (`/jackpot`): a shared pot; the players' heads spin and the head that stops in
  the middle wins. Chance = bet ÷ pot. Countdown, bet lock, quick bets, optional fee.
- **Escrow for bets:** a bet is written to `data/gambling-escrow.yml` until it is paid, and given
  back at the next start if the server crashed in between.
- **`/guide`:** a paged getting-started guide from the new `guide.yml`, with clickable commands.
- **`/sidebar`:** hide or show your scoreboard; staff edit its title and lines in game.
- **`/border`:** real Minecraft borders for the spawn and the lobby, saved in the new `borders.yml`.
- **`/spawn <player>`** for staff.
- **Roles** `smpplugin.role.manager`, `developer` and `coowner`.
- New permissions: `smpplugin.dice`, `smpplugin.roulette`, `smpplugin.guide`, `smpplugin.sidebar`,
  `smpplugin.giveaway.manage`, `smpplugin.admin.roulette`, `smpplugin.admin.sidebar`,
  `smpplugin.admin.border`, `smpplugin.bypass.border`, `smpplugin.staff.spawn.others`,
  `smpplugin.gambling.bypasscooldown`.

### Changed
- **CoinFlip:** `/cf` opens one screen with the open flips, quick stakes and a side picker; two
  clicks replace the review and confirm screens. `/cf <amount> [side]` creates a flip from chat.
  New toss animation. History, statistics and settings are under "More".
- **Giveaways** can only be started or cancelled by ops, `smpplugin.giveaway.manage` (manager,
  developer, co-owner and owner roles) and the groups in `GIVEAWAYS.MANAGER-GROUPS`.
  `smpplugin.staff.giveaway` and `smpplugin.admin.giveaway.money` were replaced; the moderator
  role no longer hosts giveaways.
- **Tab list:** the default footer shows the online count, the animated line and tips; no money,
  statistics, ping or TPS. A config.yml with the exact 3.0 footer is updated after a backup.
- **`/dice`** with an amount now bets; `/dice roll [sides]` (and `/roll`) is the free roll.
- **`/setspawn` and `/setlobby`** suggest a border; `/spawn` explains a missing spawn or an
  unloaded spawn world.

### Removed
- `/hug`, `/8ball`, `/hat`, `/trash` and `/notes` (and their aliases, permissions and settings).
  Existing staff notes stay in the `v3_staff_notes` table.

### Fixed
- `TITLE-UPDATE-TICKS` in `scoreboard.yml` was ignored; the title advanced every 2 ticks.

### Performance
- The sidebar sent every line to every player ten times a second. It now sends only lines whose
  text changed, refreshes lines every `LINE-UPDATE-TICKS` (default 10) and updates the title on
  its own interval.
- The roulette screen is one shared inventory, so a spin step is drawn once for all viewers; its
  clock runs only while a round is open.
- Border checks run only when a player moves to another block and a border is on.

## 3.0.0 — 20 October 2026

**CustomSmp 3.0: a Halloween calendar, daily rewards and challenges, live events, 63 new commands,
ready-made staff roles, and a simpler /stats.** Runs on the same versions as 2.1.0 (Minecraft 1.21.11
to 26.3) from one jar per server family.

### 🎃 Spooky Calendar (`/halloween`)
- **One door for every day of October.** Click today's door: it spins through candy, slows down
  and reveals the prize, with sounds and particles. On Paper and Purpur the reveal ends in an
  animated pop-up.
- **The calendar opens with a wave** of doors and its frame shimmers orange, black and purple.
  Locked doors show when they open, and opened doors show what was inside. Day 31 is the big
  Halloween door in the middle, and opening it is announced to the server.
- **You choose the prizes:**
  - `/halloween edit` shows every door. Click one and drop items into it.
  - Click the gold and amethyst buttons to set money and shards, then Save.
  - Console commands and defaults live in `halloween.yml`.
- **Rules you can change:** season dates, time zone, daily doors or everything on the 31st,
  catch-up for missed doors, and grace days.
- **Admin tools:**
  - `/halloween preview <date>` tests the calendar outside October.
  - `/halloween stats` shows how many players opened each door.
  - `/halloween give` and `/halloween reset`.
- **`/trickortreat`** (once an hour): a weighted treat, or a harmless trick (bats, a scare, a
  launch, darkness, a fake creeper). Rare treats are announced.
- **Halloween ambience during the season:**
  - a greeting title once a day
  - a clickable reminder about unopened doors
  - monsters drop Halloween Candy, which gives a random boost when eaten
  - zombies and skeletons spawn in pumpkin masks

### 🎁 Daily rewards and challenges
- **`/daily`:**
  - a 7-day reward strip with a streak, where day 7 is the big one
  - a weekly money bonus for long streaks
  - grace days so one missed day doesn't reset the streak
  - a spinning claim animation and a pop-up reveal
  - a join reminder
- **`/challenges`:**
  - three goals a day, the same for everyone: break blocks, place blocks, kill mobs, kill players,
    play time, or earn money
  - progress bars, automatic payouts, and a bonus for finishing all of them
  - progress comes from stats the plugin already tracks, so nothing extra runs per block or per kill

### 🎉 Live events
- **`/poll`:** clickable options, a boss-bar timer and a bar-chart result.
- **`/giveaway`:**
  - Give away the item in your hand, or money (admins only, since that money is created).
  - Players join with a click.
  - An item goes back to the host if nobody joins. A host who is offline gets it on their next join.
- **Chat games:**
  - unscramble, quick maths, fast typer and trivia
  - start by themselves every few minutes when enough players are online
  - the first correct answer in chat wins
  - muted or filtered messages never count
- **Announcer:** rotating tips from `extras.yml`. `/announcer` lists them, sends one now or pauses them.
- **`/countdown`:** a server-wide countdown with titles and a boss bar.
- **`/mail`:**
  - leave messages for offline players
  - unread mail is announced on join
  - mutes and ignores are respected

### 😄 Fun commands
- **Emotes:** `/hug`, `/slap`, `/highfive`, `/poke`, `/wave` and `/cheer`, with particles and
  sounds. They are seen by nearby players and respect `/ignore`.
- **Games:** `/dice` (animated roll), `/8ball`, `/rps` (a countdown against the server), `/rate`.
- **Others:**
  - `/firework` is harmless and can't hurt anyone.
  - `/sit` sits you down; sneak to stand up.
  - `/hat` wears the item in your hand.

### 🧰 Utility commands
- **Portable workstations:** `/craft`, `/anvil`, `/grindstone`, `/stonecutter`, `/loom`, `/smithing`
  and `/cartography`. These are rank perks.
- **`/back`:** returns you after a teleport, or after death with a perk. It never returns you into
  a duel or FFA arena or the lobby, and doesn't work in combat.
- **Information:**
  - `/near` lists nearby players with distance and direction.
  - `/seen` shows when a player was last online.
  - `/coords` shows your coordinates and can share them with your team.
  - `/online`, `/uptime` (with TPS), and `/calc`, a safe calculator that understands `1.5m` and `64x27`.
- **Items:**
  - `/trash` deletes what you put in it.
  - `/repair [all]` has a cooldown.
  - `/skull`, `/lore` and `/glow`.
- **Personal:** `/ptime` and `/pweather` change your own time and weather; `/suicide` asks you to confirm.

### 🛡️ Staff, moderation and permissions
- **Ready-made staff roles.** Give one node to a rank:
  - `smpplugin.role.helper`
  - `smpplugin.role.moderator`
  - `smpplugin.role.admin`
  - `smpplugin.role.owner`

  Each includes the one below it. No role grants anti-cheat or privacy bypasses.
- **`/perms`:**
  - list the roles
  - list every node in a role
  - explain a node
  - check why a player has a node
  - see which roles a player has
- **Watching players:**
  - `/notes <player>` keeps private staff notes; other online staff are alerted.
  - `/whois <player>` shows one player's details. The IP also needs `smpplugin.staff.alts`.
  - `/socialspy` and `/commandspy` never show login or password commands.
- **World and server tools:**
  - `/broadcast [-title]`, `/day` and `/night`, `/sun`, `/rain` and `/thunder`
  - `/smite` (harmless unless `-damage`)
  - `/butcher` never removes named, tamed or leashed mobs, or villagers
  - `/spawnmob`, `/more`, `/speed` (1 = normal, 10 = max), `/performance` (TPS, MSPT, memory,
    chunks, entities) and `/spook`
- **More than 60 new permission nodes**, all listed in `docs/permissions-reference.md`. Ops get the
  staff and admin tools through `smpplugin.admin` as before.

### 📊 /stats, simpler
- **The pop-up opens on your own stats:**
  - three short lines: money, kills and deaths, playtime
  - Search Player, Leaderboards and Close buttons
  - no player list to get lost in
- **The chest menu is one small row:** money, shards, kills, playtime and blocks mined, under your
  head, with Leaderboards and Close.
- **Unchanged:** offline players still load in the background, and disguised players still show
  their alias and no skin.

### Changed
- **`/auctionhouse` is gone; the Auction House is `/ah` only.** Menu buttons in config files written
  for 2.x that run `auctionhouse` keep working.
- **`/menu` has a new row:** daily reward, challenges, Spooky Calendar, mail and the crafting table.
  Buttons for commands a player can't use are hidden.
- **`/smpplugin setup commands <category>`** has new categories: community, fun and utility.
- **New config files:** `halloween.yml` and `extras.yml`. Every new message can be changed in
  `messages.yml` under `V3`.
- **New feature switches:** HALLOWEEN, DAILY_REWARDS, CHALLENGES, EVENTS, ANNOUNCER, MAIL, FUN,
  UTILITIES and STAFF_EXTRAS. Each is on by default.
- **New PlaceholderAPI values:** `%smpplugin_daily_streak%`, `%smpplugin_daily_ready%`,
  `%smpplugin_halloween_opened%`, `%smpplugin_halloween_ready%`, `%smpplugin_halloween_countdown%`,
  `%smpplugin_challenges_done%` and `%smpplugin_challenges_total%`.
- **Older `dialog.yml` files:** keys missing from a server's copy are read from the plugin's
  built-in copy, so new screens work without editing the file.

### Performance
- **Fast lookups:** per-player 3.0 data (streaks, opened doors, challenge progress, spy toggles)
  is loaded while the player is logging in, so every check while they play is a memory read.
- **Background writes:** saves go to the database thread in order. Data is stored in your existing
  database (SQLite, MySQL or the MongoDB bridge) in three small tables.
- **Animations cost nothing when closed:** they run only while the menu is open and stop the
  moment it closes.
- **Cheap timers:** challenge checks run every 30 seconds, and the announcer and chat-game timers
  are a time comparison when idle.

## 2.1.0 — 16 October 2026

**Runs on Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.** 2.0.0 ran on 26.3 only.

### Multi-version support
- **One jar per server family covers every version:**
  - `SmpPlugin-2.1.0-paper.jar` for Paper and Purpur
  - `SmpPlugin-2.1.0-spigot.jar` for Spigot and CraftBukkit
- **Built for the oldest version:** the jars are compiled against the 1.21.11 server API as Java 21
  bytecode.
  - 1.21.11 servers run them on Java 21.
  - 26.x servers run them on the Java 25 they already need.
- **What the server sees:**
  - `plugin.yml` now declares `api-version: 1.21`, so all of these servers load the plugin.
  - The start-up check still refuses anything older than 1.21.11.
- **Checked against the newest API:**
  - The code still compiles against the 26.3 API (`mvn clean compile -Ppaper,api-latest` /
    `-Pspigot,api-latest`).
  - Every server and Adventure method the plugin calls was confirmed to exist in the Paper and
    Spigot APIs for 26.1 to 26.3.
- **Test status:**
  - **1.21.11** runs on a Purpur 1.21.11 server.
  - **26.3** is where most of 2.x was run.
  - **26.1.1, 26.1.2 and 26.2** pass the build checks but have not been run on a live server yet.
  - **26.1** has no Paper or Purpur server to test on.
  - See [COMPATIBILITY.md](COMPATIBILITY.md).

### Fixed
- **Duel arenas on 1.21.11:** creating the generated duel worlds logged `No key layers in
  MapLike[{}]` there. They now use Minecraft's standard flat layers, which every version accepts.
- **Folia message:** it no longer names a Minecraft version.

## 2.0.0 — 16 October 2026

The first full release of SmpPlugin 2.0 (CustomSmp). It contains everything from the alpha builds
below, plus v2's home screens, a reworked tab list and proper version handling.

### Homes (from UltimateDonutSMP v2)
- **`/homes` on Paper 1.21.6+ opens v2's home screens** as pop-up dialogs:
  - **Slot grid:** your homes first, then the free slots you have, then locked slots that say what
    unlocks them. **Show More** opens more slots (3, 9, 27, then 90 by default).
  - **Manage screen per home:** Teleport, Change Icon, Rename and Delete (with a confirmation).
  - **Home icons:** pick any item for a home from a searchable list. The icon also shows in the chest
    menu and in the staff `/seehomes` view.
  - **Team Home** button when you are in a team.
  - Creating and renaming use a text box instead of typing in chat.
- **Chest menu fallback:** Spigot, servers older than 1.21.6, and Bedrock players (through Geyser)
  keep the chest menu. So does `dialog.yml SCREENS.HOMES: false`.
- **Configuration:** `dialog.yml` sections `HOMES_DIALOG`, `CREATE_HOME_DIALOG`,
  `HOME_MANAGE_DIALOG`, `CHANGE_HOME_ICON_DIALOG`, `RENAME_HOME_DIALOG` and
  `DELETE_HOME_CONFIRM_DIALOG`.
  - Locked slots read `LOCKED-TOOLTIP`.
  - `LOCKED-TOOLTIP-1`, `-2`, ... name the rank that unlocks each tier.
- **Database:** `homes` gains an `icon` column, added automatically on start.

### Tab list
- **Lighter on the server:**
  - A header, footer or name that hasn't changed is no longer re-sent. Before, every player got a
    new header and footer every 3 ticks while the title animated, and a new name every 2 seconds.
    Names are still fully resent every 30 seconds, so a missed change can't stick.
  - The animated title and line are built once per frame for everyone, instead of once per player.
  - Header and footer text is read from config.yml once per reload, not every frame.
  - The LuckPerms rank weight used for sorting is cached for 30 seconds, and refreshed at once when
    a rank changes.
  - The animation stops when nobody is online.
- **New placeholders:**
  - `%animated_line%`: a gradient bar with the same moving shine
    (`TABLIST.ANIMATION.LINE-CHAR`, `LINE-LENGTH`, `LINE-FROM`, `LINE-TO`)
  - `%rank%`: the LuckPerms prefix, or "Member"
  - `%world%`: Overworld, Nether, The End, or the world's name
- **New default layout:**
  - Header: animated title, animated line, and "Welcome, rank name".
  - Footer: money, shards, kills and playtime; then online count, ping, TPS and world; then the
    animated line and the rotating tip.
  - Existing servers keep their own header and footer; copy the new lines from the bundled
    config.yml to use them.

### Version
- **The version is now `2.0.0`** (it was `2.0.0-alpha.5`). The jars are `SmpPlugin-2.0.0-paper.jar` and
  `SmpPlugin-2.0.0-spigot.jar`.
- **`/smpplugin version`** (also `ver`, `about`) shows:
  - the installed version, and whether it is a release or a test build
  - which jar it is (paper or spigot) and the server API it was built against
  - the server and Java version
  - the latest version on GitHub, and whether an update is available
- **The update checker looks at this project's GitHub** (`LowBacon/SmpPlugin`, `version.txt` on
  `main`). Before, it asked the old author's repository and failed on every start. The in-game
  update notice links there too.
- **Version comparison follows semantic versioning:** a release is newer than its pre-releases, and
  `alpha.10` is newer than `alpha.9`. Before, `2.0.0-alpha.5` would have counted as newer than
  `2.0.0`.

## 2.0.0-alpha.5 — 15 October 2026

Rebuilds the AFK system from scratch, adds `/offend`, renames the server branding to **CustomSmp**,
and makes the built-in anti-cheat step aside for the new TitanGuard anti-cheat plugin. It also adds a
simple RTP world menu, a better spawner menu and a better tab list. The best parts of
UltimateDonutSMP v2 came across for stats, chat, homes, teams and staff tools, reworked to be lighter
on the server. Boot-tested on Purpur 26.3 (it enables, commands answer, and an existing AFK Lounge
migrates); not yet tested with players.

### Main menu (`/menu`)
- **`/menu`** (alias `/mainmenu`) is one chest screen that reaches the main features: homes, RTP,
  spawn, warps, team, friends, AFK, auction house, shop, sell, orders, shards, crates, CoinFlip,
  leaderboards, duels, FFA, bounties, ender chest, settings and rules.
- The top shows your head with money, shards, kills, playtime and team. Click it for `/stats`.
- Each button runs its command as you, so permissions and feature toggles still apply. Buttons for
  commands you can't use are hidden.
- Every button, slot, item and text is set in `menus.yml MAIN-MENU`.
- It is a chest menu, so it also works on Spigot. (v2's was a Paper dialog.)

### Stats
- **`/stats [player]` opens a stats menu** instead of printing chat lines:
  - **Player head:** rank, online or offline, team, K/D and playtime.
  - **Stats:** money, shards, kills (with deaths and K/D), kill streak and best streak, playtime,
    team, money earned and spent, blocks broken and placed, and mobs killed.
  - **Leaderboard place** (for example "#3 on the leaderboard") under money, shards, kills,
    playtime, best streak and blocks broken.
  - **Buttons:** Refresh, and Leaderboards.
- Offline players and leaderboard places are read on the database thread, so the server never waits.
- Disguised players are looked up by their alias and show a plain head.
- The console still gets a short text summary.

### Chat
- **Mentions:** writing a player's name (with or without `@`) lights it up for them, plays a sound
  and shows "X mentioned you" in their action bar. Set it up under `CHAT.MENTIONS`.
- **Public chat now respects `/ignore`.** Before, it only blocked private messages.
- **Private messages:**
  - click a received message to reply
  - a sound plays when one arrives (`sounds.yml PRIVATE_MESSAGE.RECEIVED`)
- **Colour codes:** typed colour codes are removed from `/msg` and staff chat unless the sender has
  `smpplugin.chat.color`. Before, anyone could colour their messages or hide them with `&k`.
- **Chat log:** public, team and private messages are saved (`CHAT.LOGGING`).
  - `/chatlog [player]` (`smpplugin.admin.chatlog`) shows public or team chat, newest first.
  - Pages are read in the background.
- **Mute messages:** expiry shows "Permanent" or "Expired" correctly. More placeholders work:
  `%staff%`, `%duration%`, `%id%`.

### Staff and moderation
- **`/punishments` with no name lists every punishment on the server**, with the same state and type
  filters. Each entry says whose it is.
  - Both lists load in the background.
  - The player's name is looked up once, instead of up to 50 times on every page.
- **Staff mode tools:**
  - **Inspect** (book): right-click a player to open their profile, or their inventory without
    profile access.
  - **Spectator** (ender eye, `smpplugin.staff.mode.spectator`): sneak to switch back.
- **Vanish** sends regular players a fake "left the game" and "joined the game" message
  (`STAFF-MODE.VANISH-FAKE-MESSAGES`). Staff who can see vanished players don't get them.
- **Random teleport** no longer tells the player when the staff member is vanished
  (`RANDOM-TELEPORT.NOTIFY-TARGET`).
- Staff tools ignore the double right-click the client sends, so vanish no longer toggles twice.

### Player wipe (`/playerwipe`, `/playerunwipe`)
- **`/playerwipe <player>`** shows what would be cleared for one player. **`/playerwipe <player>
  confirm`** clears it:
  - stats and balance
  - homes and team membership (a team leader's team is disbanded)
  - ender chest and crate keys
  - sell records, shop favourites and bounties
  - auction listings and claims, and orders
  - duel and FFA stats
  - friends, ignores and activity logs
- **Kept:** punishments, IP history, freeze and staff-mode records, and spawners they placed.
- **Backups:** every wipe writes a backup to `player-wipe-backups/` first. **`/playerunwipe
  <player> confirm`** puts them back.
- The whole wipe runs in one database transaction, so a failure changes nothing.
- **Permissions:** `smpplugin.admin.playerwipe` and `smpplugin.admin.playerunwipe`. Operators have
  them through `smpplugin.admin`, and the console can always use them.
- A player who has just left is refused for a few seconds, until their quit save has finished.
  Otherwise that save could write the old data back.

### Voice mute (`/vcmute`, `/vcunmute`)
- **`/vcmute <player> [time] [reason]`** mutes a player in Simple Voice Chat. With no time it is
  permanent; `/vcmute Steve 1h spam` lasts an hour.
- **`/vcunmute <player> [reason]`** lifts it.
- **While muted,** nobody hears them, and their action bar says so (at most every 4 seconds).
  - A timed mute ends on its own, without a relog.
  - Text chat is not affected.
- **History:** voice mutes show in `/punishments` (note block icon) and go to the Discord punishment
  webhook like other punishments.
- **Permissions:** `smpplugin.staff.punishments.vcmute` and `.vcunmute`.
- **Simple Voice Chat is optional.** Without it, `/vcmute` is still recorded but has nothing to
  block. The startup log says "Hooked into Simple Voice Chat" when it is active.
- **Build:** the Maven build now uses `voicechat-api` 2.6.24 (provided, not bundled).

### Homes
- **`/seehomes <player>`** (`smpplugin.staff.seehomes`, or profile viewer access): staff view of
  someone's homes, with click to teleport.
- **`/home`** with no name goes to your only home, or to the one called "home", or opens the menu.
- **`/sethome`:**
  - says how many homes you have used, for example "(3/3)"
  - is refused inside duel and FFA arenas
  - takes names of up to 16 letters, digits, `_` and `-`
- **`SETTINGS.HOME-PERMISSIONS`** gives ranks extra homes by permission (for example `group.vip: 5`),
  next to the numbered `smpplugin.homes.<n>` nodes.
- Homes are saved in the background and loaded while the player logs in.

### Teams
- **`/team info [team]`** shows any team's roster, leader, PvP setting and home status, read-only.
- Moving the team home tells the rest of the team where it went.
- The team home can't be set inside duel or FFA arenas.
- Disguised teammates show their alias and a plain head in the team menu.

### Performance
- A joining player's stats and homes are read while they log in, off the main thread. The join no
  longer waits on the database.
- Quitting saves the player in the background. A quick rejoin reuses the copy still being saved, so
  nothing is lost.
- Leaderboards share one read of the players table (refreshed every 30 seconds) instead of reading
  the whole table again for every leaderboard type every 2 seconds. That also speeds up leaderboard
  placeholders on scoreboards.
- IP history, chat logs and offline money changes are written on the database thread.

### AFK (rebuilt)

The old AFK pieces are removed:
- `AFKManager`
- the AFK Lounge
- `/setafk` points and the 600-line AFK menu
- AFK teleport areas in `menus.yml`
- the shard regions' own "send idle players to the AFK area"

These overlapped and half of them did not work. They are replaced by one system configured in the new
**`afk.yml`**:

- **AFK status:**
  - After `STATUS.IDLE-SECONDS` (300) without input a player is AFK.
  - The tab list tags them `&7[AFK]`, greys their name out and moves them to the bottom.
  - Chat says "is now AFK" and "is back (AFK for 4m 10s)". Vanished staff are only told themselves.
  - `/away` sets or clears it by hand (`smpplugin.afk.away`, default on).
  - `smpplugin.afk.statusbypass` is never marked automatically.
- **Real input only.** Head turns, walking, chat, commands, clicks, building and attacking count.
  Riding something or drifting in water does not, so water-stream AFK pools can't fake activity.
- **AFK zones.** An area where players earn shards (1 every minute by default) just for being there,
  plus the point `/afk` drops them at:
  - **On screen:** a boss bar counts down to the next shard. Each reward shows a "+1 Shard" title
    and a sound. A particle outline appears at the edge for players nearby.
  - **Rewards:** progress only counts inside the zone, and an active shard booster multiplies it.
  - **Getting there:** `/afk` goes straight to the zone when there is one, or opens a small menu
    with player counts and rewards when there are several. `/afk <zone>` goes to a specific zone.
  - **Staff:** `/afkzone` (`smpplugin.admin.afkzone`):
    - `pos1` / `pos2` mark two corners.
    - `create <name> [radius]` makes a zone from them, or a square around you.
    - `setregion`, `setspawn`, `reward <name> <shards> <time>` (e.g. `1 1m`), `rename`, `delete`,
      `list`, `show`, `reload`.
- **Idle actions** (both off by default):
  - Move long-idle players into the nearest paying zone (`SEND-TO-ZONE-SECONDS`).
  - Kick them (`KICK-SECONDS`, with a warning). Players inside a zone or with
    `smpplugin.afk.kickbypass` are never kicked.
- **Shard regions** still pause while a player is AFK.
- **Migration:** on the first start the old setup is moved into `afk.yml` once:
  - the AFK Lounge with its border, spawn and reward
  - AFK-bound cuboids
  - `/setafk` points and the AFK location (a point inside an imported zone is skipped)
- **Other places that follow the new zones:**
  - portals with an `AFK` destination (`AFK:<zone>` or the nearest zone)
  - `/smpplugin setup setafk`
  - the server wipe's protected worlds
  - the item drop guard (no drops inside a zone)
  - `/findplayer` ("in the afk")
- **Placeholders:** `%economy_afk%`, `%economy_afk_tag%`, `%economy_afk_time%`, `%economy_afk_zone%`,
  `%economy_afk_next%`, `%economy_afk_earned%`.
- **Removed:**
  - commands: `/setafk`, `/delafk`, `/afklist`, `/afklounge`, `/afk toggle`
  - permissions: `smpplugin.admin.setafk`, `smpplugin.afklounge.use`, `smpplugin.admin.afklounge`,
    `smpplugin.afk.toggle`
  - config: the `AFK-SYSTEM` settings and the `AFK-MENU` in `menus.yml`. Only `AFK-SYSTEM.SPAWN-CUBOID-NAME`
    is still read, as the fallback spawn cuboid.
### `/offend`

- `/offend <player> <offense> [time]` punishes with a preset from the new `offenses.yml`: 62 offenses from
  UltimateDonutSMP-V2, each with a type and a ladder of durations (permission
  `smpplugin.staff.punishments.offend`).
- The ladder rung comes from the player's earlier punishments **for the same offense**. In v2, earlier
  punishments of any kind counted. Removed punishments do not count. A typed time overrides the ladder.
- A `0s` rung is issued as a warning.
- `/offend <player>` previews what each offense would give that player; `/offend list` lists them.
- **Protected players**: holders of `smpplugin.admin.punishments.exempt` cannot be punished by `/offend`,
  `/ban`, `/mute` and the rest unless the staff member has `smpplugin.admin.punishments.exempt.bypass`.
  Both are security bypasses: `smpplugin.admin` does not grant them, and the console is never blocked.
- v2's `wipe` option is not included: SmpPlugin has no per-player wipe and restore system yet.

### CustomSmp branding

- Tab list header, `/smpplugin` aliases (`/customsmp`, `/csmp`), the console start-up banner, the update
  notice, the server info menu, the rules menu's Discord line, the auction bot name (`CustomBot`) and the
  premium rank name (**CustomSmp+**, formerly Donut+) now say CustomSmp.
- New PlaceholderAPI identifier `%customsmp_<placeholder>%`. `%finnishsmp_...%` still works.
- Unchanged so existing data keeps working: permission nodes (`smpplugin.donutplus`), setting ids,
  MySQL/MongoDB database names, the Redis channel and entity tags.
- **Existing servers:** files already in `plugins/SmpPlugin/` keep their old text, because admin edits are
  never overwritten. Replace `Donut+`, `DonutSMP` and `Server Name` in `config.yml`, `menus.yml`,
  `dialog.yml` and `languages/*.yml` by hand, or delete those files to get the new defaults.

### Anti-cheat

- New `anticheat.yml DEFER-TO-TITANGUARD` (default true): while the TitanGuard plugin is running, the
  built-in anti-cheat stands down, so players are never checked or set back twice. `/anticheat status`
  and the start-up report say so.

### RTP world menu

- `/rtp` now opens a small world picker: **Overworld**, **Nether** and **End**. `/rtp <world>` still
  teleports straight away. The menu existed in `rtp.yml` but nothing opened it.
  `RTP-MENU.OPEN-ON-COMMAND: false` brings back the old "RTP in the world you are in".
- Each world shows its player count and cooldown, and one status line that refreshes every second:
  - ready to teleport
  - cooldown left
  - playtime needed
  - teleport already running
- Clicking a world you can't use yet keeps the menu open.
- The menu respects `DENIED-WORLDS`, like `/rtp <world>` does.

### Tab list

- **Sorted:** highest LuckPerms rank (group weight) at the top, AFK players at the bottom
  (`TABLIST.SORTING`). Needs 1.21.2+; older servers stay alphabetical.
- **Animated title:**
  - `%animated_title%` draws `TABLIST.ANIMATION.TEXT` with a band of light sweeping across it.
  - The colours, bold and speed are configurable.
  - It pauses by itself while the server is lagging.
- **Live footer:** online count, your ping and the server TPS, each coloured green, yellow or red by how
  good they are. `%tip%` rotates through `TABLIST.TIPS` every 8 seconds.
- **New placeholders for the header and footer:** `%ping%`, `%ping_colored%`, `%tps%`, `%tps_colored%`,
  `%afk_count%`, `%tip%`, `%animated_title%`.
- **AFK players** get the `[AFK]` tag and a greyed-out name.
- **Existing servers** keep their own `HEADER` and `FOOTER`. Add `%animated_title%` and the other
  placeholders to use the new look.
- **Stat placeholders** for the header and footer: `%money%`, `%shards%`, `%kills%`, `%deaths%`,
  `%playtime%`, `%team%`.
- **Ranks show up after joining.** LuckPerms sometimes finished loading a moment after the tab list
  was drawn, so new players showed no rank. Two refreshes 1 and 3 seconds after joining catch it.

### Spawners

- **New spawner menu.** Right-clicking a spawner opens one small screen instead of the raw storage
  (`spawners.yml GUI.MAIN`; `ENABLED: false` keeps the old behaviour). It has:
  - **Info:** owner, items per hour, and a live countdown to the next drop. It says when the spawner
    is paused (chunk not loaded, or nobody nearby).
  - **Storage**
  - **Sell all**, showing what it's worth with your sell multipliers
  - **Collect XP**, showing how many levels it gives you
  - **Stack:** left-click adds every matching spawner from your inventory; right-click takes 1 out
    (owner only).
- **Spawners store XP.** Each type has `XP_PER_CYCLE`; without it the mob's vanilla value is used:
  - 2 for animals
  - 5 for monsters
  - 10 for blazes
  - 0 for golems

  XP is collected from the menu or handed over when the spawner is broken. Cap it with
  `SETTINGS.XP_STORAGE_CAP`, or turn it off with `SETTINGS.STORE_XP: false`.
- **Breaking no longer deletes stored loot.** Before, any loot still in storage was silently lost.
  `SETTINGS.PROTECT_STORED_LOOT_ON_BREAK` (default on) refuses the break until it is sold or
  collected. Admins can still use `/spawner remove`.
- **Storage menu:**
  - shows each item's sell price and total worth
  - marks full slots
  - its corner button goes back to the spawner menu
- **`/spawners` admin panel fixed:**
  - **World list:**
    - shows only worlds that have spawners, including unloaded ones, not every duel arena
    - fits 21 worlds, says so when there are more, and shows a message when there are none
    - has server-wide totals at the top
  - **Spawner lists:**
    - no longer overlap the page buttons; the old `GUI.PANEL.SIZE` / `GUI.WORLD_LIST.SIZE` options
      are gone
    - show spawners as spawner icons with their stack size
    - left-click teleports on top of a spawner; right-click opens it
    - can be sorted by stack size, stored loot or owner
  - temporary staff-bait spawners are left out
- **Loot filter:** a new button in the spawner menu lists every drop the spawner can make. Click one to
  stop or start storing it. Drops that are off never fill up storage, and they can be switched back
  on even when none are stored.
- Database: `spawners` gains `stored_xp`, `lifetime_items` and `lifetime_earnings`, added automatically
  on start.

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
