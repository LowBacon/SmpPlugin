# CustomSmp: the all-in-one SMP core

**One plugin for a whole SMP server.** Homes, teams, economy, auction house, daily rewards,
challenges, a Halloween calendar, events, spawners, crates, duels, staff tools and more. Everything
is set up from config files and built to stay light on the server.

> **4.0** makes every balance change exact and saved at once, with `/transactions`, `/baltop` and `/eco`, and adds
> seasonal events you switch live with `/events`: a ❄️ Advent Calendar, a 🎃 Halloween hub with candy, quests and a
> candy shop, a reworked Trick or Treat, and rank perks for VIP and VIP+.
> **3.1** added 🎲 `/dice` and the 🎰 `/roulette` head jackpot, a one-screen CoinFlip, `/guide`, an in-game
> scoreboard editor, a cleaner tab list and real Minecraft borders for the spawn and the lobby.
> **3.0** added the 🎃 Spooky Calendar, daily rewards and challenges, polls, giveaways, chat games and staff roles.
>
> Runs on **Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3**
> on **Paper, Purpur, Spigot and CraftBukkit**, from one jar per server type.

---

## ✨ Highlights

- **All-in-one:** 220 commands replace a stack of separate plugins, set up the same way throughout.
- **Menus that move:**
  - 110+ chest menus with animated reveals
  - pop-up dialog screens on Paper 1.21.6+
- **Feels alive:** daily rewards, daily challenges, chat games, polls, giveaways, emotes and a
  rotating announcer.
- **Light on the server:**
  - player data loads while the player is logging in
  - saving happens in the background
  - animations only run while a menu is open
  - busy tasks slow down by themselves when TPS drops
- **Works on its own:** SQLite out of the box, or MySQL; MongoDB as an off-site mirror of the local file; optional Redis for networks.
- **8 languages** included.

---

## 🎃 Spooky Calendar (new in 3.0)

- **`/halloween`:** one door for every day of October. Today's door spins through candy and reveals
  its prize with sounds and particles, then an animated pop-up.
- **You choose every prize in game:**
  - `/halloween edit`, then click a door
  - drop items in and click to set money and shards
  - Save
- **Day 31 is the big Halloween door,** and opening it is announced to everyone.
- **Your rules:** missed-door catch-up, grace days, time zone, or every door opening on the 31st.
- **`/trickortreat`:** an hourly knock for a treat, or a harmless trick (bats, a scare, a launch
  into the sky...).
- **Halloween ambience:**
  - monsters drop candy that gives a random boost
  - zombies and skeletons wear pumpkins
  - a spooky greeting on join

## 🎁 Daily Rewards & Challenges (new in 3.0)

- **`/daily`:** a 7-day streak with a big day-7 reward, weekly bonuses and a spinning reveal.
- **`/challenges`:**
  - three goals a day for everyone: mining, building, mob hunting, PvP, playtime, selling
  - progress bars and automatic rewards
  - a bonus for finishing all three

## 🎉 Events & Fun (new in 3.0)

- **`/poll`:** clickable votes with a boss-bar timer and a bar-chart result.
- **`/giveaway`:** senior staff give away an item or money; players join with one click.
- **Chat games:** unscramble, quick maths, fast typer and trivia. The first right answer wins.
- **`/countdown`, a rotating announcer, and `/mail`** for offline players.
- **Emotes:** `/slap` `/highfive` `/poke` `/wave` `/cheer`.
- **Games and toys:** `/dice` (bet against the house, odds shown first), `/roulette` (a head jackpot),
  `/coinflip`, `/rps` `/rate` `/firework` `/sit`.

## 🏠 Homes & Teleporting

- **Homes:**
  - a slot grid with a manage screen for each home
  - custom home icons
  - extra home slots per rank
- **Teams** with a team home, team chat, friendly-fire toggle and member permissions
- **Random teleport,** warps, spawn, TPA with auto-accept, and **`/back`** (never into arenas)
- **`/menu`:** one screen that reaches every main feature

## 🧰 Utilities (new in 3.0)

- **Portable workstations:** `/craft` `/anvil` `/grindstone` `/stonecutter` `/loom` `/smithing` `/cartography`
- **Information:** `/near` `/seen` `/coords` `/online` `/uptime` `/calc`
- **Items and personal:** `/repair` `/skull` `/lore` `/glow` `/ptime` `/pweather` `/suicide`

## 💰 Economy

- **Money and shards:** a second currency, with Vault support
- **Auction House (`/ah`):** search, categories and claims
- **Orders board:** request items from other players and pay them on delivery
- **Shop, Sell and Worth:** sell multipliers and sell history
- **Bounties, `/pay`, CoinFlip, Billford** (a rotating trader), and **crates** with Key-All events

## 💤 AFK Zones

- **Earn shards while AFK,** with a boss-bar countdown
- **Smart AFK detection:** water streams and minecarts don't count as being active

## 🧱 Spawners

- **Stacked spawners** that store their loot and XP, so no item entities lie around
- **One clean menu:** storage, sell all, collect XP, stack and a loot filter

## 📊 Stats, Chat & Tab List

- **`/stats`:** your stats in three clean lines, plus Search Player and Leaderboards
- **`/leaderboard`:** 12 boards
- **Chat:** LuckPerms prefixes, @mentions, a filter, slow mode, click-to-reply private messages and
  `/ignore`
- **Animated tab list:** live ping and TPS, sorted by rank

## ⚔️ PvP

- **Duels:** a matchmaking queue, claiming the loser's items, and arena rollback
- **FFA arenas,** combat tagging, anti-stasis protection, fast crystals and kill streaks

## 🛡️ Staff & Moderation

- **Ready-made roles (new in 3.0):** `smpplugin.role.helper`, `moderator`, `admin` and `owner`.
  Give one node to a rank and you're done; `/perms` explains what each one grants.
- **Staff mode:** vanish, freeze, inspect, spectator and more
- **Punishments:** ban, mute, warn, kick, blacklist, voice-chat mute, preset offenses and full history
- **New in 3.0:**
  - `/whois`, `/socialspy`, `/commandspy`, `/broadcast`
  - `/day`, `/night`, `/sun`, `/rain`, `/thunder`
  - `/butcher`, `/spawnmob`, `/speed`, `/performance`
- **Also included:** `/chatlog`, `/alts`, `/invsee`, `/playerwipe` (with undo), built-in anti-cheat
  and maintenance mode

---

## 🔌 Optional Integrations

Everything below is optional. If one is missing, only the features that need it turn off.

| Plugin | Used for |
|---|---|
| **LuckPerms** | Rank prefixes, tab list sorting, staff roles |
| **PlaceholderAPI** | Placeholders everywhere |
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
   - **Paper / Purpur:** `SmpPlugin-4.0.0-paper.jar`
   - **Spigot / CraftBukkit:** `SmpPlugin-4.0.0-spigot.jar`
2. Drop it in `plugins/` and start the server once.
3. Run `/smpplugin setup` to see what still needs setting.
4. Give your staff ranks a role, for example `smpplugin.role.moderator`.

**Requirements:**

| Minecraft | Java |
|---|---|
| 1.21.11 | 21 or newer |
| 26.1 – 26.3 | 25 |

- **Folia** is not supported.

---

## 📜 Notes

- **Upgrading from 2.x:** `/auctionhouse` is now `/ah` only, and everything else carries over.
  The new features are set up in `halloween.yml` and `extras.yml`.
- **Bugs and suggestions:** open an issue on GitHub.

*CustomSmp (SmpPlugin) by LowBacon33. All rights reserved.*
