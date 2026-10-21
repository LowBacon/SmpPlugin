# Economy

How SmpPlugin keeps money, what each command does, and what to check after a crash. Everything here applies to
4.0.0 and later.

## How money is kept

- **Whole cents.** An amount is rounded half-up to the cent once, when it comes in (`/pay 10.005` pays 10.01).
  Everything after that is whole-cent arithmetic.
- **A balance limit.** `ECONOMY.MAX-BALANCE` in `config.yml` (default 10,000,000,000,000). A stored balance keeps
  every cent exactly up to about 35,000,000,000,000, so the limit cannot be set higher than that. A balance that is
  already above the limit is kept, but cannot grow.
- **Saved at once.** Every change is written in the background the moment it happens. The rest of a player's row
  (stats, settings) is saved every `AUTOSAVE-SECONDS` (`database.yml`, default 60) and when they leave.
- **One transaction per change.** A payment changes both balances and writes both ledger rows together, or not at
  all. Offline players are changed with a compare-and-set: if their row changed meanwhile, the payment is worked
  out again instead of overwriting it.
- **Versions.** Every balance has a version number that only goes up. An older copy can never overwrite a newer
  one: not a player row that was read while they were logging in, not a full player save, not CoinFlip's writes.
- **No storage, no economy.** If the database does not start, SmpPlugin stops instead of running without it.

## The ledger

Every change is one row in the `economy_ledger` table:

| Column | Meaning |
|---|---|
| `tx_id`, `group_id` | The change, and the payment it belongs to (both sides of a `/pay` share a group) |
| `player_uuid`, `counterparty_uuid` | Whose balance changed, and the other player if there was one |
| `reason` | What it was: `PLAYER_PAY`, `ADMIN_ADD`, `ADMIN_RESET`, `VAULT_DEPOSIT`, `EVENT_REWARD`, `GAMBLE_PAYOUT`, `COINFLIP_PAYOUT` … |
| `amount_cents`, `balance_after_cents`, `balance_version` | The change, the balance after it, its version |
| `actor`, `note` | Who did it (a staff name, `advent`, `daily` …) and a note (`advent door 5 (2026)`) |

`ECONOMY.LEDGER.RETENTION-DAYS` deletes older rows once a day; `0` (the default) keeps everything.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/balance [player]` | none | A balance |
| `/pay <player> <amount>` | none | Pay someone; follows `ECONOMY.PAY` |
| `/transactions [page]` | `smpplugin.transactions` | Your own balance history |
| `/baltop [page]` | `smpplugin.baltop` | The richest players (`ECONOMY.BALTOP.PAGE-SIZE` a page) |
| `/eco give <player> <amount> [note]` | `smpplugin.admin.addmoney` | Add money |
| `/eco take <player> <amount> [note]` | `smpplugin.admin.removemoney` | Take money (never below zero) |
| `/eco set <player> <amount> [note]` | `smpplugin.admin.setmoney` | Set a balance |
| `/eco reset <player> [note]` | `smpplugin.admin.setmoney` | Back to `SETTINGS.MONEY-PER-DEFAULT` |
| `/eco history <player> [page]` | `smpplugin.admin.economy.history` | Anyone's balance history |
| `/eco stats [hours]` | `smpplugin.admin.economy.stats` | Money supply, money created and destroyed, the biggest flows |

`/addmoney`, `/removemoney` and `/setmoney` still work and go through the same path. EssentialsX also registers
`/eco`; when it owns the name, use `/smpeco` or `/smpplugin:eco`.

## Pay rules (`config.yml`)

```yaml
ECONOMY:
  MAX-BALANCE: 10000000000000
  PAY:
    MIN-AMOUNT: 0.01      # smallest payment
    MAX-AMOUNT: 0         # largest payment; 0 = no limit
    TAX-PERCENT: 0        # taken from the payment, shown to both players
    COOLDOWN-SECONDS: 0   # wait between two payments by the same player
  LEDGER:
    RETENTION-DAYS: 0
  BALTOP:
    PAGE-SIZE: 10
```

## Bets

Dice and roulette stakes are held in the `economy_holds` table, written in the same transaction that takes the
money. A round pays out (or gives back) and closes its holds in one transaction. Holds still open at the next start
are paid back automatically. Unfinished bets from 3.1 (`data/gambling-escrow.yml`) are paid back once and the file
is renamed `.recovered`.

## After a crash

- Writes still queued on the database thread at the moment of the crash can be lost (normally milliseconds of
  changes). They are never applied twice.
- Calendar doors, treats, quest rewards and `/daily` mark the reward as given before handing it over, so a crash
  can lose a reward but never give one twice.
- Check `economy_ledger` for a player's last changes with `/eco history <player>`.

## Known limits

- Two servers sharing one MySQL database each keep their online players' balances in memory; a balance changed on
  both at once is resolved by version (the newer one wins, and the conflict is counted), not merged.
- MongoDB mode is a mirror of a local SQLite file, not a live shared store.
