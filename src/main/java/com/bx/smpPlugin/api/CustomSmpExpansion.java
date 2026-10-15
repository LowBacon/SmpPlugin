package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion under the server's name, {@code %customsmp_<placeholder>%}. It serves
 * the same placeholders as {@code %economy_...%} and the older {@code %finnishsmp_...%}.
 */
public class CustomSmpExpansion extends EconomyExpansion {

    public CustomSmpExpansion(SmpPlugin plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "customsmp";
    }
}
