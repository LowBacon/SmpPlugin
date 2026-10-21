# SmpPlugin

A complete, feature-rich **Minecraft SMP core plugin** for **Minecraft 1.21.11 – 26.3** on **Paper, Purpur, Spigot and CraftBukkit**.

> **4.0.0** brands your server **CustomSMP** on the scoreboard, tab list and menus from one setting,
> counts money in whole cents with a full audit ledger, and adds ranked PvP with Elo, seasonal events
> (`/advent`, Christmas presents, an Easter egg hunt, Summer and your own events), item bans, versioned
> database migrations and `/smp diagnostics`. It runs on Minecraft **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3**
> from one jar per server family. Run `/smp version` to see what is installed. See
> [docs/INSTALL.md](docs/INSTALL.md), [CHANGELOG.md](CHANGELOG.md), [COMPATIBILITY.md](COMPATIBILITY.md) and
> [docs/MIGRATING-TO-4.0.md](docs/MIGRATING-TO-4.0.md) when upgrading.

SmpPlugin is designed to provide an entire SMP server experience from a single, highly configurable plugin. It combines gameplay, economy, social features, moderation, player utilities, competitive systems, GUI menus, statistics, server management, and extensive integrations into one unified system.

Instead of running a small army of plugins just to make an SMP function, SmpPlugin brings the majority of the core systems together in one optimized and configurable package.

## 🆕 New in 4.0

| | |
|---|---|
| 🏷️ **CustomSMP branding** | `BRANDING.SERVER-NAME` in `config.yml` (default **CustomSMP**) is the name on the scoreboard title, the tab list, menus and messages. `{server_name}`, `{server_title}` and `{server_address}` work in any text. The plugin itself stays **SmpPlugin**. |
| 📋 **New default scoreboard** | Player, Rank, Balance, Shards, Kills, Deaths, K/D, Playtime, Team, Online and Server. Placeholders work **with or without PlaceholderAPI**, and one nothing can fill is left out instead of shown as code. |
| 💵 **Safe money** | Every change is worked out in whole cents with overflow checks and a balance cap; NaN, infinite and sub-cent amounts are refused. Two quick payments to an offline player can no longer lose one. |
| 🧾 **Economy ledger** | Every money and shard change is recorded with a transaction id, the reason, who caused it and the balance before and after. `/balance history`, `/economy history <player>`. |
| 🏦 **`/economy`** | `give`, `take`, `set`, `reset` (with confirmation), `history`, `stats`. Admin changes record the admin and the reason. Optional `/pay` limits, cooldown and fee. `/baltop`. |
| 🏆 **Ranked PvP** | `/ranked`: an Elo-rated queue on your duel arenas, tiers, a leaderboard, match history, win rewards and anti-boosting (pair limits, same-IP block, minimum match length). |
| 🎄 **Seasonal events** | `/event`: the **Advent Calendar** (`/advent`), **Christmas presents**, the **Easter egg hunt** (`/easter`), **Summer Splash** and your own events, each on its dates or switched on by staff. Progress is kept and rewards are never paid twice. |
| 🚫 **Item bans** | `survival-rules.yml`: stop items from being placed, used, crafted or picked up, and disable recipes, per world if you like. |
| 🩺 **`/smp diagnostics`** | TPS, memory, database health and queue, cache sizes, the slowest commands and anything that failed to start. `/smp modules` turns modules on and off for real. |
| 🗃️ **Database migrations** | Numbered, run-once schema changes in a transaction, shown in `/smp status`. |

## 🆕 New in 3.1

| | |
|---|---|
| 🎲 **Dice** | `/dice <bet> [high\|low\|1-6]` against the house. Shows the chance, payout and average return with a **[ROLL]** button before any money moves. Fair dice rolled by the server; payouts in `extras.yml` can never be set above the fair odds. `/dice roll` is still a free roll. |
| 🪙 **CoinFlip** | `/cf`: start a flip or click someone's; it starts at once. The two heads spin and the middle one wins, a fair 50/50. No maximum bet (3.2). |
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
* Economy leaderboards
* Economy PlaceholderAPI expansions
* CoinFlip (`/cf`): start a coin flip or join one; the two players' heads spin and the middle one wins the pot

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

All integrations are optional: when a plugin is missing, the features that need it are switched off and
`/smp status` says so. **Tested on a running Paper 26.3 server with 4.0.0:** PlaceholderAPI 2.12.3,
LuckPerms 5.5.87, Vault 1.7.3 and ProtocolLib 5.4.0 (start-up, `/smp status`, PlaceholderAPI parsing).
The others below are supported by the code but were not part of that test; see
[docs/INSTALL.md](docs/INSTALL.md#optional-plugins).

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

The plugin includes database support for persistent server data and can work with multiple storage technologies, including:

* **SQLite** (default, nothing to set up)
* **MySQL** (`database.yml`)
* **MongoDB** (a bridge: SQLite runs locally and its tables are copied to MongoDB)
* **Redis** (optional, for network staff chat, alerts and cross-server sync)

Schema changes since 4.0 are numbered migrations recorded in the `schema_migrations` table.

## 🌐 Folia

Version 4.x does **not** support Folia: its modules have not been checked on Folia's region threads, so the
plugin declares `folia-supported: false`. See [COMPATIBILITY.md](COMPATIBILITY.md).

## ⚡ Performance

* Database writes run in order on one background thread with a bounded queue; joins read player data
  on the login thread, before the player is in the world.
* Leaderboards are read from the database in the background and cached; the scoreboard and placeholders
  never query the database.
* The scoreboard and tab list send only the lines that changed.
* `/smp diagnostics` shows measured numbers from your own server: TPS and tick time, the database's
  query time and queue wait, queued writes, cache sizes and the slowest commands. No benchmark numbers
  are published here because they depend on your hardware and players.
* Maven Shade relocates the bundled libraries so they cannot clash with other plugins.

## 🧩 PlaceholderAPI

SmpPlugin's own placeholders work in its scoreboard, tab list, menus and messages **without PlaceholderAPI**.
With PlaceholderAPI installed they also work in other plugins, under the prefixes `%smp_...%` (new in 4.0),
`%economy_...%`, `%smpplugin_...%` and `%customsmp_...%`. The full list is in [docs/placeholders.md](docs/placeholders.md).

This makes it possible to integrate SmpPlugin data into scoreboards, tablists, holograms, menus, chat formats, and other plugins supporting PlaceholderAPI.

## 🖥️ Command System

SmpPlugin provides **215+ commands** covering practically every major part of the plugin. The full list,
with aliases and permissions, is in [docs/commands.md](docs/commands.md).

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
/challenges
/guide
/dice
/cf
/halloween
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
/smp
/economy
/baltop
/ranked
/event
/advent
/easter
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
* Feature toggles (`/smp modules`)
* Server branding (`BRANDING` in `config.yml`)
* Ranked PvP (`ranked.yml`)
* Seasonal events (`events.yml`, `advent.yml`)
* Banned items and recipes (`survival-rules.yml`)

## 🛠️ Development

SmpPlugin is written in **Java** and uses **Maven** as its build system.

### Requirements

* JDK 25 to build (the jars it produces are Java 21 bytecode)
* Maven 3.9 or newer
* To run: Paper, Purpur, Spigot or CraftBukkit **1.21.11 – 26.3** (Java 21 on 1.21.11, Java 25 on 26.x)

Version 2.1 runs on **Minecraft 1.21.11 to 26.3** (`api-version: 1.21`, minimum checked at start-up).
For 1.21.10 and older use SmpPlugin 1.5. See [COMPATIBILITY.md](COMPATIBILITY.md).

### Build

The Maven wrapper downloads the right Maven by itself:

```bash
./mvnw clean package
```

On Windows use `mvnw.cmd clean package`; with Maven installed, `mvn clean package` works too. This builds
the jar for **Paper and Purpur** and runs the tests:

```text
target/SmpPlugin-4.0.0-paper.jar
```

For **Spigot and CraftBukkit**, build with `./mvnw clean package -Pspigot`, which produces
`target/SmpPlugin-4.0.0-spigot.jar`. The two APIs are not binary compatible; use the matching jar.

## 📦 Installation

On a fresh Paper or Purpur server (full guide: [docs/INSTALL.md](docs/INSTALL.md)):

1. Download `SmpPlugin-4.0.0-paper.jar` (Paper and Purpur) or `SmpPlugin-4.0.0-spigot.jar` (Spigot and
   CraftBukkit) from the [latest release](https://github.com/LowBacon/SmpPlugin/releases/latest).
2. Put it in the server's `plugins` folder. Optional: add PlaceholderAPI, LuckPerms and Vault.
3. Start the server once. The configuration files are created in `plugins/SmpPlugin/`.
4. Set your server's name and address in `config.yml` under `BRANDING` (the name defaults to **CustomSMP**).
5. Give your staff ranks a role node, e.g. `smpplugin.role.admin` (see
   [docs/permissions-reference.md](docs/permissions-reference.md)).
6. Run `/smp reload` (or restart) after editing the files, and `/smp status` to check everything started.

### Upgrading to 4.0

Replace the old jar and restart; nothing has to be done by hand. Settings are added to your files (old
copies go to `config-backups/`), the database is migrated, and your scoreboard and tab list keep their
lines. What changes and how to undo it: [docs/MIGRATING-TO-4.0.md](docs/MIGRATING-TO-4.0.md).

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
* CustomSMP and similar SMP servers
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

## ❤️ CustomSMP

SmpPlugin was built around the idea of creating a complete, polished SMP experience without requiring an excessive number of separate plugins.

**One plugin. One core. A complete SMP.** ⛏️
