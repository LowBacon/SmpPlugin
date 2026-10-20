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
| E1 | P0 | Online players' balance changes live only in memory until the 5-minute autosave or quit; a crash loses every payment, sale, purchase and reward since the last save. | `EconomyManager.saveLoadedAccount`, `tasks/AutoSaveTask` | open |
| E2 | P0 | Offline balance changes load a copy of the row, change it and save it asynchronously with a full-row `REPLACE`: two changes before the first save lands lose one of them (lost update). | `EconomyManager.loadAccount/saveLoadedAccount` | open |
| E3 | P0 | Gambling escrow can mint money on a crash: the bet leaves the balance only in memory (saved later), while the hold is written to `data/gambling-escrow.yml` at once and refunded at the next start. A crash in between refunds a bet whose debit was never saved. A settled win whose hold removal was not yet written is refunded too. | `gambling/GamblingEscrow`, `gambling/Wallet` | open |
| E4 | P0 | Crate money rewards bypass the economy service (`PlayerData.addMoney`): no rounding, no log, no limit, saved only at autosave. | `CrateManager.grantMoneyReward` | open |
| E5 | P0 | CoinFlip writes an absolute balance on its own database connection; it can land after a newer save of the same player and overwrite it. | `coinflip/storage/CoinFlipDao.writeBalance` | open |
| E6 | P1 | A database that fails to start is only logged; the plugin keeps running every feature (including Vault payments) against no storage. | `DatabaseManager.initialize`, `SmpPlugin.onEnable` | open |
| E7 | P1 | Money is a `double` rounded with `Math.round(x * 100) / 100`, with no upper balance limit: amounts above ~9×10^13 lose cents and above ~9.2×10^16 the rounding overflows. `/pay 1Q` style inputs parse to 10^18. | `EconomyManager`, `NumberUtils.parse` | open |
| E8 | P1 | Vault withdrawals and deposits by other plugins are logged as `AUCTION_PURCHASE` / `SELL_PAYOUT`. | `hooks/VaultEconomyHook` | open |
| E9 | P1 | The full-row `REPLACE INTO players` resets any column it does not list back to its default. | `DatabaseManager.savePlayer` | open |
| E10 | P1 | Shared-connection proxy releases transaction ownership on `commit()`/`rollback()` while auto-commit is still off, so another thread's statements can join the next transaction. | `DatabaseManager.ThreadSafeConnectionHandler` | open |
| E11 | P1 | A login preloads the player row asynchronously; an offline balance change between that read and the join is overwritten by the stale preloaded copy. | `JoinPreloadListener`, `PlayerDataManager.loadOrCreate` | open |
| C1 | P1 | Daily rewards are handed out when the claim animation ends; a crash during the spin keeps the claim and loses the prize. The animation decides delivery. | `engagement/DailyRewards` | open |
| F1 | P3 | No `/baltop`, no `/eco`, no transaction history, no pay limits or tax, no economy statistics. | commands | open |
| D1 | P3 | "MongoDB" mode is a local SQLite file copied to MongoDB on autosave; docs call it MongoDB storage. | `DatabaseManager` MongoDB bridge, docs | open |
| D2 | P3 | `SkinsRestorer` is used but missing from `softdepend`, so it can load after SmpPlugin. | `plugin.yml` | open |
| D3 | P3 | Compiled `target/` output (1,087 files) is tracked in git; `.gitignore` has no `target/` entry. | repository | open |
| D4 | P3 | Docs disagree with the code (command count, line count, config file count, package counts, "2.x" wording, mixed SmpPlugin/CustomSmp/FinnishSMP branding). | `README.md`, `Features.md`, `docs/*` | open |

## Milestones

_None completed yet._

## Current task

Repository hygiene, then the economy durability work (E1, E2, E4, E5, E6, E7, E9, E11).

## Next tasks

1. Gambling escrow in the same database transaction as the balance change (E3).
2. Economy commands and history (F1), Vault reasons (E8), connection proxy fix (E10).
3. Seasonal events platform: `/events`, `/advent`, `/halloween`, `/trickortreat`.
4. VIP / VIP+ perks.
