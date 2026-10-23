// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSettingsTest {

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                WorldSettingsTest.class.getClassLoader().getResourceAsStream("worlds.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }

    private static WorldSettings parse(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return WorldSettings.read(config);
    }

    @Test
    void theBundledFileHasTheThreeWorldsWithTheirRules() throws Exception {
        WorldSettings settings = WorldSettings.read(bundled());
        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        WorldRules lobby = settings.rules("lobby");
        WorldRules spawn = settings.rules("spawn");
        WorldRules ffa = settings.rules("ffa");
        assertEquals("lobby", lobby.worldName());
        assertEquals("spawn", spawn.worldName());
        assertEquals("ffa", ffa.worldName());
        for (WorldRules rules : java.util.List.of(lobby, spawn, ffa)) {
            assertEquals("VOID", rules.generator(), rules.key() + " starts empty to build in");
            assertEquals("VOID", WorldRules.defaults(rules.key()).generator());
        }

        assertFalse(lobby.sidebar(), "no sidebar in the lobby");
        assertTrue(spawn.sidebar());
        assertTrue(ffa.sidebar());
        assertFalse(lobby.pvp());
        assertFalse(spawn.pvp());
        assertTrue(ffa.pvp());
        assertTrue(lobby.commandAllowlist());
        assertFalse(spawn.commandAllowlist());
        assertFalse(ffa.commandAllowlist());
        assertTrue(lobby.allowedCommands().containsAll(Set.of("help", "list", "msg", "tell", "reply", "rules")));
        assertEquals(-1, lobby.protectedRadius());
        assertEquals(100, spawn.protectedRadius());
        assertEquals(Set.of("lobby", "spawn", "ffa"),
                Set.of(lobby.visibilityGroup(), spawn.visibilityGroup(), ffa.visibilityGroup()));
        assertEquals("spawn", settings.otherWorldsGroup());
    }

    @Test
    void worldsAreFoundByNameAndOthersShareTheDefaultGroup() throws Exception {
        WorldSettings settings = WorldSettings.read(bundled());
        assertEquals("lobby", settings.byWorldName("LOBBY").key());
        assertNull(settings.byWorldName("world"));
        assertEquals("lobby", settings.groupOf("lobby"));
        assertEquals("ffa", settings.groupOf("ffa"));
        assertEquals("spawn", settings.groupOf("world_nether"), "survival worlds see the spawn");
        assertEquals(settings.groupOf("world"), settings.groupOf("spawn"));
    }

    @Test
    void spawnProtectionCoversOnlyItsRadius() throws Exception {
        WorldRules spawn = WorldSettings.read(bundled()).rules("spawn");
        assertTrue(spawn.isProtectedAt(50, 50, 0, 0));
        assertTrue(spawn.isProtectedAt(100, 0, 0, 0), "the edge is inside");
        assertFalse(spawn.isProtectedAt(101, 0, 0, 0));
        assertFalse(spawn.isProtectedAt(80, 80, 0, 0), "the radius is round, not square");
        WorldRules lobby = WorldSettings.read(bundled()).rules("lobby");
        assertTrue(lobby.isProtectedAt(1_000_000, -1_000_000, 0, 0), "-1 is the whole world");
    }

    @Test
    void mistakesAreReportedAndNotApplied() throws Exception {
        WorldSettings settings = parse("""
                WORLDS:
                  LOBBY:
                    WORLD-NAME: 'my lobby!'
                    COMMAND-ALLOWLIST: true
                    ALLOWED-COMMANDS: []
                    DIFFICULTY: SCARY
                  SPAWN:
                    WORLD-NAME: ffa
                    GENERATOR: SPIRAL
                    DEATH-INVENTORY: SOMETIMES
                """);
        assertEquals(6, settings.problems().size(), settings.problems().toString());
        assertTrue(settings.problems().stream().anyMatch(problem -> problem.contains("also used by")));
    }

    @Test
    void aDisabledWorldIsNotManaged() throws Exception {
        WorldSettings settings = parse("""
                WORLDS:
                  FFA:
                    ENABLED: false
                """);
        assertTrue(settings.problems().isEmpty());
        assertNull(settings.byWorldName("ffa"));
        assertEquals("spawn", settings.groupOf("ffa"), "an unmanaged ffa world is just another world");
        WorldSettings off = parse("ENABLED: false");
        assertNull(off.byWorldName("lobby"));
    }

    @Test
    void lobbyAndFfaSeeOnlyThemselvesWhileTheSpawnSeesEveryone() throws Exception {
        WorldSettings settings = WorldSettings.read(bundled());
        assertTrue(settings.sees("lobby", "lobby"));
        assertFalse(settings.sees("lobby", "spawn"));
        assertFalse(settings.sees("lobby", "ffa"));
        assertFalse(settings.sees("lobby", "world"), "the lobby does not see survival players");
        assertTrue(settings.sees("ffa", "ffa"));
        assertFalse(settings.sees("ffa", "lobby"));
        assertFalse(settings.sees("ffa", "spawn"));
        assertFalse(settings.sees("spawn", "lobby"), "the lobby is hidden from the SMP too");
        assertTrue(settings.sees("spawn", "ffa"), "the spawn sees the rest of the server");
        assertTrue(settings.sees("spawn", "world_nether"));
        assertFalse(settings.sees("world", "lobby"));
        assertTrue(settings.sees("world", "ffa"), "survival worlds play like the spawn");
        assertTrue(settings.sees("world", "spawn"));
        assertTrue(settings.withEnabled(false).sees("lobby", "ffa"), "switched off, everyone sees everyone");
    }

    @Test
    void visibilityPoliciesAreReadAndChecked() throws Exception {
        WorldSettings settings = parse("""
                OTHER-WORLDS-VISIBILITY: same-group
                WORLDS:
                  SPAWN:
                    VISIBILITY: same_world
                  FFA:
                    VISIBILITY: EVERYONE
                """);
        assertEquals(WorldRules.SAME_WORLD, settings.otherWorldsVisibility());
        assertEquals(WorldRules.SAME_WORLD, settings.rules("spawn").visibility());
        WorldSettings open = parse("""
                WORLDS:
                  LOBBY:
                    HIDDEN-FROM-OTHER-WORLDS: false
                """);
        assertTrue(open.sees("spawn", "lobby"), "with the lobby not hidden, the spawn sees it again");
        assertFalse(open.sees("lobby", "spawn"));
        assertEquals(1, settings.problems().size(), settings.problems().toString());
        assertTrue(settings.problems().get(0).contains("FFA.VISIBILITY"));
    }

    @Test
    void theLobbyBypassUsesPermissionsAndRealGroups() throws Exception {
        WorldSettings.CommandBypass bypass = WorldSettings.read(bundled()).commandBypass();
        assertTrue(bypass.ops());
        assertTrue(bypass.permissions().contains("smpplugin.world.bypass.commands"));
        assertTrue(bypass.groups().containsAll(List.of("owner", "coowner", "dev", "manager")));
        WorldSettings.CommandBypass custom = parse("""
                COMMAND-BYPASS:
                  OPS: false
                  PERMISSIONS: []
                  GROUPS: [' CoOwner ', OWNER, owner, '']
                """).commandBypass();
        assertFalse(custom.ops());
        assertTrue(custom.permissions().isEmpty());
        assertEquals(List.of("coowner", "owner"), custom.groups());
    }

    @Test
    void switchingTheLobbyOffLeavesEverythingElseAsItWas() throws Exception {
        WorldSettings settings = WorldSettings.read(bundled());
        WorldSettings noLobby = settings.withWorld("lobby", false);
        assertNull(noLobby.byWorldName("lobby"), "the lobby world is no longer managed");
        assertEquals("spawn", noLobby.groupOf("lobby"), "it plays like any other world");
        assertEquals("spawn", noLobby.byWorldName("spawn").key());
        assertEquals(settings.rules("lobby").allowedCommands(), noLobby.rules("lobby").allowedCommands());
        assertTrue(noLobby.withWorld("lobby", true).rules("lobby").enabled());
        assertSame(settings, settings.withWorld("ffa", true), "nothing to change");
    }

    @Test
    void commandLabelsAreNormalised() {
        assertEquals("msg", WorldRules.normaliseCommand("/Essentials:MSG hello there"));
        assertEquals("help", WorldRules.normaliseCommand("help"));
        assertEquals("", WorldRules.normaliseCommand("/"));
        assertEquals("", WorldRules.normaliseCommand(null));
    }

    @Test
    void theAllowlistFollowsTheRealCommandNotTheTypedText() {
        Set<String> allowed = Set.of("msg", "help", "list");
        assertTrue(WorldCommandGate.isAllowed(allowed, "msg", "msg"));
        assertTrue(WorldCommandGate.isAllowed(allowed, "w", "msg"), "an alias of an allowed command");
        assertTrue(WorldCommandGate.isAllowed(allowed, "help", "help"), "minecraft:help is still help");
        assertFalse(WorldCommandGate.isAllowed(allowed, "home", "home"), "smpplugin:home is home");
        assertFalse(WorldCommandGate.isAllowed(allowed, "tpa", "tpa"));
        assertFalse(WorldCommandGate.isAllowed(allowed, "ah", "ah"));
        assertFalse(WorldCommandGate.isAllowed(allowed, "rtp", null), "an unknown command is refused");
    }
}
