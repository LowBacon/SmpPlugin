# SmpPlugin anti-cheat

A server-side anti-cheat built into SmpPlugin 2.0. It judges what the server can observe (movement, hits,
block actions, inventory use, mining patterns), corrects impossible actions, removes players who keep failing
checks, and gives staff evidence. It cannot switch off client mods or see what a client renders, so no
server-side anti-cheat can promise that every cheat is blocked. Every check below is aimed at something a
cheat client must do on the server to gain an advantage.

**Status:** 23 checks as of 2.0.0-alpha.3. The detection logic is unit-tested (181 tests in the build).
The thresholds have **not** been tuned on a live 26.3 server yet. Bans therefore wait for staff approval by
default (see [Enforcement](#enforcement)).

## How it works

- **Violation levels.** Each failed check adds to the player's violation level (VL) for that check. VL
  decays every second, so one-off oddities from lag fade away; only repeated or strong evidence builds up.
- **Four thresholds per check** in `anticheat.yml`:
  - `ALERT-AT`: staff with `smpplugin.security.alerts` get a message with an evidence ID.
  - `ACTION-AT`: the server corrects the player. Movement is set back to the last verified safe position
    (never into the void, a wall, lava or an unloaded chunk). A hit, break, placement, container opening or
    inventory click is cancelled. Skipped fall damage is applied. `0` = alert only.
  - `KICK-AT`: kick with the evidence ID (`ENFORCEMENT.KICKS` must be on).
  - `BAN-AT`: ban, or put on the ban queue (see below).
- **Multi-check escalation.** Four different checks at alert level within ten minutes is far stronger evidence
  than any single check, so it puts the player on the ban queue.
- **Attribute-aware limits.** Speed, jump height, step height, gravity, fall damage, safe fall distance and reach
  come from the player's live attributes. Speed potions, Soul Speed, Swift Sneak, Depth Strider,
  attribute-changing items and other plugins' modifiers are honoured automatically.
- **Server-side ground detection.** The client's own "on ground" flag is never trusted. Ground is computed
  from real collision shapes (slabs, fences, carpets, snow), liquids, climbable and sticky blocks, and entities
  you can stand on. Comparing the two is what catches NoFall.
- **Lag compensation.** Reach is measured against where the target was during the attacker's ping window.
  Knockback is only judged after the velocity packet has had time to arrive. After a **server** lag spike,
  timing checks (timer, anti-knockback) pause for two seconds, so server lag is never blamed on players.
- **Short, automatic exemptions** after teleports, joins, respawns, world changes, knockback and other velocity
  (wind charges, explosions, mace wind burst), taking damage, flight toggles, game-mode changes, dismounting,
  elytra landing, riptide and spear use. Every exemption expires; staff can add temporary ones (max 1 hour).
- **Never checked:** creative and spectator players; players allowed to fly; gliding, riptiding, riding,
  sleeping (boat fly is checked separately).
- **Bedrock players** (Floodgate) skip the movement, aim and inventory checks by default, because Bedrock
  movement, touch controls and inventories differ. Block and mining checks still apply.

## Checks

| Check | What it detects | Default action |
|---|---|---|
| **Fly** | Rising higher than jump strength allows; speeding up vertically in mid-air; hovering; staying airborne without falling. Slime/bed bounces, bubble columns and leaving water are allowed for. | Setback 6, kick 25, ban 50 |
| **Speed** | Horizontal movement above the attribute-derived limit for ground, air, ice, slime and liquid, accumulated as a balance so lag bursts do not flag. | Setback 8, kick 30, ban 60 |
| **Timer** | Movement updates arriving faster than the 20-per-second game clock. A balance tolerates lag in both directions, and bursts of queued network traffic never count. | Setback 8, kick 25, ban 50 |
| **No fall** | Claiming to be on the ground while the server sees the player falling through air. When it acts, the fall damage the player skipped is applied on the real landing (Feather Falling, armour, water, hay, beds and slime are respected). | Damage applied 3, kick 20, ban 40 |
| **Step** | Rising more in one update than the step-height attribute or a jump allows (stepping up a full block). Pistons nearby are allowed for. | Setback 3, kick 15, ban 30 |
| **Water walk** (Jesus) | Standing still on top of water or lava with nothing solid underneath, for several updates. Lily pads, frosted ice and boats are solid and fine. | Setback 5, kick 20 |
| **Boat fly** | A boat rising into the air with no water or bubble column underneath. | Ejected 4, kick 15 |
| **Reach** | Hits beyond `entity_interaction_range` + tolerance, lag-compensated. Spears get their extra reach. | Hit cancelled 5, kick 15, ban 30 |
| **Wall hit** | Hits where both the target's centre and its nearest point are behind a full, view-blocking block. | Hit cancelled 5, kick 15 |
| **Kill aura** | Hits far outside the view direction; more than 3 targets in one tick; two different targets more than 60° apart in one tick; attacking while using an item (eating, blocking, drawing a bow), which a vanilla client cannot do. | Hit cancelled 6, kick 20, ban 40 |
| **Anti-knockback** (Velocity) | Travelling less than 20% of the knockback the server applied over the following updates, unless a wall is in the way or the player is in liquid, cobweb or on a ladder. | Kick 20, ban 40 |
| **Criticals** | Critical hits while the player never rose more than a hair (0.12 blocks) above the ground they stand on: the fake mini-hop of packet criticals. Paper reports critical hits directly; on Spigot vanilla's rule is used. | Crit bonus removed 4, kick 20 |
| **Autoclicker** | Sustained click rate above 22 per second (mining and right-click swings excluded). | Alert only (butterfly clicking is legal) |
| **Fast break** | Breaking a block faster than `Block#getBreakSpeed` allows for the tool, enchantments, Haste and Mining Fatigue. | Cancelled 4, kick 15, ban 30 |
| **Nuker** | More than 4 blocks in one tick, or more than 22 per second. | Cancelled 4, kick 10, ban 20 |
| **Fast place** | More than 4 placements in one tick, or more than 15 per second. | Cancelled 5, kick 15 |
| **Block reach** | Breaking or placing beyond `block_interaction_range` + 0.5. | Cancelled 4, kick 15, ban 30 |
| **Scaffold** | Placing on a face of a full block the eye is behind (geometrically impossible to click); placing without the crosshair anywhere near the block; towering straight up faster than jumping allows. | Cancelled 3, kick 15, ban 30 |
| **Through-wall interaction** (Ghost hand) | Opening a chest, barrel, shulker box, furnace, hopper, dispenser, dropper or crafter, or breaking a block, that is completely hidden behind other blocks (15 sight lines checked). | Cancelled 3, kick 15 |
| **X-ray pattern** | Finding **hidden** diamond or ancient-debris veins at a rate far above branch mining. Ore visible from a cave does not count. | Alert only, never acts |
| **Inventory** | More than 6 item clicks in one tick or 30 per second (chest stealers, auto-armour); keeping full ground speed right after an inventory click (inventory walk). SmpPlugin's own menus are excluded. | Click cancelled / inventory closed 4, kick 20 |
| **Auto totem** | A new totem in the off-hand under 100 ms after one was used. | Alert only (players who hover a totem with the inventory open are fast too) |
| **Cheat client** | The client brand (Paper and Purpur only) or a registered mod channel matches a known cheat client (Wurst, Meteor, Aristois, LiquidBounce, RusherHack, ThunderHack, Inertia, Boze, Konas, SalHack, Phobos, Future). Clients can hide this; it catches the ones that do not. | Kick 1 |

### Why some things are not checked

- **Click timing patterns.** The server handles player packets once per tick, so millisecond click timing is
  not visible server-side. Only click *rates* are reliable.
- **Full movement prediction** (simulating every client tick, as dedicated anti-cheats do) is not implemented;
  the checks above compare against physical limits instead. That catches blatant cheats; subtle ones stay
  below the limits.

## Enforcement

```yaml
ENFORCEMENT:
  KICKS: true          # every KICK-AT applies
  BANS: QUEUE          # OFF | QUEUE | AUTO
  BAN-COMMAND: 'tempban {player} 30d Unfair advantage (anti-cheat #{id})'
  BAN-WAVE-MINUTES: 20
  KICK-ON-QUEUE: true
```

- **QUEUE (default).** A player who reaches a `BAN-AT` level, or trips four checks at once, is kicked and put on
  the ban queue (`plugins/SmpPlugin/anticheat/ban-queue.yml`, kept across restarts). Staff see it in
  `/anticheat bans` and run `approve <player|all>` or `deny <player>`. Because the thresholds are not yet tuned
  on a live server, this keeps a person between a false positive and a ban.
- **AUTO.** Queued bans are executed in **ban waves** every `BAN-WAVE-MINUTES`. A cheater banned in a wave cannot
  tell which module was detected or when. Staff can still deny a ban before the wave runs.
- **OFF.** `BAN-AT` is ignored; kicks and corrections still happen.

Bans run the configured console command, by default SmpPlugin's own `/tempban`, so they appear in punishment
history with the evidence ID. To make hackers leave without staff review, set `BANS: AUTO` once alerts on your
server look clean.

## Coverage against Meteor Client

Reference: the module list of `MeteorDevelopment/meteor-client` (`systems/modules/movement`, `combat`, `world`,
`player`). "Partial" means some behaviour of the module produces actions these checks flag, not that the
module is reliably caught.

| Meteor modules | Coverage |
|---|---|
| Flight, AirJump, HighJump, Spider | **Covered** by Fly |
| Speed, LongJump, ClickTP | **Covered/partial** by Speed |
| Timer | **Covered** by Timer (plus Speed for large speed-ups) |
| NoFall | **Covered** by No fall, which also applies the skipped damage |
| Step, ReverseStep | **Step covered** by Step; ReverseStep looks like falling |
| Jesus | **Covered** in its "solid" mode by Water walk; bobbing modes are partial |
| EntityControl / boat fly | **Covered** for boats by Boat fly |
| Velocity (anti-knockback) | **Covered** by Anti-knockback for full or strong reductions; small reductions are partial |
| Criticals | **Covered** for packet and "bypass" modes by Criticals |
| KillAura | **Partial**: Kill aura (angle, multi-target, switch angle, attacking while using items), Reach, Wall hit. An aura that aims properly within range at one target looks like normal play. |
| Hitboxes, Reach | **Covered** by Reach |
| CrystalAura, AnchorAura, BedAura, Surround, HoleFiller, AutoTrap, SelfTrap, AutoWeb, Burrow, AutoCity | **Partial**: Fast place, Block reach, Scaffold face check and Reach catch impossible rates, distances and faces |
| Scaffold, Tower | **Covered/partial** by Scaffold (hidden faces, aim, tower rate) and Fast place |
| Nuker | **Covered** by Nuker, Block reach and Through-wall |
| PacketMine, InfinityMiner, Excavator, VeinMiner, HighwayBuilder | **Partial**: Fast break, Nuker, Fast place |
| ChestStealer / AutoSteal, AutoArmor, InventoryTweaks | **Partial** by Inventory (click rate) |
| GUIMove / InvMove | **Covered** on the ground by Inventory (inventory walk) |
| AutoTotem, Offhand | **Partial**: Auto totem alerts on inhumanly fast swaps |
| Interact / ChestAura through walls | **Covered** by Through-wall interaction |
| NoSlow, FastClimb, ElytraBoost, TridentBoost, Slippy, Blink | **Not covered** (item-use slowdown, climb speed and elytra physics are not modelled) |
| AutoWeapon, AutoEXP, Quiver, AttributeSwap, BowAimbot, BowSpam, ArrowDodge, AutoLog | **Not distinguishable** from fast legitimate play server-side |
| AutoWalk, AutoJump, Sprint, Sneak, SafeWalk, Parkour, Anchor, AntiVoid, AutoWasp, world automation, StashFinder | **Not detectable** or harmless |
| X-ray / ESP / Freecam / Fullbright (render) | **X-ray**: X-ray pattern check plus Paper's native anti-Xray (below). ESP, Freecam and Fullbright are client-side; only hiding information helps. |

For full movement prediction, pair SmpPlugin with a dedicated anti-cheat and turn off the overlapping SmpPlugin
checks (`ENABLED: false`) so two engines do not set players back against each other.

## Native anti-Xray (recommended)

On Paper and Purpur, Paper's built-in anti-Xray hides ores from the client, which is far stronger than any
detection. `/smpplugin status` shows whether it is on per world. Recommended start in
`config/paper-world-defaults.yml` (then **restart** the server; `/reload` does not apply it):

```yaml
anticheat:
  anti-xray:
    enabled: true
    engine-mode: 2
    max-block-height: 64
    update-radius: 2
    lava-obscures: false
    use-permission: false
    hidden-blocks:
    - diamond_ore
    - deepslate_diamond_ore
    - ancient_debris
    - emerald_ore
    - deepslate_emerald_ore
    - gold_ore
    - deepslate_gold_ore
    - iron_ore
    - deepslate_iron_ore
    - chest
    - spawner
    replacement-blocks:
    - stone
    - deepslate
```

For the Nether, set `max-block-height: 128` and add `ancient_debris` / `nether_gold_ore` with `netherrack` as
replacement in that world's `paper-world.yml`. Spigot and CraftBukkit have no native anti-Xray.

## Staff use

| Command | Permission | |
|---|---|---|
| `/anticheat alerts` | `smpplugin.security.alerts` | Turn your alerts on or off |
| `/anticheat status` | `smpplugin.security.inspect` | Module state, enforcement mode, ban-queue size, players with violations |
| `/anticheat checks` | `smpplugin.security.inspect` | Each check's purpose and alert/act/kick/ban levels |
| `/anticheat profile <player>` | `smpplugin.security.inspect` | Client brand, Bedrock, ping and every current violation level |
| `/anticheat history <player>` | `smpplugin.security.inspect` | Last 10 flags with evidence IDs |
| `/anticheat evidence <id>` | `smpplugin.security.inspect` | Full details of a flag |
| `/anticheat bans` | `smpplugin.security.inspect` | The ban queue with reasons and evidence IDs |
| `/anticheat bans approve <player\|all>` / `deny <player>` / `run` | `smpplugin.security.manage` | Execute or remove queued bans, run a ban wave now |
| `/anticheat exempt <player> <check\|movement\|combat\|world\|player> <seconds>` | `smpplugin.security.manage` | Temporary exemption, max 1 hour, logged |
| `/anticheat reset <player>` | `smpplugin.security.manage` | Clear violation levels, logged |
| `/anticheat reload` | `smpplugin.security.manage` | Reload `anticheat.yml` (kept unchanged if the file is invalid) |

Alias: `/ac`. Ops receive alerts and inspect through `smpplugin.admin`.

**Bypass permissions** `smpplugin.bypass.anticheat.movement`, `.combat`, `.world` and `.player` must be granted
as the **exact** node: wildcards, `smpplugin.admin` and staff mode do not grant them. Their use is logged once
per session.

**Evidence files:** every flag is appended to `plugins/SmpPlugin/anticheat/evidence-YYYY-MM-DD.log` (one JSON
object per line), written off the main thread and kept for 14 days by default.

## Tuning

1. Run with the defaults for a few days and read alerts and the ban queue. Note which checks flag legitimate
   players (`/anticheat profile` shows everything for one player).
2. Raise that check's `ALERT-AT` or tolerance parameter, or disable it.
3. Deny queued bans that were false positives and report them with the evidence ID and what the player was doing.
4. When the queue only contains real cheaters, switch `ENFORCEMENT.BANS` to `AUTO`.
