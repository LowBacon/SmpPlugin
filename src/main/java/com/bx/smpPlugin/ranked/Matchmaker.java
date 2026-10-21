package com.bx.smpPlugin.ranked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Pairs waiting players by rating, free of any server call so it can be tested.
 *
 * <p>The longest-waiting player is paired first, with the closest-rated opponent both of them would
 * accept. A player's accepted gap starts at the base tolerance and widens the longer they wait, up
 * to a maximum, so nobody waits forever but close matches come first. A pair the anti-boost check
 * forbids (same IP, too many recent games together) is never formed.
 */
public final class Matchmaker {

    /** One waiting player. */
    public record Ticket(UUID player, int rating, long joinedAtMillis) {
    }

    public record Pair(Ticket first, Ticket second) {
    }

    public record Tolerance(int base, int growthPerTenSeconds, int max) {

        /** The widest rating gap this ticket accepts at {@code now}. */
        public int forTicket(Ticket ticket, long now) {
            long waitedTens = Math.max(0L, now - ticket.joinedAtMillis()) / 10_000L;
            long widened = base + waitedTens * (long) growthPerTenSeconds;
            return (int) Math.min(Math.max(base, max), Math.max(0L, widened));
        }
    }

    private Matchmaker() {
    }

    /**
     * The pairs to start now. Every player appears in at most one pair; players left over keep
     * waiting. {@code allowed} says whether two players may be matched at all.
     */
    public static List<Pair> pair(List<Ticket> waiting, Tolerance tolerance, long now, BiPredicate<UUID, UUID> allowed) {
        List<Ticket> queue = new ArrayList<>(waiting);
        queue.removeIf(Objects::isNull);
        queue.sort(Comparator.comparingLong(Ticket::joinedAtMillis));
        Set<UUID> taken = new HashSet<>();
        List<Pair> pairs = new ArrayList<>();
        for (Ticket ticket : queue) {
            if (taken.contains(ticket.player())) {
                continue;
            }
            Ticket best = null;
            int bestGap = Integer.MAX_VALUE;
            for (Ticket other : queue) {
                if (other == ticket || taken.contains(other.player()) || other.player().equals(ticket.player())) {
                    continue;
                }
                int gap = Math.abs(ticket.rating() - other.rating());
                int accepted = Math.min(tolerance.forTicket(ticket, now), tolerance.forTicket(other, now));
                if (gap > accepted || gap >= bestGap) {
                    continue;
                }
                if (allowed != null && !allowed.test(ticket.player(), other.player())) {
                    continue;
                }
                best = other;
                bestGap = gap;
            }
            if (best != null) {
                taken.add(ticket.player());
                taken.add(best.player());
                pairs.add(new Pair(ticket, best));
            }
        }
        return pairs;
    }
}
