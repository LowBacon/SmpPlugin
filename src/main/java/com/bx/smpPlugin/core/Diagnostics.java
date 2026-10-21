package com.bx.smpPlugin.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Light running numbers for /smp diagnostics: how long commands take, how quickly the database
 * answers, and the size of the plugin's caches and queues.
 *
 * <p>Recording a sample is a couple of atomic adds, cheap enough for every command. Nothing is
 * logged; the numbers are only read when staff ask for them. There is one entry per command name,
 * so the map cannot grow past the number of commands the plugin has.
 */
public final class Diagnostics {

    /** Counts and times for one command or operation. */
    public static final class Timer {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        void record(long nanos) {
            count.increment();
            totalNanos.add(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }

        public long count() {
            return count.sum();
        }

        public double averageMillis() {
            long calls = count.sum();
            return calls == 0 ? 0D : totalNanos.sum() / 1_000_000D / calls;
        }

        public double maxMillis() {
            return maxNanos.get() / 1_000_000D;
        }
    }

    public record TimerView(String name, long count, double averageMillis, double maxMillis) {
    }

    private static final int MAX_TIMERS = 512;
    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<Number>> GAUGES = new ConcurrentHashMap<>();
    private static volatile double lastDatabaseMillis = -1D;
    private static volatile double lastDatabaseQueueMillis = -1D;
    private static volatile long lastDatabaseProbeAt;
    private static volatile String lastDatabaseError;

    private Diagnostics() {
    }

    /** Adds one timing sample. */
    public static void record(String name, long nanos) {
        if (name == null) {
            return;
        }
        Timer timer = TIMERS.get(name);
        if (timer == null) {
            if (TIMERS.size() >= MAX_TIMERS) {
                return;
            }
            timer = TIMERS.computeIfAbsent(name, ignored -> new Timer());
        }
        timer.record(Math.max(0L, nanos));
    }

    /** The slowest entries by average time, slowest first. */
    public static List<TimerView> slowest(String prefix, int limit) {
        List<TimerView> views = new ArrayList<>();
        TIMERS.forEach((name, timer) -> {
            if (prefix == null || name.startsWith(prefix)) {
                views.add(new TimerView(name, timer.count(), timer.averageMillis(), timer.maxMillis()));
            }
        });
        views.sort(Comparator.comparingDouble(TimerView::averageMillis).reversed());
        return views.size() > limit ? views.subList(0, limit) : views;
    }

    public static long totalSamples(String prefix) {
        long total = 0;
        for (Map.Entry<String, Timer> entry : TIMERS.entrySet()) {
            if (prefix == null || entry.getKey().startsWith(prefix)) {
                total += entry.getValue().count();
            }
        }
        return total;
    }

    public static void reset() {
        TIMERS.clear();
    }

    /** A number worth watching, such as a cache size. Re-registering a name replaces it. */
    public static void gauge(String name, Supplier<Number> value) {
        if (name != null && value != null) {
            GAUGES.put(name, value);
        }
    }

    public static void removeGauge(String name) {
        GAUGES.remove(name);
    }

    /** Every gauge read now, sorted by name. A gauge that throws reads as -1. */
    public static Map<String, Long> gauges() {
        Map<String, Long> values = new LinkedHashMap<>();
        GAUGES.keySet().stream().sorted().forEach(name -> {
            long value;
            try {
                Number number = GAUGES.get(name).get();
                value = number == null ? -1L : number.longValue();
            } catch (RuntimeException exception) {
                value = -1L;
            }
            values.put(name, value);
        });
        return values;
    }

    public static void databaseProbe(double queueMillis, double queryMillis, String error) {
        lastDatabaseQueueMillis = queueMillis;
        lastDatabaseMillis = queryMillis;
        lastDatabaseError = error;
        lastDatabaseProbeAt = System.currentTimeMillis();
    }

    public static double lastDatabaseMillis() {
        return lastDatabaseMillis;
    }

    public static double lastDatabaseQueueMillis() {
        return lastDatabaseQueueMillis;
    }

    public static long lastDatabaseProbeAt() {
        return lastDatabaseProbeAt;
    }

    public static String lastDatabaseError() {
        return lastDatabaseError;
    }

    public static String millis(double value) {
        if (value < 0) {
            return "n/a";
        }
        return String.format(Locale.ROOT, value < 10 ? "%.2f ms" : "%.0f ms", value);
    }
}
