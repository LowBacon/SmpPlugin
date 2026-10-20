package com.bx.smpPlugin.seasonal;

import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * One place that answers "is this event running?" for every seasonal event, and the /events switches.
 *
 * <p>Each event registers a {@link Source} that knows its own config file, feature switch and dates. The status
 * combines those with the staff switch kept by {@link EventControl}. A minute tick notices when an event starts or
 * stops (by date or by /events) and tells the server once.
 */
public final class SeasonalEvents {

    /** What an event needs to tell about itself. */
    public interface Source {
        /** ENABLED in the event's own config file. */
        boolean configEnabled();

        /** The plugin feature the event belongs to is on. */
        boolean featureEnabled();

        /** The season dates. */
        CalendarRules rules();

        /** Now in the event's time zone (moved to a preview date when staff set one). */
        ZonedDateTime now();

        /** The player command that opens the event, without the slash. */
        String command();
    }

    /** What happened to a /events change. */
    public enum Change {
        CHANGED,
        /** The switch was already in that position. */
        UNCHANGED,
        /** The event did not start (its config or the feature is broken), so it has nothing to switch. */
        NOT_AVAILABLE,
        /** data/events.yml could not be read at start, so it is not overwritten. */
        STORAGE_LOCKED
    }

    private final EventControl control;
    private final Logger logger;
    private final Consumer<String> broadcaster;
    private final Map<EventId, Source> sources = new EnumMap<>(EventId.class);
    private final Map<EventId, EventStatus> lastStatus = new EnumMap<>(EventId.class);

    public SeasonalEvents(EventControl control, Logger logger, Consumer<String> broadcaster) {
        this.control = control;
        this.logger = logger;
        this.broadcaster = broadcaster;
    }

    public synchronized void register(EventId id, Source source) {
        sources.put(id, source);
        lastStatus.put(id, compute(id, source));
    }

    public synchronized boolean isRegistered(EventId id) {
        return sources.containsKey(id);
    }

    public synchronized Optional<Source> source(EventId id) {
        return Optional.ofNullable(sources.get(id));
    }

    public EventStatus status(EventId id) {
        Source source;
        synchronized (this) {
            source = sources.get(id);
        }
        return source == null ? EventStatus.DISABLED : compute(id, source);
    }

    public boolean isActive(EventId id) {
        return status(id) == EventStatus.ACTIVE;
    }

    public EventMode mode(EventId id) {
        return control.mode(id);
    }

    public EventControl.Setting setting(EventId id) {
        return control.setting(id);
    }

    public boolean isStorageReadable() {
        return control.isReadable();
    }

    /** Seconds until the event's next season starts, or 0 while it runs or when it is switched off. */
    public long secondsUntilStart(EventId id) {
        Optional<Source> source = source(id);
        if (source.isEmpty() || status(id) != EventStatus.SCHEDULED && status(id) != EventStatus.COMPLETED) {
            return 0L;
        }
        return source.get().rules().secondsUntilSeason(source.get().now());
    }

    /**
     * Turns an event on, off or back to its dates. Saved at once and logged with who did it; players hear about it
     * straight away when the change starts or stops the event.
     */
    public Change setMode(EventId id, EventMode mode, String actor) {
        Source source;
        synchronized (this) {
            source = sources.get(id);
        }
        if (source == null) {
            return Change.NOT_AVAILABLE;
        }
        EventMode before = control.mode(id);
        if (before == mode) {
            return Change.UNCHANGED;
        }
        if (!control.set(id, mode, actor, System.currentTimeMillis())) {
            return Change.STORAGE_LOCKED;
        }
        logger.info("[Events] " + actor + " switched " + id.displayName() + " from " + before + " to " + mode
                + "; it is now " + status(id) + ".");
        tick();
        return Change.CHANGED;
    }

    /** Checks every event for a start or a stop and announces it. Runs every minute and after each switch. */
    public void tick() {
        List<Map.Entry<EventId, Source>> current;
        synchronized (this) {
            current = List.copyOf(sources.entrySet());
        }
        for (Map.Entry<EventId, Source> entry : current) {
            EventId id = entry.getKey();
            EventStatus now = compute(id, entry.getValue());
            EventStatus before;
            synchronized (this) {
                before = lastStatus.put(id, now);
            }
            if (before == null || before == now) {
                continue;
            }
            if (now == EventStatus.ACTIVE) {
                logger.info("[Events] " + id.displayName() + " started (" + before + " -> ACTIVE).");
                broadcaster.accept(Text.get("EVENTS.STARTED",
                        "&6&l{event} &7has started! &8» &e/{command}", "{event}", id.displayName(),
                        "{command}", entry.getValue().command()));
            } else if (before == EventStatus.ACTIVE) {
                logger.info("[Events] " + id.displayName() + " stopped (ACTIVE -> " + now + ").");
                broadcaster.accept(Text.get("EVENTS.ENDED",
                        "&7{event} has ended. Rewards you already earned are yours to keep.",
                        "{event}", id.displayName()));
            }
        }
    }

    private EventStatus compute(EventId id, Source source) {
        try {
            ZonedDateTime now = source.now();
            return EventStatus.of(source.configEnabled(), source.featureEnabled(), control.mode(id), source.rules(),
                    now.toLocalDate());
        } catch (RuntimeException broken) {
            // A broken config must never leave an event running with unknown rules.
            logger.warning("[Events] Could not work out the status of " + id.displayName() + ": " + broken);
            return EventStatus.DISABLED;
        }
    }
}
