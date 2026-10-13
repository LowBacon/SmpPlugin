package com.bx.smpPlugin.platform;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformPolicyTest {

    @Test
    void parsesBukkitAndPlainVersionStrings() {
        assertEquals("26.3", MinecraftVersion.parse("26.3-R0.1-SNAPSHOT").toString());
        assertEquals("26.3", MinecraftVersion.parse("26.3").toString());
        assertEquals("1.21.10", MinecraftVersion.parse("1.21.10-R0.1-SNAPSHOT").toString());
        assertEquals("26.3", MinecraftVersion.parse("26.3.build.157-beta").toString());
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("snapshot"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse(" "));
    }

    @Test
    void comparesNumericallyAndIgnoresTrailingZeros() {
        assertTrue(MinecraftVersion.parse("26.3").compareTo(MinecraftVersion.parse("26.2")) > 0);
        assertTrue(MinecraftVersion.parse("26.10").compareTo(MinecraftVersion.parse("26.9")) > 0);
        assertTrue(MinecraftVersion.parse("1.21.11").compareTo(MinecraftVersion.parse("26.1")) < 0);
        assertEquals(MinecraftVersion.parse("26.3"), MinecraftVersion.parse("26.3.0"));
        assertEquals(MinecraftVersion.parse("26.3").hashCode(), MinecraftVersion.parse("26.3.0").hashCode());
    }

    @Test
    void detectsPlatformFromMarkerClasses() {
        assertEquals(ServerPlatform.FOLIA, ServerPlatform.detect(Set.of(ServerPlatform.FOLIA_CLASS, ServerPlatform.PAPER_CLASS)::contains, "Folia"));
        assertEquals(ServerPlatform.PURPUR, ServerPlatform.detect(Set.of(ServerPlatform.PURPUR_CLASS, ServerPlatform.PAPER_CLASS)::contains, "Purpur"));
        assertEquals(ServerPlatform.PAPER, ServerPlatform.detect(Set.of(ServerPlatform.PAPER_CLASS, ServerPlatform.SPIGOT_CLASS)::contains, "Paper"));
        assertEquals(ServerPlatform.SPIGOT, ServerPlatform.detect(Set.of(ServerPlatform.SPIGOT_CLASS)::contains, "CraftBukkit"));
        assertEquals(ServerPlatform.CRAFTBUKKIT, ServerPlatform.detect(Set.<String>of()::contains, "CraftBukkit"));
        assertEquals(ServerPlatform.UNKNOWN, ServerPlatform.detect(Set.<String>of()::contains, "Mystery"));
    }

    @Test
    void targetsPaperPurpurSpigotAndCraftBukkitOn263WithoutClaimingUnverifiedSupport() {
        MinecraftVersion v263 = MinecraftVersion.parse("26.3");
        for (ServerPlatform platform : Set.of(ServerPlatform.PAPER, ServerPlatform.PURPUR, ServerPlatform.SPIGOT, ServerPlatform.CRAFTBUKKIT)) {
            CompatibilityPolicy.Decision decision = CompatibilityPolicy.evaluate(platform, v263);
            assertTrue(decision.allowsEnable(), platform.name());
            // No runtime verification has been recorded yet, so nothing may report SUPPORTED.
            assertEquals(CompatibilityPolicy.Verdict.UNTESTED, decision.verdict(), platform.name());
        }
    }

    @Test
    void refusesFoliaAndOlderVersions() {
        CompatibilityPolicy.Decision folia = CompatibilityPolicy.evaluate(ServerPlatform.FOLIA, MinecraftVersion.parse("26.3"));
        assertFalse(folia.allowsEnable());

        CompatibilityPolicy.Decision old = CompatibilityPolicy.evaluate(ServerPlatform.PAPER, MinecraftVersion.parse("26.2"));
        assertFalse(old.allowsEnable());
        assertFalse(CompatibilityPolicy.evaluate(ServerPlatform.PAPER, MinecraftVersion.parse("1.21.10")).allowsEnable());
    }

    @Test
    void newerVersionsAndUnknownSoftwareRunButAreMarkedUntested() {
        CompatibilityPolicy.Decision newer = CompatibilityPolicy.evaluate(ServerPlatform.PAPER, MinecraftVersion.parse("26.4"));
        assertEquals(CompatibilityPolicy.Verdict.UNTESTED, newer.verdict());
        assertTrue(newer.allowsEnable());

        CompatibilityPolicy.Decision unknown = CompatibilityPolicy.evaluate(ServerPlatform.UNKNOWN, MinecraftVersion.parse("26.3"));
        assertEquals(CompatibilityPolicy.Verdict.UNTESTED, unknown.verdict());
    }

    @Test
    void minimumMatchesThePomApiVersion() throws Exception {
        String pom = java.nio.file.Files.readString(java.nio.file.Path.of("pom.xml"));
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<minecraft\\.api\\.version>([^<]+)</minecraft\\.api\\.version>").matcher(pom);
        assertTrue(matcher.find(), "pom.xml must define minecraft.api.version");
        assertEquals(MinecraftVersion.parse(matcher.group(1).trim()), CompatibilityPolicy.MINIMUM);
    }
}
