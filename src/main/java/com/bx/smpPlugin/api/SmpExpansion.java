package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion {@code %smp_<placeholder>%}, the short prefix added in 4.0. It serves
 * the same values as {@code %economy_...%}, plus the 4.0 names such as smp_balance and smp_rank.
 */
public class SmpExpansion extends EconomyExpansion {

    public SmpExpansion(SmpPlugin plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "smp";
    }
}
