# Seasonal events

The Advent Calendar, the Halloween hub and Trick or Treat: how they start and stop, how to set them up, how to test
them, and what rank perks add. For 3.2.0 and later.

## Switching events: `/events`

| Status | Meaning |
|---|---|
| **Running** | Players can take part (on its dates, or forced on). |
| **Starts in …** | Waiting for its dates. |
| **Over for this year** | This year's dates have passed; the next season is scheduled. |
| **Off** | Switched off with `/events`, `ENABLED: false` in its file, or its feature turned off (`/smpplugin features`). |

- `/events` opens the menu. Players see the status of each event and can click a running one to join in.
- `/events status` lists every event in chat; `/events <event>` shows one with its dates and time zone.
- Staff with `smpplugin.admin.events`: `/events <advent|halloween|trickortreat> on|off|auto`, or in the menu
  left click (on), right click (off), shift click (auto).
  - **on** runs the event now, also outside its dates. Calendar doors still open only on their own days.
  - **off** stops it, also during its dates. Nothing anyone earned is taken away.
  - **auto** follows the dates in the event's file again.
- The switch is saved in `data/events.yml` and survives restarts. If that file is damaged it is never overwritten:
  every event follows its dates and `/events` says the switches are locked until it is fixed or removed.
- Every change is written to the console with who made it, and players are told once when an event starts or ends.
- Placeholder: `%smpplugin_events_<advent|halloween|trickortreat>%` gives `active`, `scheduled`, `completed` or
  `disabled`.

## Calendars (Advent and the Spooky Calendar)

Both calendars work the same way; Advent reads `advent.yml`, the Spooky Calendar reads `halloween.yml`.

- **Dates:** `MONTH`, `FIRST-DAY`, `LAST-DAY`, `TIME-ZONE` (empty: the server's zone), `MODE` (`DAILY`, or
  `ALL-ON-LAST-DAY`), `CATCH-UP` (open missed doors later) and `GRACE-DAYS` (extra days after the last one; a
  December season may run into January).
- **Prizes:** `DOORS.<day>` or `DOORS.DEFAULT`, each with `MONEY`, `SHARDS`, `ITEMS`, `COMMANDS`, `DISPLAY` and
  `NAME`. `/advent edit` (or `/halloween edit`) sets money, shards and items in game; those are kept in
  `data/<event>-prizes.yml` and win over the file. Console commands always come from the file.
- **Paid at the click.** A door is marked opened and its prize handed over in one step, before the spin. Leaving
  during the spin, or a crash, can never pay a door twice. Items that do not fit go to the reward vault.
- **Streaks** (`STREAK.ENABLED`, on for Advent): days in a row with a door opened. `STREAK.BONUSES.<days>` pays
  once per season when the streak first reaches that length.
- **Completion** (`COMPLETION`): a prize once every door of the season is open.
- **Staff:** `give <player> <door>` hands out a prize without opening the door, `reset <player> [door]` closes doors
  again, `stats` counts players per door, `reload` reads the file again and lists any mistakes in it.
- **Test mode:** `/advent test 2026-12-13` (or `today`, or `off`) shows the calendar as on that day **to you
  only**. Doors you open in test mode give nothing and save nothing. `PREVIEW-DATE` in the file does the same for
  everyone *with real prizes*; it is meant for a test server and is warned about at start.
- `LOG-CLAIMS: true` writes every opened door to the console.

## The Halloween hub (`/halloween`)

| Part | What it is | Set in `halloween.yml` |
|---|---|---|
| Spooky Calendar | `/halloween calendar` | dates, `DOORS`, `CALENDAR` |
| Trick or Treat | knock for a treat or a harmless trick | `TRICK-OR-TREAT` |
| Daily treat bag | a free bag every day of the season | `DAILY-BAG` |
| Quests | season goals, finished once each | `QUESTS` |
| Candy shop | spend candy | `SHOP.ITEMS` |
| Milestones | prizes for the candy earned this season | `MILESTONES` |
| Leaderboard | the top candy earners this season | |

- **Candy** is earned from treats (`CANDY` on each treat), tricks (`TRICK-CANDY`), quests, the daily bag and
  monsters (`CANDY.MONSTER-KILL-CHANCE-PERCENT`). It belongs to one season. Spending candy never lowers the
  "earned" total, so milestones are paid once.
- **Quest types:** `KILL_MONSTERS`, `KILL_MOB` (`TARGET: ZOMBIE`), `BREAK_BLOCK` (`TARGET: PUMPKIN`),
  `TRICK_OR_TREAT`, `OPEN_DOORS`, `EAT_CANDY`. Progress is saved every 30 seconds, on quit and at shutdown;
  finishing a quest is saved at once with its reward.
- **Shop items** take `PRICE` (candy), `LIMIT` (per player per season, 0 = none), and `MONEY`, `SHARDS`, `ITEMS`,
  `COMMANDS` (for example `crate key %player% rare 1`) and `DISPLAY`. A purchase needs two clicks.
- **Staff:** `/halloween candy <player> give|take|set <n>` (online or offline; logged).

## Trick or Treat (`/trickortreat`)

- The outcome is decided and paid at the knock; the knock, the door and the reveal only show it.
- `TREAT-CHANCE` is the percent of knocks that are treats. Treats are picked by `WEIGHT`; a treat without a weight
  above 0, or that gives nothing, is left out with a warning. `/trickortreat odds` shows each treat's real chance.
- `COOLDOWN-MINUTES` between knocks, `DAILY-LIMIT` knocks a day (0 = no limit).
- **Houses:** `/trickortreat house add <id> [radius]` where you stand, `house remove <id>`, `house list`; they are
  kept in `data/trickortreat-houses.yml`. FancyNpcs NPCs named in `TRICK-OR-TREAT.NPCS` are houses too (click
  them). With `REQUIRE-HOUSE: true` players must knock at a house, and the wait counts per house.
- `TRICK-OR-TREAT.FIRST-DAY` / `LAST-DAY` give it its own days inside the Halloween month.
- Tricks (`TRICKS`): `SCARE`, `BATS`, `LAUNCH`, `SPIN`, `DARKNESS`, `FAKE_CREEPER`. None can hurt a player or take
  their items.

## Rank perks (`perks.yml`)

```yaml
CAPS:
  CALENDAR-BONUS-PERCENT: 50
RANKS:
  vip:
    PERMISSION: smpplugin.rank.vip
    PRIORITY: 10
    DISPLAY: "&a&lVIP"
    PERKS:
      CALENDAR-BONUS-PERCENT: 10
```

- A rank is a permission. Give it to a group (LuckPerms: `/lp group vip permission set smpplugin.rank.vip`). Rank
  permissions are `default: false`, so nobody has a rank until you give one.
- Only the rank with the highest `PRIORITY` counts; ranks never add up. There is no built-in order.

| Perk | Adds | Hard limit |
|---|---|---|
| `CALENDAR-BONUS-PERCENT` | more money and shards behind calendar doors (never items or commands) | 100 |
| `CALENDAR-STREAK-GRACE-DAYS` | missed days a calendar streak survives | 3 |
| `DAILY-BONUS-PERCENT` | more money and shards from `/daily` | 100 |
| `TRICK-OR-TREAT-COOLDOWN-CUT-PERCENT` | a shorter wait between knocks | 50 |
| `TRICK-OR-TREAT-EXTRA-KNOCKS` | extra knocks a day (with a `DAILY-LIMIT`) | 10 |
| `CANDY-BONUS-PERCENT` | more candy | 100 |

- Bonuses are rounded down to whole cents and whole units. `CAPS` can lower a limit, never raise it. A value above
  its limit, an unknown perk or two ranks with one permission are logged at start and on `/smpplugin reload`.
- No perk changes gambling odds, prices, anti-cheat, moderation or anything another player loses out on.
- `/perks` shows every rank; `/perks <rank>` one rank; `/perks check <player>` (staff) which rank a player gets.

## Reward vault (`/claimitems`)

Reward items that do not fit an inventory go to the vault instead of the ground: calendar doors, treats,
milestones, quests, shop purchases, `/daily`, and giveaway items handed back. They are handed over on join, or with
`/claimitems` after making room.

## Permissions

| Node | Default | For |
|---|---|---|
| `smpplugin.events` | everyone | `/events` |
| `smpplugin.admin.events` | op | switching events |
| `smpplugin.advent` | everyone | `/advent` |
| `smpplugin.admin.advent` | op | `/advent edit|give|reset|test|stats|reload` |
| `smpplugin.halloween` | everyone | `/halloween` |
| `smpplugin.halloween.trickortreat` | everyone | `/trickortreat` |
| `smpplugin.admin.halloween` | op | Halloween staff commands, houses, candy, odds |
| `smpplugin.perks` | everyone | `/perks` |
| `smpplugin.admin.perks` | op | `/perks check` |
| `smpplugin.rank.vip`, `smpplugin.rank.vipplus` | nobody | the bundled ranks |
| `smpplugin.claimitems` | everyone | `/claimitems` |
