// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record CandyAccount(long balance, long earned, Set<Long> milestones) {

    public static final long MAX = 1_000_000_000L;
    public static final CandyAccount EMPTY = new CandyAccount(0L, 0L, Set.of());

    public CandyAccount {
        balance = Math.max(0L, Math.min(MAX, balance));
        earned = Math.max(0L, Math.min(MAX, earned));
        milestones = Set.copyOf(new TreeSet<>(milestones));
    }

    public static CandyAccount parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return EMPTY;
        }
        String[] parts = stored.split(";", -1);
        try {
            Set<Long> milestones = new TreeSet<>();
            if (parts.length > 2 && !parts[2].isBlank()) {
                for (String milestone : parts[2].split(",")) {
                    milestones.add(Long.parseLong(milestone.trim()));
                }
            }
            return new CandyAccount(Long.parseLong(parts[0]), parts.length > 1 ? Long.parseLong(parts[1]) : 0L, milestones);
        } catch (RuntimeException ignored) {
            return EMPTY;
        }
    }

    public String serialize() {
        StringBuilder out = new StringBuilder().append(balance).append(';').append(earned).append(';');
        boolean first = true;
        for (long milestone : new TreeSet<>(milestones)) {
            if (!first) {
                out.append(',');
            }
            out.append(milestone);
            first = false;
        }
        return out.toString();
    }

    public CandyAccount earn(long amount) {
        if (amount <= 0L) {
            return this;
        }
        return new CandyAccount(safeAdd(balance, amount), safeAdd(earned, amount), milestones);
    }

    public Optional<CandyAccount> spend(long amount) {
        if (amount < 0L || amount > balance) {
            return Optional.empty();
        }
        return Optional.of(new CandyAccount(balance - amount, earned, milestones));
    }

    public CandyAccount withBalance(long newBalance) {
        long clamped = Math.max(0L, Math.min(MAX, newBalance));
        return new CandyAccount(clamped, Math.max(earned, clamped), milestones);
    }

    public boolean hasMilestone(long threshold) {
        return milestones.contains(threshold);
    }

    public CandyAccount withMilestone(long threshold) {
        Set<Long> more = new TreeSet<>(milestones);
        more.add(threshold);
        return new CandyAccount(balance, earned, more);
    }

    private static long safeAdd(long a, long b) {
        long sum = a + b;
        return sum < a || sum > MAX ? MAX : sum;
    }
}
