# Troubleshooting

Start with **`/smp status`** (what started, which plugins were found) and **`/smp diagnostics`**
(load, database, caches, slow commands, anything that failed to start). Both work from the console.

## The scoreboard does not show

- Is another scoreboard plugin installed? The console says
  `TAB is installed, so SmpPlugin's sidebar is off`. Set `SCOREBOARD.OVERRIDE-OTHER-PLUGINS: true`
  in `scoreboard.yml` to use SmpPlugin's anyway (and `TABLIST.OVERRIDE-OTHER-PLUGINS` in
  `config.yml` for the tab list).
- Is the module on? `/smp modules` → SCOREBOARD. And `SCOREBOARD.ENABLED: true`.
- Did the player hide it? `/sidebar` toggles their own sidebar.
- In the lobby, the lobby's own scoreboard from `lobby.yml` is shown.

## A line on the scoreboard is empty or a placeholder shows as code

- With `HIDE-UNRESOLVED-PLACEHOLDERS: true` (the default) a placeholder nothing can fill is left out.
  Set it to `false`, `/smp reload scoreboard`, and the line shows the placeholder as written; check
  its spelling against [placeholders.md](placeholders.md).
- Other plugins' placeholders (`%luckperms_prefix%`) need PlaceholderAPI and that plugin's expansion.
  SmpPlugin's own (`%smp_...%`, `%economy_...%`) work without PlaceholderAPI.

## The server name is wrong

`BRANDING.SERVER-NAME` in `config.yml`, then `/smp reload branding`. A scoreboard title or tab list
text you wrote yourself is not changed; put `{server_title}` (scoreboard) or `{server_name}` there.

## An event does not start

- `/event info <event>` shows its status, dates and mode.
- `disabled in events.yml`: set `ENABLED: true`.
- `off (switched off by staff)`: `/event auto <event>` puts it back on its dates.
- The dates use `TIME-ZONE` in `events.yml` (empty = the server's time zone). The manager checks
  once a minute.
- Halloween has its own dates in `halloween.yml` and is switched with the HALLOWEEN module.
- `/advent` says the calendar is closed: the ADVENT event is not running, or `advent.yml` has
  `ENABLED: false`. Test outside December with `/event enable advent` and `/advent preview 2026-12-05`.

## Ranked: "No duel arena is free for ranked matches"

Ranked matches are played on duel arenas that allow queue matches. Set one up with `/arena`
(spawn points, region and queue enabled), and make sure it is not in use by another duel.
`/ranked join` also refuses players who are already in a duel, a duel queue or FFA.

## "That would take the balance past the highest balance allowed"

The receiving balance would pass `ECONOMY.MAX-BALANCE` (90 trillion by default, the most a balance
can hold exactly). Raise or clear the setting only below that limit.

## `/pay` refuses an amount

Amounts are digits with an optional decimal point and `k`, `m`, `b`, `t` or `q`: `250`, `1.5k`,
`2m`, `$19.99`. Exponents (`1e5`), `NaN`, `Infinity` and amounts under one cent are refused. Payments
can also be limited by `ECONOMY.PAY` (smallest, largest, cooldown).

## `/smp reload` says "Reload cancelled: YAML errors"

A file you edited is not valid YAML. The message names the file and the line; the old settings stay
active until it is fixed. Quote text that starts with `&`, `%`, `{` or `*`.

## The console warns "UNTESTED PLATFORM"

SmpPlugin names the Minecraft versions each build was checked on. Others still start; the warning
asks you to back up and watch `/smp status`. It is not an error.

## The database is slow or locked

`/smp diagnostics` shows how long a test query takes and how long it waited behind other writes.
Many writes waiting means the disk is slow or a large job (a wipe, a big import) is running. SQLite
files must not be shared between servers; use MySQL for a network.

## The ledger table is large

Rows older than `ECONOMY.LEDGER.RETENTION-DAYS` are deleted on start-up. Passive shards are not
recorded unless `ECONOMY.LEDGER.RECORD-PASSIVE-SHARDS: true`. `ECONOMY.LEDGER.ENABLED: false` stops
recording.

## Something failed to start

`/smp diagnostics` lists it under "Failed to start" and the console has the full error, logged once
at start-up. The rest of the plugin keeps running. Please report it with that error and the output
of `/smp version`.
