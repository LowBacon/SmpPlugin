# SmpPlugin engineering roadmap

The working log for the 3.2 reliability pass. Each milestone records what changed, the commands that were run
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
| C1 | P1 | Daily rewards are handed out when the claim animation ends; a crash during the spin keeps the claim and loses the prize. The animation decides delivery. | `engagement/DailyRewards` | open |
| F1 | P3 | No `/baltop`, no `/eco`, no transaction history, no pay limits or tax, no economy statistics. | commands | open |
| D1 | P3 | "MongoDB" mode is a local SQLite file copied to MongoDB on autosave; docs call it MongoDB storage. | `DatabaseManager` MongoDB bridge, docs | open |
| D2 | P3 | `SkinsRestorer` is used but missing from `softdepend`, so it can load after SmpPlugin. | `plugin.yml` | open |
| D3 | P3 | Compiled `target/` output (1,087 files) is tracked in git; `.gitignore` has no `target/` entry. | repository | fixed (M0) |
| D4 | P3 | Docs disagree with the code (command count, line count, config file count, package counts, "2.x" wording, mixed SmpPlugin/CustomSmp/FinnishSMP branding). | `README.md`, `Features.md`, `docs/*` | open |

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

## Current task

Economy commands: `/baltop`, `/eco`, `/balance history` (F1).

## Next tasks

1. Seasonal events platform: `/events`, `/advent`, `/halloween`, `/trickortreat`.
2. VIP / VIP+ perks.
