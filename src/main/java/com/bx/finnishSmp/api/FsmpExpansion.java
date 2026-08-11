package com.bx.finnishSmp.api;

import com.bx.finnishSmp.FinnishSmp;
import org.jetbrains.annotations.NotNull;

public class FsmpExpansion extends EconomyExpansion {

    public FsmpExpansion(FinnishSmp plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fsmp";
    }
}
