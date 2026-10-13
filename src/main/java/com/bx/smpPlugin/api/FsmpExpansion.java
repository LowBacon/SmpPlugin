package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import org.jetbrains.annotations.NotNull;

public class FsmpExpansion extends EconomyExpansion {

    public FsmpExpansion(SmpPlugin plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fsmp";
    }
}
