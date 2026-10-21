# SmpPlugin

A complete, feature-rich **Minecraft SMP core plugin** for **Minecraft 1.21.11 – 26.3** on **Paper, Purpur, Spigot and CraftBukkit**.

> **4.1.0** shows each player's rank (for example **Owner**) above their head and removes the balance limit:
> balances stay exact to the cent all the way to 92 quadrillion.
> **4.0.0** made every balance change exact and saved at once, with a ledger behind `/transactions`, `/baltop`
> and `/eco`, and added seasonal events you can switch live with `/events`: an **Advent Calendar**, a **Halloween hub**
> (candy, quests, a candy shop, milestones, a leaderboard) and a reworked **Trick or Treat**, plus rank perks in
> `perks.yml` and a reward vault so prizes never fall on the ground. It runs on Minecraft **1.21.11, 26.1, 26.1.1,
> 26.1.2, 26.2 and 26.3** from one jar per server family. Run `/smpplugin version` to see what is installed. See
> [COMPATIBILITY.md](COMPATIBILITY.md), [CHANGELOG.md](CHANGELOG.md), [docs/economy.md](docs/economy.md) and
> [docs/seasonal-events.md](docs/seasonal-events.md).
> Permissions changed; read [docs/permissions-migration-and-luckperms.md](docs/permissions-migration-and-luckperms.md) before upgrading.

SmpPlugin is designed to provide an entire SMP server experience from a single, highly configurable plugin. It combines gameplay, economy, social features, moderation, player utilities, competitive systems, GUI menus, statistics, server management, and extensive integrations into one unified system.

Instead of running a small army of plugins just to make an SMP function, SmpPlugin brings the majority of the core systems together in one optimized and configurable package.

## 🆕 New in 4.1


| | |
|---|---|
| 🏷️ **Ranks above heads** | The name tag above a player shows their rank prefix from LuckPerms or Vault, the same one chat uses, on every scoreboard. `NAMETAGS` in `config.yml` sets the format and the name colour. It steps aside for TAB and NametagEdit, and for other plugins' teams unless you tell it not to. |
| ♾️ **No balance limit** | `ECONOMY.MAX-BALANCE` is `0` (no limit) by default, and every balance is exact to the cent up to 92,233,720,368,547,758.07 (a new `money_cents` column). A config still on the 4.0 default of 10 trillion is moved to no limit; a limit you set yourself is kept. |

## 🆕 New in 4.0

| | |
|---|---|
| 💵 **Exact, saved money** | Balances are whole cents. Every change is written at once (no more waiting for the autosave) and recorded in a ledger, in the same database transaction. Offline payments can no longer be undone by a stale login copy. |
| 📒 **`/transactions`, `/baltop`, `/eco`** | Your own balance history; the richest players; staff `give`, `take`, `set`, `reset`, `history <player>` and `stats` (money supply and flows), each with its own permission and the staff member's name in the ledger. |
| 💸 **Pay rules** | `ECONOMY.PAY` in `config.yml`: minimum and maximum, a tax and a cooldown. An optional balance limit (`ECONOMY.MAX-BALANCE`; none by default since 4.1). |
| 🎰 **Bets held safely** | Dice and roulette bets are held in the database in the same transaction that takes the money, and given back on the next start after a crash. |
| 🗓️ **`/events`** | One menu for the seasonal events: running, starts in…, over, or switched off. Staff switch an event on, off or back to its dates; the switch survives restarts and never changes the dates or anyone's progress. |
| ❄️ **Advent Calendar** | `/advent`: a door a day from 1 to 24 December, streak bonuses, a prize for opening every door, and gifts chosen in game. |
| 🎃 **Halloween hub** | `/halloween`: the Spooky Calendar, Trick or Treat, a daily treat bag, season quests, a candy shop, candy milestones and a candy leaderboard. |
| 🚪 **Trick or Treat** | Decided and paid at the knock, with checked weights (`/trickortreat odds`), a daily limit, and houses (spots or FancyNpcs NPCs) for going door to door. |
| ⭐ **Rank perks** | `perks.yml`: VIP, VIP+ or any ranks you define, by permission and priority. Bigger event rewards and shorter event waits, each capped; never odds, prices or moderation. `/perks` shows them. |
| 🧪 **Test without giving** | `/advent test 2026-12-13` (or `/halloween test …`) shows the calendar as on that day to you only; nothing is given or saved. |
| 📦 **Reward vault** | Reward items that do not fit go to `/claimitems` instead of the ground. |
| 🛠️ **Fixes** | Prizes are handed over when you click, not when an animation ends; a failed database start stops the plugin instead of running without storage; leaderboards reload in the background; connection strings with passwords stay out of the log. |

## 🆕 New in 3.1

| | |
|---|---|
| 🎲 **Dice** | `/dice <bet> [high\|low\|1-6]` against the house. Shows the chance, payout and average return with a **[ROLL]** button before any money moves. Fair dice rolled by the server; payouts in `extras.yml` can never be set above the fair odds. `/dice roll` is still a free roll. |
| 🎰 **Head roulette** | `/roulette`: everyone's bet goes in one pot, the players' heads spin and the one that stops in the middle wins. Chance = your bet ÷ the pot, shown for everyone. |
| 🪙 **Simpler CoinFlip** | `/cf` is one screen: open flips as heads (click twice to join), quick stake buttons and a side picker. `/cf <amount> [heads\|tails]` creates a flip straight from chat. A new toss animation. |
| 📖 **`/guide`** | A getting-started guide from `guide.yml`, page by page, with clickable commands. It only shows commands that exist, are turned on and the reader may use. |
| 📋 **Scoreboard text** | Edit the sidebar in game with `/sidebar lines\|add\|set\|insert\|remove\|title`. Only changed lines are sent, at a configurable `LINE-UPDATE-TICKS`. |
| 🧾 **Cleaner tab list** | The default footer no longer shows money, stats, ping or TPS; servers still on the old default footer are updated (with a backup). |
| 🧱 **Spawn and lobby borders** | `/border spawn set 200`: a real Minecraft border. In PLAYER mode only players inside are held, so it is safe in a survival world; WORLD mode uses the world's own border. |
| 🎁 **Giveaways for senior staff** | Only ops and the Manager, Developer, Co-Owner and Owner ranks (or `smpplugin.giveaway.manage`) can start or cancel giveaways. |
| 🧹 **Removed** | `/hug`, `/8ball`, `/hat`, `/trash` and `/notes`. |

## 🆕 New in 3.0

| | |
|---|---|
| 🎃 **Spooky Calendar** | `/halloween`: a door for every day of October with a spinning reveal. Choose the prizes in game with `/halloween edit`. Trick or Treat, candy drops and pumpkin-headed mobs during the season. |
| 🎁 **Daily rewards** | `/daily`: a 7-day streak with a big day-7 reward and weekly bonuses. |
| 🎯 **Daily challenges** | `/challenges`: three goals a day for everyone, paid out automatically. |
| 🎉 **Live events** | `/poll`, `/giveaway`, automatic chat games, `/countdown`, a rotating announcer and `/mail`. |
| 😄 **Fun** | `/slap` `/highfive` `/poke` `/wave` `/cheer` `/dice` `/rps` `/rate` `/firework` `/sit` |
| 🧰 **Utilities** | `/craft` `/anvil` `/grindstone` `/stonecutter` `/loom` `/smithing` `/cartography` `/back` `/near` `/seen` `/coords` `/ptime` `/pweather` `/repair` `/skull` `/lore` `/glow` `/online` `/uptime` `/calc` `/suicide` |
| 🛡️ **Staff** | Roles `smpplugin.role.helper` / `moderator` / `admin` / `manager` / `developer` / `coowner` / `owner`, `/perms`, `/whois`, `/socialspy`, `/commandspy`, `/broadcast`, `/day` `/night` `/sun` `/rain` `/thunder`, `/smite`, `/butcher`, `/spawnmob`, `/more`, `/speed`, `/performance`, `/spook` |
| 📊 **Simpler /stats** | Your own stats in three lines, with Search Player and Leaderboards buttons. |

The Auction House is now only `/ah` (`/auctionhouse` was removed). Everything new is configured in
`halloween.yml` and `extras.yml`, and each part can be switched off as a feature.

## ✨ Features

### 🏠 SMP & Player Systems

* Spawn and lobby systems
* Player homes and multiple home locations
* Warps and warp management
* Random teleportation with a safe-spot cache, rank cooldowns, a priority queue and an RTP group queue (`/rtpq`)
* AFK zones and AFK management
* Player settings (50 options; pop-up dialog screen on Paper/Purpur, chest menu on Spigot)
* Player profiles
* Player statistics
* Playtime tracking
* Ping monitoring
* Player hiding
* Vanish and staff utilities
* Night vision
* Phantom controls
* TPA, TPA Here, TPA Accept, TPA Deny and TPA Cancel
* Automatic teleportation systems
* Player social features
* Friends system
* Team system with team homes and team chat

### 💰 Economy

* Complete player economy
* Balance management
* Player-to-player payments
* Economy administration
* Item selling with sell multipliers, shulker selling and a rotating farming meta
* Sell All
* Sell Hand
* Sell history and admin sell statistics (`/topsell`)
* Item worth browser
* Shop system
* Orders system
* Auction House with Quick Buy, smarter search, Your Items and transaction history
* Exact money in whole cents, written at once on every change, with a ledger (`/transactions`, `/eco history`)
* `/baltop`, `/eco give|take|set|reset|history|stats`, pay limits, a pay tax and a pay cooldown
  (see [docs/economy.md](docs/economy.md))
* Economy leaderboards
* Economy PlaceholderAPI expansions
* CoinFlip Nexus: 1v1 coin flips for in-game currency with a journaled, crash-safe money flow, challenges,
  practice mode, history, statistics, leaderboards and achievements (`/coinflip`, see [docs/coinflip.md](docs/coinflip.md))

### 💎 Shards

* Shard currency
* Shard payments
* Shard administration
* Shard Shop
* Shard statistics and management
* Configurable shard systems

### 🎁 Crates & Rewards

* Custom crate system
* Crate creation and management
* Crate keys
* Key management
* Crate menus
* Configurable crate types
* Reward management
* Crate editing and administration

### ⚔️ PvP & Competitive Systems

* Duels
* Duel queues
* Duel arenas
* FFA
* Instanced FFA arenas
* FFA statistics
* FFA queues
* Duel draw system
* Bounties
* PvP team systems
* Competitive leaderboards

### 👥 Teams

* Create teams
* Invite players
* Join and leave teams
* Kick team members
* Team homes
* Set and delete team homes
* Team chat
* Team information
* Team PvP controls
* Team management

### 🛡️ Moderation & Staff

* Built-in anti-cheat: 23 server-side checks (fly, speed, timer, no-fall, step, water walk, boat fly, reach,
  kill aura, anti-knockback, criticals, scaffold, nuker, fast break, through-wall interaction, inventory, x-ray
  patterns, cheat-client signatures and more), evidence IDs, kicks and a staff-reviewed ban queue
  (`/anticheat`, see [docs/anticheat.md](docs/anticheat.md))
* Staff mode
* Staff chat
* Staff list
* Player reports
* HelpOp
* Freeze system
* Vanish
* Inventory inspection
* Player punishment system
* Bans
* Temporary bans
* Mutes
* Temporary mutes
* Warnings
* Kicks
* Blacklists
* Punishment management
* Alternate account detection
* Player logs
* Teleportation tools
* Gamemode management
* Fly
* Heal
* Feed
* Server maintenance controls

### 💬 Chat & Social

* Global chat management
* Chat mute
* Chat delay
* Chat clearing
* Private messaging
* Message replies
* Private message toggling
* Ignore system
* Social commands
* Discord integration commands
* Social media commands
* Server rules and help menus

### 🧰 Utility Systems

* Custom Ender Chest
* Item renaming
* Spawner management
* ClearLag
* Cuboid tools
* Portals
* Server warps
* Lobby management
* Server selector systems
* Maintenance mode
* Server wipe tools
* Fake player utilities
* Disguise systems
* Safety systems
* AFK zones that pay shards
* Spawn stash
* Custom Amethyst tools

### 📊 Statistics & Leaderboards

* Player statistics
* Kill tracking
* Death tracking
* Playtime tracking
* Economy rankings
* Economy leaderboards
* FFA statistics
* Configurable leaderboard systems
* PlaceholderAPI statistics and economy expansions

## 🔌 Integrations

SmpPlugin supports a wide range of popular Minecraft server plugins and APIs, including:

* PlaceholderAPI
* LuckPerms
* Vault
* ProtocolLib
* SkinsRestorer
* Apollo
* Multiverse-Core
* FancyNpcs
* LPC
* NickPlus
* Floodgate
* EconomyShopGUI
* UltraCosmetics
* DeluxeMenus

Optional integrations are handled through soft dependencies where possible, allowing the plugin to operate without requiring every external plugin.

## 🗄️ Storage & Databases

Set `DATABASE.TYPE` in `database.yml`:

| Type | What it really is |
|---|---|
| **SQLite** *(default)* | A local file, `data/data.db`. No setup. |
| **MySQL** | A shared MySQL or MariaDB database. |
| **MongoDB** | A **mirror**, not a live store: the plugin works on a local SQLite file and copies it to MongoDB every `MONGODB.SYNC-INTERVAL-SECONDS` and at shutdown. Use it for off-site copies, not for several servers sharing one database. |
| **Redis** *(optional)* | Only for network features (cross-server messages), never for player data. |

If the database cannot start, SmpPlugin stops instead of running without storage.

## 🌐 Folia

SmpPlugin does **not** support Folia: its modules have not been checked on Folia, so the plugin refuses to
start there and declares `folia-supported: false`. See [COMPATIBILITY.md](COMPATIBILITY.md).

## ⚡ Performance

Performance and scalability are core design goals.

SmpPlugin uses dedicated managers, tasks, storage systems, listeners, menus, APIs, and integrations to keep the codebase organized and allow individual systems to operate independently.

The project also uses Maven Shade with dependency relocation to reduce library conflicts when distributing the plugin as a standalone JAR.

## 🧩 PlaceholderAPI

SmpPlugin provides custom PlaceholderAPI expansions for server and player information, including economy-related placeholders, rankings, leaderboards, AFK status and CustomSmp-specific data (`%economy_...%`, `%smpplugin_...%` or `%customsmp_...%`).

This makes it possible to integrate SmpPlugin data into scoreboards, tablists, holograms, menus, chat formats, and other plugins supporting PlaceholderAPI.

## 🖥️ Command System

SmpPlugin provides **220 commands** covering practically every major part of the plugin.

Examples include:

```text
/lobby
/spawn
/home
/sethome
/rtp
/shop
/orders
/ah
/sell
/sellall
/balance
/pay
/shards
/shardpay
/crates
/keys
/stats
/daily
/advent
/events
/perks
/transactions
/baltop
/claimitems
/challenges
/guide
/dice
/roulette
/coinflip
/halloween
/trickortreat
/mail
/playtime
/ping
/team
/duel
/queue
/ffa
/bounty
/leaderboard
/tpa
/warp
/friends
/settings
/report
/freeze
/vanish
/staffmode
/punishments
/maintenance
/border
/sidebar
```

Administrative commands are protected using dedicated permission nodes, making the plugin suitable for both normal players and staff teams.

## ⚙️ Configuration

SmpPlugin is designed to be heavily configurable.

Server owners can configure systems such as:

* Messages
* Menus
* Economy
* Shops
* Auction House
* Crates
* Rewards
* Warps
* Homes
* AFK locations
* Duels
* FFA
* Teams
* Punishments
* Staff systems
* Server settings
* Database settings
* Feature toggles
* Seasonal events (`advent.yml`, `halloween.yml`) and rank perks (`perks.yml`)

## 🛠️ Development

SmpPlugin is written in **Java** and uses **Maven** as its build system.

### Requirements

* JDK 25 to build (the jars it produces are Java 21 bytecode)
* Maven 3.9 or newer
* To run: Paper, Purpur, Spigot or CraftBukkit **1.21.11 – 26.3** (Java 21 on 1.21.11, Java 25 on 26.x)

SmpPlugin runs on **Minecraft 1.21.11 to 26.3** (`api-version: 1.21`, minimum checked at start-up).
For 1.21.10 and older use SmpPlugin 1.5. See [COMPATIBILITY.md](COMPATIBILITY.md).

### Build

```bash
mvn clean package
```

This builds the jar for **Paper and Purpur**:

```text
target/SmpPlugin-4.1.0-paper.jar
```

For **Spigot and CraftBukkit**, build with `mvn clean package -Pspigot`, which produces
`target/SmpPlugin-4.1.0-spigot.jar`. The two APIs are not binary compatible; use the matching jar.

## 📦 Installation

1. Download the jar for your server from the latest release: `SmpPlugin-<version>-paper.jar` for Paper and
   Purpur, `SmpPlugin-<version>-spigot.jar` for Spigot and CraftBukkit.
2. Place it in your server's `plugins` folder (remove the old SmpPlugin jar when upgrading).
3. Start or restart your server.
4. Configure the generated configuration files.
5. Install optional dependencies for the integrations you want to use.
6. Restart the server, or run `/smpplugin reload`, after configuration changes.

### Upgrading to 4.0

- Your configuration is kept; `advent.yml` and `perks.yml` are created, and new settings (`ECONOMY` in
  `config.yml`, `AUTOSAVE-SECONDS` and `MONGODB.SYNC-INTERVAL-SECONDS` in `database.yml`, the new Halloween
  sections) fall back to safe defaults when missing.
- The players table gets a `money_version` column and two tables are added, `economy_ledger` and
  `economy_holds`. Nothing is deleted or reset.
- Unfinished bets in `data/gambling-escrow.yml` are paid back once and the file is renamed `.recovered`.
- `/halloween` now opens the Halloween hub; `/halloween calendar` opens the calendar. `/halloween preview` is now
  `/halloween test` and only changes what you see.
- `/eco` is also registered by EssentialsX; use `/smpeco` (or `/smpplugin:eco`) if EssentialsX owns `/eco`.
- Rank perks use `smpplugin.rank.vip` and `smpplugin.rank.vipplus` by default (renameable in `perks.yml`); they are
  `default: false`, so nobody has a rank until you give it.

### Upgrading to 3.1

- Your configuration is kept. New settings are added to your files on start-up (the old files are copied to
  `config-backups/` first), and the new files `guide.yml` and `borders.yml` are created.
- The tab list footer is only changed if it is still exactly the 3.0 default.
- `/hug`, `/8ball`, `/hat`, `/trash` and `/notes` are gone. Existing staff notes stay in the database table
  `v3_staff_notes`; nothing is deleted.
- Giveaways now need `smpplugin.giveaway.manage`, a Manager / Developer / Co-Owner / Owner group (see
  `GIVEAWAYS.MANAGER-GROUPS` in `extras.yml`), or op. `smpplugin.staff.giveaway` and
  `smpplugin.admin.giveaway.money` no longer exist.
- Bets that a crash left unfinished are given back on the next start (`data/gambling-escrow.yml`).

## 🎯 Designed For

SmpPlugin is intended for:

* Survival SMP servers
* Community servers
* CustomSmp and similar SMP servers
* Economy SMP servers
* PvP SMP servers
* Competitive survival servers
* Multi-world servers
* Large Paper/Purpur networks
* Servers looking to consolidate multiple core plugins

## 📜 License

SmpPlugin is **proprietary software**. Copyright © 2026 LowBacon. All rights reserved.

* You may run official builds on your own server and edit its config and language files.
* You may **not** claim it as your own, remove credits, reupload, redistribute, sell, rebrand, or publish modified versions or forks without written permission from LowBacon.

See [`LICENSE`](LICENSE) for the complete license.

---

## ❤️ CustomSmp

SmpPlugin was built around the idea of creating a complete, polished SMP experience without requiring an excessive number of separate plugins.

**One plugin. One core. A complete SMP.** ⛏️