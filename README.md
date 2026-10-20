# SmpPlugin

A complete, feature-rich **Minecraft SMP core plugin** for **Minecraft 1.21.11 – 26.3** on **Paper, Purpur, Spigot and CraftBukkit**.

> **3.0.0** adds the Spooky Calendar (`/halloween`), daily rewards, daily challenges, polls, giveaways, chat games,
> 63 new commands and ready-made staff roles. It runs on Minecraft **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3**
> from one jar per server family. Run `/smpplugin version` to see what is installed. See [COMPATIBILITY.md](COMPATIBILITY.md),
> [CHANGELOG.md](CHANGELOG.md) and the upgrade status in [docs/UPGRADE-26.3-STATUS.md](docs/UPGRADE-26.3-STATUS.md).
> Permissions changed; read [docs/permissions-migration-and-luckperms.md](docs/permissions-migration-and-luckperms.md) before upgrading.

SmpPlugin is designed to provide an entire SMP server experience from a single, highly configurable plugin. It combines gameplay, economy, social features, moderation, player utilities, competitive systems, GUI menus, statistics, server management, and extensive integrations into one unified system.

Instead of running a small army of plugins just to make an SMP function, SmpPlugin brings the majority of the core systems together in one optimized and configurable package.

## 🆕 New in 3.0

| | |
|---|---|
| 🎃 **Spooky Calendar** | `/halloween`: a door for every day of October with a spinning reveal. Choose the prizes in game with `/halloween edit`. Trick or Treat, candy drops and pumpkin-headed mobs during the season. |
| 🎁 **Daily rewards** | `/daily`: a 7-day streak with a big day-7 reward and weekly bonuses. |
| 🎯 **Daily challenges** | `/challenges`: three goals a day for everyone, paid out automatically. |
| 🎉 **Live events** | `/poll`, `/giveaway`, automatic chat games, `/countdown`, a rotating announcer and `/mail`. |
| 😄 **Fun** | `/hug` `/slap` `/highfive` `/poke` `/wave` `/cheer` `/dice` `/8ball` `/rps` `/rate` `/firework` `/sit` `/hat` |
| 🧰 **Utilities** | `/craft` `/anvil` `/grindstone` `/stonecutter` `/loom` `/smithing` `/cartography` `/trash` `/back` `/near` `/seen` `/coords` `/ptime` `/pweather` `/repair` `/skull` `/lore` `/glow` `/online` `/uptime` `/calc` `/suicide` |
| 🛡️ **Staff** | Roles `smpplugin.role.helper` / `moderator` / `admin` / `owner`, `/perms`, `/notes`, `/whois`, `/socialspy`, `/commandspy`, `/broadcast`, `/day` `/night` `/sun` `/rain` `/thunder`, `/smite`, `/butcher`, `/spawnmob`, `/more`, `/speed`, `/performance`, `/spook` |
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

The plugin includes database support for persistent server data and can work with multiple storage technologies, including:

* SQLite
* MySQL
* MongoDB
* Redis

This allows SmpPlugin to scale beyond a simple single-server setup and provides flexibility for different server infrastructures.

## 🌐 Folia

Version 2.x does **not** support Folia: its modules have not been checked on Folia, so the plugin refuses to
start there and declares `folia-supported: false`. See [COMPATIBILITY.md](COMPATIBILITY.md).

## ⚡ Performance

Performance and scalability are core design goals.

SmpPlugin uses dedicated managers, tasks, storage systems, listeners, menus, APIs, and integrations to keep the codebase organized and allow individual systems to operate independently.

The project also uses Maven Shade with dependency relocation to reduce library conflicts when distributing the plugin as a standalone JAR.

## 🧩 PlaceholderAPI

SmpPlugin provides custom PlaceholderAPI expansions for server and player information, including economy-related placeholders, rankings, leaderboards, AFK status and CustomSmp-specific data (`%economy_...%`, `%smpplugin_...%` or `%customsmp_...%`).

This makes it possible to integrate SmpPlugin data into scoreboards, tablists, holograms, menus, chat formats, and other plugins supporting PlaceholderAPI.

## 🖥️ Command System

SmpPlugin provides **210+ commands** covering practically every major part of the plugin.

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

## 🛠️ Development

SmpPlugin is written in **Java** and uses **Maven** as its build system.

### Requirements

* JDK 25 to build (the jars it produces are Java 21 bytecode)
* Maven 3.9 or newer
* To run: Paper, Purpur, Spigot or CraftBukkit **1.21.11 – 26.3** (Java 21 on 1.21.11, Java 25 on 26.x)

Version 2.1 runs on **Minecraft 1.21.11 to 26.3** (`api-version: 1.21`, minimum checked at start-up).
For 1.21.10 and older use SmpPlugin 1.5. See [COMPATIBILITY.md](COMPATIBILITY.md).

### Build

```bash
mvn clean package
```

This builds the jar for **Paper and Purpur**:

```text
target/SmpPlugin-3.0.0-paper.jar
```

For **Spigot and CraftBukkit**, build with `mvn clean package -Pspigot`, which produces
`target/SmpPlugin-3.0.0-spigot.jar`. The two APIs are not binary compatible; use the matching jar.

## 📦 Installation

1. Download the latest SmpPlugin release.
2. Place `SmpPlugin.jar` into your server's `plugins` folder.
3. Start or restart your server.
4. Configure the generated configuration files.
5. Install optional dependencies for the integrations you want to use.
6. Restart the server after configuration changes.

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
