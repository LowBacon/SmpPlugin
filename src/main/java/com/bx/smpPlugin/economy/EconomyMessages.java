package com.bx.smpPlugin.economy;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.EconomyFailureReason;

/** Player-facing text for a failed money or shard change, from messages.yml ECONOMY.FAILURE. */
public final class EconomyMessages {

    private EconomyMessages() {
    }

    public static String failure(SmpPlugin plugin, EconomyFailureReason reason) {
        if (reason == null) {
            reason = EconomyFailureReason.NONE;
        }
        String fallback = switch (reason) {
            case INVALID_AMOUNT -> "&cThat is not a valid amount.";
            case PLAYER_NOT_FOUND -> "&cThat player was not found.";
            case NO_PLAYER_DATA -> "&cThat player's data could not be loaded. Try again in a moment.";
            case INSUFFICIENT_FUNDS -> "&cNot enough money.";
            case SAME_ACCOUNT -> "&cYou cannot pay yourself.";
            case BALANCE_LIMIT -> "&cThat would take the balance past the highest balance allowed.";
            case NONE -> "&cThe transaction could not be completed.";
        };
        return plugin.getConfigManager().getMessageOrDefault("ECONOMY.FAILURE." + reason.name().replace('_', '-'), fallback);
    }
}
