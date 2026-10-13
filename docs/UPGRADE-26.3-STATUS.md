# SmpPlugin 26.3 upgrade — status

Tracks the upgrade specification (`SmpPlugin-26.3-upgrade-prompt.md`) against the real repository.
Each item is **Done** (implemented and covered by tests), **Partial**, or **Not started**.
Nothing here has been booted on a Minecraft server yet; see [COMPATIBILITY.md](../COMPATIBILITY.md).

Last updated: 13 October 2026 — version `2.0.0-alpha.4`.

## Evidence for this version

| Check | Result |
|---|---|
| `mvn clean package` (Paper API) | Success; `SmpPlugin-2.0.0-alpha.4-paper.jar`; 184 tests, 0 failures, 8 skipped (item tests need a server on Paper) |
| `mvn clean package -Pspigot` | Success; `SmpPlugin-2.0.0-alpha.4-spigot.jar`; 184 tests, 0 failures; built without the dialog package (no class references it) |
| Packaged jar | plugin.yml `version: 2.0.0-alpha.4`, `api-version: "26.3"`; `dialog.yml`, `coinflip.yml`, `coinflip-messages.yml`, `anticheat.yml` included; Paper jar contains the 24 dialog classes |
| Server boot test | **Not yet done for alpha.4.** Installed on the Purpur 26.3 test server, but the boot attempt from the build sandbox stopped inside Paper's bundled Spark profiler (no loopback networking) before plugins loaded. Purpur 26.3 failed with alpha.1 (`NoSuchFieldError`, fixed in alpha.2). |

## Audit findings (existing code)

These are facts about the 1.5 code that the later stages must fix. Line numbers refer to this version.

| # | Finding | Where | Risk |
|---|---|---|---|
| A1 | Money is stored and calculated as `double`, rounded with `Math.round(x * 100) / 100`. | `PlayerData` (`money`, `moneySpent`, `moneyMade`); `EconomyManager.roundCurrency` | Rounding drift; violates the exact-arithmetic requirement. |
| A2 | Balance changes for **online** players are only kept in memory; they reach the database on the periodic autosave. | `EconomyManager.saveLoadedAccount` | A crash loses acknowledged payments, sales and purchases. |
| A3 | Changes to an **offline** player's balance load the row, modify a copy and save it asynchronously with no version check. | `EconomyManager.loadAccount` / `saveLoadedAccount` | Two concurrent payments to the same offline player can overwrite each other (lost update). |
| A4 | All database work shares one JDBC connection; there are no operation IDs, journals or uniqueness constraints for value transfers. | `DatabaseManager` | No idempotency or crash recovery for transfers. |
| A5 | If the database fails to start, the plugin logs an error and keeps running every feature. | `DatabaseManager.initialize` | Value transfers run against no storage. Now visible in `/smpplugin status`; not yet blocked. |
| A6 | "MongoDB" mode runs on a local SQLite cache copied to MongoDB on autosave. | `DatabaseManager.initializeMongoBridgeConnection`, `importMongoSnapshotIntoSqlite` | Not a shared transactional store. Now reported as DEGRADED. |
| A7 | The sidebar, scoreboard-number hiding, tablist heads, fake players and `/hide` use reflection into `net.minecraft` and CraftBukkit classes. | `PacketSidebarRenderer`, `ScoreboardNumberHider`, `TablistComponentUpdater`, `FakePlayer*`, `Hide*` | May break on 26.3 internals; only a server boot can tell. |
| A8 | The shade relocation for SQLite targets `org.xerial`, but the driver lives in `org.sqlite`, so it was never relocated. | `pom.xml` | Pre-existing; left unchanged to avoid altering driver loading untested. |
| A9 | `YamlConfiguration.loadConfiguration` turns a file with a syntax error into an empty configuration. | all managers | **Fixed for reloads** (see §3); at startup the broken files are now listed loudly. |
| A10 | `PlayerAdvancementDoneListener` writes the advancement game rule into every world, permanently. | listener constructor | Survives plugin removal. Behaviour unchanged; noted for the subsystem pass. |

## Stage 1 — audit, compatibility, lifecycle safeguards, permissions

### §2 Platform compatibility — Partial

- **Done:** Java 25 build; compiled against Spigot API `26.3-R0.1-SNAPSHOT`; `api-version` filled from
  `minecraft.api.version` in the pom; shade plugin upgraded so Java 25 jars build.
- **Done:** fixed the one 26.3 API break (`GameRule.ANNOUNCE_ADVANCEMENTS` → `SHOW_ADVANCEMENT_MESSAGES`).
- **Done:** `CompatibilityPolicy` refuses Folia and versions below 26.3, and reports newer versions,
  unknown software and every not-yet-verified target as UNTESTED. Nothing claims SUPPORTED until a
  runtime verification is recorded.
- **Done:** `PlatformReport` / `/smpplugin status` and a startup summary: platform, Java, `/reload`
  detection, storage guarantees, Redis, each optional plugin, Paper native anti-Xray per world.
- **Not started:** runtime verification on Paper, Purpur, Spigot and CraftBukkit 26.3; Folia (blocked:
  no Folia 26.3 build exists); a legacy 1.21 artifact (1.5 remains the release for older servers).

### §3 Lifecycle — Partial

- **Done:** `/smpplugin reload` validates every YAML file strictly first and keeps the running
  configuration when any file fails, listing file and line (`ConfigValidator`).
- **Done:** startup lists configuration files that failed to parse.
- **Done:** re-enabling inside a running server (`/reload`) is detected, logged as unsupported and shown
  in `/smpplugin status`.
- **Not started:** module lifecycle (validate → storage → register → tasks → drain → shutdown), coherent
  configuration snapshots, restart-only setting classification.

### §4 Permissions — Mostly done

- **Done:** `PermissionRegistry` is the single list of nodes. The plugin.yml `permissions:` section and
  [permissions-reference.md](permissions-reference.md) are generated from it.
- **Done:** tests fail the build when plugin.yml or the docs are stale, when code checks an unregistered
  node, when a bundled config names an unregistered node, when a command's `permission:` is unregistered,
  or when the admin bundle contains a destructive action or security bypass.
- **Done:** default policy — player features `true`; staff, admin, perks, bypasses and destructive actions
  `false`; `smpplugin.admin` stays default-op for migration and excludes destructive actions and security
  bypasses.
- **Done:** 36 nodes that were checked but never declared (and therefore silently op-only) are declared.
- **Done:** nodes outside the namespace (`anvilmod.admin`, `safety.*`, `donutfriends.admin`,
  `donutauction.*`) moved into `smpplugin.*`; old names kept as legacy nodes that grant the new ones.
- **Done:** one node per punishment action (`tempban`, `tempmute`, `warn`, `kick` are new); 1.5 grants keep
  their exact effect through legacy bundles.
- **Done:** `smpplugin.staff.mode` only opens staff mode; `smpplugin.legacy.staff-mode-bundle` restores the
  1.5 tool set.
- **Done:** removed the `isOp()` shortcut (update notice now uses `smpplugin.admin.updatenotify`).
- **Done:** `/findplayer` now has a permission (`smpplugin.findplayer`, default true — same behaviour).
- **Done:** `/smpplugin` tab completion hides subcommands the sender cannot use; single-gate admin commands
  declare `permission:` in plugin.yml so Bukkit hides and blocks them.
- **Done:** migration map and LuckPerms examples for member, VIP, helper, moderator, administrator and
  security reviewer: [permissions-migration-and-luckperms.md](permissions-migration-and-luckperms.md).
- **Not started:** re-checking permissions inside GUI click handlers and after asynchronous work
  (needs the menu-session rework in stage 4); Bedrock form parity audit; `smpplugin.crates.open/preview`
  (added with the crate rework); `smpplugin.security.*` (added with the anti-cheat module).

## Stages 2–7 — Not started

| Spec section | Status | Notes |
|---|---|---|
| §8 Ledger, escrow, journals, crash recovery | Not started | Next stage. Must fix A1–A5 first. |
| §7 Auction house rework | Not started | Depends on §8. |
| §6 Crates rework | Not started | Depends on §8. |
| §5 GUI system and animations | Not started | |
| §9 Anti-cheat | **Partial** | 23 server-side checks including timer, no-fall (with damage), step, water walk, boat fly, anti-knockback, criticals, scaffold, through-wall interaction, inventory, auto totem and cheat-client signatures; violation levels with decay; alerts with evidence IDs and files; kick and ban levels; staff-reviewed ban queue and optional ban waves; multi-check escalation; server-lag guard; exemptions; exact-node bypasses; Bedrock handling ([anticheat.md](anticheat.md)). Not done: full movement prediction, live-server tuning, security dashboard GUI, bridge to an external anti-cheat. |
| CoinFlip Nexus (separate specification) | **Implemented, not boot-tested** | Journaled money flow, recovery, 16 menus, animation, statistics, leaderboards, achievements, admin tools ([coinflip.md](coinflip.md)). Its own journal works around findings A1–A3 for CoinFlip only; the general economy ledger (§8) is still not started. |
| §10 Subsystem improvements | **Partial** | alpha.4 replaced settings, Auction House, selling and RTP with the newer UltimateDonutSMP-V2 versions (dialogs, Quick Buy, sell statistics, RTP cache and queue); see CHANGELOG. Their money paths still use the `double` economy (findings A1–A3). |
| §11 New modules (trade, mailbox, quests, claims, seasons, …) | Not started | |
| §12 Config migration and profiles | Partial | Only the reload validation above. |
| §13 Performance and benchmarks | Not started | |
| §14 Release gates (concurrency, fault injection, boot tests) | Not started | Only unit tests so far. |

## Proposed next stage: authoritative ledger (§8)

1. Store money as `long` minor units (cents) with one rounding rule; migrate existing `double`
   balances with a backed-up, logged schema migration.
2. Add `ledger_operations` (operation UUID primary key, state, actor, server ID, amounts, timestamps) and
   `ledger_entries` (append-only) tables; every debit/credit/transfer runs as one SQL transaction that
   also writes its entries, keyed by operation UUID so retries are idempotent.
3. Make the database the authority for balances: conditional updates (`UPDATE … SET balance = balance - ?
   WHERE uuid = ? AND balance >= ?`) instead of read-modify-write of cached `PlayerData`; the cache is
   refreshed from committed results.
4. Refuse value transfers while storage is unavailable (finding A5).
5. Concurrency tests: many parallel transfers to one offline account must conserve the total.
