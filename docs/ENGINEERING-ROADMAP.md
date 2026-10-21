# SmpPlugin engineering roadmap

The working log for the 4.0 reliability pass. Each milestone records what changed, the commands that were run
and their real results. Read this first when resuming work.

## How to build and test in this environment

The build sandbox reaches Maven Central and GitHub, but **not** `repo.papermc.io`, `hub.spigotmc.org` or the
plugin repositories (CodeMC, JitPack, dmulloy2, extendedclip, lunarclient, fancyinnovations, maxhenkel). The
compile-time APIs are therefore built from their public sources first:

```bash
tools/local-api-bootstrap.sh                       # once per machine; installs the APIs into ~/.m2
mvn -s tools/central-only-settings.xml clean package
```

`tools/local-api-bootstrap.sh` lists the exact source tags and commits. The jars it builds are local
verification builds and must never be shipped. A machine that can reach the official repositories builds with
plain `mvn clean package`.

**Spigot profile:** `mvn -Pspigot package` cannot be verified here. The Spigot API is published only on
`hub.spigotmc.org` and its source lives on SpigotMC's own Git server, both unreachable from this sandbox.

## Baseline (untouched 3.1.0, commit b0f0682)

| Command | Result |
|---|---|
| `mvn -s tools/central-only-settings.xml clean package` (Paper API 1.21.11) | BUILD SUCCESS. 276 tests, 0 failures, 0 errors, 8 skipped (ItemStack tests need a running server on the Paper API). 32 compiler deprecation warnings. Jar `SmpPlugin-3.1.0-paper.jar`. |
| `mvn -Pspigot package` | Not run: Spigot API unreachable (see above). |
| Live server | Not available in this sandbox. Nothing below has been run on a Minecraft server. |

## Audit: prioritized defects

Severity: **P0** data loss, money creation, duplication, security. **P1** broken features, wrong transaction
logic, unsafe concurrency. **P2** main-thread blocking, scalability. **P3** incomplete features, inconsistent
config, docs. **P4** polish.

| # | Sev | Defect | Where | Status |
|---|---|---|---|---|
| E1 | P0 | Online players' balance changes live only in memory until the 5-minute autosave or quit; a crash loses every payment, sale, purchase and reward since the last save. | `EconomyManager.saveLoadedAccount`, `tasks/AutoSaveTask` | fixed (M1) |
| E2 | P0 | Offline balance changes load a copy of the row, change it and save it asynchronously with a full-row `REPLACE`: two changes before the first save lands lose one of them (lost update). | `EconomyManager.loadAccount/saveLoadedAccount` | fixed (M1) |
| E3 | P0 | Gambling escrow can mint money on a crash: the bet leaves the balance only in memory (saved later), while the hold is written to `data/gambling-escrow.yml` at once and refunded at the next start. A crash in between refunds a bet whose debit was never saved. A settled win whose hold removal was not yet written is refunded too. | `gambling/GamblingEscrow`, `gambling/Wallet` | fixed (M2) |
| E4 | P0 | Crate money rewards bypass the economy service (`PlayerData.addMoney`): no rounding, no log, no limit, saved only at autosave. | `CrateManager.grantMoneyReward` | fixed (M1) |
| E5 | P0 | CoinFlip writes an absolute balance on its own database connection; it can land after a newer save of the same player and overwrite it. | `coinflip/storage/CoinFlipDao.writeBalance` | fixed (M1) |
| E6 | P1 | A database that fails to start is only logged; the plugin keeps running every feature (including Vault payments) against no storage. | `DatabaseManager.initialize`, `SmpPlugin.onEnable` | fixed (M1) |
| E7 | P1 | Money is a `double` rounded with `Math.round(x * 100) / 100`, with no upper balance limit: above ~3.5×10^13 a stored balance cannot hold every cent and above ~9.2×10^16 the rounding overflows. `/pay 1Q` style inputs parse to 10^18. | `EconomyManager`, `NumberUtils.parse` | fixed (M1) |
| E8 | P1 | Vault withdrawals and deposits by other plugins are logged as `AUCTION_PURCHASE` / `SELL_PAYOUT`. | `hooks/VaultEconomyHook` | fixed (M1) |
| E9 | P1 | The full-row `REPLACE INTO players` resets any column it does not list back to its default. | `DatabaseManager.savePlayer` | fixed (M1) |
| E10 | P1 | Shared-connection proxy releases transaction ownership on `commit()`/`rollback()` while auto-commit is still off, so another thread's statements can join the next transaction. | `DatabaseManager.ThreadSafeConnectionHandler` | fixed (M1) |
| E11 | P1 | A login preloads the player row asynchronously; an offline balance change between that read and the join is overwritten by the stale preloaded copy. | `JoinPreloadListener`, `PlayerDataManager.loadOrCreate` | fixed (M1) |
| C1 | P1 | Daily rewards are handed out when the claim animation ends; a crash during the spin keeps the claim and loses the prize. The animation decides delivery. | `engagement/DailyRewards` | fixed (M6) |
| F1 | P3 | No `/baltop`, no `/eco`, no transaction history, no pay limits or tax, no economy statistics. | commands | fixed (M1, M3) |
| P1 | P2 | `LeaderboardManager` reloads every player row synchronously on the calling (main) thread when its 30 s cache expires or an offline balance changes; placeholders and /baltop then stall the tick on big databases. | `LeaderboardManager.getSortedPlayers` | fixed (M7) |
| D1 | P3 | "MongoDB" mode is a local SQLite file copied to MongoDB on autosave; docs call it MongoDB storage. | `DatabaseManager` MongoDB bridge, docs | fixed (M1 config text, M8 docs) |
| D2 | P3 | `SkinsRestorer` is used but missing from `softdepend`, so it can load after SmpPlugin. | `plugin.yml` | fixed (M7) |
| D3 | P3 | Compiled `target/` output (1,087 files) is tracked in git; `.gitignore` has no `target/` entry. | repository | fixed (M0) |
| D4 | P3 | Docs disagree with the code (command count, line count, config file count, package counts, "2.x" wording, mixed SmpPlugin/CustomSmp/FinnishSMP branding). | `README.md`, `Features.md`, `docs/*` | fixed (M8); the server name `CustomSmp` in default configs and the `%customsmp_…%` placeholder are kept, since renaming them would break existing servers |
| S1 | P0 | A failed read of 3.0 player data still marked the player loaded with empty data: every calendar door could be opened again, and the next write overwrote what the database held. | `core/PlayerStore.loadBlocking` | fixed (M5) |
| S2 | P0 | A 3.0 player-data write was a DELETE and an INSERT without a transaction; a crash between them lost the value (a calendar claim mask, so every door again). | `core/PlayerStore.writeBlocking` | fixed (M5) |
| S3 | P1 | `/halloween preview <date>` moved the date for every player, with real prizes. | `halloween/HalloweenManager` | fixed (M5, per-staff test mode that gives nothing) |
| S4 | P1 | Calendar prizes were handed out when the spin ended; a crash mid-spin kept the claim and lost the prize. | `halloween/CalendarMenu` | fixed (M5) |
| S5 | P1 | Reward items that did not fit an inventory were dropped on the ground (lost, or picked up by others). | `core/Reward.give`, `engagement/PendingItems`, `Giveaways` | fixed (M5, reward vault) |
| S6 | P1 | Trick or Treat consumed the cooldown, then decided the outcome 30 ticks later; leaving in between lost the treat. Treat weights were never checked. | `halloween/TrickOrTreatCommand` | fixed (M6) |
| S7 | P2 | A database that failed to start could log its connection string with the user name and password. | `DatabaseManager.initialize` | fixed (M7) |
| S8 | P3 | The crate editor and the feature toggle menu checked the admin permission only when opened. | `menus/CrateEditorMenu`, `menus/FeatureToggleMenu` | fixed (M7) |
| S9 | P3 | Calendar config mistakes (bad time zone, unused door or streak keys, a live PREVIEW-DATE) were ignored silently. | `seasonal/SeasonCalendar.validate` | fixed (M7) |

## Milestones

### M0: repository hygiene (commit 0d9670d)

- `target/` untracked and ignored; `tools/local-api-bootstrap.sh` and `tools/central-only-settings.xml` added.

### M1: economy durability and exact money

**Design.** `EconomyManager` is the only place that changes balances. Amounts are whole cents (`economy/Money`)
with HALF_UP rounding, overflow checks and a configurable `ECONOMY.MAX-BALANCE` (default 10 trillion, hard cap
2^45 units, the largest amount a `REAL` column stores with exact cents). Every change writes a row to the new
`economy_ledger` table in the same transaction as the new balance. `players.money_version` grows with every
change; a balance is only written when its version is newer, so the database writer, CoinFlip's own connection,
autosaves and quit saves can arrive in any order without an older balance winning.

- Online players and players whose quit save is still queued: memory changes under the PlayerData lock, then one
  queued transaction (new balance, version, ledger rows, optional extra SQL) on the database writer, normally
  within milliseconds. Not dependent on autosave any more. A hard crash can still lose the few milliseconds of
  writes that are queued but not committed.
- Offline players: one synchronous transaction with compare-and-set on the version and bounded retries (also on
  SQLite busy / MySQL deadlock). A transfer between an online and an offline player is one transaction.
- Database not ready: no money moves (`STORAGE_UNAVAILABLE`); a database that fails to start disables the plugin.
- `savePlayer` is now an upsert (SQLite `ON CONFLICT`, MySQL `ON DUPLICATE KEY`) that leaves unlisted columns alone
  and keeps a newer stored balance.
- A login that read the row before an offline balance change re-reads it (E11).
- If the stored version is found ahead of the live copy (another server sharing the database), the live balance
  is kept, written above it, and counted in `EconomyManager.versionConflicts()`.
- `/pay`: `ECONOMY.PAY.MIN-AMOUNT`, `MAX-AMOUNT`, `TAX-PERCENT` (capped at 50%, recipient always gets 1 cent),
  `COOLDOWN-SECONDS` (kept across relogs).
- Autosave interval now `DATABASE.AUTOSAVE-SECONDS` (default 60, was a fixed 300); MongoDB upload on its own
  `DATABASE.MONGODB.SYNC-INTERVAL-SECONDS` (default 300). Ledger pruning by `ECONOMY.LEDGER.RETENTION-DAYS`.
- Vault calls are recorded as `VAULT_DEPOSIT` / `VAULT_WITHDRAW`; crate money uses `CRATE_REWARD`.

**Files.** New: `economy/Money.java`, `economy/EconomyStore.java`, `economy/LedgerEntry.java`,
`economy/EconomyContext.java`. Changed: `managers/EconomyManager.java` (rewritten engine, same public API plus
overloads), `managers/DatabaseManager.java`, `managers/PlayerDataManager.java`, `models/PlayerData.java`,
`models/EconomyReason.java`, `models/EconomyFailureReason.java`, `coinflip/EconomyGateway.java`,
`coinflip/CoinFlipService.java`, `coinflip/storage/CoinFlipDao.java`, `coinflip/bukkit/SmpEconomyGateway.java`,
`hooks/VaultEconomyHook.java`, `managers/CrateManager.java`, `utils/PaymentUtils.java`, `tasks/AutoSaveTask.java`,
`SmpPlugin.java`, `config.yml`, `database.yml`.

**Tests.** New: `economy/MoneyTest` (rounding, exact round trip over the whole range incl. the top 100,000 cents),
`economy/EconomyStoreTest` (version guard, compare-and-set, rollback, nested transactions, ledger queries, holds,
6 writers x 50 concurrent compare-and-set changes on a WAL database: no lost or doubled update),
`managers/EconomyManagerTest` (15 cases: online write-through, insufficient funds, exact cents, transfers with tax,
failed transfer, offline transactions, online-to-offline transfer, balance limit, no database, late older writes,
version conflict, quit copy, tax and cooldown rules, stale login preload), `managers/DatabaseManagerPlayerSaveTest`
(upsert, older copy, unlisted columns, MySQL statement order). `testsupport/EconomyHarness` runs the real
DatabaseManager/PlayerDataManager/EconomyManager on in-memory SQLite.

**Commands run.** `mvn -o -s tools/central-only-settings.xml test` → 310 tests, 0 failures, 0 errors, 8 skipped.

**Not verified here.** MySQL statements (no MySQL server in the sandbox; the statement shape is unit-tested),
behaviour on a live server.

### M2: gambling holds in the same transaction as the money

- `/dice` and `/roulette` holds are rows of `economy_holds`. A hold is inserted in the transaction that takes the
  bet and deleted in the transaction that pays the result (a roulette payout closes every hold of the round), so
  after a crash a hold exists exactly when its bet was taken and not yet paid. Start-up refunds each one in one
  transaction with its deletion, so a second start refunds nothing.
- A refused payout (e.g. `ECONOMY.MAX-BALANCE`) keeps the winner's hold at the payout and closes the others.
- Holds left in a 3.1 `data/gambling-escrow.yml` are refunded once, entry by entry, then the file is renamed
  `.recovered`.
- Hold ids now include a time part, so a held payout from an earlier run cannot collide with a new round.
- CoinFlip: regression test that its balance write treats an equal stored version as saved and ignores an older one.

**Files.** `gambling/GamblingEscrow.java` (rewritten), `gambling/Wallet.java`, `gambling/DiceGame.java`,
`gambling/Roulette.java`, `gambling/Gambling.java`, `economy/EconomyStore.java` (hold id 64 chars).
**Tests.** `gambling/GamblingEscrowTest` (9 cases: take opens hold, unaffordable bet, win pays and closes,
loss closes, offline roulette winner closes all holds in one transaction, refused payout kept, crash recovery
once, failed take leaves no debit and no hold, 3.1 file migration once), `coinflip/CoinFlipStorageTest` (+1).
**Commands run.** `mvn -o -s tools/central-only-settings.xml test` → 320 tests, 0 failures, 0 errors, 8 skipped.

### M3: economy commands

- `/baltop [page]` (aliases `balancetop`, `moneytop`; `smpplugin.baltop`, default everyone; under the
  LEADERBOARDS feature): the money ranking from the same cache as `/leaderboard money`, own rank, clickable pages,
  page size `ECONOMY.BALTOP.PAGE-SIZE`.
- `/transactions [page]` (alias `moneylog`; `smpplugin.transactions`, default everyone): the player's own ledger,
  newest first, read on the database thread, with names of the other players resolved there.
- `/eco give|take|set|reset|history|stats` (alias `smpeco`): give/take/set reuse the `addmoney`/`removemoney`/
  `setmoney` nodes, reset (starting balance) needs `setmoney`, history needs `smpplugin.admin.economy.history`,
  stats needs `smpplugin.admin.economy.stats` (money supply, credits/debits per reason in a window, balance-writer
  queue/failures/conflicts). Every staff change stores the staff name as actor and an optional note.
- `/addmoney`, `/removemoney`, `/setmoney` now run through the same code: they record the actor and report the
  real reason a change failed (the balance limit, the database, insufficient funds) instead of "player not found".
- Permissions regenerated (`plugin.yml`, `docs/permissions-reference.md`) with
  `mvn test -Dtest=PermissionRegistryTest -Dsmpplugin.regenerate=true`.

**Note:** `/eco` is also used by EssentialsX. When both are installed the first plugin to register wins; `/smpeco`
and `/smpplugin:eco` always reach SmpPlugin.

**Tests.** `commands/EcoCommandTest` (5: actor and note recorded, insufficient funds explained, limit refused,
reset, per-subcommand permissions and tab completion). **Commands run.** full suite → 325 tests, 0 failures,
0 errors, 8 skipped.

### M4: `/events` (commit af6afc6)

- `seasonal/EventId`, `EventMode` (AUTO, ON, OFF), `EventStatus` (ACTIVE, SCHEDULED, DISABLED, COMPLETED; a pure
  rule from config switch, feature switch, mode and dates), `EventControl` (`data/events.yml`, temp file + atomic
  move, a damaged file is never overwritten and locks the switches), `SeasonalEvents` (one `Source` per event, a
  minute tick that announces each start and stop once, every change logged with the actor), `EventsCommand` and
  `EventsMenu` (permission and state checked on every click).
- **Tests.** `seasonal/SeasonalEventsTest` (12). Full suite 337, 0 failures.

### M5: one calendar engine, Advent, rank perks, reward vault (commit 2d0a058)

- `seasonal/SeasonCalendar`: rules, per-viewer test dates (nothing given or saved), claim-and-pay in one step
  (store write queued before the money), rank bonus on money and shards only, streaks (`ClaimStreak`) with one-off
  `STREAK.BONUSES`, a `COMPLETION` prize, in-game prize overrides (`data/<event>-prizes.yml`, atomic write), door
  statistics. `CalendarMenu`, `CalendarEditorMenu`, `DoorEditorMenu`, `CalendarCommand` and `CalendarTheme` are
  shared; `HalloweenManager` and `advent/AdventManager` are thin subclasses.
- `perks/`: `Perk` (six perks with hard caps), `Rank`, `Perks` (ranks by permission and priority, highest wins,
  `CAPS`, problems logged), `PerksCommand` (`/perks`, `/perks <rank>`, `/perks check <player>`).
- `engagement/PendingItems` is now a vault of independent entries (no read-modify-write, so a concurrent add can
  only be delayed to the next login, never lost); `Reward.give` and giveaways use it; `/claimitems`.
- `core/PlayerStore`: failed loads stay unloaded (S1), writes in one transaction (S2), a retry on join.
- **Tests.** `seasonal/ClaimStreakTest` (5), `perks/PerksTest` (5), `advent/AdventCalendarTest` (7: pay once,
  rank bonus, streak and completion once, catch-up streak, test mode, /events off, unloaded player). Full suite
  354, 0 failures.

### M6: Halloween hub, Trick or Treat, `/daily` (commit 28ea5fc)

- `halloween/HalloweenHub`: candy (`CandyAccount`, one season, written at once, per-player lock), milestones paid
  when reached, quests (`Quest`, `QuestType`, `QuestProgress`; progress saved in batches with
  `PlayerStore.setDeferred`, completion at once), the daily treat bag, the candy shop (two-click buy, limits), a
  one-minute leaderboard cache read on the database thread. `HalloweenMenus` (hub and five pages),
  `HalloweenCommand` (`calendar`, `candy`, staff candy changes).
- `TrickOrTreatCommand`: decide and pay at the knock, `TreatTable` (checked weights, `/trickortreat odds`),
  `KnockLog` (daily limit), houses (spots in `data/trickortreat-houses.yml`, FancyNpcs NPCs through
  `TrickOrTreatNpcListener`, created only when FancyNpcs is installed), rank perks for the wait, knocks and candy.
- `DailyRewards` pays at the click with `DAILY-BONUS-PERCENT` and checks permission, feature and data on every click.
- **Tests.** `halloween/HalloweenModelTest` (9), `halloween/HalloweenHubTest` (8). Full suite 371, 0 failures.

### M7: remaining defects (commits 0fb8b3f, 7200b2e)

- `LeaderboardManager`: stored rows reload on the database thread, single flight, stale rows shown meanwhile (P1).
- `DatabaseManager.redact` / `redacted`: credentials removed from the whole cause chain of a start failure (S7).
- Permission re-checks in the crate editor and feature menu (S8); `SkinsRestorer` in `softdepend` (D2); startup
  line says SmpPlugin; the unused legacy banner removed.
- `SeasonCalendar.validate()` at start and on reload (S9); a player is told when a reward's money is refused.
- **Tests.** `managers/DatabaseManagerRedactionTest` (2), `AdventCalendarTest` (+1), `HalloweenHubTest` (+1).
  Full suite 375, 0 failures, 8 skipped. `mvn -o -s tools/central-only-settings.xml package` builds
  `SmpPlugin-4.0.0-paper.jar` (27.7 MB); the local API stand-ins are not in it.

### M8: documentation

- Version 4.0.0 in `pom.xml`. README (New in 4.0, honest storage table, upgrade notes), Features.md (real sizes,
  New in 4.0, storage, config files), CHANGELOG 4.0.0, COMPATIBILITY (4.0.0 not yet run on a live server),
  Modrinth text, and two guides: `docs/economy.md`, `docs/seasonal-events.md`.

### M9: release 4.0.0

- Version renamed from 3.2.0 to 4.0.0 (`pom.xml`, `version.txt`, docs, permission notes).
- `.github/workflows/release.yml` builds the Paper and Spigot jars from the official repositories on GitHub
  Actions and runs the tests for both on every push. It publishes a release (`.github/release-notes/<tag>.md`,
  both jars, `SHA256SUMS.txt`) on a pushed `v*` tag, a manual run, or a pushed commit whose message contains
  `[release]`; the tag must match `pom.xml`. Release jars come from there, never from the sandbox build (which
  compiles against locally built APIs). This session can push only its own branch (a tag push got HTTP 403), so
  4.0.0 was published with a `[release]` commit.

## Risks and limits that remain

- **Nothing in 4.0 has been run on a live Minecraft server.** Everything server-facing (menus, sounds, particles,
  dialogs, FancyNpcs clicks, item rewards) is compiled against the Paper API and reviewed, not played.
- Item-giving paths and `PendingItems` serialisation need a running server (the Paper registry) and are not
  unit-tested; their logic around the store is.
- The Spigot profile (`-Pspigot`) was not built here.
- MySQL statements are tested only by shape; no MySQL server in the sandbox.
- A hard crash can lose writes still queued on the database thread (milliseconds); quest progress can lose up to
  30 seconds.
- Several servers sharing one MySQL database resolve a balance changed on both by version (newest wins, counted),
  not by merging. MongoDB is a mirror of a local file.
- Folia stays unsupported (`folia-supported: false`).

## Current task

Release 4.0.0 from branch `claude/hopeful-darwin-btvnxm` (tag `v4.0.0`).

## Next tasks (suggested)

1. Run 4.0.0 on a Paper 1.21.11 test server: `/events`, a full Advent test with `/advent test`, the Halloween hub
   pages, Trick or Treat houses with FancyNpcs, `/claimitems` with a full inventory, `/eco stats`.
2. Build the Spigot profile on a machine that reaches `hub.spigotmc.org`.
3. Run the economy against a real MySQL server (the upsert statements and deadlock retries).

## Resuming

`git log --oneline` shows the milestones above. Build with the commands at the top of this file; the full suite
must stay at 0 failures. Regenerate permissions after editing `PermissionRegistry` with
`mvn test -Dtest=PermissionRegistryTest -Dsmpplugin.regenerate=true`, then run that test again without the flag.
