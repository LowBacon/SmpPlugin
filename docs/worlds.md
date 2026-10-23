# The lobby, spawn and FFA worlds (4.2)

SmpPlugin 4.2 manages three real Minecraft worlds, each with its own rules, set in
`plugins/SmpPlugin/worlds.yml`:

| | `lobby` | `spawn` | `ffa` |
|---|---|---|---|
| Sidebar scoreboard | off (no blank sidebar) | on | on |
| Players you see and in the tab list | lobby only | everyone online except the lobby | FFA only |
| Seen from other worlds | no (`HIDDEN-FROM-OTHER-WORLDS`) | yes | yes |
| PvP | off | off within the protected radius | on |
| Breaking and placing | off | off within the protected radius | off |
| Commands | only `ALLOWED-COMMANDS` (staff ranks: everything they normally have) | normal permissions | normal permissions |
| Inventory | survival inventory saved, lobby is empty-handed | kept | kept (`DEATH-INVENTORY` for deaths) |
| Protected area | whole world | 100 blocks around the spawn point | whole world |
| Terrain | empty void | empty void | empty void |
| Set its point with | `/setlobby` (only in this world) | `/setspawn` (only in this world) | `/ffaarena setpos` (only in this world) |
| Staff bypass | `COMMAND-BYPASS` (OP, Owner, CoOwner, Dev, Manager), `smpplugin.world.bypass.build`, `smpplugin.world.seeall` | same | same |

The server name on the scoreboard and tab list is still the CustomSMP branding from the existing
scoreboard and tab list settings; nothing about it changes.

## Setting up

1. Install the jar and start the server. The worlds `lobby`, `spawn` and `ffa` are created if they
   do not exist yet (`CREATE-IF-MISSING`). The console says `Creating the lobby world 'lobby' (VOID)`.
   They are **empty void**: no terrain at all, only a 5x5 stone platform at 0 64 0 to stand on.
   An existing world (for example one made with Multiverse) is loaded as it is.
2. Go to a world with `/smp world tp lobby` (or `spawn`, `ffa`). Like the Nether and the End, players
   cannot walk or fly there from the survival world; only these teleports, `/lobby`, `/spawn` and
   joining take them there.
3. Build it. Operators can build anyway (`smpplugin.world.bypass.build` and
   `smpplugin.bypass.lobby`, both in the op bundle); `/gamemode creative` helps.
4. Stand where players should arrive and set it **in that world**:
   - lobby: `/setlobby`
   - spawn: `/setspawn`
   - FFA: `/ffaarena setpos <arena>` for each arena, and `/smp world setspawn ffa` for the arrival point.
   Each of these refuses to work in another world and offers a clickable teleport to the right one.
5. Check `/smp world status` and `/smp world info lobby`.

From then on:

- **Joining** puts players in the lobby (lobby.yml `JOIN.ENABLED`), exactly like a lobby set with
  `/setlobby`. The `lobby` world *is* the lobby unless you set another place with `/setlobby`.
- **`/lobby survival`** and the lobby NPC take players back to where they were, with their
  inventory. **`/spawn`** goes to the `spawn` world unless a spawn was set with `/setspawn`.
- **FFA:** the `ffa` world is where your `/ffa` arenas belong; the world itself has PvP on and
  nothing to break. `/smp world tp <player> ffa` sends a player to its arrival point.

## Switching the lobby or the spawn world off

Both are features you can switch in game: `/smpplugin features` (the GUI) or

- `/smpplugin features disable lobby`: players join straight into the SMP. `/lobby` and `/setlobby`
  are off, the lobby world's rules stop, and anyone in the lobby (or offline with lobby items) is
  sent back to where they were with their inventory, XP and effects.
- `/smpplugin features disable spawn_world`: `/spawn` and `/setspawn` work anywhere again, as before
  4.2, and the spawn world's rules stop.

`enable` turns them back on. Nothing is deleted either way; the worlds and their builds stay.

## How it works

- **Worlds** load at start-up, before the spawn and lobby read their locations. A world is created
  only when it does not exist and `CREATE-IF-MISSING` is true. Nothing is ever regenerated, reset,
  deleted or unloaded. Up to 1.21.11 worlds are folders next to `world`; on 26.x Paper keeps them in
  `world/dimensions/minecraft/<name>`. Both are recognised.
- **Visibility** is decided by the world the *viewer* stands in (`VISIBILITY`): `SAME_WORLD` shows
  only players whose world has the same `VISIBILITY-GROUP`, `ALL_ONLINE` shows everyone. Bukkit's
  player hiding works per viewer, so both directions are set separately: a spawn player sees a
  lobby player while that lobby player does not see the spawn player. One central place
  (`WorldSettings.sees` and `WorldSystem.refreshVisibility`) decides it, through SmpPlugin's existing
  visibility manager with its own "world" reason, so vanish, duels, FFA matches and the "hide all
  players" setting keep hiding whoever they hide and are never undone by a world change. Hidden
  players also leave the tab list (that is how the server's own visibility works). It is updated
  when a player joins (during the join event, before the new player's tab entry is sent out, so
  nobody sees them flash in), changes world (only after the teleport succeeded), gets a LuckPerms
  change, and on a reload. Each update is one pass over the online players; there is no repeating
  task. Worlds that are not one of the three (world, world_nether...) use `OTHER-WORLDS-GROUP` and
  `OTHER-WORLDS-VISIBILITY` (`ALL_ONLINE`: they play like the spawn).
- **The sidebar** is switched off by giving the player the server's main scoreboard, which has no
  sidebar, and back on with the normal one as soon as they leave the lobby. One scoreboard per
  player, as before. If another scoreboard plugin owns the sidebar, it is left alone.
- **Who keeps their commands in the lobby** (`COMMAND-BYPASS`): operators (`OPS`), anyone with a
  permission in `PERMISSIONS` (default `smpplugin.world.bypass.commands`, which the
  `smpplugin.admin` bundle and the owner, co-owner, developer and manager roles include), and
  members of the LuckPerms groups in `GROUPS` (default owner, coowner, co-owner, dev, developer,
  manager). Group membership is checked as `group.<name>`, which LuckPerms gives only to real
  members, so a player named "OwnerSteve" or with an "Owner" nickname or prefix counts for nothing.
  The bypass only lifts the allowlist: a staff member still has only the commands their own
  permissions give them.
- **Commands** in the lobby: the typed label is looked up in the server's command list, so an alias
  (`/w` for `/msg`) counts as its command and `/smpplugin:home` cannot get round the list. Unknown
  commands are refused. Tab completion only offers allowed commands. Console and command blocks are
  not affected. This is not the only guard: pearls, portals and the like are stopped by the
  protection rules too.
- **Protection** stops only what a world turns off: building, containers, item frames, armour
  stands and vehicles, PvP, fall and other damage, hunger, item drops and pick-ups, natural mob
  spawning, portals and leaving by pearl, chorus fruit or spectating. Buttons, levers, doors,
  signs, villagers and NPC plugins keep working.
- **Leaving the lobby by any route** (`/lobby survival`, the NPC, `/spawn`, a staff teleport) gives
  back the survival inventory saved on the way in. Arriving in the lobby any other way than `/lobby`
  saves and clears it the same way (`SAVE-INVENTORY-ON-ENTRY`).
- **Void worlds:** Bukkit does not remember a plugin's world generator, so SmpPlugin passes its
  void generator every time it loads a VOID world. Chunks first visited after a restart stay empty.
  Changing GENERATOR later does not change chunks that were already generated.
- **Transitions** from `/smp world tp` check the destination first, refuse a second request while
  one is running, and report a failed teleport; the player is never moved somewhere that failed the
  check. Staff moves ignore combat on purpose; the transfer refuses players in combat otherwise.

## Join notices wait for the SMP

Greetings, reminders and reward-vault items that a player gets on joining (Halloween greeting and
calendar, daily reward, Advent, mail, friends) are not shown in the lobby. They wait, one of each
kind, and are played spaced out about a second apart when the player leaves the lobby for the SMP.
Anything a player is given while in the lobby anyway is handed back on top of their survival
inventory when they leave, never overwritten.

## Permission names

The world system uses SmpPlugin's existing naming:

| Purpose | Permission |
|---|---|
| Manage the worlds (`/smp world ...`) | `smpplugin.admin.world` |
| Keep every command in the lobby | `smpplugin.world.bypass.commands` (or `COMMAND-BYPASS` groups / OP) |
| Build and leave by pearl or portal in protected areas | `smpplugin.world.bypass.build` |
| See players of every world | `smpplugin.world.seeall` |

## Lobby inventories are now kept on disk

Before 4.2 the survival inventory saved when entering the lobby lived only in memory, and was
deleted when the player logged out in the lobby. Logging out in the lobby, or a restart, lost every
survival item. It is now written to `plugins/SmpPlugin/data/lobby-inventories/<uuid>.yml`, read back
off the main thread at login, kept across log-outs and restarts, and deleted only once it has been
given back. Giving it back replaces the inventory rather than adding to it, so it cannot duplicate
items.

## Upgrading an existing server

- On the first start three empty worlds are created (a second or two, almost no disk space). Set
  `WORLDS.<NAME>.ENABLED: false` or `ENABLED: false` before starting if you do not want them.
- If you never used `/setlobby`, players now join into the new `lobby` world, and the lobby's
  inventory handling applies to them. If you did use `/setlobby`, your lobby stays where it is.
- If you never used `/setspawn`, `/spawn` now leads to the `spawn` world.
- Players in different worlds no longer see each other. Set `VISIBILITY-BY-WORLD: false` to turn
  this off.
- The commands players may use in the lobby are limited to `ALLOWED-COMMANDS`; add any you want.

## In-game checklist

1. Join: you arrive in the lobby with no sidebar and an empty inventory.
2. A second player in the lobby is visible and in the tab list; a third player at spawn is not.
   The player at spawn has both lobby players in the tab list. A fourth player in FFA sees only
   FFA players; the spawn player sees them too.
3. In the lobby: `/home`, `/ah`, `/rtp` and `/smpplugin:home` are refused; `/msg` and `/w` work;
   tab completion only lists allowed commands; breaking, placing, chests and dropping items are
   refused; buttons and the NPC work; a portal does not take you out of the lobby.
4. `/lobby survival`: back where you were, with your items and the sidebar.
5. Log out in the lobby, restart, log in, `/lobby survival`: your items are back.
6. At spawn: within 100 blocks you cannot build or hit players; beyond it you can.
7. `/smp world tp <you> ffa`: you can fight another player there; the lobby still has no PvP.
8. A player in a LuckPerms group `owner` (or OP) can use every command they normally have in the
   lobby; a player whose name or nickname contains "Owner" without the group cannot.
9. A staff member with `smpplugin.world.seeall` sees players of every world; a vanished staff member
   stays hidden from everyone.
10. `/smp world reload` with a mistake in worlds.yml: it says what is wrong and changes nothing.

## Limitations

- **Tested so far:** the visibility matrix (lobby, spawn, FFA and the survival worlds in every
  direction), the bypass settings and validation as unit tests; world creation, loading after a restart (a block placed in the lobby was still
  there), `/smp world` and reload on fresh Paper 26.3 and 1.21.11 servers, plus unit tests of the
  rules, validation and command allowlist. **Not yet tested with real players:** visibility, the
  sidebar switch, the command allowlist in game, protection, inventory restore and transitions.
  Run the checklist above before going live.
- Visibility uses the server's player hiding, which covers the world and the tab list together.
  That is exactly what the three policies need, because players in different worlds are never in
  view of each other anyway: for a spawn player, lobby and FFA players appear only in the tab list.
  Showing someone in the tab list but not in the same world (or the other way round) would need
  packets, which SmpPlugin does not do here.
- The tab list shows at most what the client shows (about 80 names); with more players online the
  spawn still sees everyone in the world but not every name in the list.
- The lobby allowlist checks typed commands. A menu or NPC from another plugin that runs a command
  for the player through the server directly (not as typed text) is not checked; the protection
  rules (no portals, no pearls, no leaving by teleport) still apply. SmpPlugin's own menus do not
  offer gameplay commands in the lobby.
- Leaving FFA in combat is stopped by the existing combat rules (`/spawn`, `/lobby` and other
  commands are blocked while tagged), not by the world system.
- Folia is not supported (as for the rest of SmpPlugin).
- No world is unloaded or switched off at runtime. Turning a world off in worlds.yml stops its
  rules on `/smp world reload`; the world itself stays loaded until a restart.
- `ffa` world rules do not replace the `/ffa` arena system; kits, killstreaks and arena respawns
  stay with `/ffa` and win over the world's respawn point.
- With Multiverse-Core, let SmpPlugin or Multiverse own a world's settings, not both: SmpPlugin sets
  PvP, difficulty, time and weather on the three worlds at every start.
