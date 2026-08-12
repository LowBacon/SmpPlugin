# SmpPlugin

A complete, feature-rich **Minecraft SMP core plugin** built for modern **Paper-based servers**, with compatibility for **Paper, Purpur, Spigot, Bukkit, and Folia**.

SmpPlugin is designed to provide an entire SMP server experience from a single, highly configurable plugin. It combines gameplay, economy, social features, moderation, player utilities, competitive systems, GUI menus, statistics, server management, and extensive integrations into one unified system.

Instead of running a small army of plugins just to make an SMP function, SmpPlugin brings the majority of the core systems together in one optimized and configurable package.

## ✨ Features

### 🏠 SMP & Player Systems

* Spawn and lobby systems
* Player homes and multiple home locations
* Warps and warp management
* Random teleportation
* AFK zones and AFK management
* Player settings
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
* Item selling
* Sell All
* Sell Hand
* Sell history
* Item worth browser
* Shop system
* Orders system
* Auction House
* Economy leaderboards
* Economy PlaceholderAPI expansions

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
* AFK lounge
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

## 🌐 Folia Support

SmpPlugin includes **Folia support**, allowing the plugin to be used on modern regionized Minecraft server environments while maintaining compatibility with traditional Bukkit/Paper-style servers.

## ⚡ Performance

Performance and scalability are core design goals.

SmpPlugin uses dedicated managers, tasks, storage systems, listeners, menus, APIs, and integrations to keep the codebase organized and allow individual systems to operate independently.

The project also uses Maven Shade with dependency relocation to reduce library conflicts when distributing the plugin as a standalone JAR.

## 🧩 PlaceholderAPI

SmpPlugin provides custom PlaceholderAPI expansions for server and player information, including economy-related placeholders, rankings, leaderboards, and FinnishSMP-specific data.

This makes it possible to integrate SmpPlugin data into scoreboards, tablists, holograms, menus, chat formats, and other plugins supporting PlaceholderAPI.

## 🖥️ Command System

SmpPlugin currently provides **130+ commands** covering practically every major part of the plugin.

Examples include:

```text
/lobby
/spawn
/home
/sethome
/rtp
/shop
/orders
/auctionhouse
/sell
/sellall
/balance
/pay
/shards
/shardpay
/crates
/keys
/stats
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

* Java 21
* Maven
* Paper / Purpur / Spigot / Bukkit-compatible server
* Minecraft 1.21.x and supported modern server versions

The project is configured around modern Minecraft server APIs and currently targets a supported range of **1.21.10 through 26.2**.

### Build

```bash
mvn clean package
```

The compiled plugin will be generated as:

```text
target/SmpPlugin-1.5-Pre-Release.jar
```

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
* Finnish SMP servers
* Economy SMP servers
* PvP SMP servers
* Competitive survival servers
* Multi-world servers
* Large Paper/Purpur networks
* Servers looking to consolidate multiple core plugins

## 📜 License

This project is licensed under the **MIT License**.

Copyright © 2026 LowBacon.

See [`LICENSE`](LICENSE) for the complete license.

---

## ❤️ FinnishSMP

SmpPlugin was built around the idea of creating a complete, polished SMP experience without requiring an excessive number of separate plugins.

**One plugin. One core. A complete SMP.** ⛏️🇫🇮
