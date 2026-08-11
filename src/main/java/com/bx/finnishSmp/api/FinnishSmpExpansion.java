package com.bx.finnishSmp.api;

import com.bx.finnishSmp.FinnishSmp;
import org.jetbrains.annotations.NotNull;

public class FinnishSmpExpansion extends EconomyExpansion {

    public FinnishSmpExpansion(FinnishSmp plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "finnishsmp";
    }
}
