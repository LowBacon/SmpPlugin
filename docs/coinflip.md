# FinnishSMP CoinFlip Nexus

A 1v1 coin flip for the server's **in-game currency** (the same balance as `/pay`, `/balance` and Vault). Two
players stake the same amount on opposite sides, the server flips a fair coin, and the winner receives both
stakes minus the fee shown before they confirm. No real money, crypto, gift cards or external sites are
involved. Wagers cannot be bought, and there are no paid odds or boosts.

**Status: 2.0.0-alpha.3, not yet run on a live server.** The money paths are covered by integration tests
against a real SQLite database (see [Test results](#test-results)). The menus, animation and commands compile
against Paper 26.3 and Spigot 26.3 but have not been clicked through on a server. Follow the
[manual test plan](#manual-test-plan) on a test server before opening it to players.

## Quick start

1. Put the jar for your server type in `plugins/` (`-paper.jar` for Paper/Purpur, `-spigot.jar` for
   Spigot/CraftBukkit) and start the server. `plugins/SmpPlugin/coinflip.yml` and `coinflip-messages.yml` are
   created. The CoinFlip tables are added to the database SmpPlugin already uses.
2. Set the stake range and fee in `coinflip.yml` (defaults: 100 to 1,000,000 coins, no fee) and run
   `/coinflipadmin reload`.
3. Players type `/coinflip` (or `/cf`). Staff check health with `/coinflipadmin status`.
4. Turn it off any time with `/smpplugin features` (COINFLIP), or pause only wagering with
   `/coinflipadmin disable`. Practice mode keeps working.

## For players

- **Create**: pick a stake (buttons, a typed amount like `2.5k`, recent or saved stakes) and heads, tails or
  random. The review screen shows stake, pot, fee, payout and expiry before anything is reserved. Large stakes
  need a second click.
- **Browse**: open matches with sorting (newest, oldest, stake, expiring), filters (joinable, low/medium/high
  stakes, friends, mine), search by name or match ID, and paging. Low stakes are below 10× the minimum; high
  stakes start at a tenth of the maximum.
- **Challenge**: send a private challenge to an online player. They get a clickable message, or can type
  `/cf accept <id>` / `/cf deny <id>`. It expires on its own and the stake is returned. Players who turned
  invitations off, or who ignore you (`/ignore`), cannot be challenged. After a match, `[REMATCH]` offers the
  same stake again; it needs a fresh review and the opponent's acceptance.
- **The flip**: when someone joins, both players see the reveal (accelerating, then slowing coin, rising tick
  sound, then the result). The result was drawn and stored before the animation started. Closing the menu,
  disconnecting or a reload changes nothing about the result or the payout. A player who is using a chest or
  another screen is not interrupted; the result arrives in chat.
- **History, statistics, leaderboards, achievements**: wins, losses, total staked, gross payouts (what you
  received) and net result (received minus staked), streaks, favourite side, weekly and monthly totals.
  Cancelled and expired matches never count as wins or losses. Achievements are cosmetic; none rewards losing
  or larger stakes.
- **Settings**: animation (Fast, Subtle, Classic, Cinematic, Reduced motion, Off), sounds, particles, readable
  menus, challenge invitations, announcing your big wins, public profile (hides you from leaderboards and
  others' views when off), quick confirm, preferred side and stake, and **take a break**: pause your own
  wagering for 1 hour, 1 day or 7 days. A break cannot be shortened.
- **Practice**: the same coin and animation with no currency. Only a practice counter is kept.

## Commands

| Command | Permission | |
|---|---|---|
| `/coinflip`, `/cf` | `smpplugin.coinflip.use` | Main menu |
| `/cf create <amount> [heads\|tails\|random]` | `smpplugin.coinflip.create` | Opens the review screen |
| `/cf browse` | `smpplugin.coinflip.use` | Match browser |
| `/cf join <id>` | `smpplugin.coinflip.join` | Review and join (quick confirm joins directly) |
| `/cf cancel <id>` | `smpplugin.coinflip.use` | Cancel your waiting match; the stake is returned |
| `/cf challenge <player> <amount> [side]` | `smpplugin.coinflip.challenge` | Review and send a challenge |
| `/cf accept <id>` / `/cf deny <id>` | `.join` / `.use` | Answer a challenge |
| `/cf rematch` | `smpplugin.coinflip.challenge` | Challenge your last opponent again (5 minutes) |
| `/cf history [player]`, `/cf stats [player]` | `smpplugin.coinflip.use` | Others only if their profile is public |
| `/cf top [category]` | `smpplugin.coinflip.use` | wins, played, win-rate, gross-payout, net-profit, largest-payout, best-streak, weekly-/monthly-played, weekly-/monthly-net |
| `/cf practice`, `/cf settings`, `/cf help` | `.practice` / `.use` | |
| `/coinflipadmin status` | `smpplugin.coinflip.inspect` | Wagering state, storage health, queue, timing, counts, pending claims, unknown transactions, config problems |
| `/coinflipadmin inspect <id>` | `smpplugin.coinflip.inspect` | Stored and in-memory state, outcome, journal, audit trail |
| `/coinflipadmin audit [id]` | `smpplugin.coinflip.inspect` | Recent audit log, optionally for one match |
| `/coinflipadmin reconcile` | `smpplugin.coinflip.inspect` | Ledger check of recent matches and every unknown transaction |
| `/coinflipadmin stats <player>` | `smpplugin.coinflip.inspect` | Anyone's statistics, including private profiles |
| `/coinflipadmin debug` | `smpplugin.coinflip.inspect` | Operation counters |
| `/coinflipadmin cancel <id> <reason>` | `smpplugin.coinflip.admin.cancel` | Refund a waiting match (audited) |
| `/coinflipadmin recover` | `smpplugin.coinflip.admin.recover` | Retry stuck payouts; reconcile held matches |
| `/coinflipadmin resolve <tx> <applied\|not-applied> <reason>` | `smpplugin.coinflip.admin.resolve` | Decide an unknown transaction (audited) |
| `/coinflipadmin resetstats <player> <reason>` | `smpplugin.coinflip.admin.resetstats` | Reset statistics (audited) |
| `/coinflipadmin disable` / `enable` | `smpplugin.coinflip.admin.toggle` | Pause or resume wagering for everyone |
| `/coinflipadmin reload` / `refresh` | `smpplugin.coinflip.admin.reload` | Reload the YAML files / refresh leaderboards |

Player nodes default to everyone. `alerts` and `inspect` are staff nodes; `admin.cancel`, `.recover`, `.reload`
and `.toggle` are in `smpplugin.admin`. **`admin.resolve` and `admin.resetstats` are never bundled** (they can
create claims or erase data) and must be granted explicitly. Every menu action re-checks the same permission
as its command.

## Configuration

`coinflip.yml` documents every key in place. Highlights:

| Section | Keys |
|---|---|
| `ECONOMY` | `MIN-STAKE`, `MAX-STAKE` (hard ceiling one trillion), `ALLOW-DECIMALS`, `FEE-PERCENT` (0-50, default 0), `ALLOW-FULL-BALANCE` (off), `HIGH-STAKE-CONFIRM`, `INCREMENTS` |
| `MATCHES` | `EXPIRY-SECONDS`, `CHALLENGE-EXPIRY-SECONDS`, `MAX-ACTIVE-PER-PLAYER`, `MAX-ACTIVE-TOTAL`, `MAX-PUBLIC-LISTED`, `ALLOW-PRIVATE`, `ALLOW-REMATCH` |
| `LIMITS` | `COOLDOWN-SECONDS`, `DAILY-WAGER-CAP`, `DAILY-NET-LOSS-CAP`, `DAILY-MATCH-CAP` (0 = none), `MIN-PLAYTIME-MINUTES`, `SESSION-REMINDER-MINUTES`, `SELF-EXCLUSION-HOURS` |
| `SECURITY` | click, command and refresh cooldowns; `MAX-PENDING-STORAGE-OPERATIONS` (restart) |
| `ANIMATION` | default preset, particles, sounds, `MAX-CONCURRENT`, sound effects |
| `BROADCAST`, `WEBHOOK` | big-win chat announcement (on) and Discord webhook (off) |
| `LEADERBOARDS`, `ACHIEVEMENTS` | refresh interval, size, win-rate minimum; achievement notices and optional reward commands (off) |
| `STORAGE` | `HISTORY-RETENTION-DAYS` (0 = keep), `QUERY-TIMEOUT-SECONDS` (restart) |
| `GUI` | title prefix, readable default, materials, main-menu slots |

**Validation.** Every problem is logged with key, expected value, found value and the action taken. An invalid
stake limit or fee **pauses wagering** until fixed; it is never replaced by a guess. Duplicate or out-of-range
menu slots send the layout back to the defaults. Unknown materials fall back to defaults. A YAML syntax error
makes `/coinflipadmin reload` keep the running settings. **Restart required:** `MAX-PENDING-STORAGE-OPERATIONS`
and `QUERY-TIMEOUT-SECONDS`. Everything else applies on reload, without touching open matches.

All messages are in `coinflip-messages.yml`. Keep them neutral: explain results and limits, never urge players
to keep playing or win losses back.

## How currency is protected

### Match states

```
CREATING ──▶ WAITING ──▶ JOINING ──▶ LOCKED ──▶ RESOLVING ──▶ SETTLING ──▶ COMPLETED
   │            │           │                                    │
   ▼            ▼           ▼                                    ▼
CANCELLED   CANCELLING   WAITING (join failed, nothing taken)   LOCKED (retry)
                │
                ▼
         CANCELLED / EXPIRED

any unfinished state ──▶ RECOVERY_REQUIRED ──▶ COMPLETED / CANCELLED (after reconciliation)
```

Legal transitions are listed in one place (`MatchState`); every change is a compare-and-set, in memory and as a
`WHERE state = ?` update in the database. A join, a cancel and an expiry racing for the same match: exactly one
wins (tested).

### Every money movement

```
1. INTENT    journal row written (stake, payout or refund)          ─ database
2. CHANGE    the online player's balance changes                    ─ server memory (the economy's own)
3. APPLIED   new balance + journal row + match state, one transaction ─ database
```

- If step 1 fails, nothing happened. If the player cannot pay at step 2, the intent is voided.
- If step 3 is **rolled back**, a stake is given back in memory; a payout or refund is retried with a fresh
  balance (it may already have been spent, so it cannot always be taken back), then held for staff.
- If the **commit's outcome is unknown**, the match becomes `RECOVERY_REQUIRED` and staff are alerted. Nothing
  guesses.
- A player who is **offline** when owed money gets a `PENDING_CLAIM`. It is paid with the same three steps the
  next time they are online. CoinFlip never edits an offline player's stored balance directly.

### Why there are no duplicate payouts or lost stakes

- **One row per (match, player, kind)** is enforced by a unique constraint. A second payout, refund or stake
  for the same match is rejected by the database, not just by code.
- **One outcome per match.** The result is drawn only after both stakes are taken, and stored with
  `WHERE outcome IS NULL`. Disconnects, restarts, animation problems and payout retries never re-draw it.
- **No payout without both stakes recorded** as taken, checked when settling and when reconciling.
- **Completion, statistics, weekly and monthly totals, achievements and the fee record** are written in the
  transaction that pays out. Statistics therefore change exactly once per match, and recovery cannot count a
  match twice.
- **Startup recovery** runs before wagering opens. Unfinished intents become `UNKNOWN` and their match is held.
  Waiting matches are refunded as claims. Locked matches are paid out as claims with their stored result. It
  only touches the database and is idempotent: running it twice changes nothing (tested).
- **Nothing is deleted to hide a problem.** History retention only removes finished matches whose transactions
  are all applied or void.

### Fee

`fee = floor(pot × FEE-PERCENT / 100)` in cents, so the stated rate is never exceeded. The fee is removed from
circulation (a currency sink) and recorded as a `FEE` row. Paying fees into an account is not implemented.

## Troubleshooting (staff)

A player says their stake disappeared, or `/coinflipadmin status` shows a held match or unknown transactions:

1. `/coinflipadmin inspect <id>`: compare the stored state with memory and read the journal. Green APPLIED rows
   happened; yellow PENDING_CLAIM rows are owed and paid on next login; red UNKNOWN/INTENT rows need a decision.
2. Check the player's balance history with `/logs <player>`. Every CoinFlip balance change writes a
   `COINFLIP_STAKE` or `COINFLIP_CREDIT` entry at the moment the balance changes. That entry is the evidence
   for whether an unknown transaction happened.
3. Decide it: `/coinflipadmin resolve <tx> applied <reason>` if the log shows the change, otherwise
   `not-applied` (a stake is then void; a payout or refund becomes a claim).
4. The match is reconciled automatically from the journal. If both stakes were taken and a result exists, the
   winner is paid as a claim; otherwise every stake taken is refunded. Everything is audited.
5. `/coinflipadmin recover` retries payouts that are stuck in `LOCKED` (for example after a database outage)
   and reconciles held matches whose transactions are all decided.
6. `/coinflipadmin reconcile` checks recent matches: completed ones must have stakes = pot and credits = payout;
   cancelled ones must refund exactly what was taken.

## Storage

Tables in SmpPlugin's database (SQLite, MySQL, or the SQLite cache in MongoDB mode): `coinflip_matches`,
`coinflip_transactions` (the journal), `coinflip_stats`, `coinflip_periods` (weekly/monthly), `coinflip_achievements`,
`coinflip_preferences`, `coinflip_audit`, `coinflip_meta` (schema version 1). All statements are prepared and have
query timeouts. Queries are bounded and indexed. CoinFlip uses its own connection and **one storage thread**
with a bounded queue: a full queue refuses new work with "busy" instead of piling up. The server thread never
waits for the database. Back up the database file (SQLite) or schema (MySQL) as you already do; CoinFlip needs no
separate backup. In MongoDB mode CoinFlip's tables stay in the local SQLite cache file; back that file up too.

## Performance and monitoring

- Menus read in-memory state or caches. Leaderboards are refreshed every few minutes in one storage task, never
  per click. History and statistics load one page at a time, off the server thread, with a loading state.
- One repeating task drives every animation; one runs expiry every second (at most 20 matches per run); one
  refreshes leaderboards; one applies history retention hourly. No task is created per match.
- Open menus are redrawn in place, at most once per tick, only when matches change.
- `/coinflipadmin status` shows queue length, average storage time, slow operations (over 250 ms) and failures.
  `/coinflipadmin debug` shows counters per operation (attempts, successes, failures). `DEBUG: true` logs each
  money step (no balances or secrets).
- No performance figures are claimed: nothing has been measured on a live server yet.

## Limitations

- The server economy keeps online balances in memory and saves them on its own schedule (autosave, quit).
  CoinFlip writes the balance together with each journal row, so a CoinFlip change is never lost. A crash can
  still lose *other* recent balance changes (not CoinFlip's) that the economy had not saved yet. This is
  existing economy behaviour, listed as finding A2 in `docs/UPGRADE-26.3-STATUS.md`.
- A crash in the milliseconds between a balance change and its commit leaves an `UNKNOWN` transaction for staff
  to decide with `/logs`, by design.
- Single server only. A network with Redis does not share CoinFlip matches between servers.
- Not implemented (off and documented, not half-built): best-of-three/five series, tournaments, live
  spectating, seasons and seasonal leaderboards, commit/reveal fairness proofs, custom model data /
  resource-pack coin icons, paying the fee into an account, history filters by date range or opponent,
  configurable log levels beyond `DEBUG`, PlaceholderAPI placeholders for CoinFlip, Folia.
- No screenshot of the original GUI was provided with the specification. The layout follows the written art
  direction (midnight and purple frame, emerald/crimson/gold/cyan accents).

## Feature checklist

Status against the specification's 340 numbered requirements, grouped. ✅ implemented, 🟡 partial, ❌ not
implemented (with reason).

| Group | Status |
|---|---|
| A. Core match and wager (1-40) | ✅ all: public and private matches, heads/tails/random, increments, custom entry, min/max, balance checks, expiry, cancel, join, lock, state machine, IDs, history, live count, paging, search, filters, sorting, visibility, my active and completed matches, quick-match (saved stake/side + quick confirm), cooldown, active-match limit, rematch with fresh acceptance, accept/decline/timeout/cancel challenge, duplicate and self-challenge prevention, equal stakes, eligibility, owner checks, listing cap |
| B. GUI and UX (41-80) | ✅ all 12 menus plus review, join, confirm and flip screens, borders, configurable icons/names, dynamic lore, balance, stake/pot/net/fee displays, side indicator, disabled-button reasons, empty/loading/error/permission states, paging, search, refresh cooldown, stale-menu protection, session ownership, slot validation (main menu), material fallback, readable mode, number formatting, title prefix, safe and cancellable custom amount. ❌ 75 resource-pack models (not implemented) |
| C. Animation (81-110) | ✅ coin sequence, alternating faces, accelerate/decelerate, result highlight, titles, neutral result text, configurable sounds, particles, silent (sounds off), reduced motion, Fast/Subtle/Classic/Cinematic presets, payout reveal, state transitions, broadcast, cancellation and cleanup, disconnect/reload/disable safety, outcome independent of animation, preset timing tests. 🟡 94 low-particle mode is particles on/off with a small fixed count |
| D. Economy and transactions (111-150) | ✅ existing economy, exact cents, precision and bounds, stake reservation, pot, fee (zero by default), payout, IDs and idempotency keys, durable journal, duplicate payout/refund prevention, retry, unknown handling, startup recovery, offline claims, provider-unavailable handling, alerts, reconciliation, inspection, fee accounting, audit trail, consistency checks, rounding, overflow guards, no payout without both stakes, nothing deleted, cancel/expiry refunds, admin audit, reason codes, durable result, player feedback. ❌ 135 fee destination (sink only) |
| E. Security (151-180) | ✅ click, command, creation, join and challenge rate limits, replay protection (CAS + unique keys), slot-bound actions (forged items cannot do anything), forged/stale ID rejection, server-side balance/stake/permission/state validation, race protection, duplicate settlement and stats protection, active-match lock, config validation, material fallback, webhook timeout and rate limit, no secrets in logs, admin audit, mandatory reasons, restricted recovery and reload, safe storage failure, global pause. 🟡 171 a broken message falls back to its key text |
| F. Randomness (181-200) | ✅ SecureRandom, 50/50, one result per match stored before the reveal, never re-drawn, injectable source for tests, outcome ID and audit row, `/coinflipadmin inspect` shows it, duplicate-result guard (`outcome IS NULL`), no stake/rank/streak/staff influence (the source sees none of them), rules shown, sanity-only statistical test. ❌ 193 commit/reveal (not implemented) |
| G. Statistics (201-230) | ✅ all listed statistics, paginated history, result filter, audited reset, exactly-once updates, cancelled/refunded exclusion, privacy. 🟡 224 filters cover result type, not date range or opponent |
| H. Leaderboards and achievements (231-255) | ✅ every listed leaderboard except seasonal, minimum sample, cache with refresh, deterministic ties, 17 achievements with progress and history, duplicate-reward prevention, optional reward commands (off), no loss-chasing achievements. ❌ 241 seasonal (seasons not implemented) |
| I. Social (256-270) | ✅ clickable and typed challenge messages, start/complete notices, private invites, rematch, sounds, per-player notification settings, friends filter (SmpPlugin friends), `/ignore` respected, Discord webhook (off), staff alerts, broadcast cooldown, `/cf stats <player>`, match IDs in messages, no balances or transaction details in public messages |
| J. Administration (271-290) | ✅ status, inspect, cancel with reason, recover, reconcile, metrics (`debug`), config validation on reload, cache refresh, audit filter by match, active/held/pending/unknown overviews, pause, safe reload, permission docs, debug logging, counters, shutdown cleanup. 🟡 288 log level is `DEBUG` on/off only |
| K. Performance (291-310) | ✅ event-driven redraws, bounded caches (heads LRU), bounded queue, indexed prepared queries, no server-thread or per-tick DB access, paged history, cached leaderboards, bounded animation work, executor and task shutdown, menu cleanup, query timeouts, slow-operation counter, retention, schema versioning. 🟡 305 connection timeout comes from the shared database settings; 308 storage time is averaged, not split per operation |
| L. Testing and delivery (311-340) | ✅ payout, fee, invalid stake, insufficient balance, creation, join, concurrent join and cancel race, cancellation, expiry, disconnect (offline winner and creator), restart recovery and idempotency, duplicate settlement, duplicate payout at DB level, economy failure, storage write failure, config validation, permission registry, RNG sanity, practice isolation, partial recovery with staff resolution, leaderboard ordering, history paging, rate limits, artefact inspection, this guide. ❌ 328 GUI paging and 329 animation cancellation are not unit-tested (need a server); 332 schema migration only has version 1 |

Optional modes (§13): standard 1v1 ✅, quick match ✅, private challenge ✅, practice ✅, friend browser ✅
(filter). Best-of-N, tournament, spectator and seasonal ❌ (not implemented; nothing half-built is reachable).

## Test results

`mvn clean package` (Paper API) and `mvn clean package -Pspigot`, 12 October 2026: **BUILD SUCCESS**,
184 tests, 0 failures (Paper skips 8 older item tests that need a running server). CoinFlip tests:
`CoinFlipServiceTest` (12 end-to-end money flows on SQLite), `CoinFlipModelTest` (11), `CoinFlipStorageTest` (3).

## Manual test plan

On a Paper or Purpur 26.3 test server, with two accounts (A and B) and an op:

1. `/cf` opens the menu; the balance matches `/balance`. Every main-menu button opens its screen.
2. A creates a 100-coin match (heads). The balance drops by 100 at once. `/coinflipadmin inspect <id>` shows
   STAKE APPLIED.
3. B joins from the browser. Both see the reveal. The winner's balance rises by 200 (with no fee); the loser
   keeps -100. Both get chat messages and titles. The match is in history with the right result.
4. Repeat with B closing the reveal menu at once, and again with B disconnecting during the reveal. The payout
   still happens (a claim if the winner left), paid on their next login.
5. A creates a match and cancels it from My matches: the full stake returns. Create another and let it expire
   (`EXPIRY-SECONDS: 30` for the test): refunded automatically.
6. A challenges B: B gets clickable [REVIEW]/[DECLINE]. Decline returns A's stake. Turn invitations off for B
   and confirm A can no longer challenge B.
7. Spam-click Create, Join and Confirm: only one action happens each time.
8. Set `FEE-PERCENT: 75` and reload: wagering pauses and the problem is listed. Set it back.
9. `/coinflipadmin disable`: creating and joining are refused, practice still works. `enable` restores.
10. Kill the server (not a clean stop) while a match is waiting and while one is revealing. After the restart,
    `/coinflipadmin status` shows the recovery. The waiting stake is refunded as a claim, the revealing match is
    paid out with its stored result, and nothing is paid twice (`/coinflipadmin reconcile`).
11. Set a daily cap and confirm it refuses further stakes with a neutral message. Take a 1-hour break in Settings
    and confirm wagering is refused but practice works.
