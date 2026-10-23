// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RateLimiter {

    private final Map<UUID, Long> last = new HashMap<>();

    public long tryAcquire(UUID player, long nowMillis, long cooldownMillis) {
        Long previous = last.get(player);
        if (previous != null && nowMillis - previous < cooldownMillis) {
            return cooldownMillis - (nowMillis - previous);
        }
        last.put(player, nowMillis);
        return 0L;
    }

    public long remaining(UUID player, long nowMillis, long cooldownMillis) {
        Long previous = last.get(player);
        return previous == null ? 0L : Math.max(0L, cooldownMillis - (nowMillis - previous));
    }

    public void forget(UUID player) {
        last.remove(player);
    }
}
