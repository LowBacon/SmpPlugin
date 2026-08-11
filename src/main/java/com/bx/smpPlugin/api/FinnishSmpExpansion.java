package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy PlaceholderAPI expansion for backwards compatibility.
 * Use SmpPluginExpansion for new integrations.
 */
public class FinnishSmpExpansion extends EconomyExpansion {

    public FinnishSmpExpansion(SmpPlugin plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "finnishsmp";
    }
}
