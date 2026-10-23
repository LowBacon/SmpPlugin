// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CoinFlipMetrics {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void attempt(String operation) {
        increment(operation + ".attempt");
    }

    public void success(String operation) {
        increment(operation + ".ok");
    }

    public void failure(String operation) {
        increment(operation + ".fail");
    }

    public void increment(String key) {
        counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long get(String key) {
        AtomicLong value = counters.get(key);
        return value == null ? 0L : value.get();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new TreeMap<>();
        counters.forEach((key, value) -> snapshot.put(key, value.get()));
        return snapshot;
    }
}
