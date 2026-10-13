package com.bx.smpPlugin.anticheat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiCheatConfigTest {

    private static AntiCheatConfig bundled() {
        return AntiCheatConfig.from(YamlConfiguration.loadConfiguration(new File("src/main/resources/anticheat.yml")));
    }

    @Test
    void bundledFileConfiguresEveryCheck() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File("src/main/resources/anticheat.yml"));
        for (CheckType check : CheckType.values()) {
            assertTrue(yaml.isConfigurationSection("CHECKS." + check.configKey()), check.configKey() + " missing");
        }
    }

    @Test
    void enforcementEscalatesAlertActKickBanAndBansNeedStaffReviewByDefault() {
        AntiCheatConfig config = bundled();
        assertTrue(config.enabled());
        assertTrue(config.kicksEnabled());
        assertEquals(AntiCheatConfig.BanMode.QUEUE, config.banMode(), "bans wait for staff approval by default");
        for (CheckType check : CheckType.values()) {
            CheckSettings settings = config.settings(check);
            assertTrue(settings.enabled(), check.name());
            assertTrue(settings.alertAt() > 0D, check.name());
            if (settings.actionAt() > 0D) {
                assertTrue(settings.actionAt() >= settings.alertAt(), check + " acts before staff are alerted");
            }
            if (settings.kickAt() > 0D && check != CheckType.BAD_CLIENT) {
                assertTrue(settings.kickAt() >= 10D, check + " kicks on too little evidence");
                assertTrue(settings.kickAt() > settings.alertAt() * 2D, check + " kicks before repeated alerts");
            }
            if (settings.banAt() > 0D) {
                assertTrue(settings.kickAt() > 0D && settings.banAt() >= settings.kickAt() * 2D,
                        check + " bans without first passing the kick level twice over");
            }
        }
        for (CheckType evidenceOnly : new CheckType[]{CheckType.XRAY, CheckType.AUTOCLICKER, CheckType.AUTO_TOTEM}) {
            CheckSettings settings = config.settings(evidenceOnly);
            assertFalse(settings.shouldAct(1_000_000D), evidenceOnly + " is evidence only");
            assertFalse(settings.shouldKick(1_000_000D), evidenceOnly + " is evidence only");
            assertFalse(settings.shouldBan(1_000_000D), evidenceOnly + " is evidence only");
        }
    }

    @Test
    void banModeParsesAndFallsBackToTheQueue() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ENFORCEMENT.BANS", "auto");
        assertEquals(AntiCheatConfig.BanMode.AUTO, AntiCheatConfig.from(yaml).banMode());
        yaml.set("ENFORCEMENT.BANS", "nonsense");
        assertEquals(AntiCheatConfig.BanMode.QUEUE, AntiCheatConfig.from(yaml).banMode());
        assertEquals(AntiCheatConfig.BanMode.QUEUE, AntiCheatConfig.from(null).banMode());
    }

    @Test
    void bundledValuesMatchTheBuiltInDefaults() {
        AntiCheatConfig config = bundled();
        for (CheckType check : CheckType.values()) {
            CheckSettings fromFile = config.settings(check);
            CheckSettings builtIn = AntiCheatConfig.DEFAULTS.get(check);
            assertEquals(builtIn.alertAt(), fromFile.alertAt(), check + " ALERT-AT");
            assertEquals(builtIn.actionAt(), fromFile.actionAt(), check + " ACTION-AT");
            assertEquals(builtIn.kickAt(), fromFile.kickAt(), check + " KICK-AT");
            assertEquals(builtIn.banAt(), fromFile.banAt(), check + " BAN-AT");
            assertEquals(builtIn.decayPerSecond(), fromFile.decayPerSecond(), 1e-12, check + " DECAY");
            builtIn.params().forEach((key, value) ->
                    assertEquals(value, fromFile.param(key, Double.NaN), 1e-12, check + " " + key));
        }
    }

    @Test
    void overridesApplyAndMissingFileFallsBackToDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("CHECKS.REACH.ENABLED", false);
        yaml.set("CHECKS.REACH.TOLERANCE", 0.5D);
        yaml.set("CHECKS.SPEED.KICK-AT", 20);
        yaml.set("BEDROCK.SKIP-CHECKS", java.util.List.of("fly"));
        AntiCheatConfig config = AntiCheatConfig.from(yaml);

        assertFalse(config.settings(CheckType.REACH).enabled());
        assertEquals(0.5D, config.settings(CheckType.REACH).param("TOLERANCE", 0D));
        assertEquals(400D, config.settings(CheckType.REACH).param("MAX-PING-MS", 0D), "untouched params keep defaults");
        assertTrue(config.settings(CheckType.SPEED).shouldKick(20D));
        assertTrue(config.skipForBedrock(CheckType.FLY));
        assertFalse(config.skipForBedrock(CheckType.SPEED));

        AntiCheatConfig empty = AntiCheatConfig.from(null);
        assertTrue(empty.enabled());
        assertEquals(AntiCheatConfig.DEFAULTS.get(CheckType.FLY), empty.settings(CheckType.FLY));
        assertTrue(empty.xrayOreFamilies().containsKey("DIAMOND"));
        assertTrue(empty.isXrayFiller("DEEPSLATE"));
    }

    @Test
    void checkNamesParseFromConfigAndCommandSpellings() {
        assertEquals(CheckType.FAST_BREAK, CheckType.fromInput("fast-break").orElseThrow());
        assertEquals(CheckType.WALL_HIT, CheckType.fromInput("Wall Hit").orElseThrow());
        assertTrue(CheckType.fromInput("teleport").isEmpty());
    }
}
