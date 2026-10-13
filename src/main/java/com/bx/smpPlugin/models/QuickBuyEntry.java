package com.bx.smpPlugin.models;

import org.bukkit.Material;

public record QuickBuyEntry(
        int slot,
        Material material,
        int buyAmount,
        String customItemData,
        boolean active
) {

    public QuickBuyEntry {
        material = material == null ? Material.AIR : material;
        buyAmount = Math.max(1, buyAmount);
        customItemData = customItemData == null ? "" : customItemData;
    }

    public QuickBuyEntry(int slot, Material material, int buyAmount) {
        this(slot, material, buyAmount, "", true);
    }

    public QuickBuyEntry withActive(boolean newActive) {
        return new QuickBuyEntry(slot, material, buyAmount, customItemData, newActive);
    }

    public QuickBuyEntry withAmount(int newAmount) {
        return new QuickBuyEntry(slot, material, Math.max(1, newAmount), customItemData, active);
    }

    public boolean isEmpty() {
        return material == Material.AIR;
    }

    public boolean hasEnchantments() {
        return !getEnchantments().isEmpty();
    }

    public java.util.Map<org.bukkit.enchantments.Enchantment, Integer> getEnchantments() {
        return deserializeEnchantments(customItemData);
    }

    public QuickBuyEntry withEnchantments(java.util.Map<org.bukkit.enchantments.Enchantment, Integer> enchants) {
        return new QuickBuyEntry(slot, material, buyAmount, serializeEnchantments(enchants), active);
    }

    public org.bukkit.inventory.ItemStack createItem(int amount) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, Math.max(1, amount));
        return applyTo(item);
    }

    public org.bukkit.inventory.ItemStack applyTo(org.bukkit.inventory.ItemStack item) {
        if (item == null) return null;
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> enchants = getEnchantments();
        if (enchants.isEmpty()) return item;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (item.getType() == Material.ENCHANTED_BOOK && meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
                for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchants.entrySet()) {
                    esm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                }
            } else {
                for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchants.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String serializeEnchantments(java.util.Map<org.bukkit.enchantments.Enchantment, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("ENCHANTS:");
        boolean first = true;
        for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            if (!first) {
                sb.append(";");
            }
            first = false;
            sb.append(entry.getKey().getKey().getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    public static java.util.Map<org.bukkit.enchantments.Enchantment, Integer> deserializeEnchantments(String customItemData) {
        if (customItemData == null || customItemData.isBlank()) {
            return java.util.Collections.emptyMap();
        }
        String data = customItemData.trim();
        if (!data.startsWith("ENCHANTS:")) {
            if (com.bx.smpPlugin.utils.ItemSerializationUtils.isByteSerialized(data) || data.length() > 50) {
                try {
                    org.bukkit.inventory.ItemStack item = com.bx.smpPlugin.utils.ItemSerializationUtils.deserialize(data);
                    if (item != null && !item.getEnchantments().isEmpty()) {
                        return item.getEnchantments();
                    }
                } catch (Throwable ignored) {}
            }
            return java.util.Collections.emptyMap();
        }

        String content = data.substring("ENCHANTS:".length());
        if (content.isBlank()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<org.bukkit.enchantments.Enchantment, Integer> result = new java.util.LinkedHashMap<>();
        for (String part : content.split(";")) {
            if (part.isBlank()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                kv = part.split(":", 2);
                if (kv.length != 2) continue;
            }
            String key = kv[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (key.startsWith("minecraft:")) {
                key = key.substring(10);
            }
            try {
                int level = Integer.parseInt(kv[1].trim());
                org.bukkit.enchantments.Enchantment ench = findEnchantment(key);
                if (ench != null && level > 0) {
                    result.put(ench, level);
                }
            } catch (NumberFormatException ignored) {}
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public static org.bukkit.enchantments.Enchantment findEnchantment(String key) {
        if (key == null || key.isBlank()) return null;
        String clean = key.trim().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
        try {
            if (org.bukkit.Bukkit.getServer() == null) return null;
            org.bukkit.NamespacedKey nsk = org.bukkit.NamespacedKey.minecraft(clean);
            org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByKey(nsk);
            if (ench != null) return ench;
            for (org.bukkit.enchantments.Enchantment e : org.bukkit.enchantments.Enchantment.values()) {
                if (e.getKey().getKey().equalsIgnoreCase(clean) || e.getName().equalsIgnoreCase(clean)) {
                    return e;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static QuickBuyEntry empty(int slot) {
        return new QuickBuyEntry(slot, Material.AIR, 1, "", false);
    }
}
