// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.core.V3Module;
import com.bx.smpPlugin.extras.FunCommands;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryTest {

    // Removed in 4.3 (and /hug in 3.1): no command, alias or permission may bring them back.
    private static final List<String> REMOVED = List.of(
            "wave", "hug", "findplayer", "fp", "chatgame", "chatgames", "poll", "rps", "rockpaperscissors");
    private static final List<String> REMOVED_PERMISSIONS = List.of(
            "smpplugin.findplayer", "smpplugin.poll.vote", "smpplugin.staff.poll", "smpplugin.staff.chatgame");

    private static YamlConfiguration pluginYml;
    private static Map<String, Map<String, Object>> commands;

    @BeforeAll
    static void load() {
        pluginYml = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        commands = new LinkedHashMap<>();
        ConfigurationSection section = pluginYml.getConfigurationSection("commands");
        for (String name : section.getKeys(false)) {
            ConfigurationSection details = section.getConfigurationSection(name);
            commands.put(name, details == null ? Map.of() : details.getValues(false));
        }
    }

    @Test
    void everyCommandHasAHelpCategoryAndNothingExtra() {
        for (String name : commands.keySet()) {
            assertTrue(CommandCatalog.categoryOf(name).isPresent(), "no /help category for /" + name);
        }
        for (String name : CommandCatalog.catalogued()) {
            assertTrue(commands.containsKey(name), "/help lists /" + name + " but plugin.yml has no such command");
        }
        assertEquals(commands.size(), CommandCatalog.entries(commands).size());
    }

    @Test
    void removedCommandsAreGoneEverywhere() {
        Set<String> names = new HashSet<>(commands.keySet());
        for (Map<String, Object> details : commands.values()) {
            names.addAll(CommandCatalog.aliases(details.get("aliases")));
        }
        for (String removed : REMOVED) {
            assertFalse(names.contains(removed), "/" + removed + " is still a command or alias");
            assertFalse(CommandCatalog.catalogued().contains(removed), "/" + removed + " is still in /help");
            assertFalse(V3Module.COMMANDS.contains(removed), "/" + removed + " is still registered by V3Module");
        }
        assertFalse(FunCommands.EMOTES.contains("wave"));
        ConfigurationSection permissions = pluginYml.getConfigurationSection("permissions");
        for (String node : REMOVED_PERMISSIONS) {
            assertFalse(permissions.contains(node, true), node + " is still declared");
        }
    }

    @Test
    void removedFeatureClassesAreDeleted() {
        for (String file : List.of("engagement/Polls.java", "engagement/ChatGames.java", "commands/FindPlayerCommand.java")) {
            assertFalse(Files.exists(Path.of("src/main/java/com/bx/smpPlugin", file)), file + " still exists");
        }
    }

    @Test
    void aliasesNeverCollide() {
        Map<String, String> owner = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> command : commands.entrySet()) {
            for (String alias : CommandCatalog.aliases(command.getValue().get("aliases"))) {
                assertFalse(commands.containsKey(alias), "alias /" + alias + " of /" + command.getKey()
                        + " is also a command name");
                String previous = owner.put(alias, command.getKey());
                assertTrue(previous == null, "alias /" + alias + " belongs to both /" + previous + " and /" + command.getKey());
            }
        }
    }

    @Test
    void everyDeclaredCommandIsWiredToAnExecutor() throws IOException {
        Set<String> registered = new HashSet<>(V3Module.COMMANDS);
        Pattern call = Pattern.compile("(?:setExecutor|register|registerCommand|getCommand)\\(\\s*\"([a-z]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = call.matcher(Files.readString(file));
                while (matcher.find()) {
                    registered.add(matcher.group(1));
                }
            }
        }
        List<String> unwired = new ArrayList<>();
        for (String name : commands.keySet()) {
            if (!registered.contains(name)) {
                unwired.add(name);
            }
        }
        assertTrue(unwired.isEmpty(), "declared in plugin.yml but never given an executor: " + unwired);
    }

    @Test
    void everyCommandHasADescriptionAndUsage() {
        for (CommandCatalog.Entry entry : CommandCatalog.entries(commands)) {
            assertFalse(entry.description().isBlank(), "/" + entry.name() + " has no description");
            assertTrue(entry.usage().startsWith("/"), "/" + entry.name() + " usage does not start with /: " + entry.usage());
        }
    }

    @Test
    void aliasesResolveToTheirCommand() {
        List<CommandCatalog.Entry> entries = CommandCatalog.entries(commands);
        assertEquals("balance", CommandCatalog.find(entries, "bal").orElseThrow().name());
        assertEquals("msg", CommandCatalog.find(entries, "/w").orElseThrow().name());
        assertEquals("guide", CommandCatalog.find(entries, "tutorial").orElseThrow().name());
        assertTrue(CommandCatalog.find(entries, "fp").isEmpty());
    }

    @Test
    void helpPagesAreStable() {
        assertEquals(1, HelpCommand.pageCount(0));
        assertEquals(1, HelpCommand.pageCount(8));
        assertEquals(2, HelpCommand.pageCount(9));
        assertEquals(2, HelpCommand.clampPage(99, 9));
        assertEquals(1, HelpCommand.clampPage(-3, 9));
        assertEquals(List.of(9), HelpCommand.page(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), 2));
        assertEquals(1, HelpCommand.parsePage("abc"));
    }

    @Test
    void categoriesParseByKeyAndCommonWords() {
        assertEquals(CommandCatalog.Category.TRAVEL, CommandCatalog.Category.parse("travel").orElseThrow());
        assertEquals(CommandCatalog.Category.TRAVEL, CommandCatalog.Category.parse("HOMES").orElseThrow());
        assertEquals(CommandCatalog.Category.MARKET, CommandCatalog.Category.parse("shops").orElseThrow());
        assertTrue(CommandCatalog.Category.parse("nothing").isEmpty());
    }
}
