package com.bx.smpPlugin.models;

import java.util.Arrays;
import java.util.Optional;

public enum SellCategory {
    CROPS("CROPS", "CROPS"),
    ORES("ORES", "ORES"),
    MOBS("MOBS", "MOBS"),
    NATURAL("NATURAL", "NATURAL"),
    ARMOR_AND_TOOLS("ARMOR_AND_TOOLS", "ARMOR_AND_TOOLS"),
    FISH("FISH", "FISH"),
    BOOK("BOOK", "BOOK"),
    POTIONS("POTIONS", "POTION"),
    BLOCKS("BLOCKS", "BLOCKS");

    private final String configKey;
    private final String worthSectionKey;

    SellCategory(String configKey, String worthSectionKey) {
        this.configKey = configKey;
        this.worthSectionKey = worthSectionKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getWorthSectionKey() {
        return worthSectionKey;
    }


    public static Optional<SellCategory> fromConfigKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(category -> category.configKey.equalsIgnoreCase(key)
                        || category.name().equalsIgnoreCase(key)
                        || category.worthSectionKey.equalsIgnoreCase(key))
                .findFirst();
    }
}
