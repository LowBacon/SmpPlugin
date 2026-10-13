package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    @Test
    void acceptsValidFilesAndReportsBrokenOnes(@TempDir Path dataFolder) throws Exception {
        Files.writeString(dataFolder.resolve("config.yml"), "SETTINGS:\n  ENABLED: true\n");
        Files.writeString(dataFolder.resolve("worth.yml"), "ITEMS:\n  DIAMOND: 100\n    BROKEN: [\n");
        Files.createDirectories(dataFolder.resolve("languages"));
        Files.writeString(dataFolder.resolve("languages/en_US.yml"), "key: 'unterminated\n");

        List<ConfigValidator.Problem> problems = ConfigValidator.validateDirectory(dataFolder.toFile());

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(problem -> problem.file().toString().equals("worth.yml")));
        assertTrue(problems.stream().anyMatch(problem -> problem.file().endsWith(Path.of("languages", "en_US.yml"))));
    }

    @Test
    void ignoresRuntimeDataDirectories(@TempDir Path dataFolder) throws Exception {
        Files.createDirectories(dataFolder.resolve("data"));
        Files.writeString(dataFolder.resolve("data/broken.yml"), "a: [\n");

        assertTrue(ConfigValidator.validateDirectory(dataFolder.toFile()).isEmpty());
    }

    @Test
    void everyBundledDefaultConfigIsValidYaml() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                if (file.getFileName().toString().equals("plugin.yml")) {
                    continue; // contains Maven placeholders, validated by PermissionRegistryTest
                }
                assertTrue(ConfigValidator.validateFile(file).isEmpty(), file + " is not valid YAML");
            }
        }
    }
}
