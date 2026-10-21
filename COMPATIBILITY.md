# Compatibility

SmpPlugin **4.1.0** is one jar per server family that runs on every Minecraft version from
**1.21.11** to **26.3**.

> 4.1.0 compiles and passes its automated tests against the Paper 1.21.11 and Spigot APIs on every push. The
> features new in 4.0 and 4.1 (the economy ledger, `/events`, the Advent Calendar, the Halloween hub, rank perks,
> rank name tags, unlimited balances) have not been run on a live server yet; the server results in the table below
> are from 3.1.0.

| Jar | Servers |
|---|---|
| `SmpPlugin-4.1.0-paper.jar` | Paper, Purpur (and other Paper forks) |
| `SmpPlugin-4.1.0-spigot.jar` | Spigot, CraftBukkit |

Use the jar that matches your server. Paper and Spigot are not binary compatible, so the wrong jar
can fail at runtime.

## Minecraft versions

| Minecraft | Java | Status |
|---|---|---|
| 1.21.11 | 21+ | ✅ Supported. Runs on a Purpur 1.21.11 server. |
| 26.1 | 25 | ⚙️ Allowed. No Paper or Purpur 26.1 server exists to test on (Paper went to 26.1.1). |
| 26.1.1 | 25 | ⚙️ Supported by build checks, not yet run on a live server. |
| 26.1.2 | 25 | ⚙️ Supported by build checks, not yet run on a live server. |
| 26.2 | 25 | ⚙️ Supported by build checks, not yet run on a live server. |
| 26.3 | 25 | ✅ Supported. Runs on Purpur 26.3, where most of 2.x was developed. |

**"Build checks"** means:
- the plugin compiles against both the 1.21.11 and the 26.3 server API
- every server and Adventure method it calls (about 1,500) was confirmed to exist with the same
  signature in the Paper and Spigot APIs for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3

**Older and newer versions:**
- **Older than 1.21.11:** the plugin refuses to start. Use SmpPlugin 1.5 for 1.21.10 and earlier.
- **Newer than 26.3:** the plugin starts, but reports the version as untested in the console and in
  `/smpplugin status`.

## Java

The jars are Java 21 bytecode:
- **1.21.11 servers** run them on Java 21 or newer.
- **26.x servers** need Java 25 for Minecraft itself, and run the plugin fine on it.

## Folia

Not supported. The plugin disables itself on Folia, because its modules have not been checked for
Folia's region threads.

## Optional plugins

LuckPerms, PlaceholderAPI, Vault, ProtocolLib, Simple Voice Chat, SkinsRestorer, Floodgate,
FancyNpcs, Apollo and Multiverse-Core are all optional. Their own compatibility with each Minecraft
version is up to their authors. `/smpplugin status` shows which ones were found and what they
enable.

## Building

```bash
mvn clean package                   # Paper/Purpur jar (compiled against 1.21.11, Java 21)
mvn clean package -Pspigot          # Spigot/CraftBukkit jar
mvn clean compile -Ppaper,api-latest   # check the code still compiles against Paper 26.3 (Java 25)
mvn clean compile -Pspigot,api-latest  # same for Spigot 26.3
```
