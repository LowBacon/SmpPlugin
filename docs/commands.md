# Command reference

Generated from `plugin.yml` for SmpPlugin 4.0.0. Most commands also check their permission in code; see
[permissions-reference.md](permissions-reference.md) for what each node does and who has it by default.
A command whose module is turned off (`/smp modules`) answers that the feature is off. "none" means plugin.yml
lets anyone run the command; the command may still check a node for some of its parts.

216 commands.

## Administration (4.0)

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/smpplugin` | `/smp`, `/csmp`, `/customsmp` | `/smp <help\|status\|modules\|reload [module]\|diagnostics\|version\|statswipe\|optimize\|setup\|maintenance>` | SmpPlugin administration | `smpplugin.admin.status` `smpplugin.admin.reload` `smpplugin.admin.statswipe` `smpplugin.admin.optimize` `smpplugin.admin.setup` `smpplugin.admin.features` `smpplugin.admin.maintenance` |
| `/economy` | `/eco` | `/economy <give\|take\|set\|reset\|history\|stats> [player] [amount] [reason]` | Manage money (give, take, set, reset) and read the transaction history | `smpplugin.admin.economy` `smpplugin.admin.economy.history` |

## Economy and shards

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/balance` | `/bal`, `/money` | `/balance [player] \| /balance history [page]` | Check balance | none |
| `/pay` |  | `/pay <player> <amount>` | Pay a player | none |
| `/addmoney` |  | `/addmoney <player> <amount> [reason]` | Add money to a player | `smpplugin.admin.addmoney` |
| `/removemoney` |  | `/removemoney <player> <amount> [reason]` | Remove money from a player | `smpplugin.admin.removemoney` |
| `/setmoney` |  | `/setmoney <player> <amount> [reason]` | Set a player's money balance | `smpplugin.admin.setmoney` |
| `/logs` |  | `/logs <player>` | View a player's activity log | `smpplugin.admin.logs` |
| `/shards` |  | `/shards [player] \| /shards everywhere <status\|debug> [player]` | Check shards | none |
| `/shardpay` |  | `/shardpay <player> <amount>` | Pay shards to a player | none |
| `/addshards` |  | `/addshards <player> <amount> [reason]` | Add shards to a player | `smpplugin.admin.shards` |
| `/removeshards` |  | `/removeshards <player> <amount> [reason]` | Remove shards from a player | `smpplugin.admin.shards` |
| `/setshards` |  | `/setshards <player> <amount> [reason]` | Set a player's shard balance | `smpplugin.admin.shards` |
| `/leaderboard` | `/lb`, `/top`, `/leaderboards`, `/baltop` | `/leaderboard [type]` | View leaderboards | none |

## Market

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/shop` |  | `/shop [reload]` | Open the shop | none |
| `/shardshop` |  | `/shardshop` | Open the shop that charges shards | none |
| `/orders` |  | `/orders [my\|collect\|reload]` | Open the Orders board | none |
| `/ah` |  | `/ah [sell\|my\|claims\|cancel\|reload]` | Open the Auction House | none |
| `/sell` |  | `/sell` | Open sell menu | none |
| `/sellhand` |  | `/sellhand [amount]` | Sell item in hand | none |
| `/sellall` |  | `/sellall` | Sell all sellable items | none |
| `/sellhistory` |  | `/sellhistory` | View sell history | none |
| `/sellmulti` |  | `/sellmulti [category]` | Open the sell multiplier menu | none |
| `/sellmultiplier` |  | `/sellmultiplier [category]` | Open the sell multiplier menu | none |
| `/sellprogress` |  | `/sellprogress [category]` | Open the sell multiplier progress menu | none |
| `/topsell` | `/sellstats` | `/topsell [gui\|items\|volume\|sellers\|export\|reset]` | View and export sell statistics | `smpplugin.admin.sellstats` |
| `/meta` | `/farmingmeta` | `/meta` | Show the item that is currently the farming meta | none |
| `/worth` | `/prices` | `/worth [hand\|reload]` | Open the worth browser or check held item worth | none |
| `/billford` |  | `/billford` | Open Billford trade | none |

## Crates and rewards

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/crate` |  | `/crate <create\|delete\|type\|open\|keys\|reload\|key\|take\|set\|keyall\|add\|edit\|remove\|bind\|unbind\|listbound\|info>` | Manage crates | none |
| `/crates` |  | `/crates` | Open the crates menu | none |
| `/keys` |  | `/keys` | Open your crate keys menu | none |
| `/daily` | `/dailyreward` | `/daily` | Claim your daily reward and keep your streak | `smpplugin.daily` |
| `/challenges` | `/challenge` | `/challenges` | See today's challenges and your progress | `smpplugin.challenges` |
| `/halloween` | `/spooky`, `/spookycalendar` | `/halloween [edit\|give\|reset\|preview\|stats\|reload]` | Open the Spooky Calendar and open today's door | `smpplugin.halloween` |
| `/trickortreat` | `/tot` | `/trickortreat` | Knock for a Halloween treat (or a harmless trick) | `smpplugin.halloween.trickortreat` |
| `/advent` | `/adventcalendar` | `/advent [edit\|give\|reset\|preview\|stats\|reload]` | Open the Advent Calendar | `smpplugin.advent` |
| `/event` | `/events` | `/event [status\|info <event>\|open <event>\|enable <event>\|disable <event>\|auto <event>\|reload]` | Seasonal events (Advent, Christmas, Easter, Summer and custom events) | `smpplugin.events` |
| `/easter` | `/egghunt` | `/easter [top\|addegg\|removeegg [id]\|eggs\|reset <player>]` | The Easter egg hunt | `smpplugin.events` |

## PvP

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/duel` |  | `/duel [player\|accept\|deny\|claims\|reload]` | Challenge or manage duels | none |
| `/create` |  | `/create <invite\|friends> <player> [map]` | Create a custom duel and invite a player or friend | none |
| `/queue` |  | `/queue [join\|leave]` | Open or join the duel queue | none |
| `/leave` |  | `/leave` | Leave a duel or FFA queue/match | none |
| `/draw` |  | `/draw` | Request or accept a duel draw | none |
| `/arena` | `/duelarena` | `/arena <create\|delete\|setpos1\|setpos2\|setreturn\|setdisplay\|enable\|disable\|queue\|list\|reload>` | Manage duel arenas | `smpplugin.admin.duels` |
| `/ffa` |  | `/ffa [join\|reload\|arena ...]` | Join the instanced FFA queue | none |
| `/ffastats` |  | `/ffastats [player]` | View FFA stats | none |
| `/ffaarena` |  | `/ffaarena <create\|delete\|setpos\|setdisplay\|settings\|enable\|disable\|list\|reload>` | Manage FFA arenas | `smpplugin.admin.ffa` |
| `/ranked` |  | `/ranked [join\|leave\|stats [player]\|top [page]\|history [player]]` | Ranked PvP with Elo ratings | `smpplugin.ranked` |
| `/bounty` |  | `/bounty <add\|set\|info\|list> [player] [amount]` | Manage bounties | none |

## Teleporting

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/spawn` |  | `/spawn [player]` | Teleport to spawn (staff can send another player) | none |
| `/setspawn` | `/delspawn` | `/setspawn [delete]` | Set the primary server spawn at your current location, or delete it | `smpplugin.admin.setspawn` |
| `/lobby` | `/dellobby` | `/lobby [survival\|delete\|reload\|debug\|help]` | Go to the lobby, return to Survival, or manage the lobby | none |
| `/setlobby` |  | `/setlobby` | Set the protected lobby location at your current location | `smpplugin.admin.lobby.setlobby` |
| `/home` |  | `/home [name]` | Teleport to your home | none |
| `/homes` |  | `/homes` | List your homes | none |
| `/sethome` |  | `/sethome [name]` | Set your home | none |
| `/delhome` |  | `/delhome <name>` | Delete a home | none |
| `/renamehome` |  | `/renamehome <old> <new>` | Rename a home | none |
| `/warp` |  | `/warp [name]` | Teleport to a warp | none |
| `/warpmanager` |  | `/warpmanager <create\|delete\|list> [name]` | Manage warp points | `smpplugin.admin.warpmanager` `smpplugin.admin.setwarp` `smpplugin.admin.delwarp` |
| `/setwarp` |  | `/setwarp <name>` | Set a warp point | `smpplugin.admin.warpmanager` `smpplugin.admin.setwarp` |
| `/delwarp` |  | `/delwarp <name>` | Delete a warp point | `smpplugin.admin.warpmanager` `smpplugin.admin.delwarp` |
| `/rtp` |  | `/rtp [world]` | Random teleport | none |
| `/rtpq` | `/rtpqueue` | `/rtpq [join\|leave]` | Join the RTP queue to be teleported together with other players | none |
| `/tpa` |  | `/tpa <player>` | Request teleport to player | none |
| `/tpahere` |  | `/tpahere <player>` | Request player to teleport to you | none |
| `/tpaccept` |  | `/tpaccept [player]` | Accept a TPA request | none |
| `/tpadeny` |  | `/tpadeny [player]` | Deny a TPA request | none |
| `/tpacancel` |  | `/tpacancel` | Cancel your TPA requests | none |
| `/tpauto` |  | `/tpauto` | Toggle TPA auto-accept | none |
| `/tpahereauto` |  | `/tpahereauto` | Toggle TPA Here auto-accept | none |
| `/back` |  | `/back` | Go back to where you were before your last teleport | `smpplugin.back` |
| `/portalmanager` |  | `/portalmanager <list\|info\|create\|delete\|setcuboid\|setdestination\|setdisplay\|toggle\|setpriority\|sethologramhere>` | Manage RTP portal triggers | `smpplugin.admin.portalmanager` |
| `/border` | `/spawnborder` | `/border [spawn\|lobby] [info\|set <size>\|center\|warning <blocks> [seconds]\|damage <amount> [buffer]\|mode <player\|world>\|on\|off]` | Put a real Minecraft world border around the spawn or the lobby | `smpplugin.admin.border` |

## Social

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/team` |  | `/team <create\|disband\|invite\|kick\|join\|leave\|home\|sethome\|delhome\|chat\|info\|pvp>` | Team management | none |
| `/friends` |  | `/friends [list\|following\|followers\|friends\|add\|remove\|search\|reload]` | Manage friends and follows | none |
| `/friend` |  | `/friend [list\|following\|followers\|friends\|add\|remove\|search\|reload]` | Manage friends and follows | none |
| `/msg` | `/message`, `/tell`, `/whisper`, `/w` | `/msg <player> <message>` | Send a private message | `smpplugin.message` |
| `/reply` | `/r` | `/reply <message>` | Reply to your last private conversation | `smpplugin.message` |
| `/pm` | `/togglepm`, `/privatemessages` | `/pm` | Toggle private messages | `smpplugin.message.toggle` |
| `/ignore` |  | `/ignore <player\|list>` | Add or remove a player from your ignore list | `smpplugin.ignore` |
| `/unignore` |  | `/unignore <player>` | Remove a player from your ignore list | `smpplugin.ignore` |
| `/mail` | `/inbox` | `/mail [read\|send <player> <message>\|clear]` | Send mail to any player, even offline | `smpplugin.mail` |
| `/chat` |  | `/chat <help\|mute\|unmute\|delay\|clear>` | Manage global chat settings | `smpplugin.staff.chat.use` `smpplugin.staff.chat.mute` `smpplugin.staff.chat.unmute` `smpplugin.staff.chat.delay` `smpplugin.staff.chat.clear` |

## Everything else

| Command | Aliases | Usage | What it does | Permission in plugin.yml |
|---|---|---|---|---|
| `/adddeaths` |  | `/adddeaths <player> <amount>` | Add deaths to a player | `smpplugin.admin.adddeaths` |
| `/addkills` |  | `/addkills <player> <amount>` | Add kills to a player | `smpplugin.admin.addkills` |
| `/addplaytime` |  | `/addplaytime <player> <seconds\|1d2h30m>` | Add playtime to a player | `smpplugin.admin.addplaytime` |
| `/afk` |  | `/afk [zone]` | Go to an AFK zone and earn shards while you wait | none |
| `/afkzone` | `/afkzones` | `/afkzone <pos1\|pos2\|create\|setregion\|setspawn\|reward\|rename\|delete\|list\|show\|reload>` | Build and edit AFK zones | `smpplugin.admin.afkzone` |
| `/alts` |  | `/alts <player>` | View alternate accounts by shared IP history | `smpplugin.staff.alts` |
| `/amethysttool` |  | `/amethysttool give <player> <type> [duration] \| /amethysttool reload` | Manage amethyst tools | `smpplugin.admin.amethysttool` |
| `/amod` |  | `/amod <add\|reload> [word]` | Manage anvil moderation banned words | `smpplugin.admin.anvilmod` |
| `/announcer` |  | `/announcer [list\|send <n>\|toggle]` | Manage the automatic announcements | `smpplugin.admin.announcer` |
| `/anticheat` | `/ac` | `/anticheat <alerts\|status\|checks\|history\|evidence\|profile\|bans\|exempt\|reset\|reload>` | Anti-cheat alerts, evidence, profiles, ban queue, exemptions and status | `smpplugin.security.alerts` `smpplugin.security.inspect` `smpplugin.security.manage` |
| `/anvil` |  | `/anvil` | Open an anvil | `smpplugin.workbench.anvil` |
| `/away` |  | `/away` | Mark yourself AFK, or come back | `smpplugin.afk.away` |
| `/ban` |  | `/ban <player> [reason]` | Ban a player and record punishment history | `smpplugin.staff.punishments.ban` |
| `/blacklist` |  | `/blacklist <player> [reason]` | Blacklist a player and record punishment history | `smpplugin.staff.punishments.blacklist` |
| `/broadcast` | `/bc`, `/announce` | `/broadcast [-title] <message>` | Send a server-wide announcement | `smpplugin.admin.broadcast` |
| `/butcher` | `/killmobs` | `/butcher [radius] [all]` | Remove nearby hostile mobs | `smpplugin.admin.butcher` |
| `/calc` | `/calculate`, `/math` | `/calc <sum>` | Do a sum | `smpplugin.calc` |
| `/cartography` | `/cartographytable` | `/cartography` | Open a cartography table | `smpplugin.workbench.cartography` |
| `/chatgame` | `/chatgames` | `/chatgame [start [type]\|stop]` | See the current chat game, or start one | none |
| `/chatlog` |  | `/chatlog [player]` | Browse logged public and team chat | `smpplugin.admin.chatlog` |
| `/cheer` |  | `/cheer` | Cheer! | `smpplugin.fun.emotes` |
| `/clearlag` |  | `/clearlag` | Manually clear lag entities | `smpplugin.admin.clearlag` |
| `/coinflip` | `/cf` | `/cf [<amount>\|join <player>\|cancel\|help]` | Coin flips - start one or join someone's; the flip starts at once | `smpplugin.coinflip.use` |
| `/commandspy` | `/cspy` | `/commandspy` | See the commands players run | `smpplugin.staff.commandspy` |
| `/coords` | `/coordinates`, `/pos` | `/coords [share]` | Show (or share) your coordinates | `smpplugin.coords` |
| `/countdown` |  | `/countdown <seconds> [what is starting]` | Show a countdown to everyone | `smpplugin.staff.countdown` |
| `/craft` | `/workbench`, `/wb` | `/craft` | Open a crafting table | `smpplugin.workbench.craft` |
| `/cuboid` |  | `/cuboid <wand\|create\|delete\|list\|bind <cuboid> <spawn\|shard\|rtp-zone> <true\|false>\|reload>` | Manage cuboid regions | `smpplugin.admin.cuboid` |
| `/day` |  | `/day [world]` | Set the time to day | `smpplugin.admin.time` |
| `/dice` | `/roll` | `/dice <bet> [high\|low\|1-6] \| /dice roll [sides] \| /dice odds` | Bet money on a dice roll, or roll one just for fun | none |
| `/discord` |  | `/discord` | Get Discord link | none |
| `/disguise` |  | `/disguise <alias>` | Disguise as a configured alias | `smpplugin.hide.disguise` `smpplugin.hide.admin` |
| `/ecsee` |  | `/ecsee <player>` | View another player's Ender Chest | `smpplugin.admin.ecsee` |
| `/enderchest` | `/ec` | `/enderchest [reload]` | Open your custom Ender Chest | none |
| `/fakeplayer` |  | `/fakeplayer` | Spawn a packet-based fake player (requires ProtocolLib) | `smpplugin.staff.fakeplayer` |
| `/feed` |  | `/feed [player]` | Restore hunger for yourself or another player | `smpplugin.staff.feed` |
| `/findplayer` | `/fp` | `/findplayer <player>` | See which dimension and biome an online player is in | `smpplugin.findplayer` |
| `/firework` | `/fw` | `/firework` | Launch a harmless firework | `smpplugin.fun.firework` |
| `/fly` |  | `/fly [player]` | Toggle flight mode for yourself or another player | `smpplugin.staff.fly` `smpplugin.player.fly` |
| `/freeze` |  | `/freeze <player> \| /freeze reload` | Freeze or unfreeze a player for moderation | `smpplugin.staff.freeze` `smpplugin.admin.freeze` |
| `/gamemode` | `/gm`, `/gmc`, `/gms`, `/gma`, `/gmsp` | `/gamemode <mode> [player]` | Change your gamemode or another online player's gamemode | `smpplugin.staff.gamemode` |
| `/giveaway` | `/ga` | `/giveaway [join\|start [seconds]\|money <amount> [seconds] [winners]\|cancel]` | Join (or host) a giveaway | `smpplugin.giveaway.join` |
| `/glow` |  | `/glow` | Toggle a glowing outline | `smpplugin.glow` |
| `/god` |  | `/god [player]` | Toggle god mode for yourself or another player | `smpplugin.staff.god` |
| `/grindstone` |  | `/grindstone` | Open a grindstone | `smpplugin.workbench.grindstone` |
| `/guide` | `/tutorial`, `/starterguide` | `/guide [page\|topic]` | A short guide to getting started on the server | none |
| `/heal` |  | `/heal [player]` | Restore health for yourself or another player | `smpplugin.staff.heal` |
| `/help` |  | `/help` | View help | none |
| `/helpop` |  | `/helpop <message>` | Request staff assistance | `smpplugin.helpop` |
| `/hide` |  | `/hide <disguise\|status\|scramble\|remove\|list>` | Scramble your identity or disguise as a configured alias | `smpplugin.hide.scramble` `smpplugin.hide.disguise` `smpplugin.hide.admin` |
| `/highfive` |  | `/highfive <player>` | High-five a player | `smpplugin.fun.emotes` |
| `/invsee` | `/inventorysee` | `/invsee <player> \| /invsee reload` | View another player's inventory | `smpplugin.staff.invsee` `smpplugin.admin.invsee` |
| `/kick` |  | `/kick <player> [reason]` | Kick a player and record punishment history | `smpplugin.staff.punishments.kick` |
| `/kill` |  | `/kill` | Kill yourself after a confirmation menu (blocked in combat) | none |
| `/loom` |  | `/loom` | Open a loom | `smpplugin.workbench.loom` |
| `/lore` |  | `/lore <add\|set\|remove\|clear>` | Edit the lore of the item in your hand | `smpplugin.lore` |
| `/maintenance` |  | `/maintenance <on\|off\|status\|setlobby>` | Turn maintenance mode on or off | `smpplugin.admin.maintenance` |
| `/menu` | `/mainmenu` | `/menu` | Open the main menu | none |
| `/more` |  | `/more` | Fill the held stack | `smpplugin.admin.more` |
| `/mute` |  | `/mute <player> [reason]` | Mute a player and record punishment history | `smpplugin.staff.punishments.mute` |
| `/near` |  | `/near` | List players near you | `smpplugin.near` |
| `/night` |  | `/night [world]` | Set the time to night | `smpplugin.admin.time` |
| `/nightvision` | `/nv` | `/nightvision` | Toggle night vision | none |
| `/offend` |  | `/offend <player> <offense> [time] \| /offend <player> \| /offend list` | Punish a player with a preset offense from offenses.yml | `smpplugin.staff.punishments.offend` |
| `/online` | `/players` | `/online` | List the players online | `smpplugin.online` |
| `/performance` | `/perf` | `/performance` | TPS, memory, chunks and entities | `smpplugin.admin.performance` |
| `/perms` | `/permissions` | `/perms [roles\|role <name>\|explain <node>\|check <player> <node>\|player <player>]` | Permission and role tools | `smpplugin.admin.perms` |
| `/phantom` |  | `/phantom` | Toggle phantom spawning | none |
| `/ping` |  | `/ping [player]` | Check your ping or another online player's ping | none |
| `/playerunwipe` | `/punwipe` | `/playerunwipe <player> [confirm]` | Restore a wiped player from the backup their wipe left behind | none |
| `/playerwipe` | `/pwipe` | `/playerwipe <player> [confirm]` | Wipe everything this plugin stores about one player (backed up first) | none |
| `/playtime` | `/pt` | `/playtime [player]` | Check your playtime or another player's playtime | none |
| `/poke` |  | `/poke <player>` | Poke a player | `smpplugin.fun.emotes` |
| `/poll` |  | `/poll [vote <n>\|start <seconds> <question> \| <a> \| <b>\|end]` | Vote in (or start) a poll | `smpplugin.poll.vote` |
| `/profileviewer` | `/pv` | `/profileviewer <player>` | View a player's profile and homes | `smpplugin.staff.profileviewer` |
| `/ptime` | `/playertime` | `/ptime <day\|night\|...\|reset>` | Set your own time of day | `smpplugin.ptime` |
| `/punishments` | `/phistory` | `/punishments [player]` | View a player's punishment history, or everyone's with no name | `smpplugin.staff.punishments.view` |
| `/pweather` | `/playerweather` | `/pweather <clear\|rain\|reset>` | Set your own weather | `smpplugin.pweather` |
| `/rain` |  | `/rain [world]` | Make it rain | `smpplugin.admin.weather` |
| `/randomteleport` | `/randomtp` | `/randomteleport` | Teleport to a random online player | `smpplugin.staff.mode.randomtp` |
| `/rate` |  | `/rate <anything>` | Let the server rate anything out of 10 | `smpplugin.fun.games` |
| `/rename` |  | `/rename <name...\|reset>` | Rename the item in your hand | `smpplugin.staff.rename` |
| `/repair` | `/fix` | `/repair [all]` | Repair the item in your hand | `smpplugin.repair` |
| `/report` |  | `/report <player> <reason>` | Report a player to online staff | `smpplugin.report` |
| `/rps` | `/rockpaperscissors` | `/rps <rock\|paper\|scissors>` | Rock, paper, scissors against the server | `smpplugin.fun.games` |
| `/rules` |  | `/rules` | View server rules | none |
| `/safety` |  | `/safety [reload\|add <player>\|give <player>]` | Read the server safety book, or give it to a player | none |
| `/seehomes` |  | `/seehomes <player>` | View another player's homes | none |
| `/seen` | `/lastseen` | `/seen <player>` | See when a player was last online | `smpplugin.seen` |
| `/servers` |  | `/servers` | View network server status | `smpplugin.servers` |
| `/serverwipe` |  | `/serverwipe <preview\|status\|prepare\|confirm\|cancel>` | Prepare and run a season wipe (backs up worlds first) | `smpplugin.admin.serverwipe` |
| `/setdeaths` |  | `/setdeaths <player> <amount>` | Set a player's death count | `smpplugin.admin.setdeaths` |
| `/setkills` |  | `/setkills <player> <amount>` | Set a player's kill count | `smpplugin.admin.setkills` |
| `/setplaytime` |  | `/setplaytime <player> <seconds\|1d2h30m>` | Set a player's playtime | `smpplugin.admin.setplaytime` |
| `/settings` |  | `/settings` | Open player settings | none |
| `/sidebar` | `/sb` | `/sidebar [toggle] \| /sidebar lines\|add\|set\|insert\|remove\|title\|reload` | Show or hide your scoreboard, or (staff) edit its text | none |
| `/sit` |  | `/sit` | Sit down where you stand | `smpplugin.fun.sit` |
| `/skull` | `/playerhead` | `/skull [player]` | Get a player's head | `smpplugin.skull` |
| `/slap` |  | `/slap <player>` | Slap a player | `smpplugin.fun.emotes` |
| `/smite` | `/lightning` | `/smite <player> [-damage]` | Strike a player with lightning | `smpplugin.admin.smite` |
| `/smithing` | `/smithingtable` | `/smithing` | Open a smithing table | `smpplugin.workbench.smithing` |
| `/social` | `/media` | `/social` | Get social links | none |
| `/socialspy` | `/sspy` | `/socialspy` | See private messages between players | `smpplugin.staff.socialspy` |
| `/spawner` | `/spawners` | `/spawner [give\|info\|panel\|split\|reload\|remove]` | Manage Donut-style spawners | `smpplugin.admin.spawner` |
| `/spawnmob` | `/mob` | `/spawnmob <type> [amount] [player]` | Spawn mobs | `smpplugin.admin.spawnmob` |
| `/spawnstash` |  | `/spawnstash [spawn\|list\|remove\|reload] [type]` | Spawn and manage staff bait stashes | `smpplugin.staff.spawnstash` `smpplugin.admin.spawnstash` |
| `/speed` |  | `/speed <1-10> [walk\|fly] [player]` | Set walk or fly speed | `smpplugin.staff.speed` |
| `/spook` | `/boo` | `/spook <player\|all>` | Jump-scare a player | `smpplugin.admin.spook` |
| `/staffchat` | `/sc` | `/staffchat <message>` | Send a message to staff chat | `smpplugin.staff.chat.use` |
| `/stafflist` |  | `/stafflist` | Open the online staff list | `smpplugin.staff.mode.stafflist` |
| `/staffmode` | `/staff` | `/staffmode [player\|reload]` | Toggle the staff moderation hotbar | `smpplugin.staff.mode` `smpplugin.staff.mode.others` `smpplugin.admin.staffmode` |
| `/stats` |  | `/stats [player]` | View player stats | none |
| `/stonecutter` |  | `/stonecutter` | Open a stonecutter | `smpplugin.workbench.stonecutter` |
| `/store` |  | `/store` | Get store link | none |
| `/suicide` |  | `/suicide` | Respawn (asks to confirm) | `smpplugin.suicide` |
| `/sun` |  | `/sun [world]` | Clear the weather | `smpplugin.admin.weather` |
| `/teleport` | `/tp`, `/tphere`, `/tpall` | `/teleport <player\|here <player>\|all\|top\|x y z [world]>` | Staff teleport command suite | `smpplugin.staff.teleport` |
| `/tempban` |  | `/tempban <player> <time> [reason] (time: 30s, 15m, 2h, 5d, or 5d 15m 30s)` | Temporarily ban a player and record punishment history | `smpplugin.staff.punishments.tempban` |
| `/tempmute` |  | `/tempmute <player> <time> [reason] (time: 30s, 15m, 2h, 5d, or 5d 15m 30s)` | Temporarily mute a player and record punishment history | `smpplugin.staff.punishments.tempmute` |
| `/thunder` |  | `/thunder [world]` | Start a thunderstorm | `smpplugin.admin.weather` |
| `/twitter` |  | `/twitter` | Get Twitter/X link | none |
| `/unban` | `/pardon` | `/unban <player> [reason]` | Remove active ban records for a player | `smpplugin.staff.punishments.unban` |
| `/unblacklist` |  | `/unblacklist <player> [reason]` | Remove active blacklist records for a player | `smpplugin.staff.punishments.unblacklist` |
| `/unmute` |  | `/unmute <player> [reason]` | Remove active mute records for a player | `smpplugin.staff.punishments.unmute` |
| `/uptime` |  | `/uptime` | Server uptime and TPS | `smpplugin.uptime` |
| `/vanish` |  | `/vanish` | Toggle vanish while in staff mode | `smpplugin.staff.mode.vanish` |
| `/vcmute` |  | `/vcmute <player> [time] [reason]` | Mute a player in Simple Voice Chat, for a time or for good | `smpplugin.staff.punishments.vcmute` |
| `/vcunmute` |  | `/vcunmute <player> [reason]` | Lift a voice mute | `smpplugin.staff.punishments.vcunmute` |
| `/warn` |  | `/warn <player> [reason]` | Warn a player and record punishment history | `smpplugin.staff.punishments.warn` |
| `/wave` |  | `/wave` | Wave at everyone | `smpplugin.fun.emotes` |
| `/whois` | `/lookup` | `/whois <player>` | Look up a player | `smpplugin.staff.whois` |
