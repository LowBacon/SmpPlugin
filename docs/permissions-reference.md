# SmpPlugin permissions reference

> Generated from `PermissionRegistry.java`. Do not edit by hand; run
> `mvn test -Dtest=PermissionRegistryTest -Dsmpplugin.regenerate=true` after changing the registry.

**Defaults.** Player features are granted to everyone. Every other node defaults to *nobody*.
`smpplugin.admin` is granted to server operators by default and contains staff tools, administration
and operational bypasses. It never contains destructive actions or security bypasses; grant those to
specific people.

**Wildcards.** A permissions plugin wildcard such as LuckPerms `smpplugin.*` or `*` grants *every* node,
including destructive actions and bypasses. Only use one when you mean that.

## Player features (default: everyone)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.message` | Send private messages with /msg and reply with /reply. | everyone |  |
| `smpplugin.message.toggle` | Turn receiving private messages on or off with /pm. | everyone |  |
| `smpplugin.ignore` | Add and remove players from your ignore list. | everyone |  |
| `smpplugin.helpop` | Ask online staff for help with /helpop. | everyone |  |
| `smpplugin.report` | Report a player to staff with /report. | everyone |  |
| `smpplugin.enderchest` | Open your own custom Ender Chest with /enderchest. | everyone |  |
| `smpplugin.coinflip.use` | Play /cf: start coin flips and join other players' flips. | everyone | New in 2.0. Since 3.2 it is the only node /cf needs. |
| `smpplugin.afk.away` | Mark yourself AFK where you stand with /away. | everyone | New in 2.0.0-alpha.5. |
| `smpplugin.findplayer` | See which dimension and biome another online player is in with /findplayer. | everyone | New in 2.0. /findplayer had no permission check in 1.5; default true keeps that behaviour. |
| `smpplugin.friends` | Use /friends to add, remove and follow friends. | everyone | Declared in 2.0 with default true. It was undeclared in 1.5, which made it op-only unless granted. |
| `smpplugin.lobby` | Use /lobby and /lobby survival. | everyone | Declared in 2.0 with default true. It was undeclared in 1.5, which made it op-only unless granted. |
| `smpplugin.safety.use` | Read the server safety book with /safety. | everyone | Renamed from safety.use in 2.0; default changed from op-only to true. |
| `smpplugin.auctionhouse.use` | Open and browse the Auction House. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.limit` | See your Auction House listing limit with /ah limit. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.sell` | List your own items on the Auction House. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.buy` | Buy Auction House listings. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.cancel` | Cancel your own active Auction House listings. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.claims` | Collect your own sold, expired and cancelled Auction House items. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.my` | View your own Auction House listings. | everyone | Declared in 2.0 with default true; previously undeclared (op-only unless granted). |
| `smpplugin.halloween` | Open the Spooky Calendar with /halloween and open its doors. | everyone | New in 3.0. |
| `smpplugin.halloween.trickortreat` | Knock for a treat (or a harmless trick) with /trickortreat during the Halloween season. | everyone | New in 3.0. |
| `smpplugin.daily` | Claim the daily reward and keep a streak with /daily. | everyone | New in 3.0. |
| `smpplugin.challenges` | See and finish the daily challenges with /challenges. | everyone | New in 3.0. |
| `smpplugin.poll.vote` | Vote in polls started by staff. | everyone | New in 3.0. |
| `smpplugin.giveaway.join` | Join giveaways started by staff. | everyone | New in 3.0. |
| `smpplugin.mail` | Send and read /mail, including to offline players. | everyone | New in 3.0. |
| `smpplugin.fun.emotes` | Use /slap, /highfive, /poke, /wave and /cheer. | everyone | New in 3.0; /hug removed in 3.1. |
| `smpplugin.fun.games` | Use /rps, /rate and /dice roll. | everyone | New in 3.0; /8ball removed in 3.1. |
| `smpplugin.fun.sit` | Sit down where you stand with /sit. | everyone | New in 3.0. |
| `smpplugin.back` | Return to where you were before your last teleport with /back. | everyone | New in 3.0. |
| `smpplugin.seen` | See when a player was last online with /seen. | everyone | New in 3.0. |
| `smpplugin.coords` | Show your coordinates and share them with your team with /coords. | everyone | New in 3.0. |
| `smpplugin.suicide` | Respawn with /suicide (asks to confirm; not in combat). | everyone | New in 3.0. |
| `smpplugin.online` | List the players online with /online. | everyone | New in 3.0. |
| `smpplugin.uptime` | See how long the server has been up, and its TPS, with /uptime. | everyone | New in 3.0. |
| `smpplugin.calc` | Do sums with /calc. | everyone | New in 3.0. |
| `smpplugin.dice` | Bet money on a dice roll against the house with /dice. | everyone | New in 3.1. |
| `smpplugin.guide` | Read the getting-started guide with /guide. | everyone | New in 3.1. |
| `smpplugin.sidebar` | Show or hide your own scoreboard with /sidebar. | everyone | New in 3.1. |
| `smpplugin.balance.history` | See your own money and shard history with /balance history. | everyone | New in 4.0. |
| `smpplugin.ranked` | Play ranked PvP with /ranked: the queue, stats, the leaderboard and history. | everyone | New in 4.0. |
| `smpplugin.events` | Use /event and /easter: see the seasonal events and take part in them. | everyone | New in 4.0. |
| `smpplugin.advent` | Open the Advent Calendar with /advent. | everyone | New in 4.0. |

## Rank perks (default: nobody)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.chat.color` | Keep colour codes typed into /msg and staff chat. | nobody | New in 2.0.0-alpha.5. Without it the codes are removed. |
| `smpplugin.auctionhouse.fastbuy` | Allow the quick-purchase setting that skips the Auction House buy confirmation. | nobody | Declared in 2.0; previously undeclared (op-only unless granted). |
| `smpplugin.auctionhouse.fastsell` | Allow the quick-sell setting for Auction House listings. | nobody | Declared in 2.0; previously undeclared (op-only unless granted). |
| `smpplugin.donutplus` | Show the server's + badge (player setting). | nobody | Declared in 2.0; previously undeclared (op-only unless granted). |
| `smpplugin.donutplusplus` | ++ rank: unlocks the second page of Auction House listing slots. | nobody | New in 2.0 with the merged Auction House. |
| `smpplugin.donutplusplusplus` | +++ rank: same Auction House slots as the ++ rank. | nobody | New in 2.0 with the merged Auction House. |
| `smpplugin.rtp.priority.vip++` | RTP waiting-queue priority 30 (rtp.yml PRIORITY-QUEUE). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.rtp.priority.vip+` | RTP waiting-queue priority 20 (rtp.yml PRIORITY-QUEUE). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.rtp.priority.vip` | RTP waiting-queue priority 10 (rtp.yml PRIORITY-QUEUE). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.rtp.priority.high` | RTP waiting-queue priority 5 (rtp.yml PRIORITY-QUEUE). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.rtp.cooldown.vip++` | 3-second RTP cooldown (rtp.yml RANK-COOLDOWNS). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.rtp.cooldown.vip+` | 10-second RTP cooldown (rtp.yml RANK-COOLDOWNS). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.rtp.cooldown.vip` | 15-second RTP cooldown (rtp.yml RANK-COOLDOWNS). | nobody | New in 2.0 with the merged RTP. |
| `smpplugin.player.fly` | Fly with /fly where the server allows player flight (config FLY-SYSTEM). | nobody | Declared in 2.0; previously undeclared (op-only unless granted). |
| `smpplugin.shards.everywhere` | Receive passive Shards Everywhere income. | nobody |  |
| `smpplugin.servers` | Open the network server status menu with /servers. | nobody |  |
| `smpplugin.hide.scramble` | Scramble your displayed name with /hide scramble. | nobody | Declared in 2.0; previously undeclared (op-only unless granted). |
| `smpplugin.hide.disguise` | Disguise as a configured alias with /hide disguise. | nobody | Declared in 2.0; previously undeclared (op-only unless granted). |
| `smpplugin.afk.statusbypass` | Never get the automatic [AFK] status after idling. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.fun.firework` | Launch a harmless firework with /firework. | nobody | New in 3.0. |
| `smpplugin.fun.bypasscooldown` | No cooldown on emotes and the fun commands. | nobody | New in 3.0. |
| `smpplugin.workbench.craft` | Open a crafting table anywhere with /craft. | nobody | New in 3.0. |
| `smpplugin.workbench.anvil` | Open an anvil anywhere with /anvil. | nobody | New in 3.0. |
| `smpplugin.workbench.grindstone` | Open a grindstone anywhere with /grindstone. | nobody | New in 3.0. |
| `smpplugin.workbench.stonecutter` | Open a stonecutter anywhere with /stonecutter. | nobody | New in 3.0. |
| `smpplugin.workbench.loom` | Open a loom anywhere with /loom. | nobody | New in 3.0. |
| `smpplugin.workbench.smithing` | Open a smithing table anywhere with /smithing. | nobody | New in 3.0. |
| `smpplugin.workbench.cartography` | Open a cartography table anywhere with /cartography. | nobody | New in 3.0. |
| `smpplugin.back.death` | Use /back to return to where you died. | nobody | New in 3.0. |
| `smpplugin.near` | List players near you with /near. | nobody | New in 3.0. |
| `smpplugin.ptime` | Set your own time of day with /ptime. | nobody | New in 3.0. |
| `smpplugin.pweather` | Set your own weather with /pweather. | nobody | New in 3.0. |
| `smpplugin.repair` | Repair the item in your hand with /repair (cooldown in extras.yml). | nobody | New in 3.0. |
| `smpplugin.repair.all` | Repair your whole inventory with /repair all. | nobody | New in 3.0. |
| `smpplugin.repair.bypasscooldown` | No cooldown on /repair. | nobody | New in 3.0. |
| `smpplugin.skull` | Get a player's head with /skull. | nobody | New in 3.0. |
| `smpplugin.lore` | Edit the lore of the item in your hand with /lore. | nobody | New in 3.0. |
| `smpplugin.glow` | Toggle a glowing outline with /glow. | nobody | New in 3.0. |
| `smpplugin.gambling.bypasscooldown` | No cooldown between /dice rolls. | nobody | New in 3.1. |
| `smpplugin.bypass.paylimit` | Pay more than ECONOMY.PAY.MAX-AMOUNT in one /pay. | nobody | New in 4.0. |
| `smpplugin.bypass.paycooldown` | No wait between payments (ECONOMY.PAY.COOLDOWN-SECONDS). | nobody | New in 4.0. |

## Staff tools (default: nobody; ops via smpplugin.admin)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.staff.mode` | Enter staff mode. Each hotbar tool still needs its own permission. | nobody | In 1.5 this node also granted every staff tool. In 2.0 it only opens staff mode; grant smpplugin.legacy.staff-mode-bundle for the old behaviour. |
| `smpplugin.staff.mode.vanish` | Use vanish (staff-mode hotbar and /vanish). | nobody |  |
| `smpplugin.staff.mode.stafflist` | Open the staff list. | nobody |  |
| `smpplugin.staff.mode.betterview` | Toggle better view from the staff-mode hotbar. | nobody |  |
| `smpplugin.staff.mode.randomtp` | Teleport to a random player from the staff-mode hotbar. | nobody |  |
| `smpplugin.staff.mode.spectator` | Switch to spectator from the staff-mode hotbar; sneak to switch back. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.staff.mode.seevanished` | See vanished staff members. | nobody |  |
| `smpplugin.staff.mode.others` | Toggle staff mode for other players. | nobody |  |
| `smpplugin.staff.freeze` | Freeze and unfreeze players. | nobody |  |
| `smpplugin.staff.freeze.alert` | Receive freeze alerts. | nobody |  |
| `smpplugin.staff.fly` | Toggle flight for yourself or others with /fly. | nobody |  |
| `smpplugin.staff.heal` | Heal yourself or others with /heal. | nobody |  |
| `smpplugin.staff.feed` | Feed yourself or others with /feed. | nobody |  |
| `smpplugin.staff.god` | Toggle god mode with /god. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.staff.gamemode` | Change your own game mode. | nobody |  |
| `smpplugin.staff.gamemode.others` | Change another player's game mode. | nobody |  |
| `smpplugin.staff.chat.use` | Send and read staff chat. | nobody |  |
| `smpplugin.staff.chat.mute` | Mute global chat. | nobody |  |
| `smpplugin.staff.chat.unmute` | Unmute global chat. | nobody |  |
| `smpplugin.staff.chat.delay` | Set the global chat slow-mode delay. | nobody |  |
| `smpplugin.staff.chat.clear` | Clear global chat. | nobody |  |
| `smpplugin.staff.helpop.receive` | Receive /helpop alerts. | nobody |  |
| `smpplugin.staff.report.receive` | Receive /report alerts. | nobody |  |
| `smpplugin.staff.alerts.receive` | Receive both /helpop and /report alerts. | nobody | Grants: `smpplugin.staff.helpop.receive`, `smpplugin.staff.report.receive` |
| `smpplugin.staff.teleport` | Use the staff /teleport suite (/tp, /tphere, /tpall, /tp top). | nobody |  |
| `smpplugin.staff.rename` | Rename the held item with /rename. | nobody |  |
| `smpplugin.staff.alts` | See accounts that share an IP address with /alts. | nobody |  |
| `smpplugin.staff.invsee` | View another player's inventory (read-only). | nobody | Upgrade spec name: smpplugin.staff.invsee.view. |
| `smpplugin.staff.profileviewer` | View player profiles and homes with /profileviewer. | nobody |  |
| `smpplugin.staff.punishments.view` | View punishment history with /punishments, or everyone's with no name. | nobody |  |
| `smpplugin.staff.punishments.vcmute` | Mute players in Simple Voice Chat with /vcmute, for a time or for good. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.staff.punishments.vcunmute` | Lift voice mutes with /vcunmute. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.staff.seehomes` | View another player's homes with /seehomes. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.staff.punishments.ban` | Permanently ban players with /ban. Also grants /tempban. | nobody | Grants smpplugin.staff.punishments.tempban as a child, matching 1.5. Grants: `smpplugin.staff.punishments.tempban` |
| `smpplugin.staff.punishments.tempban` | Temporarily ban players with /tempban. | nobody | New in 2.0; in 1.5 /tempban used smpplugin.staff.punishments.ban. |
| `smpplugin.staff.punishments.mute` | Permanently mute players with /mute. Also grants /tempmute. | nobody | Grants smpplugin.staff.punishments.tempmute as a child, matching 1.5. Grants: `smpplugin.staff.punishments.tempmute` |
| `smpplugin.staff.punishments.tempmute` | Temporarily mute players with /tempmute. | nobody | New in 2.0; in 1.5 /tempmute used smpplugin.staff.punishments.mute. |
| `smpplugin.staff.punishments.warn` | Warn players with /warn. | nobody | New in 2.0; in 1.5 /warn used smpplugin.staff.punishments.create. |
| `smpplugin.staff.punishments.kick` | Kick players with /kick. | nobody | New in 2.0; in 1.5 /kick used smpplugin.staff.punishments.create. |
| `smpplugin.staff.punishments.blacklist` | Blacklist players with /blacklist. | nobody |  |
| `smpplugin.staff.punishments.unban` | Lift bans with /unban (/pardon). | nobody |  |
| `smpplugin.staff.punishments.unmute` | Lift mutes with /unmute. | nobody |  |
| `smpplugin.staff.punishments.unblacklist` | Lift blacklists with /unblacklist. | nobody |  |
| `smpplugin.staff.punishments.offend` | Punish players with the preset offense rules in offenses.yml using /offend. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.staff.fakeplayer` | Spawn fake players with /fakeplayer. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.staff.fakeplayer.alert` | Receive fake-player alerts. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.staff.spawnstash` | Spawn and manage bait stashes with /spawnstash. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.staff.spawnstash.alert` | Receive alerts when a bait stash is found. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.security.alerts` | Receive anti-cheat alerts and toggle them with /anticheat alerts. | nobody | New in 2.0. |
| `smpplugin.security.inspect` | View anti-cheat status, check settings, flag history and evidence. | nobody | New in 2.0. |
| `smpplugin.staff.poll` | Start and end polls with /poll. | nobody | New in 3.0. |
| `smpplugin.staff.chatgame` | Start and stop chat games with /chatgame. | nobody | New in 3.0. |
| `smpplugin.staff.countdown` | Show a server-wide countdown with /countdown. | nobody | New in 3.0. |
| `smpplugin.staff.speed` | Change walk and fly speed with /speed. | nobody | New in 3.0. |
| `smpplugin.staff.whois` | Look up a player's details with /whois (the IP also needs smpplugin.staff.alts). | nobody | New in 3.0. |
| `smpplugin.staff.socialspy` | See private messages between players with /socialspy. | nobody | New in 3.0. |
| `smpplugin.staff.commandspy` | See the commands players run with /commandspy. | nobody | New in 3.0. |
| `smpplugin.staff.spawn.others` | Send another player to spawn with /spawn <player>. | nobody | New in 3.1. |

## Administration (default: nobody; ops via smpplugin.admin)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.admin.status` | View /smpplugin status (platform, storage, integrations, anti-Xray). | nobody | New in 2.0. |
| `smpplugin.admin.reload` | Reload configuration with /smpplugin reload. | nobody |  |
| `smpplugin.admin.optimize` | View and change runtime optimization with /smpplugin optimize. | nobody |  |
| `smpplugin.admin.setup` | Use the /smpplugin setup wizard. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.admin.features` | Turn features on and off with /smpplugin features. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.admin.maintenance` | Turn maintenance mode on and off. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.admin.updatenotify` | See the update-available notice on join. | nobody | New in 2.0; replaces the isOp() check and smpplugin.updatechecker. |
| `smpplugin.admin.shop` | Reload and validate shop settings. | nobody |  |
| `smpplugin.admin.orders` | Administer the Orders board. | nobody |  |
| `smpplugin.admin.auctionhouse` | Administer the Auction House; also passes every Auction House action check. | nobody |  |
| `smpplugin.admin.duels` | Manage duels and duel arenas. | nobody |  |
| `smpplugin.admin.ffa` | Manage FFA and FFA arenas. | nobody |  |
| `smpplugin.admin.clearlag` | Run /clearlag. | nobody |  |
| `smpplugin.admin.cuboid` | Manage cuboid regions. | nobody |  |
| `smpplugin.admin.warpmanager` | Manage warps with /warpmanager. | nobody |  |
| `smpplugin.admin.setwarp` | Create warps with /setwarp. | nobody |  |
| `smpplugin.admin.delwarp` | Delete warps with /delwarp. | nobody |  |
| `smpplugin.admin.portalmanager` | Manage RTP portals. | nobody |  |
| `smpplugin.admin.setspawn` | Set the server spawn. | nobody |  |
| `smpplugin.admin.afkzone` | Build and edit AFK zones with /afkzone. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.admin.teleportareas.delete` | Delete spawn areas from the spawn menu. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.admin.amethysttool` | Give amethyst tools. | nobody |  |
| `smpplugin.admin.worth` | Reload worth settings. | nobody |  |
| `smpplugin.admin.sellstats` | View and export sell statistics with /topsell. Resetting them needs smpplugin.admin.statswipe. | nobody | New in 2.0 with the merged sell system. |
| `smpplugin.admin.enderchest` | Reload custom Ender Chest settings. | nobody |  |
| `smpplugin.admin.ecsee` | View another player's Ender Chest with /ecsee (read-only). | nobody | Upgrade spec name: smpplugin.staff.ecsee.view. |
| `smpplugin.admin.freeze` | Reload freeze settings and freeze exempt players. | nobody |  |
| `smpplugin.admin.staffmode` | Reload staff-mode settings. | nobody |  |
| `smpplugin.admin.invsee` | Reload invsee settings. | nobody |  |
| `smpplugin.admin.spawner` | Give and manage managed spawners. | nobody |  |
| `smpplugin.admin.crate` | Create, edit and bind crates; give and take keys. | nobody |  |
| `smpplugin.admin.crate.reload` | Reload crate settings. | nobody |  |
| `smpplugin.admin.crate.keyall` | Start a key-all manually. | nobody |  |
| `smpplugin.admin.shards` | Add and remove shards; inspect Shards Everywhere. | nobody |  |
| `smpplugin.admin.addmoney` | Add money to a balance. | nobody |  |
| `smpplugin.admin.removemoney` | Remove money from a balance. | nobody |  |
| `smpplugin.admin.addkills` | Add kills to a player. | nobody |  |
| `smpplugin.admin.adddeaths` | Add deaths to a player. | nobody |  |
| `smpplugin.admin.addplaytime` | Add playtime to a player. | nobody |  |
| `smpplugin.admin.lobby.setlobby` | Set the lobby location with /setlobby. | nobody |  |
| `smpplugin.admin.lobby.reload` | Reload lobby.yml. | nobody |  |
| `smpplugin.admin.lobby.debug` | Toggle lobby debug output. | nobody |  |
| `smpplugin.admin.logs` | View a player's activity log with /logs. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.admin.chatlog` | Browse logged public and team chat with /chatlog. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.admin.spawnstash` | Administer bait stashes (reload, remove any). | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.admin.anvilmod` | Manage anvil moderation words (/amod) and bypass its filter. | nobody | Renamed from anvilmod.admin in 2.0. |
| `smpplugin.admin.safety.reload` | Reload the safety book. | nobody | Renamed from safety.reload in 2.0. |
| `smpplugin.admin.safety.give` | Give the safety book to a player. | nobody | Renamed from safety.add in 2.0. |
| `smpplugin.admin.friends` | Use administrative /friends subcommands. | nobody | Renamed from donutfriends.admin in 2.0. |
| `smpplugin.hide.admin` | Manage other players' hide and disguise state. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.security.manage` | Grant temporary anti-cheat exemptions, reset violation levels and reload anticheat.yml. | nobody | New in 2.0. |
| `smpplugin.admin.playerwipe` | Wipe one player's stats, homes, items and progress with /playerwipe (backed up first). | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.admin.playerunwipe` | Restore a wiped player from their backup with /playerunwipe. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.admin.halloween` | Edit Spooky Calendar prizes and use /halloween give, reset, preview and stats. | nobody | New in 3.0. |
| `smpplugin.admin.announcer` | List, send and pause the automatic announcements with /announcer. | nobody | New in 3.0. |
| `smpplugin.admin.broadcast` | Send a server-wide announcement with /broadcast. | nobody | New in 3.0. |
| `smpplugin.admin.time` | Set the time with /day and /night. | nobody | New in 3.0. |
| `smpplugin.admin.weather` | Set the weather with /sun, /rain and /thunder. | nobody | New in 3.0. |
| `smpplugin.admin.smite` | Strike a player with (harmless) lightning with /smite. | nobody | New in 3.0. |
| `smpplugin.admin.butcher` | Remove nearby hostile mobs with /butcher. | nobody | New in 3.0. |
| `smpplugin.admin.spawnmob` | Spawn mobs with /spawnmob. | nobody | New in 3.0. |
| `smpplugin.admin.more` | Fill the held stack with /more. | nobody | New in 3.0. |
| `smpplugin.admin.performance` | See TPS, memory, chunks and entities with /performance. | nobody | New in 3.0. |
| `smpplugin.admin.spook` | Jump-scare a player (or everyone) with /spook. | nobody | New in 3.0. |
| `smpplugin.admin.perms` | Inspect roles and permissions with /perms. | nobody | New in 3.0. |
| `smpplugin.admin.coinflip` | Cancel every open coin flip and refund it with /cf cancelall. | nobody | New in 3.2. |
| `smpplugin.admin.sidebar` | Edit the scoreboard title and lines in game with /sidebar. | nobody | New in 3.1. |
| `smpplugin.admin.border` | Set up the spawn and lobby world borders with /border. | nobody | New in 3.1. |
| `smpplugin.admin.economy` | Give and take money, see the economy totals and anyone's history with /economy. | nobody | New in 4.0. Every change is written to the economy ledger with the admin's name. |
| `smpplugin.admin.economy.history` | Read any player's money and shard history with /economy history <player>. | nobody | New in 4.0. |
| `smpplugin.admin.ranked` | Set or reset ranked ratings with /ranked setrating and /ranked reset. | nobody | New in 4.0. |
| `smpplugin.admin.events` | Switch seasonal events on and off, reload them, and hide Easter eggs with /easter addegg. | nobody | New in 4.0. |
| `smpplugin.admin.advent` | Edit Advent Calendar gifts, preview dates and reset doors with /advent. | nobody | New in 4.0. |

## Operational bypasses (default: nobody; ops via smpplugin.admin)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.bypass.lobby` | Ignore lobby protection and the lobby border (needed to build the lobby). | nobody |  |
| `smpplugin.bypass.lobby.border` | Ignore only the lobby border. | nobody |  |
| `smpplugin.afk.kickbypass` | Never get kicked for being AFK (afk.yml IDLE-ACTIONS.KICK-SECONDS). | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.admin.maintenance.bypass` | Join and stay on the server during maintenance. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.bypass.border` | Walk through the spawn and lobby borders (staff building or moderating). | nobody | New in 3.1. |
| `smpplugin.bypass.itemban` | Use, place, craft and pick up items banned in survival-rules.yml. | nobody | New in 4.0. |

## Security and privacy bypasses (default: nobody; never bundled)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.admin.punishments.exempt` | Cannot be punished by staff unless they hold the exempt bypass. | nobody | New in 2.0.0-alpha.5. Not granted by smpplugin.admin, so moderators cannot make each other exempt. |
| `smpplugin.admin.punishments.exempt.bypass` | Punish players who hold smpplugin.admin.punishments.exempt. | nobody | New in 2.0.0-alpha.5. |
| `smpplugin.ignore.bypass` | Message players who ignore you. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.message.bypass-disabled` | Message players who turned private messages off. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.chat.bypass.mute` | Talk while global chat is muted. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.chat.bypass.delay` | Ignore the chat slow-mode delay. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.chat.bypass.filter` | Bypass the chat filter. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.freeze.exempt` | Cannot be frozen by staff without smpplugin.admin.freeze. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.alerts.bypass-cooldown` | Skip the /helpop and /report cooldowns. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.admin.spawner.seeall` | See spawners that anti-ESP hides from other players. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.hide.bypass` | See the real names behind hidden and disguised players. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.preventdrop.bypass` | Drop items where item dropping is blocked. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.staff.fakeplayer.bypass` | Not affected by fake-player restrictions. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.staff.spawnstash.bypass` | Do not trigger bait-stash alerts. | nobody | Declared in 2.0; previously undeclared. |
| `smpplugin.bypass.anticheat.movement` | Skip anti-cheat movement checks (fly, speed, timer, no-fall, step, water walk, boat fly). Use is logged once per session. | nobody | New in 2.0. Must be granted as the exact node; staff mode does not grant it. |
| `smpplugin.bypass.anticheat.combat` | Skip anti-cheat combat checks (reach, wall hit, kill aura, autoclicker, anti-knockback, criticals). Use is logged. | nobody | New in 2.0. Must be granted as the exact node. |
| `smpplugin.bypass.anticheat.world` | Skip anti-cheat block checks (fast break, nuker, fast place, block reach, scaffold, through-wall, x-ray). Use is logged. | nobody | New in 2.0. Must be granted as the exact node. |
| `smpplugin.bypass.anticheat.player` | Skip anti-cheat inventory, auto-totem and cheat-client checks. Use is logged. | nobody | New in 2.0. Must be granted as the exact node. |

## Destructive actions (default: nobody; never bundled)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.admin.serverwipe` | Run the season wipe workflow (/serverwipe). | nobody | Declared in 2.0 with default false; previously undeclared (op-only). |
| `smpplugin.admin.statswipe` | Wipe stats with /smpplugin statswipe. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.punishments.delete` | Delete punishment records from history. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.admin.setmoney` | Overwrite a player's balance with /setmoney. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.admin.setkills` | Overwrite a player's kill count. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.admin.setdeaths` | Overwrite a player's death count. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.admin.setplaytime` | Overwrite a player's playtime. | nobody | Removed from smpplugin.admin in 2.0; grant explicitly. |
| `smpplugin.staff.invsee.modify` | Edit another player's inventory through invsee. | nobody | Not part of smpplugin.admin. Upgrade spec name: smpplugin.staff.invsee.modify. |
| `smpplugin.admin.ecsee.edit` | Edit another player's Ender Chest through /ecsee. | nobody | Declared in 2.0 with default false; previously undeclared (op-only). Upgrade spec name: smpplugin.staff.ecsee.modify. |
| `smpplugin.admin.economy.set` | Overwrite or reset a balance with /economy set and /economy reset. | nobody | New in 4.0. Destructive like smpplugin.admin.setmoney. |

## Senior staff (default: nobody, but always allowed for ops; only the manager, developer, co-owner and owner roles)

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.giveaway.manage` | Start, cancel and give away money with /giveaway. Ops are always allowed. | nobody | New in 3.1. Replaces smpplugin.staff.giveaway and smpplugin.admin.giveaway.money. Only the manager, developer, co-owner and owner roles include it. |

## Bundles

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.admin` | Default-op administrator bundle: staff tools, administration and operational bypasses. Excludes destructive actions and security bypasses. | ops | In 2.0 this no longer includes destructive actions or security bypasses. Contains 137 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.helper` | Helper role: staff chat, reports, warn, kick, temp-mute, /whois and events. | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 14 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.moderator` | Moderator role: everything a helper has, plus staff mode, vanish, freeze, bans, mutes, invsee and spy. | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 31 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.admin` | Admin role: everything a moderator has plus every tool in smpplugin.admin (no destructive actions). | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 2 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.manager` | Manager role: the admin role plus managing giveaways. | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 2 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.developer` | Developer role: the admin role plus managing giveaways. | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 2 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.owner` | Owner role: the admin role plus every destructive action (wipes, set-money) and every rank perk. Security bypasses (anti-cheat, hidden names) are still granted one by one. | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 53 nodes: every node in the staff, administration and operational-bypass tables. |
| `smpplugin.role.coowner` | Co-owner role: everything the owner role has. | nobody | New in 3.0. Give this one node to a rank (for example with LuckPerms) instead of picking nodes. Contains 1 nodes: every node in the staff, administration and operational-bypass tables. |

## Legacy names and bundles

| Node | Allows | Default | Notes |
|---|---|---|---|
| `smpplugin.staff.punishments.create` | Legacy bundle: every punishment-creating action. | nobody | Kept from 1.5. Prefer the individual nodes. Grants: `smpplugin.staff.punishments.warn`, `smpplugin.staff.punishments.kick`, `smpplugin.staff.punishments.ban`, `smpplugin.staff.punishments.tempban`, `smpplugin.staff.punishments.mute`, `smpplugin.staff.punishments.tempmute`, `smpplugin.staff.punishments.blacklist` |
| `smpplugin.staff.punishments.remove` | Legacy bundle: lift bans, mutes and blacklists. | nobody | Kept from 1.5. Prefer the individual nodes. Grants: `smpplugin.staff.punishments.unban`, `smpplugin.staff.punishments.unmute`, `smpplugin.staff.punishments.unblacklist` |
| `smpplugin.legacy.staff-mode-bundle` | Everything smpplugin.staff.mode granted in 1.5. | nobody | New in 2.0. Grant this where you granted smpplugin.staff.mode in 1.5 to keep the old tool set. Grants: `smpplugin.staff.mode`, `smpplugin.staff.freeze`, `smpplugin.staff.freeze.alert`, `smpplugin.staff.fly`, `smpplugin.staff.heal`, `smpplugin.staff.feed`, `smpplugin.staff.gamemode`, `smpplugin.staff.gamemode.others`, `smpplugin.staff.mode.vanish`, `smpplugin.staff.mode.stafflist`, `smpplugin.staff.mode.betterview`, `smpplugin.staff.mode.randomtp`, `smpplugin.staff.mode.seevanished`, `smpplugin.staff.mode.others`, `smpplugin.staff.chat.use`, `smpplugin.staff.teleport`, `smpplugin.staff.rename`, `smpplugin.staff.alts`, `smpplugin.staff.alerts.receive`, `smpplugin.staff.alerts.bypass-cooldown`, `smpplugin.staff.invsee`, `smpplugin.staff.profileviewer`, `smpplugin.staff.punishments.view`, `smpplugin.staff.punishments.create`, `smpplugin.staff.punishments.remove` |
| `anvilmod.admin` | Legacy name for smpplugin.admin.anvilmod. | nobody | Kept so existing grants keep working. Grant smpplugin.admin.anvilmod instead. Grants: `smpplugin.admin.anvilmod` |
| `safety.use` | Legacy name for smpplugin.safety.use. | nobody | Kept so existing grants keep working. Grant smpplugin.safety.use instead. Grants: `smpplugin.safety.use` |
| `safety.reload` | Legacy name for smpplugin.admin.safety.reload. | nobody | Kept so existing grants keep working. Grant smpplugin.admin.safety.reload instead. Grants: `smpplugin.admin.safety.reload` |
| `safety.add` | Legacy name for smpplugin.admin.safety.give. | nobody | Kept so existing grants keep working. Grant smpplugin.admin.safety.give instead. Grants: `smpplugin.admin.safety.give` |
| `smpplugin.updatechecker` | Legacy name for smpplugin.admin.updatenotify. | nobody | Kept so existing grants keep working. Grant smpplugin.admin.updatenotify instead. Grants: `smpplugin.admin.updatenotify` |
| `donutfriends.admin` | Legacy name for smpplugin.admin.friends. | nobody | Kept so existing grants keep working. Grant smpplugin.admin.friends instead. Grants: `smpplugin.admin.friends` |
| `smpplugin.admin.lobby.border` | Legacy name for smpplugin.admin.border. | nobody | Kept so existing grants keep working. Grant smpplugin.admin.border instead. Grants: `smpplugin.admin.border` |
| `donutauction.use` | Legacy name for smpplugin.auctionhouse.use. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.use instead. Grants: `smpplugin.auctionhouse.use` |
| `donutauction.sell` | Legacy name for smpplugin.auctionhouse.sell. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.sell instead. Grants: `smpplugin.auctionhouse.sell` |
| `donutauction.buy` | Legacy name for smpplugin.auctionhouse.buy. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.buy instead. Grants: `smpplugin.auctionhouse.buy` |
| `donutauction.cancel` | Legacy name for smpplugin.auctionhouse.cancel. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.cancel instead. Grants: `smpplugin.auctionhouse.cancel` |
| `donutauction.claims` | Legacy name for smpplugin.auctionhouse.claims. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.claims instead. Grants: `smpplugin.auctionhouse.claims` |
| `donutauction.my` | Legacy name for smpplugin.auctionhouse.my. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.my instead. Grants: `smpplugin.auctionhouse.my` |
| `donutauction.limit` | Legacy name for smpplugin.auctionhouse.limit. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.limit instead. Grants: `smpplugin.auctionhouse.limit` |
| `donutauction.fastbuy` | Legacy name for smpplugin.auctionhouse.fastbuy. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.fastbuy instead. Grants: `smpplugin.auctionhouse.fastbuy` |
| `donutauction.fastsell` | Legacy name for smpplugin.auctionhouse.fastsell. | nobody | Kept so existing grants keep working. Grant smpplugin.auctionhouse.fastsell instead. Grants: `smpplugin.auctionhouse.fastsell` |

## Numbered permissions

Grant the exact node with the number you want, e.g. `smpplugin.homes.10`. These are not declared
in plugin.yml because the numbers are your choice. Wildcards do not grant them.

| Pattern | Meaning |
|---|---|
| `smpplugin.homes.<n>` | Maximum number of homes. The highest granted number wins; SETTINGS.HOME-DEFAULT applies otherwise. |
| `smpplugin.homes.page.<n>` | Number of home pages; each page adds a fixed number of homes. |
| `smpplugin.auctionhouse.limit.<n>` | Maximum active Auction House listings. Also accepted: smpplugin.auctionhouse.<n> and legacy donutauction.limit.<n>. |
| `smpplugin.auctionhouse.<n>` | Older spelling of smpplugin.auctionhouse.limit.<n>. |
| `donutauction.limit.<n>` | Legacy spelling of smpplugin.auctionhouse.limit.<n>. |
| `smpplugin.orders.limit.<n>` | Maximum active buy orders. |
| `smpplugin.rtp.cooldown.<n>` | RTP cooldown in seconds. The lowest granted value wins (named nodes in rtp.yml RANK-COOLDOWNS count too); 0 removes the cooldown. |
| `smpplugin.rtp.priority.<n>` | Priority in the RTP waiting queue; higher goes first. Named nodes can be mapped in rtp.yml PRIORITY-QUEUE.PERMISSIONS. |

