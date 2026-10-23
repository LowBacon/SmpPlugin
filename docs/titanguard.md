# Anti-cheat: TitanGuard

Since 4.2 SmpPlugin has no anti-cheat of its own. The anti-cheat is **TitanGuard**, a separate plugin
by the same author. Install both and they work as one:

| What | How |
|---|---|
| Alerts | TitanGuard alerts staff in game (`/tg alerts`). Every alert is also written to the player's SmpPlugin logs (category `AntiCheat`, with the check, violation level, confidence, reason and evidence id). |
| Bans | TitanGuard's ban ladder runs SmpPlugin's `/tempban` and `/ban`, so anti-cheat bans show in `/history`, on the ban screen and across the network like any other punishment. |
| Permissions | SmpPlugin's staff roles grant TitanGuard's permissions: `smpplugin.security.alerts` → `titanguard.alerts`, `smpplugin.security.inspect` → `titanguard.evidence`, `smpplugin.security.manage` → `titanguard.admin`. The `smpplugin.bypass.anticheat.*` nodes grant the matching TitanGuard bypasses; no role grants a bypass. |
| Status | `/smpplugin status` shows whether TitanGuard is installed and linked. |
| Amethyst tools | The blocks a drill, shovel or chopper breaks around the mined one are announced as their own break events, so protection plugins can refuse them, and TitanGuard does not count them as nuker or reach. |

The link is optional on both sides: SmpPlugin runs without TitanGuard (with no anti-cheat), TitanGuard
runs without SmpPlugin, and neither needs the other to build. The alert link needs TitanGuard 1.2.0
or newer.

## Upgrading from the built-in anti-cheat

- `/anticheat` is gone; use `/tg` (`/tg alerts`, `/tg evidence <player>`, `/tg status`, `/tg review`).
- `anticheat.yml` is no longer read. The file in `plugins/SmpPlugin/` can be deleted.
- Players keep their permissions: the `smpplugin.security.*` nodes now grant TitanGuard's.
