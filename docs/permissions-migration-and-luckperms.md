# Permissions: upgrading from 1.5 and LuckPerms examples

The full node list is in [permissions-reference.md](permissions-reference.md), generated from
`PermissionRegistry.java`.

## What changed from 1.5

### Defaults

| Change | Effect on an existing server | What to do |
|---|---|---|
| Staff and admin nodes now default to **nobody** instead of op | Ops still get them through `smpplugin.admin` (default op). Non-op staff are unaffected if you grant nodes with LuckPerms. | Nothing, unless you relied on bare `op` without `smpplugin.admin` being negated. |
| `smpplugin.admin` no longer grants **destructive actions** | Ops lose `/serverwipe`, `/smpplugin statswipe`, punishment-history deletion, `/setmoney`, `/setkills`, `/setdeaths`, `/setplaytime`, invsee editing and `/ecsee` editing. | Grant those nodes to the specific people who should have them. |
| `smpplugin.admin` no longer grants **security bypasses** | Ops are now subject to ignore lists, disabled PMs, chat mute/slow-mode/filter, helpop/report cooldowns, spawner anti-ESP, hide/disguise, drop prevention and bait-stash alerts like everyone else. | Grant individual bypass nodes where needed. |
| `smpplugin.staff.mode` only opens staff mode | In 1.5 it also granted every staff tool. | Where you granted `smpplugin.staff.mode`, also grant `smpplugin.legacy.staff-mode-bundle` to keep the 1.5 tool set, or grant tools individually. |
| Previously undeclared player nodes now default to **everyone** | `smpplugin.lobby`, `smpplugin.friends` and `smpplugin.auctionhouse.{use,sell,buy,cancel,claims,my,limit}` used to be op-only unless granted. | If you want any of them restricted, negate it for the default group: `lp group default permission set smpplugin.friends false`. |
| `/safety` reading defaults to **everyone** | `safety.use` was op-only unless granted. | Negate `smpplugin.safety.use` to restrict. |

### Renamed nodes

The old names are still declared and grant the new ones, so existing grants keep working.

| 1.5 node | 2.0 node |
|---|---|
| `anvilmod.admin` | `smpplugin.admin.anvilmod` |
| `safety.use` | `smpplugin.safety.use` |
| `safety.reload` | `smpplugin.admin.safety.reload` |
| `safety.add` | `smpplugin.admin.safety.give` |
| `donutfriends.admin` | `smpplugin.admin.friends` |
| `smpplugin.updatechecker` (and the old `isOp()` check) | `smpplugin.admin.updatenotify` |
| `donutauction.<action>` | `smpplugin.auctionhouse.<action>` |

### Punishments: one node per action

| Command | 2.0 node | 1.5 node that still grants it |
|---|---|---|
| `/ban` | `smpplugin.staff.punishments.ban` | same |
| `/tempban` | `smpplugin.staff.punishments.tempban` | `…ban`, `…create` |
| `/mute` | `smpplugin.staff.punishments.mute` | same |
| `/tempmute` | `smpplugin.staff.punishments.tempmute` | `…mute`, `…create` |
| `/warn` | `smpplugin.staff.punishments.warn` | `…create` |
| `/kick` | `smpplugin.staff.punishments.kick` | `…create` |
| `/blacklist` | `smpplugin.staff.punishments.blacklist` | same, `…create` |
| `/unban`, `/unmute`, `/unblacklist` | `…unban`, `…unmute`, `…unblacklist` | same, `…remove` |
| `/punishments` | `smpplugin.staff.punishments.view` | same |
| delete history records | `smpplugin.staff.punishments.delete` | same (no longer in `smpplugin.admin`) |

Granting `…ban` also grants `…tempban`, and `…mute` also grants `…tempmute`, exactly as in 1.5. To give
someone temporary bans only, grant `…tempban` alone.

## Wildcards

`smpplugin.*` or `*` in LuckPerms grants **every** node, including destructive actions and security
bypasses, regardless of what the `smpplugin.admin` bundle excludes. Use a wildcard only when you mean it.
Numbered nodes such as `smpplugin.homes.10` are matched exactly and are not granted by wildcards.

## LuckPerms examples

Run these in the console. Group names are examples.

### Member

Player features already default to everyone, so the default group needs nothing. Optional limits:

```
lp group default permission set smpplugin.homes.3 true
lp group default permission set smpplugin.auctionhouse.limit.5 true
lp group default permission set smpplugin.orders.limit.5 true
```

### VIP

```
lp creategroup vip
lp group vip parent add default
lp group vip permission set smpplugin.homes.10 true
lp group vip permission set smpplugin.auctionhouse.limit.15 true
lp group vip permission set smpplugin.orders.limit.15 true
lp group vip permission set smpplugin.auctionhouse.fastbuy true
lp group vip permission set smpplugin.auctionhouse.fastsell true
lp group vip permission set smpplugin.shards.everywhere true
lp group vip permission set smpplugin.donutplus true
```

Add `smpplugin.player.fly` only if your `FLY-SYSTEM` config allows player flight where you want it.

### Helper

```
lp creategroup helper
lp group helper parent add default
lp group helper permission set smpplugin.staff.mode true
lp group helper permission set smpplugin.staff.mode.vanish true
lp group helper permission set smpplugin.staff.mode.stafflist true
lp group helper permission set smpplugin.staff.chat.use true
lp group helper permission set smpplugin.staff.alerts.receive true
lp group helper permission set smpplugin.staff.freeze true
lp group helper permission set smpplugin.staff.freeze.alert true
lp group helper permission set smpplugin.staff.invsee true
lp group helper permission set smpplugin.staff.punishments.view true
lp group helper permission set smpplugin.staff.punishments.warn true
lp group helper permission set smpplugin.staff.punishments.kick true
lp group helper permission set smpplugin.staff.punishments.tempmute true
```

### Moderator

```
lp creategroup moderator
lp group moderator parent add helper
lp group moderator permission set smpplugin.staff.punishments.tempban true
lp group moderator permission set smpplugin.staff.punishments.mute true
lp group moderator permission set smpplugin.staff.punishments.unmute true
lp group moderator permission set smpplugin.staff.punishments.unban true
lp group moderator permission set smpplugin.staff.teleport true
lp group moderator permission set smpplugin.staff.alts true
lp group moderator permission set smpplugin.staff.profileviewer true
lp group moderator permission set smpplugin.staff.mode.randomtp true
lp group moderator permission set smpplugin.staff.mode.betterview true
lp group moderator permission set smpplugin.staff.mode.seevanished true
lp group moderator permission set smpplugin.staff.chat.mute true
lp group moderator permission set smpplugin.staff.chat.unmute true
lp group moderator permission set smpplugin.staff.chat.delay true
lp group moderator permission set smpplugin.staff.chat.clear true
lp group moderator permission set smpplugin.admin.ecsee true
```

### Administrator

```
lp creategroup admin
lp group admin parent add moderator
lp group admin permission set smpplugin.admin true
```

`smpplugin.admin` does not include destructive actions or security bypasses. Grant those to named
people, not to the whole group:

```
lp user <owner> permission set smpplugin.admin.serverwipe true
lp user <owner> permission set smpplugin.admin.statswipe true
lp user <owner> permission set smpplugin.admin.setmoney true
lp user <owner> permission set smpplugin.staff.punishments.delete true
```

### Security reviewer

Read-only access for someone auditing the server:

```
lp creategroup security-reviewer
lp group security-reviewer permission set smpplugin.admin.status true
lp group security-reviewer permission set smpplugin.admin.logs true
lp group security-reviewer permission set smpplugin.staff.alts true
lp group security-reviewer permission set smpplugin.staff.punishments.view true
```

The upgrade plan adds dedicated `smpplugin.security.*` nodes (alerts, evidence, management) together
with the anti-cheat module; they are not declared yet because that module does not exist yet.
