package com.bx.smpPlugin.managers;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Name tags above heads: one team per player with their rank prefix, on every scoreboard players look at. */
class NametagManagerTest {

    private static final UUID OWNER = UUID.fromString("70000000-0000-0000-0000-0000000000c1");
    private static final UUID MEMBER = UUID.fromString("70000000-0000-0000-0000-0000000000c2");

    private final YamlConfiguration config = new YamlConfiguration();
    private final FakeBoard main = new FakeBoard();
    private final List<Scoreboard> privateBoards = new ArrayList<>();
    private final Map<UUID, String> ranks = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private boolean otherPlugin;
    private NametagManager nametags;

    @BeforeEach
    void setUp() {
        nametags = new NametagManager(() -> config, main::proxy, () -> List.copyOf(privateBoards),
                player -> ranks.getOrDefault(player.getUniqueId(), ""), player -> hidden.contains(player.getUniqueId()),
                () -> otherPlugin, (text, player) -> text.replace('&', '§'));
        ranks.put(OWNER, "&c&lOWNER &r");
    }

    @Test
    void theRankPrefixIsShownAboveTheHeadOnEveryBoard() {
        FakeBoard sidebar = board();
        Player owner = player(OWNER, "Lenni");

        nametags.refresh(owner);

        for (FakeBoard board : List.of(main, sidebar)) {
            FakeTeam team = board.teamOf("Lenni");
            assertNotNull(team, "the owner is in a name tag team");
            assertEquals("§c§lOWNER §r", team.prefix);
            assertEquals(ChatColor.WHITE, team.color, "the name itself stays white, as in the tab list");
            assertTrue(team.name.startsWith(NametagManager.TEAM_PREFIX), team.name);
        }
    }

    @Test
    void aSidebarBoardMadeLaterGetsEveryoneAlreadyOnline() {
        nametags.refresh(player(OWNER, "Lenni"));
        nametags.refresh(player(MEMBER, "Steve"));

        FakeBoard late = board();
        nametags.applyAllTo(late.proxy());

        assertEquals("§c§lOWNER §r", late.teamOf("Lenni").prefix);
        assertEquals("", late.teamOf("Steve").prefix, "a player without a rank still gets a plain team");
    }

    @Test
    void aRankChangeUpdatesTheSameTeamInPlace() {
        Player owner = player(OWNER, "Lenni");
        nametags.refresh(owner);
        String team = main.teamOf("Lenni").name;
        int writes = main.teamOf("Lenni").prefixWrites;

        nametags.refresh(owner);
        assertEquals(writes, main.teamOf("Lenni").prefixWrites, "nothing is sent when nothing changed");

        ranks.put(OWNER, "&6[Admin] ");
        nametags.refresh(owner);
        assertEquals("§6[Admin] ", main.teamOf("Lenni").prefix);
        assertEquals(team, main.teamOf("Lenni").name);
    }

    @Test
    void leavingRemovesTheTeamFromEveryBoard() {
        FakeBoard sidebar = board();
        Player owner = player(OWNER, "Lenni");
        nametags.refresh(owner);

        nametags.remove(owner);

        assertNull(main.teamOf("Lenni"));
        assertNull(sidebar.teamOf("Lenni"));
        assertTrue(main.ourTeams().isEmpty());
    }

    @Test
    void anotherPluginsTeamOnTheMainBoardIsLeftAlone() {
        FakeBoard sidebar = board();
        FakeTeam minigame = main.register("redteam");
        minigame.entries.add("Lenni");

        nametags.refresh(player(OWNER, "Lenni"));

        assertEquals("redteam", main.teamOf("Lenni").name, "not taken out of the other plugin's team");
        assertEquals("§c§lOWNER §r", sidebar.teamOf("Lenni").prefix, "the plugin's own boards still show the rank");

        config.set("NAMETAGS.OVERRIDE-OTHER-TEAMS", true);
        nametags.refresh(player(OWNER, "Lenni"));
        assertTrue(main.teamOf("Lenni").name.startsWith(NametagManager.TEAM_PREFIX));
    }

    @Test
    void hiddenPlayersNeverShowTheirRealRank() {
        Player owner = player(OWNER, "Lenni");
        nametags.refresh(owner);
        assertNotNull(main.teamOf("Lenni"));

        hidden.add(OWNER);
        nametags.refresh(owner);
        assertNull(main.teamOf("Lenni"), "the Hide feature owns a hidden player's name tag");
    }

    @Test
    void switchedOffOrHandledByAnotherPluginMeansNoTeams() {
        config.set("NAMETAGS.ENABLED", false);
        nametags.refresh(player(OWNER, "Lenni"));
        assertTrue(main.ourTeams().isEmpty());

        config.set("NAMETAGS.ENABLED", true);
        otherPlugin = true;
        nametags.refresh(player(OWNER, "Lenni"));
        assertTrue(main.ourTeams().isEmpty(), "TAB or NametagEdit keep control of name tags");
    }

    @Test
    void startingCleansLeftoversAndShutdownRemovesEveryTeam() {
        FakeTeam leftover = main.register(NametagManager.TEAM_PREFIX + "deadbeef000000000000000000000000");
        leftover.entries.add("OldPlayer");
        FakeTeam vanilla = main.register("vanilla");

        nametags.start(List.of(player(OWNER, "Lenni")));
        assertNull(main.teams.get(leftover.name), "a team left over from a crash is removed");
        assertNotNull(main.teamOf("Lenni"));

        FakeBoard sidebar = board();
        nametags.applyAllTo(sidebar.proxy());
        nametags.shutdown();
        assertTrue(main.ourTeams().isEmpty(), "the main scoreboard is saved with the world, so nothing is left in it");
        assertTrue(sidebar.ourTeams().isEmpty());
        assertNotNull(main.teams.get(vanilla.name), "other teams stay");
    }

    @Test
    void autoNameColourFollowsThePrefix() {
        config.set("NAMETAGS.NAME-COLOR", "AUTO");
        nametags.refresh(player(OWNER, "Lenni"));
        assertEquals(ChatColor.RED, main.teamOf("Lenni").color);

        config.set("NAMETAGS.NAME-COLOR", "GOLD");
        nametags.refresh(player(OWNER, "Lenni"));
        assertEquals(ChatColor.GOLD, main.teamOf("Lenni").color);
        assertEquals(ChatColor.WHITE, NametagManager.nameColor("nonsense", "§c"), "an unknown colour falls back to white");
        assertEquals(ChatColor.WHITE, NametagManager.nameColor("AUTO", "§lBOLD ONLY"));
    }

    @Test
    void theSuffixAndPlayerPlaceholderAreFilledIn() {
        config.set("NAMETAGS.PREFIX", "{prefix}&7| ");
        config.set("NAMETAGS.SUFFIX", " &8({player})");
        nametags.refresh(player(OWNER, "Lenni"));
        assertEquals("§c§lOWNER §r§7| ", main.teamOf("Lenni").prefix);
        assertEquals(" §8(Lenni)", main.teamOf("Lenni").suffix);
    }

    // ------------------------------------------------------------------ fakes

    private FakeBoard board() {
        FakeBoard board = new FakeBoard();
        privateBoards.add(board.proxy());
        return board;
    }

    private static Player player(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(NametagManagerTest.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "isOnline" -> true;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player[" + name + "]";
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }

    /** A scoreboard that keeps its teams in memory. */
    private static final class FakeBoard {
        final Map<String, FakeTeam> teams = new LinkedHashMap<>();
        private Scoreboard proxy;

        Scoreboard proxy() {
            if (proxy == null) {
                proxy = (Scoreboard) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Scoreboard.class},
                        (self, method, args) -> switch (method.getName()) {
                            case "getTeam" -> teams.containsKey((String) args[0]) ? teams.get((String) args[0]).proxy() : null;
                            case "registerNewTeam" -> register((String) args[0]).proxy();
                            case "getTeams" -> {
                                Set<Team> all = new HashSet<>();
                                teams.values().forEach(team -> all.add(team.proxy()));
                                yield all;
                            }
                            case "getEntryTeam" -> {
                                FakeTeam team = teamOf((String) args[0]);
                                yield team == null ? null : team.proxy();
                            }
                            case "hashCode" -> System.identityHashCode(self);
                            case "equals" -> self == args[0];
                            default -> null;
                        });
            }
            return proxy;
        }

        FakeTeam register(String name) {
            if (teams.containsKey(name)) {
                throw new IllegalArgumentException("Team name '" + name + "' is already in use");
            }
            FakeTeam team = new FakeTeam(this, name);
            teams.put(name, team);
            return team;
        }

        FakeTeam teamOf(String entry) {
            for (FakeTeam team : teams.values()) {
                if (team.entries.contains(entry)) {
                    return team;
                }
            }
            return null;
        }

        List<FakeTeam> ourTeams() {
            return teams.values().stream().filter(team -> team.name.startsWith(NametagManager.TEAM_PREFIX)).toList();
        }
    }

    /** A team with the parts a name tag uses; an entry is in one team per board, as in Minecraft. */
    private static final class FakeTeam {
        final FakeBoard board;
        final String name;
        final Set<String> entries = new HashSet<>();
        String prefix = "";
        String suffix = "";
        ChatColor color = ChatColor.RESET;
        int prefixWrites;
        private Team proxy;

        FakeTeam(FakeBoard board, String name) {
            this.board = board;
            this.name = name;
        }

        Team proxy() {
            if (proxy == null) {
                proxy = (Team) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Team.class},
                        (self, method, args) -> switch (method.getName()) {
                            case "getName" -> name;
                            case "getPrefix" -> prefix;
                            case "setPrefix" -> {
                                prefix = (String) args[0];
                                prefixWrites++;
                                yield null;
                            }
                            case "getSuffix" -> suffix;
                            case "setSuffix" -> {
                                suffix = (String) args[0];
                                yield null;
                            }
                            case "getColor" -> color;
                            case "setColor" -> {
                                color = (ChatColor) args[0];
                                yield null;
                            }
                            case "addEntry" -> {
                                FakeTeam current = board.teamOf((String) args[0]);
                                if (current != null) {
                                    current.entries.remove(args[0]);
                                }
                                entries.add((String) args[0]);
                                yield null;
                            }
                            case "removeEntry" -> entries.remove(args[0]);
                            case "hasEntry" -> entries.contains(args[0]);
                            case "getEntries" -> Set.copyOf(entries);
                            case "getSize" -> entries.size();
                            case "unregister" -> {
                                board.teams.remove(name);
                                yield null;
                            }
                            case "hashCode" -> System.identityHashCode(self);
                            case "equals" -> self == args[0];
                            default -> method.getReturnType() == boolean.class ? false : null;
                        });
            }
            return proxy;
        }
    }

    @Test
    void playersWhoseUuidsStartAlikeStillGetTheirOwnTeams() {
        assertFalse(NametagManager.teamName(OWNER).equals(NametagManager.teamName(MEMBER)));
        nametags.refresh(player(OWNER, "Lenni"));
        nametags.refresh(player(MEMBER, "Steve"));
        assertEquals("§c§lOWNER §r", main.teamOf("Lenni").prefix);
        assertEquals("", main.teamOf("Steve").prefix);
    }

    @Test
    void theFakesBehaveLikeMinecraftTeams() {
        FakeBoard board = new FakeBoard();
        Team a = board.proxy().registerNewTeam("a");
        Team b = board.proxy().registerNewTeam("b");
        a.addEntry("x");
        b.addEntry("x");
        assertFalse(a.hasEntry("x"), "an entry moves to the team it was last added to");
        assertTrue(b.hasEntry("x"));
    }
}
