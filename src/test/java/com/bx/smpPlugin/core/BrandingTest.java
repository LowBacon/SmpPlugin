package com.bx.smpPlugin.core;

import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandingTest {

    @AfterEach
    void restoreDefaults() {
        Branding.load(null);
    }

    private static YamlConfiguration bundled(String name) throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                BrandingTest.class.getClassLoader().getResourceAsStream(name)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    /** Colour codes out, so the visible text can be compared. */
    private static String visible(String text) {
        return text.replaceAll("&#[0-9A-Fa-f]{6}|&[0-9a-fk-or]", "");
    }

    @Test
    void theBundledServerNameIsCustomSMP() throws Exception {
        YamlConfiguration config = bundled("config.yml");
        assertEquals("CustomSMP", config.getString("BRANDING.SERVER-NAME"));
        Branding.load(config);
        assertEquals("CustomSMP", Branding.name());
        assertEquals("CustomSMP", visible(Branding.title()));
        assertEquals("play.customsmp.example", Branding.address());
    }

    @Test
    void theScoreboardAndTabListUseTheBrand() throws Exception {
        YamlConfiguration scoreboard = bundled("scoreboard.yml");
        assertEquals(List.of("{server_title}"), scoreboard.getStringList("SCOREBOARD.TITLE"));
        assertTrue(scoreboard.getStringList("SCOREBOARD.LINES").stream().anyMatch(line -> line.contains("{server_name}")));
        YamlConfiguration config = bundled("config.yml");
        assertEquals("{server_name}", config.getString("TABLIST.ANIMATION.TEXT"));
        assertTrue(config.getStringList("TABLIST.HEADER").contains("%animated_title%"));
        assertTrue(config.getStringList("TABLIST.FOOTER").stream().anyMatch(line -> line.contains("{server_address}")));
    }

    @Test
    void renamingTheServerChangesOnlyWhatPlayersSee() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("BRANDING.SERVER-NAME", "Night Realm");
        Branding.load(config);
        assertEquals("Night Realm", Branding.name());
        assertEquals("Welcome to Night Realm!", Branding.apply("Welcome to {server_name}!"));
        assertEquals("Night Realm", visible(Branding.title()));
        // The plugin keeps its own name whatever the brand is.
        YamlConfiguration plugin = bundled("plugin.yml");
        assertEquals("SmpPlugin", plugin.getString("name"));
        assertTrue(plugin.getConfigurationSection("commands").contains("smpplugin"));
    }

    @Test
    void blankOrColouredNamesFallBackCleanly() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("BRANDING.SERVER-NAME", "   ");
        Branding.load(config);
        assertEquals("CustomSMP", Branding.name());
        config.set("BRANDING.SERVER-NAME", "&a&lGreen&#FF0000SMP");
        Branding.load(config);
        assertEquals("GreenSMP", Branding.name());
        config.set("BRANDING.SERVER-NAME", "&c");
        Branding.load(config);
        assertEquals("CustomSMP", Branding.name());
    }

    @Test
    void tokensAreReplacedEverywhereTheyAppear() {
        assertEquals("CustomSMP | play.customsmp.example | CustomSMP",
                Branding.apply("{server_name} | {server_address} | %smp_server_name%"));
        assertEquals("no tokens here", Branding.apply("no tokens here"));
        assertEquals(null, Branding.apply(null));
    }

    @Test
    void titleCasingKeepsTheBrandSpelling() {
        Branding.load(null);
        assertEquals("Server CustomSMP", ColorUtils.toTitleCaseSmart("server CustomSMP"));
        assertEquals("Server CustomSMP", ColorUtils.toTitleCaseSmart("server customsmp"));
        assertFalse(ColorUtils.toTitleCaseSmart("Customsmp").equals("Customsmp"));
    }
}
