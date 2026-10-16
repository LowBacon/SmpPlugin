# CustomSmp: the all-in-one SMP core

**One plugin for a whole SMP server.** Homes, teams, economy, auction house, AFK rewards, spawners,
crates, duels, staff tools and more, all set up from config files and built to stay light on the
server.

> Runs on **Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3**
> on **Paper, Purpur, Spigot and CraftBukkit**, from one jar per server type

---

## ✨ Highlights

- **All-in-one:** replaces a stack of separate plugins with one that is set up the same way throughout.
- **Menus for everything:** 110+ chest menus, plus pop-up dialog screens on Paper 1.21.6+.
- **Light on the server:**
  - player data loads while the player is logging in
  - saving happens in the background
  - the tab list only sends what changed
  - busy tasks slow down by themselves when TPS drops
- **Works on its own:** SQLite out of the box, with MySQL, MongoDB and optional Redis for networks.
- **8 languages** included: English, German, Spanish, French, Indonesian, Portuguese, Russian, Chinese.

---

## 🏠 Homes & Teleporting

- **Homes:**
  - a slot grid with a manage screen for each home
  - custom home icons picked from a searchable list
  - extra home slots per rank
- **Teams** with a team home, team chat, friendly-fire toggle and member permissions
- **Random teleport:** a simple world picker for Overworld, Nether and End, with cooldowns and a live status
- **Warps, spawn and TPA:** `/tpa`, `/tpahere` and auto-accept
- **`/menu`:** one screen that reaches every main feature

## 💰 Economy

- **Money and shards:** a second currency, with Vault support
- **Auction House:** search, categories and claims
- **Orders board:** request items from other players and pay them on delivery
- **Shop, Sell and Worth:** sell multipliers and sell history
- **Bounties, `/pay`, CoinFlip** (with leaderboards and achievements), and **Billford**, a rotating trader
- **Crates:** spin and pick-one crates, a reward editor, crates bound to chests, and Key-All events

## 💤 AFK Zones

- **Earn shards while AFK:** stand in an AFK zone with a boss-bar countdown to your next reward.
- **Smart AFK detection:** water streams and minecarts don't count as being active.
- **Tab list:** AFK players get a `[AFK]` tag, a grey name, and move to the bottom.

## 🧱 Spawners

- **Stacked spawners** that store their loot and XP, so there are no item entities lying around
- **One clean menu:** storage, sell all, collect XP, add or remove from the stack, and a loot filter
- **Broken spawners keep their loot:** breaking one is refused until its stored loot is sold or collected
- **`/spawners` admin panel** for every world

## 📊 Stats, Chat & Tab List

- **`/stats`** menu with every stat and your leaderboard place, plus `/leaderboard` with 12 boards
- **Chat:**
  - formats with LuckPerms prefixes and hover stats
  - **@mentions** with a sound
  - a filter and slow mode
- **Private messages:** click to reply, `/ignore`, and a friends and follow system
- **Animated tab list:**
  - a shining title and a moving gradient bar
  - live ping and TPS
  - money, shards and kills in the footer
  - rotating tips
  - sorted by rank

## ⚔️ PvP

- **Duels:**
  - invites and a matchmaking queue with map choice
  - winners claim the loser's items
  - the arena rolls back after every match
  - duel stats
- **FFA arenas** that put your inventory back when you leave
- **Combat tagging** with an action-bar timer, **anti-stasis-chamber** protection, fast crystals,
  and kill streaks

## 🛡️ Staff & Moderation

- **Staff mode** with hotbar tools:
  - vanish (with fake leave and join messages)
  - freeze
  - inspect
  - spectator
  - random teleport
  - better view
- **Punishments:**
  - ban, temp-ban, mute, warn, kick and blacklist, plus **voice chat mute** (Simple Voice Chat)
  - preset offenses with `/offend`
  - a full history browser
- **`/chatlog`, `/logs`, `/alts`, `/invsee`, `/ecsee`, profile viewer, `/seehomes`**
- **`/playerwipe`:** wipe one player's progress, with an automatic backup and `/playerunwipe` to undo it
- **Built-in anti-cheat.** It steps aside automatically when TitanGuard is installed.
- **Maintenance mode** and a full season `/serverwipe` tool

---

## 🔌 Optional Integrations

Everything below is optional. If one is missing, only the features that need it turn off.

| Plugin | Used for |
|---|---|
| **LuckPerms** | Rank prefixes, tab list sorting |
| **PlaceholderAPI** | Placeholders everywhere, plus `%economy_*%` placeholders of our own |
| **Vault** | Lets other plugins use CustomSmp's economy |
| **ProtocolLib** | Disguises, fake players, worth display |
| **Simple Voice Chat** | `/vcmute` |
| **SkinsRestorer** | Disguise skins |
| **Floodgate** | Bedrock forms |
| **FancyNpcs** | Lobby and trader NPCs |
| **Apollo (Lunar Client)** | Rich presence, teammate overlay |

---

## 📥 Installation

1. Download the jar that matches your server:
   - **Paper / Purpur:** `SmpPlugin-2.1.0-paper.jar`
   - **Spigot / CraftBukkit:** `SmpPlugin-2.1.0-spigot.jar`
2. Drop it in `plugins/` and start the server once.
3. Run `/smpplugin setup` to see what still needs setting (spawn, AFK zone and so on).
4. Check `/smpplugin version` and `/smpplugin status` at any time.

**Requirements:**

| Minecraft | Java |
|---|---|
| 1.21.11 | 21 or newer |
| 26.1 – 26.3 | 25 |

- **Folia** is not supported.
- **1.21.10 and older:** use version 1.5.

---

## 📜 Notes

- **Permissions:** every permission is documented. Operators get the admin tools by default, and
  regular players get the player features.
- **Upgrading from 1.5?** Read the permissions migration guide before updating.
- **Bugs and suggestions:** open an issue on GitHub.

*CustomSmp (SmpPlugin) by LowBacon33. All rights reserved.*
