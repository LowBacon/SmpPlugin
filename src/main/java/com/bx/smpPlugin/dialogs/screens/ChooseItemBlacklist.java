// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ChooseItemBlacklist {

    public static final String CONFIG_PATH = "QUICK-BUY.CHOOSE-ITEM.BLACKLIST";

    public static final Set<String> BUILT_IN_NAMES = Set.of(
            "BEDROCK",
            "BARRIER",
            "COMMAND_BLOCK",
            "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK",
            "COMMAND_BLOCK_MINECART",
            "STRUCTURE_BLOCK",
            "STRUCTURE_VOID",
            "JIGSAW",
            "LIGHT",
            "DEBUG_STICK",
            "KNOWLEDGE_BOOK",
            "TEST_BLOCK",
            "TEST_INSTANCE_BLOCK",
            "END_PORTAL_FRAME",
            "SPAWNER",
            "TRIAL_SPAWNER",
            "VAULT",
            "SPAWNER_EGG",
            "REINFORCED_DEEPSLATE",
            "BUDDING_AMETHYST",
            "FARMLAND",
            "DIRT_PATH",
            "CHORUS_PLANT",
            "PETRIFIED_OAK_SLAB",
            "FROGSPAWN",
            "SUSPICIOUS_SAND",
            "SUSPICIOUS_GRAVEL"
    );

    private ChooseItemBlacklist() {
    }

    public static boolean isBlacklisted(SmpPlugin plugin, Material material) {
        return isBlacklisted(material, configuredEntries(plugin));
    }

    public static boolean isBlacklisted(Material material, Collection<String> extraEntries) {
        if (material == null) {
            return true;
        }
        String name = material.name();
        if (isBuiltInBlacklistedName(name)) {
            return true;
        }
        if (extraEntries == null || extraEntries.isEmpty()) {
            return false;
        }
        for (String extra : extraEntries) {
            if (matchesEntry(name, extra)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuiltInBlacklistedName(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return false;
        }
        String name = materialName.trim().toUpperCase(Locale.ROOT);
        if (BUILT_IN_NAMES.contains(name)) {
            return true;
        }
        if (name.contains("COMMAND_BLOCK")) {
            return true;
        }
        if (name.startsWith("INFESTED_")) {
            return true;
        }
        return name.endsWith("_SPAWN_EGG") || name.equals("SPAWNER_EGG");
    }

    public static List<String> configuredEntries(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return List.of();
        }
        FileConfiguration shop = plugin.getConfigManager().getShop();
        List<String> raw = shop.getStringList(CONFIG_PATH);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>(raw.size());
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                entries.add(value.trim());
            }
        }
        return entries;
    }

    static boolean matchesEntry(String materialName, String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String pattern = entry.trim().toUpperCase(Locale.ROOT);
        if ("*".equals(pattern)) {
            return true;
        }
        if (!pattern.contains("*")) {
            return materialName.equals(pattern);
        }
        if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 1) {
            String needle = pattern.substring(1, pattern.length() - 1);
            return !needle.contains("*") && materialName.contains(needle);
        }
        if (pattern.startsWith("*") && !pattern.substring(1).contains("*")) {
            return materialName.endsWith(pattern.substring(1));
        }
        if (pattern.endsWith("*") && !pattern.substring(0, pattern.length() - 1).contains("*")) {
            return materialName.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return false;
    }
}
