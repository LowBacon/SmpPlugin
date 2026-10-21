package com.bx.smpPlugin.survival;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalRulesTest {

    private static SurvivalRules.Rules rules(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return SurvivalRules.Rules.read(config);
    }

    @Test
    void bannedItemsBlockOnlyTheListedActions() throws Exception {
        SurvivalRules.Rules rules = rules("""
                BANNED-ITEMS:
                  TNT_MINECART: [PLACE, CRAFT]
                  bedrock: []
                  NOT_A_REAL_ITEM: [USE]
                  END_CRYSTAL: [use, nonsense]
                """);
        assertTrue(rules.isBanned(Material.TNT_MINECART, SurvivalRules.Action.PLACE));
        assertTrue(rules.isBanned(Material.TNT_MINECART, SurvivalRules.Action.CRAFT));
        assertFalse(rules.isBanned(Material.TNT_MINECART, SurvivalRules.Action.USE));
        for (SurvivalRules.Action action : SurvivalRules.Action.values()) {
            assertTrue(rules.isBanned(Material.BEDROCK, action), "an empty list blocks everything");
        }
        assertTrue(rules.isBanned(Material.END_CRYSTAL, SurvivalRules.Action.USE));
        assertFalse(rules.isBanned(Material.END_CRYSTAL, SurvivalRules.Action.PLACE));
        assertEquals(3, rules.banned().size(), "unknown materials are skipped");
        assertFalse(rules.isBanned(Material.STONE, SurvivalRules.Action.PLACE));
    }

    @Test
    void recipesAndWorldsAreNormalised() throws Exception {
        SurvivalRules.Rules rules = rules("""
                DISABLED-RECIPES: [end_crystal, 'minecraft:TNT_MINECART', 'mypack:special']
                WORLDS: [World_Nether]
                """);
        assertTrue(rules.isRecipeDisabled("minecraft:end_crystal"));
        assertTrue(rules.isRecipeDisabled("minecraft:tnt_minecart"));
        assertTrue(rules.isRecipeDisabled("mypack:special"));
        assertFalse(rules.isRecipeDisabled("minecraft:stone"));
        assertTrue(rules.appliesIn("world_nether"));
        assertFalse(rules.appliesIn("world"));
    }

    @Test
    void turnedOffMeansNoRules() throws Exception {
        SurvivalRules.Rules rules = rules("""
                ENABLED: false
                BANNED-ITEMS:
                  BEDROCK: []
                """);
        assertTrue(rules.isEmpty());
        assertTrue(rules.appliesIn("any"), "no WORLDS means every world");
    }

    @Test
    void theBundledFileParses() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                SurvivalRulesTest.class.getClassLoader().getResourceAsStream("survival-rules.yml")), StandardCharsets.UTF_8)) {
            SurvivalRules.Rules rules = SurvivalRules.Rules.read(YamlConfiguration.loadConfiguration(reader));
            assertTrue(rules.isBanned(Material.DEBUG_STICK, SurvivalRules.Action.USE));
            assertTrue(rules.disabledRecipes().isEmpty());
        }
    }
}
