# SmpPlugin — Full Feature List

An all-in-one **Minecraft SMP core plugin**. One jar replaces the usual stack of economy, homes, TPA, crates, duels, staff, punishment, scoreboard and tablist plugins.

---

## 📦 Version & Compatibility

| | |
|---|---|
| **Plugin version** | `2.0.0-alpha.4` (test build) |
| **Minecraft** | `26.3` on Paper, Purpur, Spigot and CraftBukkit |
| **Folia** | Not supported in 2.0 (no Folia 26.3 build exists) |
| **Java** | 25 |
| **API** | Paper API `26.3.build.157-beta` (Spigot build: Spigot API `26.3-R0.1-SNAPSHOT`), `api-version: 26.3`, `folia-supported: false` |
| **Build** | `mvn clean package` → `target/SmpPlugin-2.0.0-alpha.4-paper.jar` (Paper/Purpur); `-Pspigot` → `…-spigot.jar` (Spigot/CraftBukkit) |
| **Size** | ~590 classes, ~152,000 lines of Java, 145 commands, 110+ GUI menus, 36 config files |
| **Update checker** | Checks GitHub (`BeestoXd/SmpPlugin`) for a newer version on startup |

Older servers refuse the jar (api-version 26.3); for 1.21.10 – 26.2 use version 1.5. Folia makes the plugin
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

### Homes
- `/home [name]`, `/homes` (GUI), `/sethome [name]`, `/delhome <name>`, `/renamehome <old> <new>`
- Multiple named homes per player

### Warps
- `/warp [name]`: teleport or open the warp list
- `/setwarp <name>`, `/delwarp <name>` *(admin)*
- `/warpmanager <create|delete|list> [name]` *(admin)*

### AFK
- `/afk`: go to an AFK zone (menu if there are several)
- `/setafk [name]`, `/delafk <name>`, `/afklist` *(admin)*: manage AFK zones
- Automatic AFK detection task
- **AFK Lounge**: an area with a border that pays **shards** every X seconds while you stay inside
  - `/afklounge <setlocation|border|info|help>` *(admin)*

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
- `/rtp [world]`: RTP menu or directly into a world
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
- `/findplayer <player>` (`/fp`): find a player's location *(staff)*

---

## 💰 Economy

The plugin registers itself as a **Vault economy provider**, so other plugins can use its balances.

### Money
- `/balance [player]` (`/bal`, `/money`)
- `/pay <player> <amount>`, with an optional confirm menu
- `/addmoney`, `/removemoney`, `/setmoney <player> <amount>` *(admin)*
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
- `/auctionhouse` (`/ah`) `[sell|my|claims|cancel|search|reload]`
- Browse by category and filter, list items, see your listings, claim expired or sold items
- Smarter search: partial names (`neth`) and enchant shortcuts (`prot 4`)
- **Quick Buy**: pin items and buy the cheapest listing in one click (dialogs on Paper/Purpur, forms on Bedrock)
- **Your Items** page with extra listing slots for Donut+ / Donut++ ranks, and a transaction history
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
- **AFK Lounge** shard rewards
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
- `/team info`, `/team pvp`: friendly-fire toggle
- Team menus (edit, kick confirm, disband confirm)
- **Lunar Client teammate overlay** (Apollo)
- Team name placeholder for the scoreboard

---

## 💬 Social

### Chat
- `/chat <help|mute|unmute|delay|clear|off>` *(staff)*
- Global mute, slow mode (delay), clear chat
- Chat filter with bypass permissions

### Private Messages
- `/msg <player> <message>` (`/message`, `/tell`, `/whisper`, `/w`)
- `/reply` (`/r`)
- `/pm` (`/togglepm`): toggle receiving messages

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
- Storage GUI, panel, and a list of spawners per world
- Owner-only access mode
- **Anti-ESP**: spawners are hidden from players outside a reveal radius (stops X-ray and ESP finding bases)

---

## 🛡️ Staff & Moderation

### Staff Mode
- `/staffmode [player|reload]` (`/staff`)
- Hotbar tools: **Vanish**, **Freeze**, **Staff List**, **Better View**, **Random Teleport**
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
| `/punishments <player>` (`/phistory`) | `smpplugin.staff.punishments.view` |

`…ban` also grants `…tempban` and `…mute` also grants `…tempmute`. The 1.5 bundles `…create` and `…remove` still work.

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

### Tablist
- Custom header and footer, LuckPerms role prefixes, and live refresh when ranks or skins change (LuckPerms and SkinsRestorer bridges)

### Leaderboards
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
| Notification Sounds | Follow Alerts | Display Donut+ |
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
| **MongoDB** | With a local cache file and sync on autosave |
| **Redis** *(optional)* | Only for network features, not main storage |

- Data is auto-saved periodically
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
| `afk-lounge.yml` | AFK Lounge |
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
9. **18 background tasks** start: scoreboard, tablist, shard income, shard cuboids, RTP zones, clearlag, key-all, auto-save, AFK check, Lunar teammates, Billford rotation, order and auction expiry, auction bot, amethyst tool expiry, spawner generation, duel and FFA match timers
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
  findplayer, Auction House actions, safety book).
- Upgrading from 1.5: [docs/permissions-migration-and-luckperms.md](docs/permissions-migration-and-luckperms.md).
