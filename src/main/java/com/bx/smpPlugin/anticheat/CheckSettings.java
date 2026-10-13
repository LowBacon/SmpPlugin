package com.bx.smpPlugin.anticheat;

import java.util.Map;

/**
 * Thresholds for one check. Each flag adds to the player's violation level (VL), which decays over time.
 *
 * @param enabled        whether the check runs
 * @param alertAt        VL at which staff are alerted
 * @param actionAt       VL at which the check corrects the player (setback or cancelled action); 0 = never
 * @param kickAt         VL at which the player is kicked; 0 = never
 * @param banAt          VL at which the player is banned or queued for a ban (see ENFORCEMENT); 0 = never
 * @param decayPerSecond how much VL is forgiven per second
 * @param params         check-specific tuning values
 */
public record CheckSettings(
        boolean enabled,
        double alertAt,
        double actionAt,
        double kickAt,
        double banAt,
        double decayPerSecond,
        Map<String, Double> params
) {
    public CheckSettings {
        params = Map.copyOf(params);
    }

    public double param(String key, double fallback) {
        return params.getOrDefault(key, fallback);
    }

    public boolean shouldAct(double level) {
        return actionAt > 0D && level >= actionAt;
    }

    public boolean shouldKick(double level) {
        return kickAt > 0D && level >= kickAt;
    }

    public boolean shouldBan(double level) {
        return banAt > 0D && level >= banAt;
    }
}
