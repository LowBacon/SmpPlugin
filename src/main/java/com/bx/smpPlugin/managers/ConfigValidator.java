// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ConfigValidator {

    public record Problem(Path file, String message) {
        public String describe() {
            return file + ": " + message.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        }
    }

    private static final List<String> SKIPPED_DIRECTORIES = List.of("data", "backups", "server-wipe-backups", "logs");

    private ConfigValidator() {
    }

    public static List<Problem> validateDirectory(File dataFolder) {
        if (dataFolder == null || !dataFolder.isDirectory()) {
            return List.of();
        }

        Path root = dataFolder.toPath();
        List<Problem> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yml"))
                    .filter(path -> !isInSkippedDirectory(root, path))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> validateFile(path).ifPresent(message -> problems.add(new Problem(root.relativize(path), message))));
        } catch (IOException exception) {
            problems.add(new Problem(root, "could not list configuration files: " + exception.getMessage()));
        }
        return List.copyOf(problems);
    }

    public static java.util.Optional<String> validateFile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            new YamlConfiguration().loadFromString(content);
            return java.util.Optional.empty();
        } catch (InvalidConfigurationException exception) {
            return java.util.Optional.of(exception.getMessage() == null ? "invalid YAML" : exception.getMessage());
        } catch (IOException exception) {
            return java.util.Optional.of("could not read file: " + exception.getMessage());
        }
    }

    private static boolean isInSkippedDirectory(Path root, Path file) {
        Path relative = root.relativize(file);
        return relative.getNameCount() > 1 && SKIPPED_DIRECTORIES.contains(relative.getName(0).toString());
    }
}
