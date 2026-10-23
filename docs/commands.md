# SmpPlugin commands

Generated from `plugin.yml` by `CommandReferenceTest`; do not edit by hand. 214 commands.

- **Usage:** `<value>` is required, `[value]` is optional, `a|b` means pick one.
- **Permission / default:** the node checked before the command runs and who has it out of the box (`everyone`, `op` or `nobody` until a rank is given it). Commands without a node are open to everyone; their staff-only parts check their own permissions (see `docs/permissions-reference.md`).
- **Feature:** the `/smpplugin features` switch that turns the command off. A switched-off command says so and does nothing, and `/help` leaves it out.
- `/help <category>` and `/help <command>` show the same information in game, filtered to what the reader may use.

## General (`/help general`)

Menus, help, rules and server links.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/menu` | `/mainmenu` | `/menu` | Open the main menu |  | everyone |  |
| `/discord` |  | `/discord` | Get Discord link |  | everyone | social |
| `/twitter` |  | `/twitter` | Get Twitter/X link |  | everyone | social |
| `/store` |  | `/store` | Get store link |  | everyone | social |
| `/social` | `/media` | `/social` | Get social links |  | everyone | social |
| `/rules` |  | `/rules` | View server rules |  | everyone | rules |
| `/safety` |  | `/safety [reload\|add <player>\|give <player>]` | Read the server safety book, or give it to a player |  | everyone | safety |
| `/help` |  | `/help [category\|command] [page]` | Help with every command you can use, by category |  | everyone | help |
| `/servers` |  | `/servers` | View network server status | `smpplugin.servers` | nobody | network_servers |
| `/perks` | `/ranks` `/vip` | `/perks [rank\|check <player>]` | See the ranks and what each one adds | `smpplugin.perks` | everyone | perks |
| `/guide` | `/tutorial` `/starterguide` | `/guide [page\|topic]` | A short guide to getting started on the server |  | everyone | guide |

## Travel and homes (`/help travel`)

Spawn, lobby, homes, warps, RTP and teleport requests.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/lobby` |  | `/lobby [survival\|border [radius]\|reload\|debug\|help]` | Go to the lobby, return to Survival, or manage the lobby |  | everyone | lobby |
| `/home` |  | `/home [name]` | Teleport to your home |  | everyone | homes |
| `/homes` |  | `/homes` | List your homes |  | everyone | homes |
| `/sethome` |  | `/sethome [name]` | Set your home |  | everyone | homes |
| `/delhome` |  | `/delhome <name>` | Delete a home |  | everyone | homes |
| `/renamehome` |  | `/renamehome <old> <new>` | Rename a home |  | everyone | homes |
| `/spawn` |  | `/spawn [player]` | Teleport to spawn (staff can send another player) |  | everyone | spawn |
| `/afk` |  | `/afk [zone]` | Go to an AFK zone and earn shards while you wait |  | everyone | afk |
| `/away` |  | `/away` | Mark yourself AFK, or come back | `smpplugin.afk.away` | everyone |  |
| `/rtp` |  | `/rtp [world]` | Random teleport |  | everyone | rtp |
| `/rtpq` | `/rtpqueue` | `/rtpq [join\|leave]` | Join the RTP queue to be teleported together with other players |  | everyone |  |
| `/tpa` |  | `/tpa <player>` | Request teleport to player |  | everyone | tpa |
| `/tpahere` |  | `/tpahere <player>` | Request player to teleport to you |  | everyone | tpa |
| `/tpaccept` |  | `/tpaccept [player]` | Accept a TPA request |  | everyone | tpa |
| `/tpadeny` |  | `/tpadeny [player]` | Deny a TPA request |  | everyone | tpa |
| `/tpacancel` |  | `/tpacancel` | Cancel your TPA requests |  | everyone | tpa |
| `/warp` |  | `/warp [name]` | Teleport to a warp |  | everyone | warps |
| `/back` |  | `/back` | Go back to where you were before your last teleport | `smpplugin.back` | everyone | utilities |

## Chat (`/help chat`)

Private messages, mail, ignoring and reporting.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/ignore` |  | `/ignore <player\|list>` | Add or remove a player from your ignore list | `smpplugin.ignore` | everyone | ignore |
| `/unignore` |  | `/unignore <player>` | Remove a player from your ignore list | `smpplugin.ignore` | everyone | ignore |
| `/msg` | `/message` `/tell` `/whisper` `/w` | `/msg <player> <message>` | Send a private message | `smpplugin.message` | everyone | messaging |
| `/reply` | `/r` | `/reply <message>` | Reply to your last private conversation | `smpplugin.message` | everyone | messaging |
| `/helpop` |  | `/helpop <message>` | Request staff assistance | `smpplugin.helpop` | everyone | staff_alerts |
| `/report` |  | `/report <player> <reason>` | Report a player to online staff | `smpplugin.report` | everyone | staff_alerts |
| `/mail` | `/inbox` | `/mail [read\|send <player> <message>\|clear]` | Send mail to any player, even offline | `smpplugin.mail` | everyone | mail |

## Teams and friends (`/help social`)

Teams, friends and emotes.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/team` |  | `/team <create\|disband\|invite\|kick\|join\|leave\|home\|sethome\|delhome\|chat\|info\|pvp>` | Team management |  | everyone | teams |
| `/friends` |  | `/friends [list\|following\|followers\|friends\|add\|remove\|search\|reload]` | Manage friends and follows |  | everyone | friends |
| `/friend` |  | `/friend [list\|following\|followers\|friends\|add\|remove\|search\|reload]` | Manage friends and follows |  | everyone | friends |
| `/slap` |  | `/slap <player>` | Slap a player | `smpplugin.fun.emotes` | everyone | fun |
| `/highfive` |  | `/highfive <player>` | High-five a player | `smpplugin.fun.emotes` | everyone | fun |
| `/poke` |  | `/poke <player>` | Poke a player | `smpplugin.fun.emotes` | everyone | fun |
| `/cheer` |  | `/cheer` | Cheer! | `smpplugin.fun.emotes` | everyone | fun |
| `/firework` | `/fw` | `/firework` | Launch a harmless firework | `smpplugin.fun.firework` | nobody | fun |
| `/sit` |  | `/sit` | Sit down where you stand | `smpplugin.fun.sit` | everyone | fun |

## Economy (`/help economy`)

Money, shards and payments.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/balance` | `/bal` `/money` | `/balance [player]` | Check balance |  | everyone |  |
| `/pay` |  | `/pay <player> <amount>` | Pay a player |  | everyone |  |
| `/baltop` | `/balancetop` `/moneytop` | `/baltop [page]` | See the richest players |  | everyone |  |
| `/transactions` | `/moneylog` | `/transactions [page]` | See your own balance history |  | everyone |  |
| `/shards` |  | `/shards [player] \| /shards everywhere <status\|debug> [player]` | Check shards |  | everyone | shards |
| `/shardpay` |  | `/shardpay <player> <amount>` | Pay shards to a player |  | everyone | shards |

## Shops and auctions (`/help market`)

Shop, selling, Auction House and Orders.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/shop` |  | `/shop [reload]` | Open the shop |  | everyone | shop |
| `/shardshop` |  | `/shardshop` | Open the shop that charges shards |  | everyone | shop, shards |
| `/orders` |  | `/orders [my\|collect\|reload]` | Open the Orders board |  | everyone | orders |
| `/ah` |  | `/ah [sell\|my\|claims\|cancel\|reload]` | Open the Auction House |  | everyone | auction_house |
| `/enderchest` | `/ec` | `/enderchest [reload]` | Open your custom Ender Chest |  | everyone | ender_chest |
| `/sell` |  | `/sell` | Open sell menu |  | everyone | sell |
| `/sellhand` |  | `/sellhand [amount]` | Sell item in hand |  | everyone | sell |
| `/sellall` |  | `/sellall` | Sell all sellable items |  | everyone | sell |
| `/sellhistory` |  | `/sellhistory` | View sell history |  | everyone | sell |
| `/sellmulti` |  | `/sellmulti [category]` | Open the sell multiplier menu |  | everyone |  |
| `/sellmultiplier` |  | `/sellmultiplier [category]` | Open the sell multiplier menu |  | everyone |  |
| `/sellprogress` |  | `/sellprogress [category]` | Open the sell multiplier progress menu |  | everyone |  |
| `/meta` | `/farmingmeta` | `/meta` | Show the item that is currently the farming meta |  | everyone |  |
| `/worth` | `/prices` | `/worth [hand\|reload]` | Open the worth browser or check held item worth |  | everyone | sell, worth |
| `/billford` |  | `/billford` | Open Billford trade |  | everyone | billford |

## Rewards and events (`/help rewards`)

Daily rewards, challenges and seasonal calendars.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/halloween` | `/spooky` `/spookycalendar` | `/halloween [edit\|give\|reset\|preview\|stats\|reload]` | Open the Spooky Calendar and open today's door | `smpplugin.halloween` | everyone | halloween |
| `/trickortreat` | `/tot` | `/trickortreat` | Knock for a Halloween treat (or a harmless trick) | `smpplugin.halloween.trickortreat` | everyone | halloween |
| `/advent` | `/adventcalendar` | `/advent [edit\|give\|reset\|test\|stats\|reload]` | Open the Advent Calendar and open today's door | `smpplugin.advent` | everyone | advent |
| `/events` | `/seasonalevents` `/smpevents` | `/events [status\|<advent\|halloween\|trickortreat> [on\|off\|auto]]` | See the seasonal events; staff switch them on, off or back to their dates | `smpplugin.events` | everyone |  |
| `/claimitems` | `/rewardvault` | `/claimitems` | Collect reward items that did not fit in your inventory | `smpplugin.claimitems` | everyone |  |
| `/daily` | `/dailyreward` | `/daily` | Claim your daily reward and keep your streak | `smpplugin.daily` | everyone | daily_rewards |
| `/challenges` | `/challenge` | `/challenges` | See today's challenges and your progress | `smpplugin.challenges` | everyone | challenges |

## Statistics (`/help stats`)

Stats, playtime, leaderboards and who is online.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/stats` |  | `/stats [player]` | View player stats |  | everyone | stats |
| `/ping` |  | `/ping [player]` | Check your ping or another online player's ping |  | everyone | stats |
| `/playtime` | `/pt` | `/playtime [player]` | Check your playtime or another player's playtime |  | everyone | stats |
| `/leaderboard` | `/lb` `/top` `/leaderboards` | `/leaderboard [type]` | View leaderboards |  | everyone | leaderboards |
| `/near` |  | `/near` | List players near you | `smpplugin.near` | nobody | utilities |
| `/seen` | `/lastseen` | `/seen <player>` | See when a player was last online | `smpplugin.seen` | everyone | utilities |
| `/online` | `/players` | `/online` | List the players online | `smpplugin.online` | everyone | utilities |
| `/uptime` |  | `/uptime` | Server uptime and TPS | `smpplugin.uptime` | everyone | utilities |

## PvP (`/help pvp`)

Duels, FFA and bounties.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/duel` |  | `/duel [player\|accept\|deny\|claims\|reload]` | Challenge or manage duels |  | everyone | duels |
| `/create` |  | `/create <invite\|friends> <player> [map]` | Create a custom duel and invite a player or friend |  | everyone | duels |
| `/queue` |  | `/queue [join\|leave]` | Open or join the duel queue |  | everyone | duels |
| `/leave` |  | `/leave` | Leave a duel or FFA queue/match |  | everyone |  |
| `/draw` |  | `/draw` | Request or accept a duel draw |  | everyone | duels |
| `/ffa` |  | `/ffa [join\|reload\|arena ...]` | Join the instanced FFA queue |  | everyone | ffa |
| `/ffastats` |  | `/ffastats [player]` | View FFA stats |  | everyone | ffa |
| `/bounty` |  | `/bounty <add\|set\|info\|list> [player] [amount]` | Manage bounties |  | everyone | bounty |

## Games (`/help games`)

Crates, CoinFlip, dice, roulette and giveaways.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/crate` |  | `/crate <create\|delete\|type\|open\|keys\|reload\|key\|take\|set\|keyall\|add\|edit\|remove\|bind\|unbind\|listbound\|info>` | Manage crates |  | everyone | crates |
| `/crates` |  | `/crates` | Open the crates menu |  | everyone | crates |
| `/keys` |  | `/keys` | Open your crate keys menu |  | everyone | crates |
| `/coinflip` | `/cf` | `/coinflip [create\|browse\|join\|cancel\|challenge\|accept\|deny\|rematch\|history\|stats\|top\|practice\|settings\|help]` | Open CoinFlip - 1v1 coin flips for in-game currency | `smpplugin.coinflip.use` | everyone | coinflip |
| `/giveaway` | `/ga` | `/giveaway [join\|start [seconds]\|money <amount> [seconds] [winners]\|cancel]` | Join (or host) a giveaway | `smpplugin.giveaway.join` | everyone | events |
| `/dice` | `/roll` | `/dice <bet> [high\|low\|1-6] \| /dice roll [sides] \| /dice odds` | Bet money on a dice roll, or roll one just for fun |  | everyone | gambling |
| `/roulette` | `/jackpot` `/headroulette` | `/roulette [join <amount>\|leave\|info]` | Put money in the head roulette; the head that stops in the middle wins the pot |  | everyone | gambling |
| `/rate` |  | `/rate <anything>` | Let the server rate anything out of 10 | `smpplugin.fun.games` | everyone | fun |

## Utility (`/help utility`)

Workbenches, repair and other handy tools.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/fly` |  | `/fly [player]` | Toggle flight mode for yourself or another player | `smpplugin.staff.fly` `smpplugin.player.fly` | nobody |  |
| `/kill` |  | `/kill` | Kill yourself after a confirmation menu (blocked in combat) |  | everyone |  |
| `/craft` | `/workbench` `/wb` | `/craft` | Open a crafting table | `smpplugin.workbench.craft` | nobody | utilities |
| `/anvil` |  | `/anvil` | Open an anvil | `smpplugin.workbench.anvil` | nobody | utilities |
| `/grindstone` |  | `/grindstone` | Open a grindstone | `smpplugin.workbench.grindstone` | nobody | utilities |
| `/stonecutter` |  | `/stonecutter` | Open a stonecutter | `smpplugin.workbench.stonecutter` | nobody | utilities |
| `/loom` |  | `/loom` | Open a loom | `smpplugin.workbench.loom` | nobody | utilities |
| `/smithing` | `/smithingtable` | `/smithing` | Open a smithing table | `smpplugin.workbench.smithing` | nobody | utilities |
| `/cartography` | `/cartographytable` | `/cartography` | Open a cartography table | `smpplugin.workbench.cartography` | nobody | utilities |
| `/coords` | `/coordinates` `/pos` | `/coords [share]` | Show (or share) your coordinates | `smpplugin.coords` | everyone | utilities |
| `/suicide` |  | `/suicide` | Respawn (asks to confirm) | `smpplugin.suicide` | everyone | utilities |
| `/ptime` | `/playertime` | `/ptime <day\|night\|...\|reset>` | Set your own time of day | `smpplugin.ptime` | nobody | utilities |
| `/pweather` | `/playerweather` | `/pweather <clear\|rain\|reset>` | Set your own weather | `smpplugin.pweather` | nobody | utilities |
| `/repair` | `/fix` | `/repair [all]` | Repair the item in your hand | `smpplugin.repair` | nobody | utilities |
| `/skull` | `/playerhead` | `/skull [player]` | Get a player's head | `smpplugin.skull` | nobody | utilities |
| `/lore` |  | `/lore <add\|set\|remove\|clear>` | Edit the lore of the item in your hand | `smpplugin.lore` | nobody | utilities |
| `/glow` |  | `/glow` | Toggle a glowing outline | `smpplugin.glow` | nobody | utilities |
| `/calc` | `/calculate` `/math` | `/calc <sum>` | Do a sum | `smpplugin.calc` | everyone | utilities |

## Settings (`/help settings`)

Your settings and toggles.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/pm` | `/togglepm` `/privatemessages` | `/pm` | Toggle private messages | `smpplugin.message.toggle` | everyone | messaging |
| `/tpauto` |  | `/tpauto` | Toggle TPA auto-accept |  | everyone | tpa, tpa_auto |
| `/tpahereauto` |  | `/tpahereauto` | Toggle TPA Here auto-accept |  | everyone | tpa, tpa_auto |
| `/nightvision` | `/nv` | `/nightvision` | Toggle night vision |  | everyone | night_vision |
| `/phantom` |  | `/phantom` | Toggle phantom spawning |  | everyone | phantom |
| `/settings` |  | `/settings` | Open player settings |  | everyone | settings |
| `/sidebar` | `/sb` | `/sidebar [toggle] \| /sidebar lines\|add\|set\|insert\|remove\|title\|reload` | Show or hide your scoreboard, or (staff) edit its text |  | everyone | scoreboard |

## Staff (`/help staff`)

Moderation and punishments.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/chat` |  | `/chat <help\|mute\|unmute\|delay\|clear>` | Manage global chat settings | `smpplugin.staff.chat.use` `smpplugin.staff.chat.mute` `smpplugin.staff.chat.unmute` `smpplugin.staff.chat.delay` `smpplugin.staff.chat.clear` | nobody | chat |
| `/ecsee` |  | `/ecsee <player>` | View another player's Ender Chest | `smpplugin.admin.ecsee` | nobody | ender_chest |
| `/logs` |  | `/logs <player>` | View a player's activity log | `smpplugin.admin.logs` | nobody |  |
| `/freeze` |  | `/freeze <player> \| /freeze reload` | Freeze or unfreeze a player for moderation | `smpplugin.staff.freeze` `smpplugin.admin.freeze` | nobody | freeze |
| `/heal` |  | `/heal [player]` | Restore health for yourself or another player | `smpplugin.staff.heal` | nobody |  |
| `/feed` |  | `/feed [player]` | Restore hunger for yourself or another player | `smpplugin.staff.feed` | nobody |  |
| `/god` |  | `/god [player]` | Toggle god mode for yourself or another player | `smpplugin.staff.god` | nobody |  |
| `/gamemode` | `/gm` `/gmc` `/gms` `/gma` `/gmsp` | `/gamemode <mode> [player]` | Change your gamemode or another online player's gamemode | `smpplugin.staff.gamemode` | nobody | gamemode |
| `/staffmode` | `/staff` | `/staffmode [player\|reload]` | Toggle the staff moderation hotbar | `smpplugin.staff.mode` `smpplugin.staff.mode.others` `smpplugin.admin.staffmode` | nobody | staff_mode |
| `/stafflist` |  | `/stafflist` | Open the online staff list | `smpplugin.staff.mode.stafflist` | nobody | staff_mode |
| `/staffchat` | `/sc` | `/staffchat <message>` | Send a message to staff chat | `smpplugin.staff.chat.use` | nobody | staff_chat |
| `/rename` |  | `/rename <name...\|reset>` | Rename the item in your hand | `smpplugin.staff.rename` | nobody |  |
| `/randomteleport` | `/randomtp` | `/randomteleport` | Teleport to a random online player | `smpplugin.staff.mode.randomtp` | nobody |  |
| `/teleport` | `/tp` `/tphere` `/tpall` | `/teleport <player\|here <player>\|all\|top\|x y z [world]>` | Staff teleport command suite | `smpplugin.staff.teleport` | nobody |  |
| `/alts` |  | `/alts <player>` | View alternate accounts by shared IP history | `smpplugin.staff.alts` | nobody |  |
| `/vanish` |  | `/vanish` | Toggle vanish while in staff mode | `smpplugin.staff.mode.vanish` | nobody | staff_mode |
| `/invsee` | `/inventorysee` | `/invsee <player> \| /invsee reload` | View another player's inventory | `smpplugin.staff.invsee` `smpplugin.admin.invsee` | nobody | invsee |
| `/profileviewer` | `/pv` | `/profileviewer <player>` | View a player's profile and homes | `smpplugin.staff.profileviewer` | nobody | profile_viewer |
| `/chatlog` |  | `/chatlog [player]` | Browse logged public and team chat | `smpplugin.admin.chatlog` | nobody |  |
| `/seehomes` |  | `/seehomes <player>` | View another player's homes | `smpplugin.staff.seehomes` `smpplugin.staff.profileviewer` | nobody |  |
| `/punishments` | `/phistory` | `/punishments [player]` | View a player's punishment history, or everyone's with no name | `smpplugin.staff.punishments.view` | nobody | punishments |
| `/ban` |  | `/ban <player> [reason]` | Ban a player and record punishment history | `smpplugin.staff.punishments.ban` | nobody | punishments |
| `/tempban` |  | `/tempban <player> <time> [reason] (time: 30s, 15m, 2h, 5d, or 5d 15m 30s)` | Temporarily ban a player and record punishment history | `smpplugin.staff.punishments.tempban` | nobody | punishments |
| `/mute` |  | `/mute <player> [reason]` | Mute a player and record punishment history | `smpplugin.staff.punishments.mute` | nobody | punishments |
| `/tempmute` |  | `/tempmute <player> <time> [reason] (time: 30s, 15m, 2h, 5d, or 5d 15m 30s)` | Temporarily mute a player and record punishment history | `smpplugin.staff.punishments.tempmute` | nobody | punishments |
| `/warn` |  | `/warn <player> [reason]` | Warn a player and record punishment history | `smpplugin.staff.punishments.warn` | nobody | punishments |
| `/kick` |  | `/kick <player> [reason]` | Kick a player and record punishment history | `smpplugin.staff.punishments.kick` | nobody | punishments |
| `/blacklist` |  | `/blacklist <player> [reason]` | Blacklist a player and record punishment history | `smpplugin.staff.punishments.blacklist` | nobody | punishments |
| `/unban` | `/pardon` | `/unban <player> [reason]` | Remove active ban records for a player | `smpplugin.staff.punishments.unban` | nobody | punishments |
| `/unmute` |  | `/unmute <player> [reason]` | Remove active mute records for a player | `smpplugin.staff.punishments.unmute` | nobody | punishments |
| `/unblacklist` |  | `/unblacklist <player> [reason]` | Remove active blacklist records for a player | `smpplugin.staff.punishments.unblacklist` | nobody | punishments |
| `/vcmute` |  | `/vcmute <player> [time] [reason]` | Mute a player in Simple Voice Chat, for a time or for good | `smpplugin.staff.punishments.vcmute` | nobody |  |
| `/vcunmute` |  | `/vcunmute <player> [reason]` | Lift a voice mute | `smpplugin.staff.punishments.vcunmute` | nobody |  |
| `/offend` |  | `/offend <player> <offense> [time] \| /offend <player> \| /offend list` | Punish a player with a preset offense from offenses.yml | `smpplugin.staff.punishments.offend` | nobody |  |
| `/spawnstash` |  | `/spawnstash [spawn\|list\|remove\|reload] [type]` | Spawn and manage staff bait stashes | `smpplugin.staff.spawnstash` `smpplugin.admin.spawnstash` | nobody | spawn_stash |
| `/fakeplayer` |  | `/fakeplayer` | Spawn a packet-based fake player (requires ProtocolLib) | `smpplugin.staff.fakeplayer` | nobody | staff_mode |
| `/hide` |  | `/hide <disguise\|status\|scramble\|remove\|list>` | Scramble your identity or disguise as a configured alias | `smpplugin.hide.scramble` `smpplugin.hide.disguise` `smpplugin.hide.admin` | nobody | hide |
| `/disguise` |  | `/disguise <alias>` | Disguise as a configured alias | `smpplugin.hide.disguise` `smpplugin.hide.admin` | nobody | hide |
| `/countdown` |  | `/countdown <seconds> [what is starting]` | Show a countdown to everyone | `smpplugin.staff.countdown` | nobody | events |
| `/speed` |  | `/speed <1-10> [walk\|fly] [player]` | Set walk or fly speed | `smpplugin.staff.speed` | nobody | staff_extras |
| `/whois` | `/lookup` | `/whois <player>` | Look up a player | `smpplugin.staff.whois` | nobody | staff_extras |
| `/socialspy` | `/sspy` | `/socialspy` | See private messages between players | `smpplugin.staff.socialspy` | nobody | staff_extras |
| `/commandspy` | `/cspy` | `/commandspy` | See the commands players run | `smpplugin.staff.commandspy` | nobody | staff_extras |

## Administration (`/help admin`)

Server setup and player data.

| Command | Aliases | Usage | Description | Permission | Default | Feature |
|---|---|---|---|---|---|---|
| `/setspawn` |  | `/setspawn` | Set the primary server spawn at your current location | `smpplugin.admin.setspawn` | nobody |  |
| `/setlobby` |  | `/setlobby` | Set the protected lobby location at your current location | `smpplugin.admin.lobby.setlobby` | nobody | lobby |
| `/afkzone` | `/afkzones` | `/afkzone <pos1\|pos2\|create\|setregion\|setspawn\|reward\|rename\|delete\|list\|show\|reload>` | Build and edit AFK zones | `smpplugin.admin.afkzone` | nobody |  |
| `/arena` | `/duelarena` | `/arena <create\|delete\|setpos1\|setpos2\|setreturn\|setdisplay\|enable\|disable\|queue\|list\|reload>` | Manage duel arenas | `smpplugin.admin.duels` | nobody | duels |
| `/ffaarena` |  | `/ffaarena <create\|delete\|setpos\|setdisplay\|settings\|enable\|disable\|list\|reload>` | Manage FFA arenas | `smpplugin.admin.ffa` | nobody | ffa |
| `/topsell` | `/sellstats` | `/topsell [gui\|items\|volume\|sellers\|export\|reset]` | View and export sell statistics | `smpplugin.admin.sellstats` | nobody |  |
| `/eco` | `/smpeco` | `/eco <give\|take\|set\|reset\|history\|stats> ...` | Staff economy tools (give, take, set, reset, history, stats) | `smpplugin.admin.addmoney` `smpplugin.admin.removemoney` `smpplugin.admin.setmoney` `smpplugin.admin.economy.history` `smpplugin.admin.economy.stats` | nobody |  |
| `/addmoney` |  | `/addmoney <player> <amount>` | Add money to a player | `smpplugin.admin.addmoney` | nobody |  |
| `/removemoney` |  | `/removemoney <player> <amount>` | Remove money from a player | `smpplugin.admin.removemoney` | nobody |  |
| `/setmoney` |  | `/setmoney <player> <amount>` | Set a player's money balance | `smpplugin.admin.setmoney` | nobody |  |
| `/addplaytime` |  | `/addplaytime <player> <seconds\|1d2h30m>` | Add playtime to a player | `smpplugin.admin.addplaytime` | nobody |  |
| `/addkills` |  | `/addkills <player> <amount>` | Add kills to a player | `smpplugin.admin.addkills` | nobody |  |
| `/adddeaths` |  | `/adddeaths <player> <amount>` | Add deaths to a player | `smpplugin.admin.adddeaths` | nobody |  |
| `/setkills` |  | `/setkills <player> <amount>` | Set a player's kill count | `smpplugin.admin.setkills` | nobody |  |
| `/setdeaths` |  | `/setdeaths <player> <amount>` | Set a player's death count | `smpplugin.admin.setdeaths` | nobody |  |
| `/setplaytime` |  | `/setplaytime <player> <seconds\|1d2h30m>` | Set a player's playtime | `smpplugin.admin.setplaytime` | nobody |  |
| `/addshards` |  | `/addshards <player> <amount>` | Add shards to a player | `smpplugin.admin.shards` | nobody | shards |
| `/removeshards` |  | `/removeshards <player> <amount>` | Remove shards from a player | `smpplugin.admin.shards` | nobody | shards |
| `/setshards` |  | `/setshards <player> <amount>` | Set a player's shard balance | `smpplugin.admin.shards` | nobody | shards |
| `/warpmanager` |  | `/warpmanager <create\|delete\|list> [name]` | Manage warp points | `smpplugin.admin.warpmanager` `smpplugin.admin.setwarp` `smpplugin.admin.delwarp` | nobody | warps |
| `/setwarp` |  | `/setwarp <name>` | Set a warp point | `smpplugin.admin.warpmanager` `smpplugin.admin.setwarp` | nobody | warps |
| `/delwarp` |  | `/delwarp <name>` | Delete a warp point | `smpplugin.admin.warpmanager` `smpplugin.admin.delwarp` | nobody | warps |
| `/portalmanager` |  | `/portalmanager <list\|info\|create\|delete\|setcuboid\|setdestination\|setdisplay\|toggle\|setpriority\|sethologramhere>` | Manage RTP portal triggers | `smpplugin.admin.portalmanager` | nobody | portals |
| `/spawner` | `/spawners` | `/spawner [give\|info\|panel\|split\|reload\|remove]` | Manage Donut-style spawners | `smpplugin.admin.spawner` | nobody | spawners |
| `/clearlag` |  | `/clearlag` | Manually clear lag entities | `smpplugin.admin.clearlag` | nobody | clear_lag |
| `/cuboid` |  | `/cuboid <wand\|create\|delete\|list\|bind <cuboid> <spawn\|shard\|rtp-zone> <true\|false>\|reload>` | Manage cuboid regions | `smpplugin.admin.cuboid` | nobody | cuboids |
| `/amethysttool` |  | `/amethysttool give <player> <type> [duration] \| /amethysttool reload` | Manage amethyst tools | `smpplugin.admin.amethysttool` | nobody | amethyst_tools |
| `/smpplugin` | `/smp` `/csmp` `/customsmp` | `/smp <status\|reload\|statswipe\|optimize\|setup\|features\|maintenance\|world>` | SmpPlugin administration | `smpplugin.admin.status` `smpplugin.admin.reload` `smpplugin.admin.statswipe` `smpplugin.admin.optimize` `smpplugin.admin.setup` `smpplugin.admin.features` `smpplugin.admin.maintenance` `smpplugin.admin.world` | nobody |  |
| `/maintenance` |  | `/maintenance <on\|off\|status\|setlobby>` | Turn maintenance mode on or off | `smpplugin.admin.maintenance` | nobody |  |
| `/playerwipe` | `/pwipe` | `/playerwipe <player> [confirm]` | Wipe everything this plugin stores about one player (backed up first) | `smpplugin.admin.playerwipe` | nobody |  |
| `/playerunwipe` | `/punwipe` | `/playerunwipe <player> [confirm]` | Restore a wiped player from the backup their wipe left behind | `smpplugin.admin.playerunwipe` | nobody |  |
| `/serverwipe` |  | `/serverwipe <preview\|status\|prepare\|confirm\|cancel>` | Prepare and run a season wipe (backs up worlds first) | `smpplugin.admin.serverwipe` | nobody |  |
| `/amod` |  | `/amod <add\|reload> [word]` | Manage anvil moderation banned words | `smpplugin.admin.anvilmod` | nobody |  |
| `/coinflipadmin` | `/cfadmin` | `/coinflipadmin <status\|inspect\|audit\|cancel\|recover\|reconcile\|resolve\|stats\|resetstats\|disable\|enable\|reload\|refresh\|debug>` | CoinFlip staff tools - status, inspect, audit, cancel, recover, reconcile, resolve | `smpplugin.coinflip.inspect` | nobody |  |
| `/announcer` |  | `/announcer [list\|send <n>\|toggle]` | Manage the automatic announcements | `smpplugin.admin.announcer` | nobody | announcer |
| `/border` | `/spawnborder` | `/border [spawn\|lobby] [info\|set <size>\|center\|warning <blocks> [seconds]\|damage <amount> [buffer]\|mode <player\|world>\|on\|off]` | Put a real Minecraft world border around the spawn or the lobby | `smpplugin.admin.border` | nobody | borders |
| `/perms` | `/permissions` | `/perms [roles\|role <name>\|explain <node>\|check <player> <node>\|player <player>]` | Permission and role tools | `smpplugin.admin.perms` | nobody | staff_extras |
| `/broadcast` | `/bc` `/announce` | `/broadcast [-title] <message>` | Send a server-wide announcement | `smpplugin.admin.broadcast` | nobody | staff_extras |
| `/day` |  | `/day [world]` | Set the time to day | `smpplugin.admin.time` | nobody | staff_extras |
| `/night` |  | `/night [world]` | Set the time to night | `smpplugin.admin.time` | nobody | staff_extras |
| `/sun` |  | `/sun [world]` | Clear the weather | `smpplugin.admin.weather` | nobody | staff_extras |
| `/rain` |  | `/rain [world]` | Make it rain | `smpplugin.admin.weather` | nobody | staff_extras |
| `/thunder` |  | `/thunder [world]` | Start a thunderstorm | `smpplugin.admin.weather` | nobody | staff_extras |
| `/smite` | `/lightning` | `/smite <player> [-damage]` | Strike a player with lightning | `smpplugin.admin.smite` | nobody | staff_extras |
| `/butcher` | `/killmobs` | `/butcher [radius] [all]` | Remove nearby hostile mobs | `smpplugin.admin.butcher` | nobody | staff_extras |
| `/spawnmob` | `/mob` | `/spawnmob <type> [amount] [player]` | Spawn mobs | `smpplugin.admin.spawnmob` | nobody | staff_extras |
| `/more` |  | `/more` | Fill the held stack | `smpplugin.admin.more` | nobody | staff_extras |
| `/performance` | `/perf` | `/performance` | TPS, memory, chunks and entities | `smpplugin.admin.performance` | nobody | staff_extras |
| `/spook` | `/boo` | `/spook <player\|all>` | Jump-scare a player | `smpplugin.admin.spook` | nobody | staff_extras |

