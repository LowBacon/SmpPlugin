package com.bx.smpPlugin.api;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.utils.RolePrefixResolver;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

public class EconomyLeaderboardExpansion extends PlaceholderExpansion {

    private final SmpPlugin plugin;

    public EconomyLeaderboardExpansion(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "economylb";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SmpPlugin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (params.isBlank()) {
            return null;
        }

        String[] parts = params.split("_");
        if (parts.length < 3) {
            return null;
        }

        int positionIndex = -1;
        for (int i = 1; i < parts.length - 1; i++) {
            if (isPositiveInteger(parts[i])) {
                positionIndex = i;
                break;
            }
        }

        if (positionIndex < 1 || positionIndex >= parts.length - 1) {
            return null;
        }

        int position = Integer.parseInt(parts[positionIndex]);
        String typeKey = String.join("_", Arrays.copyOfRange(parts, 0, positionIndex));
        String outputKey = String.join("_", Arrays.copyOfRange(parts, positionIndex + 1, parts.length))
                .toLowerCase(Locale.US);

        LeaderboardManager.LeaderboardType type = plugin.getLeaderboardManager().parseType(typeKey).orElse(null);
        if (type == null) {
            return null;
        }

        LeaderboardManager.LeaderboardEntry entry = plugin.getLeaderboardManager().getEntryAt(type, position);
        String entryName = resolveEntryName(entry);
        String fullValue = entry == null ? "0" : plugin.getLeaderboardManager().formatValue(type, entry.playerData(), false, false);
        String shortValue = entry == null ? "0" : plugin.getLeaderboardManager().formatValue(type, entry.playerData(), true, false);

        return switch (outputKey) {
            case "name" -> entryName;
            case "value" -> fullValue;
            case "value_short", "value-short", "short" -> shortValue;
            case "rank" -> String.valueOf(position);
            case "display" -> "#" + position + " " + entryName + ": " + shortValue;
            default -> null;
        };
    }

    private String resolveEntryName(LeaderboardManager.LeaderboardEntry entry) {
        if (entry == null || entry.playerData() == null) {
            return "none";
        }

        String username = entry.playerData().getUsername();
        if (username == null || username.isBlank()) {
            return "unknown";
        }

        String role = RolePrefixResolver.resolve(Bukkit.getOfflinePlayer(entry.playerData().getUuid()));
        return role + username;
    }

    private boolean isPositiveInteger(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        for (int i = 0; i < input.length(); i++) {
            if (!Character.isDigit(input.charAt(i))) {
                return false;
            }
        }

        return !input.equals("0");
    }
}
