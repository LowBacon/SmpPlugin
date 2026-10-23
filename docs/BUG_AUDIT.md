# SmpPlugin bug audit (4.2.0, 2026-10-21)

## Baseline

- Branch `main`, 4.1.0 plus the uncommitted 4.2.0 work (35 changed files before this audit).
- Java 25.0.4, Maven 3.10.0. Builds: Paper 1.21.11 API (default), Paper 26.3 API (`-Ppaper,api-latest`), Spigot (`-Pspigot`).
- Baseline: `mvn -o clean package` passed, 411 tests, 0 failures, 8 skipped. No existing failures.

## How it was audited

Code paths read end to end:
- the lobby/world system and the lobby inventory snapshot;
- the auction house (manager and repository);
- orders delivery, crates (keys, gacha, choose-one), daily rewards;
- bounties and kill rewards, the shop and spawner sales;
- amethyst tools, `/invsee`, menu click handling;
- combat command blocking and the teleport queue, number parsing.

Code-wide scans:
- Bukkit calls inside async blocks: none found.
- Swallowed broad exceptions: 85, almost all version fallbacks.
- Raw `Double.parseDouble` on player input.
- Commands without a declared permission: all checked in code.

Not audited line by line: duels, FFA, coinflip, gambling, the anti-cheat, dialogs, menus beyond click
handling. Those were reviewed only where the scans pointed.

## Register

Severity follows the definitions in the audit brief. "Unit" means an automated test covers it; "boot"
means a Paper 26.3 test server enabled, reloaded and disabled the plugin without errors; "in-game" is
not done yet for any item.

| ID | Sev. | Where | Defect and root cause | Fix | Verified |
|---|---|---|---|---|---|
| AUD-01 | High | `LobbyManager.applyJoinEffects` | Every join into the lobby reset XP to 0 and cleared potion effects, and nothing gave them back: XP was lost on every login. It also healed and fed on every login (relog to heal). | The lobby snapshot now holds XP, health, hunger, saturation and effects and gives them back on leaving; the join effects only run when a snapshot holds the real values. | compile, boot |
| AUD-02 | High | `LobbyManager.sendToLobby` | The inventory was saved and cleared *before* the teleport. A failed teleport left the player empty-handed in the SMP, and the next `/lobby` saved the empty inventory over the real one. | A failed teleport gives the inventory straight back; an existing snapshot is never overwritten (carried items go to the reward vault). | compile, boot |
| AUD-03 | Medium | `LobbyListener.onQuit` | The in-memory snapshot was dropped on quit while its file write could still be queued; a quick rejoin read no file and the items were gone. | The memory copy is kept; it is newer than the file and always wins. | compile |
| AUD-04 | Medium | `LobbyManager.persistSavedInventory` | Snapshot files were written in place; a crash mid-write left an unreadable file. | Written to a temp file and moved over the old one (atomic where supported). | compile |
| AUD-05 | Low | `LobbyManager.restoreInventory` | An empty saved off-hand left the lobby's off-hand item in place. | The off-hand is always set from the snapshot. | compile |
| AUD-06 | Medium | `WorldListener.onDamage` | The spawn world is void now; falling off the spawn island inside the protected radius killed the player with their items. | Void damage in a protected, non-PvP area returns the player to the world's spawn; FFA's void stays deadly. | compile |
| AUD-07 | Medium | `AuctionHouseManager.cancelListing` | When the cache refresh after a cancel failed, the item was handed back directly while the database claim stayed, so the claim paid it out a second time. | The claim is always collected through the leased claim path; the item is never handed over directly. | compile |
| AUD-08 | Medium (perf) | `AuctionHouseRepository.loadSnapshot` | Every list, buy and cancel decoded the item of every listing ever made; the cost grew with the server's whole sales history. | Decoded items are reused by listing id (items never change), clones handed out. Not measured. | compile |
| AUD-09 | Medium | `PlayerVisibilityManager.apply` | Hiding was always deferred a tick, so a joining player's tab entry reached players who should not see them, and hide/show requests from one event landed a tick apart. | Applied immediately on the main thread; deferred only from other threads (Folia). | compile, boot |
| AUD-10 | Low | `WorldSystem.safe` | On an empty column (a void world without its platform) the "safe" spot was the bottom of the world. | The spot is left unchanged. | compile |
| AUD-11 | Medium | `NumberUtils.parse` | `NaN`, `Infinity` and overflowing input parsed, and `amount < 1` style checks let NaN through (bounties, `/lobby border NaN`, `/tp NaN ...`). The economy rejected non-finite amounts later, other callers did not. | Non-finite results are refused centrally; the two raw parsers (lobby radius, teleport coordinates) check it too. | unit (`NumberUtilsParseTest`) |
| AUD-12 | High | `TeleportManager.queue`, `CombatManager.isBlockedCommand` | Combat logging escapes: warps, `/tpa`, team homes, the homes menu (opened before the fight) and countdowns started before the tag all teleported a tagged player; `/smpplugin:spawn` and aliases dodged the blocked-command list. | Every queued teleport stops for a player in combat, also when tagged during the countdown; blocked commands are matched by their real command name. | unit (`CombatCommandLabelTest`), compile |
| AUD-13 | Medium | `SpawnerManager` loot sale | Stored loot was deleted and sell progress counted before the payout; a refused payout (balance cap, storage down) destroyed the loot. | Paid first; loot is removed and progress counted only after a successful payout. | compile |
| AUD-14 | High | `AmethystToolsListener` | The drill, shovel and chopper broke the extra blocks directly, without asking protection: one block just outside spawn protection, a claim or a crate could tear out what was inside, and unbreakable blocks were not excluded. | Each extra block fires an `AmethystBlockBreakEvent` that protection can cancel; unbreakable blocks are skipped. SmpPlugin's anti-cheat and TitanGuard skip these derived events so drill users are not flagged. | compile (SmpPlugin and TitanGuard) |
| AUD-15 | Low | `SmpArrival` | Held-back join notices were not cleared when the plugin was disabled. | Cleared in `WorldSystem.stop()`. | compile |
| AUD-16 | Medium | `LobbyManager.warnIfNearEdge` | (fixed earlier in 4.2) The lobby border warning did not check the world, so SMP players were warned every few seconds. | Only in the lobby's world. | compile, boot |
| AUD-17 | High | join handlers, `LobbyManager.restoreInventory` | (fixed earlier in 4.2) Reward-vault items handed out on join in the lobby were overwritten when the survival inventory came back. | Join deliveries wait for the SMP; items gained in the lobby are handed back on top. | compile, boot |
| AUD-18 | Medium | `OrdersManager.processAutoClaims` | An order item claim was marked collected, then delivered a tick later without checking the player was still online; logging out in between lost the item. | Offline at delivery: the claim is reopened. | compile |
| AUD-19 | Medium | `AuctionHouseManager` claim delivery | Same pattern for auction item claims: completed in the database, then added to a player who may have left. | Offline at delivery: the whole item becomes a new pending claim. | compile |
| AUD-20 | Low | `Giveaways` winner delivery | A winner who left in the tick after the draw lost the item prize; leftovers of a full inventory were dropped on the ground. | Offline winners get it in the reward vault; leftovers go to the vault. | compile |
| AUD-21 | Low | `AmethystToolCommand` give | A tool given to a full inventory vanished. | Given through the reward vault fallback. | compile |
| AUD-22 | Medium | `SpigotScheduler.dispatchPlayerCommand` | Commands run by menu and dialog buttons used `performCommand`, which skips `PlayerCommandPreprocessEvent`: the lobby allowlist, the combat block and command spy never saw them, so a menu could run blocked commands in the lobby or in combat. | The command is announced as typed first; a cancelled event stops it. | compile, boot |
| AUD-23 | Low (perf) | `FfaManager.tick` | Copied every active match each tick although matches are only checked once a second. | Returns early outside the once-a-second pulse. | compile |
| AUD-24 | Low | `DatabaseManager.addCrateKeys` | Adding keys past `Integer.MAX_VALUE` wrapped negative and was stored as 0, wiping the balance. | Capped at the maximum. | compile |
| AUD-25 | Low | `PlayerData.addShards/removeShards` | Shards wrapped the same way; `removeShards` with a negative amount added shards (no current caller passes one). | Saturating add; negative removals refused. | unit (`PlayerDataShardsTest`) |
| AUD-26 | Medium | `PlayerDeathListener`, `ShardManager` | Shards per kill are new currency and had no limit: killing a second account over and over printed shards. | The same victim pays again only after `SETTINGS.KILL-REWARD-SAME-VICTIM-COOLDOWN-SECONDS` (300); no shards when both players share an address (`SETTINGS.KILL-REWARD-SKIP-SAME-IP`). | unit (`KillRewardCooldownTest`) |
| AUD-27 | High | `DuelManager.handleQuit` | A duel loser who quit on the death screen had their pending respawn state removed, including the items they keep after the winner's loot: those items were gone for good. | The owed items are kept and given back when they rejoin and respawn. | compile, boot |
| AUD-28 | Medium | `DuelManager.consumeRespawn`, `FfaManager.queueRespawnRestore/consumeRespawn` | When no arena or lobby respawn spot resolved, the gear owed after a death was never queued (FFA) or was dropped at respawn (duels). | The gear is given back wherever the player respawns. | compile |
| AUD-29 | Medium | FFA and duel owed restores | Gear owed to an offline or dead player lived only in memory; a restart before they came back lost it. | Written to `data/ffa-restores` and `data/duel-restores` (atomic), loaded at start, deleted once given back; a player who is alive at rejoin gets it at once. | compile, boot |

## Informational (not changed)

- **Bounties move money between players and create none,** so a bounty on an alt only gives the money
  back. Only the shard kill reward needed a limit (AUD-26).
- **The auction snapshot still reads every row of `auction_listings`;** only the item decoding is cached
  now. Paging the history menu would remove the rest.
- **`ItemKey` (orders, worth) matches by material, potion type and required enchantments.** Extra
  enchantments, names and lore are accepted. This is by design.
- **The lobby restore puts back anything carried on top of the survival inventory.** If the server
  crashes in the milliseconds between a restore and the deletion of the snapshot file, the next lobby
  entry can give a copy back. This is not something a player can trigger.
- **AoE blocks from the amethyst tools now fire break events,** so they count toward block statistics
  and Halloween break quests like hand-mined blocks.
- **The 85 swallowed exceptions are almost all reflection and version fallbacks;** a sample was read
  and left as is.

## Counts

- Confirmed defects fixed in this audit: 27 (AUD-01 to AUD-15, AUD-18 to AUD-29), plus 2 from earlier 4.2 work (AUD-16, AUD-17).
- Covered by new unit tests: 4 (AUD-11, AUD-25, AUD-26, the label part of AUD-12).
- Fixed but not verified in game: all 29.
- Outstanding: the informational items above, and in-game verification.

## Verification run

- `mvn -o clean package`: 419 tests, 0 failures, 8 skipped.
- Compiles against the Paper 26.3 API (`-Ppaper,api-latest`) and the Spigot API (`-Pspigot`).
- Fresh Paper 26.3 server: enable, `/smp world status`, `/smpplugin status`, `/smp world reload` and
  disable, all without exceptions.
- **Cleanup check:** all `/** */` and `/* */` comments were removed and a `// SmpPlugin by LowBacon`
  header added to every Java file. All 1,332 compiled classes were disassembled before and after
  (`javap -c -p`), and the output is byte-for-byte identical.
