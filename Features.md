# SmpPlugin — Full Feature List

An all-in-one **Minecraft SMP core plugin**. One jar replaces the usual stack of economy, homes, TPA, crates, duels, staff, punishment, scoreboard and tablist plugins.

---

## 📦 Version & Compatibility

| | |
|---|---|
| **Plugin version** | `4.1.0`; `/smpplugin version` shows the installed build |
| **Minecraft** | `1.21.11`, `26.1`, `26.1.1`, `26.1.2`, `26.2` and `26.3` on Paper, Purpur, Spigot and CraftBukkit |
| **Folia** | Not supported |
| **Java** | 21 on 1.21.11, 25 on 26.x (the jars are Java 21 bytecode) |
| **API** | Compiled against Paper API `1.21.11` (Spigot build: Spigot API `1.21.11`), checked against 26.3; `api-version: 1.21`, `folia-supported: false` |
| **Build** | `mvn clean package` → `target/SmpPlugin-4.1.0-paper.jar` (Paper/Purpur); `-Pspigot` → `…-spigot.jar` (Spigot/CraftBukkit) |
| **Size** | ~700 Java files, ~177,000 lines of Java, 220 commands, 100+ GUI menus, 43 config files and 8 language files |
| **Update checker** | Checks GitHub (`LowBacon/SmpPlugin`, `version.txt` on `main`) for a newer version on startup |

Servers older than 1.21.11 are refused at start-up; for 1.21.10 and older use version 1.5. Folia makes the plugin
disable itself. See [COMPATIBILITY.md](COMPATIBILITY.md).

---

## 📑 Contents

1. [Core SMP](#-core-smp)
2. [Teleportation](#-teleportation)
3. [Economy](#-economy)
4. [Shards](#-shards-second-currency)
5. [Crates & Key-All](#-crates--key-all)
6. [PvP: Duels, FFA, Bounties, Combat](#%EF%B8%8F-pvp)
7. [Teams](#-teams)
8. [Social: Chat, Messages, Friends](#-social)
9. [Amethyst Tools](#-amethyst-tools)
10. [Spawners](#-spawners)
11. [Staff & Moderation](#%EF%B8%8F-staff--moderation)
12. [Punishments](#-punishments)
13. [Server Management](#-server-management)
14. [Display: Scoreboard, Tablist, Leaderboards](#-display)
15. [Player Settings](#%EF%B8%8F-player-settings)
16. [PlaceholderAPI](#-placeholderapi)
17. [Network / Multi-Server](#-network--multi-server)
18. [Integrations](#-integrations)
19. [Storage](#%EF%B8%8F-storage)
20. [Configuration Files](#%EF%B8%8F-configuration-files)
21. [How It Works](#-how-it-works)
22. [New in 3.0](#-new-in-30)
23. [New in 3.1](#-new-in-31)
24. [New in 4.0](#-new-in-40)

---

## 🆕 New in 3.0

### 🎃 Spooky Calendar (`/halloween`, `halloween.yml`)
- **The calendar:**
  - one door per day of the season (1 to 31 October by default)
  - the last door is the big Halloween prize, in the middle of the bottom row
- **Opening it:**
  - the doors appear in a wave and the frame shimmers
  - today's door spins through candy, slows down and reveals its prize
  - on Paper and Purpur, an animated pop-up finishes the reveal
- **Door states:** open (glowing jack-o'-lantern), locked (shows when it opens), opened (shows
  what was inside), and missed (when catch-up is off).
- **Prizes:**
  - `/halloween edit`: drop items into a door, click to set money and shards, then Save. These are
    stored in `data/halloween-prizes.yml`.
  - `halloween.yml` `DOORS.<day>` and `DOORS.DEFAULT` cover the rest, with console commands.
- **Rules:** time zone, month and days, `DAILY` or `ALL-ON-LAST-DAY`, catch-up, grace days, and a
  preview date for testing.
- **Admin:** `/halloween give <player> <door>`, `reset <player> [door]`, `preview <yyyy-MM-dd|off>`,
  `stats`, `reload`.
- **`/trickortreat`:**
  - hourly by default
  - treats are picked by weight
  - tricks are harmless: SCARE, BATS, LAUNCH, SPIN, DARKNESS, FAKE_CREEPER
- **Ambience during the season:**
  - a daily greeting title and an unopened-door reminder
  - Halloween Candy from monsters, which gives a random 20-second boost when eaten
  - pumpkin masks on naturally spawning zombies and skeletons

### 🎁 Daily rewards and challenges (`extras.yml`)
- **`/daily`:**
  - seven reward days, then the cycle repeats with a weekly money bonus
  - `GRACE-DAYS` days can be skipped without breaking the streak
  - spinning claim animation, pop-up reveal and a join reminder
- **`/challenges`:**
  - `PER-DAY` goals picked from `POOL` with the date as the seed, so everyone gets the same ones
  - types: BREAK_BLOCKS, PLACE_BLOCKS, KILL_MOBS, KILL_PLAYERS, PLAY_MINUTES, EARN_MONEY
  - automatic payouts every 30 seconds, plus `ALL-DONE-BONUS`

### 🎉 Events
- **`/giveaway start [seconds]`:** gives away the held item.
- **`/giveaway money <amount> [seconds] [winners]`:** gives away money that is created for it.
- Since 3.1 only senior staff can start or cancel giveaways: ops, `smpplugin.giveaway.manage` (the manager,
  developer, co-owner and owner roles) and members of the `GIVEAWAYS.MANAGER-GROUPS` groups. Anyone can join.
- **Announcer:** `ANNOUNCER.MESSAGES` rotate every `INTERVAL-SECONDS`. `/announcer list|send|toggle`.
- **`/countdown <seconds> [what]`:** titles and a boss bar for everyone.
- **`/mail send|read|clear`:**
  - offline delivery, with a join notice for unread mail
  - an inbox limit and a send cooldown
  - muted players cannot send mail

### 😄 Fun and 🧰 utilities
- **Emotes** (`EMOTES.LIST`): slap, highfive, poke and cheer, each with a message,
  particle, sound and optional push. They are seen by `NEARBY` players or the whole `SERVER`.
- **Games and extras:**
  - `/dice roll [sides]`, `/rate` (betting with `/dice` is in 3.1 below)
  - `/firework` never damages anyone
  - `/sit` (sneak to stand)
- **Workstations:** `/craft`, `/anvil`, `/grindstone`, `/stonecutter`, `/loom`, `/smithing` and
  `/cartography`, each with its own perk node.
- **Teleport and location:**
  - `/back` covers teleports, and deaths with `smpplugin.back.death`. It never goes into arenas or
    the lobby and is blocked in combat.
  - `/near`, `/seen`, `/coords [share]`
- **Items and personal:** `/repair [all]`, `/skull`, `/lore`, `/glow`, `/ptime`, `/pweather`.
- **Information:** `/online`, `/uptime`, `/calc`, and `/suicide` (asks to confirm).

### 🛡️ Staff roles and tools
- **Roles:** `smpplugin.role.helper` → `moderator` → `admin` → `owner`. Each contains the one
  before it. 3.1 adds `manager` and `developer` (admin plus giveaways) and `coowner` (same as owner).
  - Owner adds destructive actions and rank perks.
  - No role grants security bypasses.
- **`/perms`:** `roles`, `role <name>`, `explain <node>`, `check <player> <node>`, `player <player>`.
- **Player tools:**
  - `/whois <player>`
  - `/socialspy`, `/commandspy` (logins and passwords are never shown)
- **World tools:** `/broadcast [-title]`, `/day`, `/night`, `/sun`, `/rain`, `/thunder`, `/smite
  [-damage]`, `/butcher [radius] [all]`, `/spawnmob`, `/more`, `/speed`, `/performance`, `/spook`.

### 📊 Simpler /stats
- **The pop-up** shows your own stats in three lines, with Search Player, Leaderboards and Close.
- **The chest menu** is one row of five numbers under your head.

---

## 🏠 Core SMP

### Spawn & Lobby
- `/spawn`: teleport to spawn (with a spawn menu)
- `/setspawn`: set the main spawn *(admin)*
- `/lobby [survival]`: go to the lobby or pick Survival
- `/setlobby`: set the lobby location *(admin)*
- `/lobby border [radius] | reload | debug | help`: lobby admin tools
- Protected lobby: block, damage and interaction protection, plus a configurable border
- Lobby NPCs through **FancyNpcs**
- Bypass permissions: `smpplugin.bypass.lobby`, `smpplugin.bypass.lobby.border`

### Main Menu
- `/menu` (`/mainmenu`): one screen with every main feature; set it up in `menus.yml MAIN-MENU`

### Homes
- `/homes` on Paper 1.21.6+: a grid of home slots (filled, free, locked by rank) with **Show More**,
  a manage screen per home (teleport, change icon, rename, delete) and a Team Home button
- **Home icons**: pick any item for a home from a searchable list
- The chest menu is used on Spigot, for Bedrock players, or with `dialog.yml SCREENS.HOMES: false`
- `/home [name]`, `/homes` (GUI), `/sethome [name]`, `/delhome <name>`, `/renamehome <old> <new>`
- Multiple named homes per player; extra homes by rank with `SETTINGS.HOME-PERMISSIONS`
- `/seehomes <player>` *(staff)*: view and teleport to someone's homes

### Warps
- `/warp [name]`: teleport or open the warp list
- `/setwarp <name>`, `/delwarp <name>` *(admin)*
- `/warpmanager <create|delete|list> [name]` *(admin)*

### AFK
Everything is configured in `afk.yml`.
- `/afk [zone]`: go to an AFK zone (a small menu when there are several)
- `/away`: mark yourself AFK where you stand, or come back
- **AFK status**:
  - After 5 minutes without input you get an `[AFK]` tag, a grey name, and a place at the bottom of
    the tab list.
  - Chat is told "is now AFK" / "is back" (vanished staff are never announced).
  - Only real input counts: water streams and vehicles don't keep you active.
- **AFK zones**: stand inside and earn **1 shard every minute** (per zone, configurable).
  - A boss bar counts down; each shard pops up as a title with a sound.
  - A particle outline marks the edge; shard boosters multiply the reward.
- `/afkzone pos1|pos2|create|setregion|setspawn|reward|rename|delete|list|show|reload` *(admin)*: build zones in game
- Optional idle actions: move idle players into a zone, or kick them (both off by default)
- Placeholders: `%economy_afk%`, `_tag%`, `_time%`, `_zone%`, `_next%`, `_earned%`

### Player Utilities
- `/stats [player]`: player statistics
- `/playtime [player]` (`/pt`)
- `/ping [player]`
- `/nightvision` (`/nv`): toggle night vision
- `/phantom`: toggle phantom spawning for yourself
- `/settings`: player settings GUI (see [Player Settings](#%EF%B8%8F-player-settings))
- `/enderchest` (`/ec`): custom, configurable Ender Chest
- `/rename <name...|reset>`: rename the held item
- `/kill`: suicide with a confirm menu (blocked while combat-tagged)
- `/safety [reload|add <player>|give <player>]`: opens or gives the server's safety / info book
- `/rules`, `/help`: rules menu and help/server-info menu
- `/discord`, `/twitter`, `/store`, `/social` (`/media`): social links
- Custom **death messages**, shown only to players within a radius (in chunks) if you want
- Item-drop prevention, mob-spawn policy, respawn handling

---

## 🧭 Teleportation

### TPA
- `/tpa <player>`, `/tpahere <player>`
- `/tpaccept [player]`, `/tpadeny [player]`, `/tpacancel`
- `/tpauto`, `/tpahereauto`: auto-accept toggles
- Optional TPA confirm menu and TPA queue menu
- Teleport cooldown and "don't move" warmup

### Random Teleport (RTP)
- `/rtp`: simple world picker for Overworld, Nether and End, with your cooldown counting down live
- `/rtp <world>`: straight into a world
- Safe-location search with an action-bar progress display, plus a cache of ready safe spots so most teleports are instant
- Per-player cooldowns (lower per rank), a limit on concurrent RTP players and a priority waiting queue
- `/rtpq` (`/rtpqueue`): **RTP queue**, wait with other players and get dropped together at one shared random spot
- Entering combat cancels an RTP in progress
- **RTP Zones**: stand inside a cuboid, a countdown runs, then you get teleported
- **RTP Portals**: `/portalmanager <list|info|create|delete|setcuboid|setdestination|setdisplay|toggle|setpriority|sethologramhere>`
  - Walk-in portal regions with holograms and priorities
- Optional **randomized coordinates** display setting

### Other
- `/randomteleport` (`/randomtp`): teleport to a random online player *(staff)*

---

## 💰 Economy

The plugin registers itself as a **Vault economy provider**, so other plugins can use its balances.

### Money
- `/balance [player]` (`/bal`, `/money`)
- `/pay <player> <amount>`, with an optional confirm menu
- `/addmoney`, `/removemoney`, `/setmoney <player> <amount>` *(admin)*, and `/eco give|take|set|reset|history|stats`
- `/baltop` and `/transactions [page]` (your own balance history)
- Exact money: whole cents, written at once on every change, each change recorded in a ledger with who did it
  and why (see [docs/economy.md](docs/economy.md))
- Pay rules in `config.yml` `ECONOMY.PAY`: minimum, maximum, tax and cooldown
- No balance limit by default; balances are exact to the cent up to 92,233,720,368,547,758.07. `ECONOMY.MAX-BALANCE`
  sets a limit if you want one
- Tracks **money made** and **money spent** per player
- Configurable currency name, symbol and colours

### Selling
- `/sell`: sell GUI
- `/sellhand [amount]`: sell the held item
- `/sellall`: sell everything sellable (with a confirm menu)
- `/sellhistory`: your past sales
- `/worth [hand|reload]` (`/prices`): worth browser or check the held item
- **Worth display** in item lore (per-player toggle)
- Shulker box contents can be sold too (per-player setting)
- `/sellmulti`, `/sellprogress`: **sell multipliers** per category that level up the more you sell
- `/meta`: the **farming meta**, one item that sells for a bonus multiplier until it rotates
- Items missing from worth.yml get an automatic balanced price; unobtainable items are never sellable
- `/topsell` (`/sellstats`): **sell statistics** for admins (top items, sellers, activity charts, export, optional local web dashboard)

### Shop
- `/shop [reload]`: categories, purchase confirmation, remembered shop preferences
- `/shardshop`: shop that charges **shards**

### Orders Board
- `/orders [my|collect|reload]`
- Players post **buy orders** for items. Other players deliver the items and get paid.
- Search, sort, edit, deposit and delete orders, and collect items that were delivered to you
- Orders expire automatically
- **Bedrock players** get native forms through Floodgate
- Orders can **sync across servers** through Redis

### Auction House
- `/ah` `[sell|my|claims|cancel|search|reload]` (`/auctionhouse` was removed in 3.0)
- Browse by category and filter, list items, see your listings, claim expired or sold items
- Smarter search: partial names (`neth`) and enchant shortcuts (`prot 4`)
- **Quick Buy**: pin items and buy the cheapest listing in one click (dialogs on Paper/Purpur, forms on Bedrock)
- **Your Items** page with extra listing slots for CustomSmp+ / CustomSmp++ ranks, and a transaction history
- Optional quick purchase and quick sell (player settings), optional server announcements for listings and sales
- Automatic expiry and an **Auction order bot**

### Billford (rotating trader)
- `/billford`: opens the current trade
- The trade rotates on a schedule (every 3 days by default), either in sequence or randomly
- Broadcasts on rotation, has a countdown placeholder, and can be opened from an NPC

### Bounties
- `/bounty <add|set|info|list> [player] [amount]`: put money on a player's head, with confirm menus

### CoinFlip Nexus (new in 2.0.0-alpha.3)
- `/coinflip` (`/cf`): 1v1 coin flips for in-game currency. Pick a stake and heads, tails or random; another
  player stakes the same on the other side; the server flips a fair 50/50 coin; the winner gets the pot minus
  the fee shown before confirming (0% by default)
- Public matches with a browser (sort, filter, search, pages), private challenges with clickable accept/decline,
  rematches, practice mode with no currency
- Animated reveal with Fast/Subtle/Classic/Cinematic/Reduced/Off presets; closing it never changes the result
- History, statistics (gross and net), 11 leaderboards, 17 cosmetic achievements, personal settings and a
  take-a-break option
- Journaled money flow: intent, balance change and commit together, unique keys against double payouts, offline
  claims, crash recovery, staff tools (`/coinflipadmin`)
- Details: [docs/coinflip.md](docs/coinflip.md)

---

## 💎 Shards (second currency)

- `/shards [player]`
- `/shardpay <player> <amount>`
- `/addshards`, `/removeshards`, `/setshards` *(admin)*
- `/shards everywhere <status|debug> [player]` *(admin)*: inspect passive income
- **Shards Everywhere**: passive shard income per minute (permission `smpplugin.shards.everywhere`)
- **Shard Cuboids**: regions (for example at spawn) with a countdown that reward players standing inside
- **AFK zone** shard rewards (`/afk`)
- **Shard Booster** item (see Amethyst Tools)
- `/shardshop`: spend shards

---

## 🎁 Crates & Key-All

- `/crates`: crate list menu
- `/keys`: your virtual keys
- `/crate <create|delete|type|open|keys|reload|key|take|set|keyall|add|edit|remove|bind|unbind|listbound|info>` *(admin)*
- **Open types**: `GACHA` (spinning animation, direction left, right or random) and `CHOOSE_ONE` (pick a reward)
- **Reward types**: `ITEM`, `COMMAND`, `MONEY`, `SHARDS`, and enchant rewards with an enchant-select GUI
- **Rarities**: Common, Uncommon, Rare, Epic, Legendary
- In-game **crate editor**, reward preview, and **reward history**
- **Bind crates to physical chests** with visual effects
- **Key-All**: hands out keys to everyone online on a timer, with a countdown on the scoreboard

---

## ⚔️ PvP

### Duels
- `/duel [player|accept|deny|claims|reload]`
- `/create invite <player> [map]`, `/create friends <player> [map]`: create a custom duel
- `/queue [join|leave]`: matchmaking queue with map selection
- `/draw`: request or accept a draw
- `/leave`: leave a queue or match
- `/arena <create|delete|setpos1|setpos2|setreturn|setdisplay|enable|disable|queue|list|reload>` (`/duelarena`) *(admin)*
- Countdown with titles and sounds, match time limit, request timeouts
- **Arena rollback** after each match
- **Duel claims**: winners claim the loser's items through a preview menu
- Privacy modes, duel stats, **cross-server duels**

### FFA (Free-For-All)
- `/ffa [join|reload|arena ...]`: join an instanced FFA match
- `/ffastats [player]`
- `/ffaarena <create|delete|setpos|setdisplay|settings|enable|disable|list|reload>` *(admin)*
- Restores inventory, health and effects after leaving
- Arena rollback that clears projectiles, drops, fire and fluids

### Combat
- **Combat tagging** (16 s by default) with an action-bar timer
- Blocks commands while tagged
- Tags on end crystal, ender pearl and respawn anchor damage
- **Anti-stasis-chamber** protection
- **Fast crystals** (per-player toggle)
- Explosion particle and sound toggles (packet filter through ProtocolLib)
- Kill and death tracking, **kill streaks** and **highest kill streak**

---

## 👥 Teams

- `/team create | disband | invite | kick | join | leave`
- `/team home | sethome | delhome`: team home
- `/team chat`: toggle team chat
- `/team info [team]`: read-only roster of any team
- `/team pvp`: friendly-fire toggle
- Team menus (edit, kick confirm, disband confirm)
- **Lunar Client teammate overlay** (Apollo)
- Team name placeholder for the scoreboard

---

## 💬 Social

### Chat
- `/chat <help|mute|unmute|delay|clear|off>` *(staff)*
- Global mute, slow mode (delay), clear chat
- Chat filter with bypass permissions
- **Mentions**: your name in chat lights up with a sound
- Public chat respects `/ignore`
- `/chatlog [player]` *(admin)*: browse logged public and team chat

### Private Messages
- `/msg <player> <message>` (`/message`, `/tell`, `/whisper`, `/w`)
- `/reply` (`/r`)
- `/pm` (`/togglepm`): toggle receiving messages
- Click a received message to reply; a sound plays when one arrives

### Ignore
- `/ignore <player|list>`, `/unignore <player>`

### Friends / Follow
- `/friends` (`/friend`) `[list|following|followers|friends|add|remove|search|reload]`
- Follow system with **follow alerts** (configurable in settings)

---

## 🔮 Amethyst Tools

Special items, optionally **time-limited**, given with `/amethysttool give <player> <type> [duration]` *(admin)*:

| Tool | What it does |
|---|---|
| **Amethyst Drill** | Breaks 9 blocks (3×3) per strike; Efficiency V, Unbreaking III, Fortune III |
| **Amethyst Tree Chopper** | Chops entire trees instantly (vein-style) |
| **Amethyst Sell Axe** | Hit a chest to instantly sell everything inside |
| **Amethyst Shovel** | Breaks 9 blocks (3×3) per strike |
| **Amethyst Bucket** | Drains 27 water blocks at once |
| **Shard Booster** | 4× shard multiplier (countdown shown on the scoreboard) |

- Particles and sounds, excluded worlds (for example `duels`), item-ID security check, automatic expiry task

---

## 🧱 Spawners

- `/spawner` (`/spawners`) `[give|info|panel|split|remove|reload]`
- Stacked spawners (up to 100,000 per block by default)
- **Internal loot storage**: spawners generate drops into storage instead of spawning mobs
- **Spawner menu** (right-click): one small screen with the spawner's info and a next-drop timer, plus
  Storage, Sell all, Collect XP, and add or take spawners from the stack
- **`/spawners` panel** *(staff)*: worlds with spawners and server totals.
  - Each world's spawners can be sorted.
  - Left-click teleports to a spawner; right-click opens it.
- **Stored XP** per spawner (`XP_PER_CYCLE`, vanilla values by default)
- Stored loot is protected: a spawner with loot inside can't be broken until it is emptied
- Storage GUI with item worth, admin panel, and a list of spawners per world
- Access modes: owner only, owner and team, or everyone
- **Anti-ESP**: spawners are hidden from players outside a reveal radius (stops X-ray and ESP finding bases)

---

## 🛡️ Staff & Moderation

### Staff Mode
- `/staffmode [player|reload]` (`/staff`)
- Hotbar tools: **Vanish**, **Freeze**, **Inspect**, **Staff List**, **Spectator**, **Better View**, **Random Teleport**
- Vanishing sends regular players a fake leave message, and a fake join message when you reappear
- Saves your inventory and restores it afterwards; can persist through quitting and restarts
- `/vanish`, `/stafflist`

### Staff Tools
- `/freeze <player>`: freeze for screenshare, with a frozen-players menu and alerts
- `/invsee <player>` (`/inventorysee`): view an inventory
- `/ecsee <player>`: view an Ender Chest
- `/profileviewer <player>` (`/pv`): profile and homes browser
- `/alts <player>`: alternate accounts by shared IP
- `/logs <player>`: player activity logs GUI
- `/teleport` (`/tp`, `/tphere`, `/tpall`) `<player | here <player> | all | top | x y z [world]>`
- `/fly [player]`, `/heal [player]`, `/feed [player]`, `/god [player]`
- `/gamemode <mode> [player]` (`/gm`, `/gmc`, `/gms`, `/gma`, `/gmsp`)

### Alerts
- `/report <player> <reason>`: report to staff
- `/helpop <message>`: ask staff for help
- `/staffchat <message>` (`/sc`)
- Cooldowns with a bypass permission, staff join/leave alerts
- Can run **network-wide** through Redis

### Anti-Cheat (new in 2.0)
- 23 server-side checks: **Fly, Speed, Timer, No fall, Step, Water walk, Boat fly, Reach, Wall hit, Kill aura,
  Anti-knockback, Criticals, Autoclicker, Fast break, Nuker, Fast place, Block reach, Scaffold, Through-wall
  interaction, X-ray pattern, Inventory, Auto totem, Cheat client**
- Attribute-aware limits, server-side ground detection, lag-compensated reach, 26.3 mechanics (spears, mace,
  wind charges, happy ghasts) allowed for
- Staff alerts with evidence IDs, `/anticheat` (`/ac`) for history, evidence, profiles, the ban queue, exemptions and status
- Setback, cancelled action or applied fall damage for clear violations; kicks at high sustained levels;
  bans go to a staff-reviewed queue (or automatic ban waves with `ENFORCEMENT.BANS: AUTO`)
- Details and limits: [docs/anticheat.md](docs/anticheat.md)

### Anti-Abuse
- **Anvil Moderation**: catches banned words in anvil renames and **escalates punishments automatically** (mute → tempban 7d → 14d → 30d → 50d)
  - `/amod <add|reload> [word]`
- **Spawn Stash**: spawns temporary staff "bait" stashes that alert staff when someone finds them. They are cleaned up and rolled back automatically.
  - `/spawnstash [spawn|list|remove|reload] [type]`
- **Hide / Disguise**: scramble your identity or disguise as a preset alias (name and skin)
  - `/hide <disguise|status|scramble|remove|list>`, `/disguise`
- `/fakeplayer`: spawns a packet-based fake player with skin lookup (needs ProtocolLib)
- Crash protection manager

---

## 🔨 Punishments

- `/vcmute <player> [time] [reason]` and `/vcunmute <player>`: mute someone in Simple Voice Chat
- `/playerwipe <player> [confirm]` and `/playerunwipe <player> [confirm]` *(admin)*: wipe one player's
  progress, backed up first and restorable

| Command | Permission |
|---|---|
| `/ban <player> [reason]` | `smpplugin.staff.punishments.ban` |
| `/tempban <player> <time> [reason]` | `smpplugin.staff.punishments.tempban` |
| `/mute <player> [reason]` | `smpplugin.staff.punishments.mute` |
| `/tempmute <player> <time> [reason]` | `smpplugin.staff.punishments.tempmute` |
| `/warn <player> [reason]` | `smpplugin.staff.punishments.warn` |
| `/kick <player> [reason]` | `smpplugin.staff.punishments.kick` |
| `/blacklist <player> [reason]` | `smpplugin.staff.punishments.blacklist` |
| `/unban` (`/pardon`) | `smpplugin.staff.punishments.unban` |
| `/unmute` | `smpplugin.staff.punishments.unmute` |
| `/unblacklist` | `smpplugin.staff.punishments.unblacklist` |
| `/punishments [player]` (`/phistory`): one player, or everyone with no name | `smpplugin.staff.punishments.view` |
| `/offend <player> <offense> [time]` | `smpplugin.staff.punishments.offend` |

`…ban` also grants `…tempban` and `…mute` also grants `…tempmute`. The 1.5 bundles `…create` and `…remove` still work.

- **`/offend`**: 62 preset offenses in `offenses.yml` (cheating, chat spam, duping, X-ray, racism…). Each has a
  punishment type and a ladder of durations; the player's earlier punishments **for the same offense** pick the
  rung, so a first chat-spam offense is a warning and a third is a 30-day mute. `/offend <player>` previews
  what every offense would give that player; `/offend list` lists them.
- **Protected players**: `smpplugin.admin.punishments.exempt` cannot be punished unless the staff member has
  `smpplugin.admin.punishments.exempt.bypass` (applies to every punishment command)

- Time formats: `30s`, `15m`, `2h`, `5d`, or combined like `5d 15m 30s`
- Full history GUI with filtering and sorting, plus deleting records (`smpplugin.staff.punishments.delete`)
- **Discord webhook** embeds with the player's avatar and skin render

---

## 🔧 Server Management

### `/smpplugin` (`/fsmp`)
- `reload`: reload all configs
- `statswipe`: stats-wipe menu with confirmation
- `optimize`: runtime optimization monitor and adaptive task skipping
- `features`: **turn any feature on or off in-game** (feature toggle menu)
- `maintenance`
- `setup status | apply single-paper confirm | setspawn | setafk | commands [all|starter|economy|market|pvp|staff|admin|setup]`: **setup wizard** for new servers

### Other Admin Tools
- `/maintenance <on|off|status|setlobby>`: maintenance mode that redirects players to a lobby (synced across the network)
- `/serverwipe <preview|status|prepare|confirm|cancel>`: **season reset** with world backups, protected worlds and expiring confirmation tokens
- `/clearlag`: manual or scheduled entity cleanup
- `/cuboid <wand|create|delete|list|bind <cuboid> <spawn|shard|rtp-zone> <true|false>|reload>`: region tool
- `/addkills`, `/setkills`, `/adddeaths`, `/setdeaths`, `/addplaytime`, `/setplaytime`
- `/servers`: network server status menu

### Feature Toggles
Every one of these systems can be disabled on its own:

Chat · Ignore · Messaging · Bounty · Cuboids · AFK · Shards · Warps · Teams · Billford · Homes · Leaderboards · Night Vision · Phantom · RTP · RTP Zone · Sell · Worth · Settings · Shop · Ender Chest · Gamemode · Social · Spawn · Stats · TPA · TPA Auto · Find Player · Crates · Rules · Help · Network Servers · Scoreboard · Tablist · Auction House · Orders · Duels · FFA · Staff Mode · Staff Chat · Staff Alerts · Spawn Stash · Freeze · Invsee · Profile Viewer · Punishments · Spawners · ClearLag · Portals · Amethyst Tools · Combat · Fast Crystals · Key-All · Lunar Rich Presence · Lunar Team View · Optimization · Maintenance · Hide · Friends · Safety

---

## 📊 Display

### Scoreboard
- Packet-based sidebar with an animated gradient title
- Shows money, shards, kills, deaths, key-all countdown, playtime, team, shard booster and shard cuboid
- Scoreboard numbers are hidden

### Name tags
- The rank prefix (from LuckPerms or Vault, the same one chat uses) above each player's head, on every scoreboard
- `NAMETAGS` in `config.yml`: prefix and suffix format (PlaceholderAPI works), name colour (`AUTO` takes the prefix
  colour), how often ranks are checked, and whether to take over another plugin's team
- Steps aside on its own when TAB or NametagEdit runs, on Folia, and for hidden players

### Tablist
- Custom header and footer, LuckPerms role prefixes, and live refresh when ranks or skins change (LuckPerms and SkinsRestorer bridges)
- **Sorted by rank** (LuckPerms group weight), AFK players at the bottom with a grey name
- **Animated title** (`%animated_title%`): a band of light sweeps across the server name
- **Live footer**: coloured ping and TPS, online count, and rotating tips (`%tip%`)
- Stat placeholders: `%money%`, `%shards%`, `%kills%`, `%deaths%`, `%playtime%`, `%team%`, `%rank%`, `%world%`
- `%animated_line%`: a gradient bar with a moving shine
- Only changed headers, footers and names are sent, so a big player count costs little

### Stats
- `/stats [player]`: your own stats (or another player's) in a short pop-up on Paper/Purpur, with Search
  Player and Leaderboards; a one-row chest menu on Spigot or when `SCREENS.STATS` is off
- `/leaderboard [type]` (`/lb`, `/top`, `/leaderboards`) with a GUI
- **12 types**: money, shards, kills, deaths, playtime, blocksPlaced, blocksBroken, mobsKilled, killStreak, highestKillStreak, moneySpent, moneyMade

### Other
- **Lunar Client Rich Presence** (Apollo)
- Hover over a name in chat to see that player's stats
- **8 languages**: English (`en_US`), German (`de_DE`), Spanish (`es_ES`), French (`fr_FR`), Indonesian (`id_ID`), Portuguese BR (`pt_BR`), Russian (`ru_RU`), Chinese (`zh_CN`)

---

## ⚙️ Player Settings

`/settings` opens the settings screen: a pop-up **dialog** with categories (Chat, Notifications, PvP,
Visuals, Privacy, General) on Paper and Purpur, and the chest menu on Spigot. Each player can toggle 50
options, including:

| | | |
|---|---|---|
| Public Chat | Private Messages | Server Broadcasts |
| Team Chat Visibility | Team Chat Mode | Lunar Teammates |
| TPA Confirm Menus | Quick Auction Purchase / Sell | Destroy Pearl on Death |
| Pay Confirm Menus | TP Auto | Hotbar Messages |
| Notification Sounds | Follow Alerts | Display CustomSmp+ |
| Chainmail on Respawn | Explosion Particles / Sounds | Teleport Alerts |
| Fast Crystals | Totem Particles | Randomized Coords |
| TPA / TPA-Here Requests | Payments | Worth Display |
| Join/Leave, Advancement, Death Messages | Pay / Bounty / Auction / Order Alerts | Combat Timer |
| Show Scoreboard | RTP Coordinates | Hide All Players |
| Quiet Spawn | Duel Music / Requests | Team Invites |
| Sell Shulker Contents | Private Transactions | Mob / Phantom Spawn, Night Vision |

Server owners can hide any option (`ENABLED: false`), set the default new players start with (`DEFAULT:`),
and add their own buttons that run commands, all in `menus.yml`. Values are also available as
`%economy_setting_<id>%`.

---

## 🧩 PlaceholderAPI

**Expansions:** `economy`, `smpplugin`, `fsmp`, `finnishsmp` (aliases of each other), `hide`, `economylb`, `economyrank`

### Player placeholders (`%economy_<x>%`)
| Placeholder | Value |
|---|---|
| `money`, `nicestMoney`, `money_formatted`, `money_short_formatted` | Balance (raw, short, formatted) |
| `shards`, `nicestShards`, `shards_formatted`, `shards_short_formatted` | Shards |
| `kills`, `deaths`, `killStreak`, `highestKillStreak` | PvP stats |
| `playtime` | Playtime |
| `blocksPlaced`, `blocksBroken`, `mobsKilled` | Activity stats |
| `moneySpent`, `moneyMade` | Economy stats |
| `x`, `y`, `z`, `coords` | Location |
| `ping`, `username`, `team` | Misc |
| `keyall_countdown`, `booster_countdown`, `rtp_countdown`, `billford_countdown` | Timers |
| `shard_cuboid_display`, `shard_cuboid_status`, `shard_cuboid_name` | Shard cuboid |
| `money_symbol`, `money_name`, `shards_symbol`, `shards_name` (+ `_color`, `_colored`, `_plural`) | Currency formatting |
| `donutplus`, `randomized_coords` | Settings |

### Leaderboards
- `%economy_top_<type>_<pos>_<name|value|value_short|rank|display>%`, for example `%economy_top_money_1_name%`
- `%economylb_<type>_<pos>_<name|value|value_short|rank|display>%`
- `%economyrank_<type>%`: the player's position on that leaderboard

### Hide
- `%hide_active%`, `%hide_name%`, `%hide_plain_name%`, `%hide_mode%`, `%hide_alias%`, `%hide_skin%`

---

## 🌐 Network / Multi-Server

When **Redis** is enabled (`database.yml` + `network.yml`):
- Network-wide **staff chat**, **helpop**, **reports** and staff join/leave alerts
- **Server status** menu (`/servers`)
- **Orders** sync between servers
- **Maintenance** sync
- **Cross-server duels**
- BungeeCord plugin channel for sending players between servers
- Falls back to local delivery if Redis goes down

---

## 🔌 Integrations

All of these are **optional**. If one is missing, only the features that depend on it turn off.

| Plugin | Used for |
|---|---|
| **PlaceholderAPI** | Placeholders |
| **Vault** | SmpPlugin acts as the economy provider |
| **LuckPerms** | Role prefixes, tablist refresh |
| **ProtocolLib** | Explosion particle filter, Hide, fake players, worth display packets |
| **SkinsRestorer** | Disguise skins, tablist refresh |
| **Apollo (Lunar Client)** | Rich presence, teammate overlay |
| **Floodgate** | Bedrock forms for Orders |
| **FancyNpcs** | Lobby and Billford NPCs |
| **Multiverse-Core** | Multi-world support |
| **LPC**, **NickPlus** | Chat formatting and nicknames |
| **EconomyShopGUI**, **UltraCosmetics**, **DeluxeMenus** | SmpPlugin loads *before* these so its economy is ready for them |

---

## 🗄️ Storage

Set in `database.yml`:

| Type | Notes |
|---|---|
| **SQLite** *(default)* | `data/data.db`, no setup needed |
| **MySQL** | Host, port, database, user, password, auto-create database |
| **MongoDB** | A mirror, not a live store: the plugin works on a local SQLite file (`data/mongodb-cache.db`) and copies it to MongoDB every `MONGODB.SYNC-INTERVAL-SECONDS` and at shutdown. Not for several servers sharing one database. |
| **Redis** *(optional)* | Only for network features, not main storage |

- Money is written at once on every change; the rest of a player's row every `AUTOSAVE-SECONDS` (60 by default)
  and when they leave
- If the database cannot start, the plugin stops instead of running without storage
- SQLite, Redis and commons-pool are shaded and relocated into the jar to avoid conflicts with other plugins

---

## 🗂️ Configuration Files

| File | Controls |
|---|---|
| `config.yml` | Main settings: locations, portals, chat, AFK, fly, worth lore, crystals, anchors, ender chest, Lunar, shards, key-all, teams, leaderboards, tablist, optimization, clearlag, combat, RTP zone, teleport cooldown, bounty, amethyst tools, commands |
| `database.yml` | Storage type and Redis |
| `network.yml` | Multi-server settings |
| `messages.yml`, `languages/*.yml` | All messages, 8 languages |
| `menus.yml` | GUI layouts, including which `/settings` options exist and their defaults |
| `dialog.yml` | Pop-up dialog screens on Paper/Purpur (`/settings`, `/rtpq`) |
| `scoreboard.yml` | Sidebar |
| `sounds.yml` | Sounds |
| `shop.yml`, `worth.yml` | Shop, Auction House Quick Buy, sell prices, farming meta, sell statistics dashboard |
| `orders.yml`, `auction-house.yml`, `filter.yml` | Orders and Auction House (categories and filters) |
| `crates.yml`, `enchantments.yml` | Crates and the enchant picker |
| `billford.yml` | Rotating trader |
| `duels.yml`, `ffa.yml` | PvP modes |
| `rtp.yml` | Random teleport, rank cooldowns and priorities, RTP queue |
| `lobby.yml` | Lobby |
| `afk.yml` | AFK status, AFK zones and their rewards |
| `amethyst-tools.yml` | Amethyst tools |
| `spawners.yml` | Spawners and anti-ESP |
| `spawn-stash.yml` | Bait stashes |
| `staff-mode.yml`, `freeze.yml`, `invsee.yml` | Staff tools |
| `anvil-moderation.yml` | Banned anvil words and escalation |
| `hide.yml` | Hide and disguise aliases |
| `discord.yml` | Discord webhooks |
| `death-messages.yml` | Death messages |
| `ender-chest.yml` | Custom Ender Chest |
| `server-wipe.yml` | Season reset |
| `anticheat.yml` | Anti-cheat checks, thresholds, enforcement and ban queue |
| `coinflip.yml`, `coinflip-messages.yml` | CoinFlip limits, fee, animation, leaderboards and all its messages |
| `halloween.yml` | The Halloween hub, Spooky Calendar, Trick or Treat, candy, quests, candy shop and milestones |
| `advent.yml` | The Advent Calendar, its streak bonuses and completion prize |
| `perks.yml` | Ranks (VIP, VIP+ …) and what each one adds |
| `extras.yml` | Daily rewards, challenges, giveaways, dice, roulette, mail and other 3.0 extras |
| `guide.yml`, `borders.yml` | `/guide` pages; spawn and lobby borders |

---

## 🧠 How It Works

### Startup order
1. **Version check**: disables itself on unsupported versions
2. **Configs and language** are loaded
3. **Database** connects (SQLite, MySQL or MongoDB) and recovers any pending server wipe
4. **Data managers** start: player data, economy (registered with Vault), chat, friends, teams, homes, bounties, warps, cuboids, spawn, AFK, lobby
5. **Gameplay managers** start: combat, TPA, shards, crates, shop, orders, auction house, duels, FFA, spawners, staff, punishments and the rest
6. **Network** connects: Redis, BungeeCord channel, staff chat and alerts, maintenance, cross-server duels, Discord webhooks
7. **Display** starts: scoreboard, tablist, hide, teleports, RTP, portals
8. **Listeners and commands** are registered (disabled features are skipped)
9. **18 background tasks** start: scoreboard, tablist, shard income, shard cuboids, RTP zones, clearlag, key-all, auto-save, AFK, Lunar teammates, Billford rotation, order and auction expiry, auction bot, amethyst tool expiry, spawner generation, duel and FFA match timers
10. **PlaceholderAPI** expansions are registered
11. **Update check** runs asynchronously

### Code layout
```
com.bx.smpPlugin
├── SmpPlugin.java   main class, startup and shutdown
├── managers/        feature logic (89 classes)
├── listeners/       game event handlers (36)
├── commands/        command executors and tab completers (108)
├── menus/           inventory GUIs (85)
├── models/          data classes (73)
├── tasks/           repeating background tasks (18)
├── api/             PlaceholderAPI expansions (8)
├── storage/         repositories (auction house, shop preferences)
├── hooks/           Vault economy hook
├── amethyst/        amethyst tools
├── enderchest/      custom ender chest
├── staff/           staff mode sessions and tools
├── freeze/, invsee/ moderation helpers
└── utils/           colours, items, scheduler, packets, etc.
```

### Folia
Scheduling goes through `SpigotScheduler`, which has a Folia code path, but version 2.0 refuses to run on Folia
until a Folia 26.3 build exists and the modules are verified there.

### Permissions
- Full list: [docs/permissions-reference.md](docs/permissions-reference.md) (generated from `PermissionRegistry`).
- `smpplugin.admin` (default: op): staff tools, administration and operational bypasses — **not** destructive
  actions or security bypasses, which must be granted individually.
- `smpplugin.staff.mode`: opens staff mode only; each tool has its own node.
- Player features default to everyone (messaging, ignore, helpop, report, Ender Chest, lobby, friends,
  Auction House actions, safety book).
- Upgrading from 1.5: [docs/permissions-migration-and-luckperms.md](docs/permissions-migration-and-luckperms.md).

---

## 🆕 New in 3.1

### 🎲 /dice
- **`/dice <bet> [high|low|1-6]`** against the house. Leave the bet type out to roll against the
  house's die (a tie gives the bet back, `TIE-REFUNDS`).
- **Odds:** beat the house 41.7%, high or low 50%, one number 16.7%. The payout is the total a win
  pays back; defaults 1.9x, 1.9x and 5.7x (about 95% back on average). A payout above the fair one
  is lowered to it, so the house never loses on average.
- **Before any money moves** the bet, chance, payout and average return are shown with
  **[ROLL]** and **[Cancel]** (`CONFIRM`). **[Again]** repeats a bet in one click.
- The dice are rolled by the server (SecureRandom) before the animation; the stake is held in
  escrow until the roll is paid once, and given back after a crash.
- Limits, cooldown and the big-win broadcast are in `extras.yml` `DICE`. `/dice roll [sides]` is a
  free roll.

### 🎰 /roulette (head jackpot)
- Everyone's bet goes into one pot. When `MIN-PLAYERS` are in, a `COUNTDOWN-SECONDS` countdown
  starts; bets lock `LOCK-SECONDS` before the end.
- The server draws the winner (chance = bet ÷ pot), then a reel of the players' heads spins,
  slows down and stops on that head in the middle. Everyone watching shares one screen.
- The winner gets the pot minus `FEE-PERCENT`. A round nobody else joins is refunded after
  `WAIT-TIMEOUT-SECONDS`; a server stop before the draw refunds everyone, during the spin pays
  the winner. Staff can cancel a waiting round with `/roulette cancel`.

### 🪙 CoinFlip
- `/cf` is one screen: open flips as heads (click to arm, click again to join or to cancel your
  own), five quick stakes, a heads / tails / random picker and "another amount".
- `/cf <amount> [heads|tails|random]` creates a flip from chat. Large stakes still get the review screen.
- The flip animation tosses the coin into the air, flashes the colour of the face showing and
  lands on the stored result; the winner's head lights up and the loser's becomes a skull.

### 📖 /guide
- Pages from `guide.yml`, with clickable commands, Previous / Next and `/guide topics`.
- A command line only shows when the command exists (from any plugin), its feature is on and the
  reader may use it.

### 📋 Scoreboard and tab list
- `/sidebar` hides or shows your own sidebar. Staff edit the text in game:
  `/sidebar lines|add|set|insert|remove|title|reload`.
- Only changed lines are sent; lines refresh every `LINE-UPDATE-TICKS`.
- `%player_name%`, `%server_online%` and `%server_max_players%` work without PlaceholderAPI.
- The default tab list footer no longer shows money, statistics, ping or TPS.

### 🧱 Spawn, lobby and borders
- `/border spawn|lobby set <size> [here]`, `center`, `warning`, `damage`, `mode player|world`, `on|off`.
- PLAYER mode sends the border to players inside the square and stops walking, riding, flying, ender
  pearls and chorus fruit from leaving; teleport commands still work. WORLD mode sets the world's own
  border and puts the old one back when it is turned off.
- `/border lobby` also makes the lobby area the same square.
- `/spawn <player>` for staff (`smpplugin.staff.spawn.others`), and clearer messages when the spawn is
  not set or its world is not loaded.

### 🧹 Removed
- `/hug`, `/8ball`, `/hat`, `/trash` and `/notes`.

---

## 🆕 New in 4.1

- **Ranks above heads.** The name tag above a player shows their rank prefix, e.g. `Owner` (see Name tags above).
- **No balance limit.** `ECONOMY.MAX-BALANCE` is `0` (no limit) by default. Balances are stored as whole cents in
  `players.money_cents`, exact up to 92,233,720,368,547,758.07; a deposit past that is refused, never wrapped around.
  A config still on the 4.0 default of 10 trillion is moved to no limit; a limit you chose yourself is kept.

## 🆕 New in 4.0

### 💵 Economy you can trust
- **Whole cents.** Amounts are rounded half-up to the cent once, when they come in; nothing is stored as a
  fraction of a cent. (4.0 also capped balances at 10 trillion by default; 4.1 removed the cap.)
- **Saved at once.** Every balance change is written in the background the moment it happens, in one database
  transaction with its ledger row. A crash loses at most the writes still queued, never a whole autosave interval.
- **Versioned.** Each balance has a version; an older copy can never overwrite a newer one (an offline payment
  made while the player logs in, a full player save racing a payment, CoinFlip's own writes).
- **Ledger.** Every change is a row in `economy_ledger`: player, other player, reason, amount, balance after, who
  did it and a note. `ECONOMY.LEDGER.RETENTION-DAYS` prunes it daily (0 keeps everything).
- **Commands:** `/transactions [page]`, `/baltop [page]`, `/eco give|take|set|reset <player> <amount> [note]`,
  `/eco history <player> [page]`, `/eco stats [hours]`. Each `/eco` action has its own permission.
- **Bets:** dice and roulette stakes are held in `economy_holds` in the same transaction that takes the money, and
  paid back at the next start if a crash interrupted the round.

### 🗓️ Seasonal events (`/events`)
- One status per event: **running**, **starts in …**, **over for this year** or **switched off**.
- Staff: `/events <advent|halloween|trickortreat> on|off|auto`, or left click / right click / shift click in the
  menu. ON runs the event outside its dates, OFF stops it during them, AUTO follows the dates again. The switch is
  kept in `data/events.yml` (a damaged file is never overwritten) and every change is logged with who made it.
- The server is told once when an event starts or ends. Placeholders: `%smpplugin_events_<event>%`.

### ❄️ Advent Calendar (`/advent`, `advent.yml`)
- A door a day from 1 to 24 December (any month and days can be set), with catch-up and grace days that may run
  into January.
- The gift is the player's the moment they click; the spin and the pop-up only show it.
- **Streaks:** days in a row with a door opened; `STREAK.BONUSES` pay once at 3, 7, 14 and 24 days by default.
- **Completion prize** for opening every door.
- `/advent edit` to choose gifts in game, `/advent give|reset|stats|reload`, and
  **`/advent test <date>`**: see the calendar as on that day, only for you, with nothing given or saved.
- Placeholders: `advent_opened`, `advent_ready`, `advent_streak`, `advent_countdown`.

### 🎃 Halloween hub (`/halloween`)
- **Spooky Calendar** (`/halloween calendar`), on the same engine as Advent, paid at the click.
- **Candy:** the Halloween currency, earned from treats, quests, the daily treat bag and monsters; it belongs to
  one season. `/halloween candy` shows yours; staff use `/halloween candy <player> give|take|set <n>`.
- **Daily treat bag:** a free bag of money and candy every day of the season.
- **Quests:** season goals (monsters, a mob, a block, knocks, doors, candy eaten) with candy and a reward.
- **Candy shop:** items, shards, money or console commands (crate keys, titles from other plugins), with a
  per-season limit and a two-click buy.
- **Milestones:** prizes for the candy earned this season, given the moment they are reached.
- **Leaderboard:** the top candy earners this season.

### 🚪 Trick or Treat (`/trickortreat`)
- Decided and paid at the knock; the knock and the reveal only show it.
- Treat weights are checked when read; `/trickortreat odds` shows each treat's real chance.
- A wait between knocks and a daily limit. **Houses:** spots set with `/trickortreat house add <id>`, or FancyNpcs
  NPCs named in `TRICK-OR-TREAT.NPCS`; with `REQUIRE-HOUSE` players go door to door and the wait counts per house.
- Tricks are harmless: a scare, bats, a launch with slow falling, a spin, darkness, a fake creeper.

### ⭐ Rank perks (`/perks`, `perks.yml`)
- Ranks are permissions with a priority; there is no built-in order and any number of ranks can exist. A player
  gets the perks of their highest rank only.
- Perks: more calendar money and shards, streak grace days, more `/daily` money and shards, a shorter Trick or
  Treat wait, extra knocks and more candy. Each has a hard limit in code and an optional lower `CAPS` value.
- No perk touches gambling odds, prices, anti-cheat or moderation. `/perks check <player>` shows staff which rank a
  player gets.

### 📦 Reward vault (`/claimitems`)
- Reward items that do not fit an inventory (calendar doors, treats, milestones, the shop, giveaways handed back)
  go to the vault instead of the ground, and are handed over on join or with `/claimitems`.
