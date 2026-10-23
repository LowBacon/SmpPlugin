// SmpPlugin by LowBacon
package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import org.jetbrains.annotations.NotNull;

public class CustomSmpExpansion extends EconomyExpansion {

    public CustomSmpExpansion(SmpPlugin plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "customsmp";
    }
}
