# Installing SmpPlugin

## What you need

| | |
|---|---|
| Server | Paper or Purpur **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 or 26.3** (`SmpPlugin-<version>-paper.jar`), or Spigot / CraftBukkit on the same versions (`SmpPlugin-<version>-spigot.jar`). Folia is not supported. |
| Java | The Java your server version needs: 21 for 1.21.11, 25 for 26.x. |
| Required plugins | None. |
| Recommended | PlaceholderAPI (placeholders in other plugins), LuckPerms (ranks and prefixes), Vault (lets other plugins use SmpPlugin's economy). |

## A fresh server, step by step

1. **Download** the jar for your server from the
   [latest release](https://github.com/LowBacon/SmpPlugin/releases/latest).
2. **Install it:** put the jar in `plugins/`. Add LuckPerms, Vault and PlaceholderAPI too if you
   want them.
3. **Start the server once.** SmpPlugin creates `plugins/SmpPlugin/` with every config file, the
   SQLite database (`data/data.db`) and its tables. The console shows
   `SmpPlugin v4.0.0 | server name: CustomSMP` and, at the end, a list of what started.
4. **Name your server.** In `plugins/SmpPlugin/config.yml`:
   ```yaml
   BRANDING:
     SERVER-NAME: CustomSMP          # shown on the scoreboard, tab list and menus
     SERVER-ADDRESS: play.example.net  # shown in the tab list footer
   ```
   The default address `play.customsmp.example` is a placeholder, not a real server.
5. **Set the spawn:** stand where it should be and run `/setspawn`. `/smp setup` shows a checklist
   of the first-time setup.
6. **Give your staff their roles.** One node per rank does it, for example with LuckPerms:
   ```
   /lp group admin permission set smpplugin.role.admin
   /lp group mod permission set smpplugin.role.moderator
   /lp group owner permission set smpplugin.role.owner
   ```
   Every node is listed in [permissions-reference.md](permissions-reference.md).
7. **Apply your changes** with `/smp reload` (or `/smp reload <module>` for one part), then check
   `/smp status`: every line should read SUPPORTED, RUNNING or (for plugins you did not install)
   UNAVAILABLE.

## Turning modules on and off

`/smp modules` opens a menu of every module (chat, homes, crates, ranked PvP, seasonal events and
around 75 more). Click to switch one off or on; the change is saved in `config.yml` under
`FEATURES` and takes effect at once. A switched-off module's commands answer that the feature is
off. For the 4.0 modules (`RANKED`, `SEASONAL_EVENTS`, `SURVIVAL_RULES`) switching off also removes
their listeners and timers straight away.

## Seasonal events

`/event` lists the events and when they run. Each event runs between its `START` and `END` dates in
`events.yml`; staff can run one now with `/event enable <event>`, stop it with
`/event disable <event>`, and put it back on its dates with `/event auto <event>`. The Advent
Calendar's doors and gifts are in `advent.yml` (`/advent edit` changes them in game); Halloween keeps
its own `halloween.yml`. Hide Easter eggs with `/easter addegg` while looking at a block.

## The scoreboard and tab list

- Scoreboard: `scoreboard.yml`, or in game with `/sidebar`. The title `{server_title}` is the
  server name in a gradient. Players hide their own sidebar with `/sidebar`.
- Tab list: `TABLIST` in `config.yml`.
- If another scoreboard plugin is installed (TAB, FeatherBoard, AnimatedScoreboard, SimpleScore,
  KiteBoard), SmpPlugin leaves the sidebar (and with TAB, the tab list) to it. Set
  `OVERRIDE-OTHER-PLUGINS: true` to use SmpPlugin's anyway.

## Optional plugins

| Plugin | What SmpPlugin uses it for | Without it |
|---|---|---|
| PlaceholderAPI | SmpPlugin's placeholders in other plugins; other plugins' placeholders in SmpPlugin's text | SmpPlugin's own placeholders still work in its own text |
| LuckPerms | Rank prefixes (`%smp_rank%`, the tab list), tab list order by group weight, refresh on rank change | Vault chat prefixes, if any |
| Vault | Other plugins can use SmpPlugin's economy | Only SmpPlugin uses its economy |
| ProtocolLib | Explosion particle filter, `/hide`, `/fakeplayer`, item worth on held items | Those features are off |
| SkinsRestorer | Skins for disguises | Disguises keep the player's own skin |
| Floodgate | Bedrock forms for Orders and Quick Buy | Bedrock players get the chest menus |
| FancyNpcs | Lobby and Billford NPCs | No NPCs |
| Apollo | Lunar Client rich presence and teammate overlay | Off |
| Multiverse-Core | Multi-world helpers | Off |
| Simple Voice Chat | `/vcmute` | Off |

`/smp status` shows which of these were found. On a test run of 4.0.0 on Paper 26.3,
PlaceholderAPI 2.12.3, LuckPerms 5.5.87, Vault 1.7.3 and ProtocolLib 5.4.0 loaded and SmpPlugin
started with no errors; the others were not part of that test.

## Databases

SQLite needs no setup. For MySQL, edit `database.yml` (`DATABASE.TYPE: MYSQL` and the connection
details) before the first start; switching later starts with an empty MySQL database. SmpPlugin creates and upgrades
its own tables; `/smp status` and `/smp diagnostics` show the schema version. Back up `data/data.db`
(or your MySQL database) before upgrading.

## Building it yourself

```bash
./mvnw clean package            # Paper and Purpur jar, runs the tests
./mvnw clean package -Pspigot   # Spigot and CraftBukkit jar
```

On Windows use `mvnw.cmd`. Building needs JDK 25; the jars run on Java 21 and newer. The jars are in
`target/`.
