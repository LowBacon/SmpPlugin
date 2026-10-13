package com.bx.smpPlugin.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildInfoTest {

    @Test
    void readsTheFlavorWrittenByMaven(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("smpplugin-build.properties"),
                "flavor=paper\nserver.api=io.papermc.paper:paper-api:26.3.build.157-beta\n");

        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
            BuildInfo info = BuildInfo.load(loader);
            assertEquals(BuildInfo.PAPER, info.flavor());
            assertEquals("io.papermc.paper:paper-api:26.3.build.157-beta", info.serverApi());
        }
    }

    @Test
    void missingOrUnfilteredFileIsUnknown(@TempDir Path dir) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
            assertEquals(BuildInfo.UNKNOWN, BuildInfo.load(loader).flavor());
        }
        Files.writeString(dir.resolve("smpplugin-build.properties"), "flavor=${smpplugin.flavor}\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
            assertEquals(BuildInfo.UNKNOWN, BuildInfo.load(loader).flavor());
        }
    }

    @Test
    void warnsOnlyWhenTheJarDoesNotMatchTheServerFamily() {
        BuildInfo paper = new BuildInfo(BuildInfo.PAPER, "paper-api");
        BuildInfo spigot = new BuildInfo(BuildInfo.SPIGOT, "spigot-api");

        assertNull(paper.mismatchWarning(ServerPlatform.PURPUR));
        assertNull(paper.mismatchWarning(ServerPlatform.PAPER));
        assertNotNull(paper.mismatchWarning(ServerPlatform.SPIGOT));
        assertNull(spigot.mismatchWarning(ServerPlatform.SPIGOT));
        assertNotNull(spigot.mismatchWarning(ServerPlatform.PURPUR));
    }

    @Test
    void theBuiltJarRecordsItsFlavor() {
        // Maven filters src/main/resources into target/classes, which is on the test class path.
        BuildInfo info = BuildInfo.load(getClass().getClassLoader());
        org.junit.jupiter.api.Assertions.assertNotEquals(BuildInfo.UNKNOWN, info.flavor(),
                "smpplugin-build.properties was not filtered by Maven");
    }
}
