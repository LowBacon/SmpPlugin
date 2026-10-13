package com.bx.smpPlugin.coinflip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player CoinFlip settings, stored as one short {@code key=value;...} string. Unknown keys are ignored
 * and missing keys fall back to defaults, so older or newer rows always load.
 *
 * @param selfExcludedUntilMillis while in the future, the player cannot stake currency (practice still works)
 */
public record CoinFlipPreferences(
        AnimationPreset animation,
        boolean sounds,
        boolean particles,
        boolean readableMode,
        boolean inviteNotifications,
        boolean announceMyWins,
        boolean publicProfile,
        boolean quickConfirm,
        CoinSide savedSide,
        long savedStakeCents,
        long selfExcludedUntilMillis,
        List<Long> recentStakes
) {

    public static final int MAX_RECENT = 5;

    public CoinFlipPreferences {
        recentStakes = List.copyOf(recentStakes);
    }

    public static CoinFlipPreferences defaults(AnimationPreset animation, boolean readable) {
        return new CoinFlipPreferences(animation, true, true, readable, true, true, true, false, null, 0L, 0L, List.of());
    }

    public boolean selfExcluded(long nowMillis) {
        return selfExcludedUntilMillis > nowMillis;
    }

    public CoinFlipPreferences withAnimation(AnimationPreset value) {
        return new CoinFlipPreferences(value, sounds, particles, readableMode, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withSounds(boolean value) {
        return new CoinFlipPreferences(animation, value, particles, readableMode, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withParticles(boolean value) {
        return new CoinFlipPreferences(animation, sounds, value, readableMode, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withReadableMode(boolean value) {
        return new CoinFlipPreferences(animation, sounds, particles, value, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withInviteNotifications(boolean value) {
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, value, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withAnnounceMyWins(boolean value) {
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, inviteNotifications, value,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withPublicProfile(boolean value) {
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, inviteNotifications, announceMyWins,
                value, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withQuickConfirm(boolean value) {
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, inviteNotifications, announceMyWins,
                publicProfile, value, savedSide, savedStakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withSaved(CoinSide side, long stakeCents) {
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, side, stakeCents, selfExcludedUntilMillis, recentStakes);
    }

    public CoinFlipPreferences withSelfExclusionUntil(long untilMillis) {
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, Math.max(selfExcludedUntilMillis, untilMillis),
                recentStakes);
    }

    public CoinFlipPreferences withRecentStake(long stakeCents) {
        List<Long> updated = new ArrayList<>(recentStakes);
        updated.remove(stakeCents);
        updated.addFirst(stakeCents);
        while (updated.size() > MAX_RECENT) {
            updated.removeLast();
        }
        return new CoinFlipPreferences(animation, sounds, particles, readableMode, inviteNotifications, announceMyWins,
                publicProfile, quickConfirm, savedSide, savedStakeCents, selfExcludedUntilMillis, updated);
    }

    public String encode() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("anim", animation.name());
        values.put("snd", bit(sounds));
        values.put("ptc", bit(particles));
        values.put("read", bit(readableMode));
        values.put("inv", bit(inviteNotifications));
        values.put("ann", bit(announceMyWins));
        values.put("pub", bit(publicProfile));
        values.put("quick", bit(quickConfirm));
        values.put("side", savedSide == null ? "" : savedSide.name());
        values.put("stake", Long.toString(savedStakeCents));
        values.put("excl", Long.toString(selfExcludedUntilMillis));
        values.put("recent", String.join(",", recentStakes.stream().map(String::valueOf).toList()));
        StringBuilder builder = new StringBuilder();
        values.forEach((key, value) -> builder.append(key).append('=').append(value).append(';'));
        return builder.toString();
    }

    public static CoinFlipPreferences decode(String encoded, CoinFlipPreferences fallback) {
        if (encoded == null || encoded.isBlank()) {
            return fallback;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : encoded.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) {
                values.put(part.substring(0, split), part.substring(split + 1));
            }
        }
        List<Long> recent = new ArrayList<>();
        for (String raw : values.getOrDefault("recent", "").split(",")) {
            long value = parseLong(raw, -1L);
            if (value > 0L && recent.size() < MAX_RECENT) {
                recent.add(value);
            }
        }
        return new CoinFlipPreferences(
                AnimationPreset.parse(values.get("anim")).orElse(fallback.animation()),
                flag(values.get("snd"), fallback.sounds()),
                flag(values.get("ptc"), fallback.particles()),
                flag(values.get("read"), fallback.readableMode()),
                flag(values.get("inv"), fallback.inviteNotifications()),
                flag(values.get("ann"), fallback.announceMyWins()),
                flag(values.get("pub"), fallback.publicProfile()),
                flag(values.get("quick"), fallback.quickConfirm()),
                CoinSide.parse(values.get("side")).orElse(null),
                Math.max(0L, parseLong(values.get("stake"), 0L)),
                Math.max(0L, parseLong(values.get("excl"), 0L)),
                recent);
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }

    private static boolean flag(String raw, boolean fallback) {
        if ("1".equals(raw)) {
            return true;
        }
        if ("0".equals(raw)) {
            return false;
        }
        return fallback;
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
