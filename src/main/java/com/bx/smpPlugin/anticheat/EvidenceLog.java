package com.bx.smpPlugin.anticheat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bounded record of every flag: per-player history in memory, a global index for evidence-ID lookups,
 * and an optional append-only daily file written off the main thread.
 */
public final class EvidenceLog {

    /** One recorded flag. */
    public record Flag(
            String id,
            UUID playerId,
            String playerName,
            CheckType check,
            String detail,
            double violationLevel,
            int pingMillis,
            String world,
            double x,
            double y,
            double z,
            long timeMillis,
            String action
    ) {
        String toJson() {
            return "{\"id\":\"" + id + "\",\"time\":\"" + Instant.ofEpochMilli(timeMillis)
                    + "\",\"player\":\"" + escape(playerName) + "\",\"uuid\":\"" + playerId
                    + "\",\"check\":\"" + check.name() + "\",\"detail\":\"" + escape(detail)
                    + "\",\"vl\":" + String.format(Locale.ROOT, "%.2f", violationLevel)
                    + ",\"ping\":" + pingMillis + ",\"world\":\"" + escape(world)
                    + "\",\"x\":" + String.format(Locale.ROOT, "%.2f", x)
                    + ",\"y\":" + String.format(Locale.ROOT, "%.2f", y)
                    + ",\"z\":" + String.format(Locale.ROOT, "%.2f", z)
                    + ",\"action\":\"" + escape(action) + "\"}";
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            StringBuilder escaped = new StringBuilder(value.length());
            for (char c : value.toCharArray()) {
                switch (c) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    default -> {
                        if (c < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) c));
                        } else {
                            escaped.append(c);
                        }
                    }
                }
            }
            return escaped.toString();
        }
    }

    private static final int GLOBAL_INDEX_SIZE = 5_000;
    private static final String FILE_PREFIX = "evidence-";
    private static final String FILE_SUFFIX = ".log";

    private final Path directory;
    private final Logger logger;
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() % 1_000_000L);
    private final Map<UUID, ArrayDeque<Flag>> perPlayer = new ConcurrentHashMap<>();
    private final Map<String, Flag> byId = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Flag> eldest) {
            return size() > GLOBAL_INDEX_SIZE;
        }
    });
    private final ThreadPoolExecutor writer;
    private volatile boolean fileLogging;
    private volatile int memoryPerPlayer;

    public EvidenceLog(Path directory, Logger logger, boolean fileLogging, int memoryPerPlayer) {
        this.directory = directory;
        this.logger = logger;
        this.fileLogging = fileLogging;
        this.memoryPerPlayer = memoryPerPlayer;
        this.writer = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10_000), runnable -> {
            Thread thread = new Thread(runnable, "SmpPlugin-AntiCheat-Evidence");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
        this.writer.allowCoreThreadTimeOut(true);
    }

    void configure(boolean fileLogging, int memoryPerPlayer) {
        this.fileLogging = fileLogging;
        this.memoryPerPlayer = memoryPerPlayer;
    }

    /** Short, unique-enough ID for staff to quote, e.g. {@code K3F9Q2}. */
    String nextId() {
        long value = (sequence.incrementAndGet() * 2_654_435_761L) & 0x7FFFFFFFL;
        String id = Long.toString(value, 36).toUpperCase(Locale.ROOT);
        return id.length() > 6 ? id.substring(id.length() - 6) : id;
    }

    void record(Flag flag) {
        ArrayDeque<Flag> history = perPlayer.computeIfAbsent(flag.playerId(), ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(flag);
            while (history.size() > memoryPerPlayer) {
                history.removeFirst();
            }
        }
        byId.put(flag.id(), flag);
        if (fileLogging) {
            String line = flag.toJson();
            LocalDate day = Instant.ofEpochMilli(flag.timeMillis()).atZone(ZoneId.systemDefault()).toLocalDate();
            writer.execute(() -> append(day, line));
        }
    }

    public List<Flag> history(UUID playerId) {
        ArrayDeque<Flag> history = perPlayer.get(playerId);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public Flag find(String id) {
        return id == null ? null : byId.get(id.trim().toUpperCase(Locale.ROOT));
    }

    void forget(UUID playerId) {
        perPlayer.remove(playerId);
    }

    /** Deletes this log's own daily files older than {@code retentionDays}. */
    void pruneOldFiles(int retentionDays) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String date = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
                try {
                    if (LocalDate.parse(date).isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                    }
                } catch (DateTimeParseException ignored) {
                    // Not one of ours.
                }
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not prune old anti-cheat evidence files", exception);
        }
    }

    void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void append(LocalDate day, String line) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(FILE_PREFIX + day + FILE_SUFFIX);
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(line);
                out.newLine();
            }
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not write anti-cheat evidence", exception);
        }
    }
}
