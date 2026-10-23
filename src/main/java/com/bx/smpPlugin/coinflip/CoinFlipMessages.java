// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CoinFlipMessages {

    static final Map<String, String> DEFAULTS = defaults();

    private final Map<String, String> messages;

    private CoinFlipMessages(Map<String, String> messages) {
        this.messages = messages;
    }

    public static CoinFlipMessages from(ConfigurationSection file) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULTS);
        if (file != null) {
            for (String key : DEFAULTS.keySet()) {
                if (file.isString(key)) {
                    merged.put(key, file.getString(key));
                }
            }
        }
        return new CoinFlipMessages(Collections.unmodifiableMap(merged));
    }

    public String raw(String key, Map<String, String> placeholders) {
        String text = messages.getOrDefault(key, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return text;
    }

    public String chat(String key, Map<String, String> placeholders) {
        return messages.get("prefix") + raw(key, placeholders);
    }

    public boolean has(String key) {
        return messages.containsKey(key);
    }

    private static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("prefix", "&8[&5&lCoinFlip&8] &r");
        m.put("virtual-notice", "&7All stakes are in-game currency only and have no real-world value.");
        m.put("disabled", "&cCoinFlip is turned off on this server.");
        m.put("maintenance", "&eCoinFlip wagering is paused right now. Practice mode still works.");
        m.put("wagering-blocked", "&eCoinFlip wagering is paused because of a configuration problem. Staff have been told.");
        m.put("no-permission", "&cYou do not have permission to do that.");
        m.put("storage-unavailable", "&cCoinFlip storage is not available, so no currency was moved. Please try again later.");
        m.put("storage-busy", "&eCoinFlip is busy. Nothing was moved; please try again in a moment.");
        m.put("match-changed", "&eThat match changed while you were acting. Nothing was moved; please look again.");
        m.put("held-for-review", "&eMatch &f#{id} &ehit a storage problem. Your currency is safe and staff have been told; it will be settled after review.");
        m.put("loading", "&7Your CoinFlip data is still loading. Try again in a moment.");
        m.put("in-progress", "&7Your last CoinFlip action is still being processed.");
        m.put("cooldown", "&7Please wait {seconds}s before starting another match.");
        m.put("invalid-amount", "&cThat is not a valid amount ({reason}).");
        m.put("amount-empty", "enter an amount");
        m.put("amount-malformed", "use digits, for example 1500 or 2.5k");
        m.put("amount-negative", "amounts cannot be negative");
        m.put("amount-decimals", "use at most two decimals");
        m.put("amount-too-large", "that number is too large");
        m.put("amount-zero", "the amount must be above zero");
        m.put("whole-numbers-only", "&cStakes must be whole numbers on this server.");
        m.put("stake-below-min", "&cThe minimum stake is {min}.");
        m.put("stake-above-max", "&cThe maximum stake is {max}.");
        m.put("insufficient-funds", "&cThat needs {amount}, but your balance is {balance}.");
        m.put("active-limit", "&cYou already have {count} open match(es). Cancel or finish one first.");
        m.put("global-limit", "&eThe match board is full right now. Please try again soon.");
        m.put("daily-wager-cap", "&7You have reached today's staking limit of {cap}. It resets at midnight server time.");
        m.put("daily-loss-cap", "&7You have reached today's net-loss limit of {cap}. It resets at midnight server time.");
        m.put("daily-match-cap", "&7You have created today's maximum of {cap} matches. It resets at midnight server time.");
        m.put("playtime-required", "&7CoinFlip unlocks after {minutes} minutes of playtime.");
        m.put("self-excluded", "&7You paused CoinFlip wagering until {time}. Practice mode is still available.");
        m.put("match-created", "&aMatch &f#{id} &acreated: stake &f{stake}&a, you picked &f{side}&a. It expires in {expiry}.");
        m.put("match-joined", "&aYou joined match &f#{id}&a. Flipping...");
        m.put("challenge-sent", "&aChallenge &f#{id} &asent to &f{target} &afor &f{stake}&a. It expires in {expiry}.");
        m.put("challenge-received", "&d{creator} &7challenged you to a coin flip: &f{stake} &7each, pot &f{pot}&7, fee &f{fee}&7. They picked &f{side}&7. &8(#{id})");
        m.put("challenge-buttons", "&a&l[REVIEW] &r &c&l[DECLINE]");
        m.put("challenge-fallback", "&7Type &f/cf accept {id} &7to review or &f/cf deny {id} &7to decline.");
        m.put("challenges-disabled", "&cPrivate challenges are turned off on this server.");
        m.put("challenge-self", "&cYou cannot challenge yourself.");
        m.put("player-offline", "&c{player} is not online.");
        m.put("challenge-not-accepting", "&c{player} is not accepting challenges right now.");
        m.put("challenge-duplicate", "&cYou already have a pending challenge to {player}.");
        m.put("challenge-declined", "&e{target} declined your challenge &f#{id}&e. Your stake of &f{stake} &ewas returned.");
        m.put("challenge-declined-self", "&7You declined challenge &f#{id}&7.");
        m.put("not-your-challenge", "&cThat challenge is not for you.");
        m.put("match-not-found", "&cThere is no open match &f#{id}&c.");
        m.put("match-not-joinable", "&cMatch &f#{id} &ccan no longer be joined ({state}).");
        m.put("match-own", "&cYou cannot join your own match.");
        m.put("match-expired", "&7Your match &f#{id} &7expired. Your stake of &f{stake} &7was returned.");
        m.put("match-cancelled", "&7Match &f#{id} &7was cancelled. Your stake of &f{stake} &7was returned.");
        m.put("cancel-not-allowed", "&cOnly a waiting match you created can be cancelled.");
        m.put("claim-delivered", "&aYou received &f{amount} &afrom CoinFlip match &f#{id} &8({kind})&a.");
        m.put("result-win", "&a&lYou won! &7The coin landed on &f{outcome}&7. You receive &f{payout} &8(net +{net})&7. &8#{id}");
        m.put("result-loss", "&7The coin landed on &f{outcome}&7. &f{winner} &7wins this one. Your stake was &f{stake}&7. &8#{id}");
        m.put("result-win-claim", "&a&lYou won &f#{id}&a! &7Your &f{payout} &7will be paid when you are next online.");
        m.put("title-win", "&a&lYou won");
        m.put("subtitle-win", "&f{outcome} &8- &a+{net}");
        m.put("title-result", "&f&l{outcome}");
        m.put("subtitle-result", "&7{winner} wins this round");
        m.put("broadcast", "&d{winner} &7won a CoinFlip pot of &f{pot} &7against &f{loser}&7.");
        m.put("practice-result", "&7Practice flip: &f{outcome}&7. You picked {side}: {verdict}&7. No currency moved.");
        m.put("practice-right", "&aright");
        m.put("practice-wrong", "&7not this time");
        m.put("session-reminder", "&7You have been playing CoinFlip for {minutes} minutes. Take a break whenever you like.");
        m.put("self-exclusion-set", "&7CoinFlip wagering is paused for you until {time}. This cannot be shortened.");
        m.put("settings-saved", "&aSettings saved.");
        m.put("achievement-unlocked", "&6Achievement unlocked: &f{name} &8- &7{description}");
        m.put("custom-amount-prompt", "&7Type the stake in chat (for example &f2500 &7or &f2.5k&7), or &fcancel&7.");
        m.put("custom-amount-cancelled", "&7Custom amount cancelled.");
        m.put("custom-amount-timeout", "&7Custom amount timed out.");
        m.put("search-prompt", "&7Type a player name or match ID in chat, or &fcancel&7.");
        m.put("rematch-sent", "&aRematch challenge &f#{id} &asent to &f{target}&a.");
        m.put("admin-reason-required", "&cA reason is required.");
        m.put("admin-cancel-locked", "&cMatch &f#{id} &cis {state}; only waiting matches can be cancelled. Use /coinflipadmin recover or resolve.");
        m.put("admin-resolved", "&aMatch &f#{id} &areconciled; it is now {state}.");
        m.put("admin-tx-not-found", "&cNo transaction with ID {id}.");
        m.put("admin-tx-not-unknown", "&cTransaction {id} is not unknown; only unknown transactions can be resolved.");
        m.put("admin-stats-reset", "&aCoinFlip statistics of {player} were reset.");
        m.put("help-1", "&7Pick heads or tails and a stake. Another player stakes the same amount on the other side.");
        m.put("help-2", "&7The server flips a fair coin: each side has exactly a 50% chance. Your pick, stake, rank and past results never change the odds.");
        m.put("help-3", "&7The winner receives both stakes minus the fee shown before you confirm (currently {fee}).");
        m.put("help-4", "&7Open matches expire after a while and your stake is returned automatically.");
        m.put("help-5", "&7Only in-game currency is used. It cannot be bought with or exchanged for real money.");
        m.put("help-6", "&7Practice mode flips without any currency. You can pause wagering for yourself in Settings.");
        return Collections.unmodifiableMap(m);
    }
}
