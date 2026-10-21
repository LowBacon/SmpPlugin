package com.bx.smpPlugin.coinflip.bukkit;

/** The small part of the module that item-building helpers need. */
public interface CoinFlipModuleView {

    /** Formats cents with the server's currency symbol, e.g. "$1,234". */
    String money(long cents);
}
