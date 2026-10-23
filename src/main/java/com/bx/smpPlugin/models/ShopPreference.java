// SmpPlugin by LowBacon
package com.bx.smpPlugin.models;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ShopPreference(
        UUID playerId,
        Set<String> favorites,
        Map<Integer, QuickBuyEntry> quickBuyEntries
) {

    public ShopPreference(UUID playerId, Set<String> favorites) {
        this(playerId, favorites, Map.of());
    }

    public ShopPreference {
        favorites = favorites == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(favorites));
        quickBuyEntries = quickBuyEntries == null ? Map.of() : Map.copyOf(new HashMap<>(quickBuyEntries));
    }

    public ShopPreference withFavorite(String favoriteId, boolean favorite) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(favorites);
        if (favorite) {
            updated.add(favoriteId);
        } else {
            updated.remove(favoriteId);
        }
        return new ShopPreference(playerId, updated, quickBuyEntries);
    }

    public ShopPreference withQuickBuy(int slot, QuickBuyEntry entry) {
        Map<Integer, QuickBuyEntry> updated = new HashMap<>(quickBuyEntries);
        if (entry == null || entry.isEmpty()) {
            updated.remove(slot);
        } else {
            updated.put(slot, entry);
        }
        return new ShopPreference(playerId, favorites, updated);
    }

    public ShopPreference withoutQuickBuy(int slot) {
        Map<Integer, QuickBuyEntry> updated = new HashMap<>(quickBuyEntries);
        updated.remove(slot);
        return new ShopPreference(playerId, favorites, updated);
    }
}
