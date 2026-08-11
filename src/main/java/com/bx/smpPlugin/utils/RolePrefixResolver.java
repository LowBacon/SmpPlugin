package com.bx.smpPlugin.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/** Resolves the role prefix supplied by LuckPerms/EssentialsX through PAPI or Vault. */
public final class RolePrefixResolver {

    private static final String[] PLACEHOLDERS = {
            "%luckperms_prefix%",
            "%vault_prefix%",
            "%prefix%"
    };

    private RolePrefixResolver() {
    }

    public static String resolve(OfflinePlayer player) {
        if (player == null) {
            return "";
        }

        String luckPermsPrefix = resolveLuckPermsPrefix(player.getUniqueId());
        if (isUsable(luckPermsPrefix)) {
            return luckPermsPrefix;
        }

        if (ColorUtils.hasPAPI()) {
            try {
                for (String placeholder : PLACEHOLDERS) {
                    String prefix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
                    if (isUsable(prefix)) {
                        return prefix;
                    }
                }
            } catch (Exception ignored) {
                // Vault remains available as a fallback for online players.
            }
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer != null && Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> registration = Bukkit.getServicesManager()
                        .getRegistration(net.milkbowl.vault.chat.Chat.class);
                if (registration != null && registration.getProvider() != null) {
                    String prefix = registration.getProvider().getPlayerPrefix(onlinePlayer);
                    if (isUsable(prefix)) {
                        return prefix;
                    }
                }
            } catch (Exception ignored) {
                // A missing chat provider simply means no role prefix is available.
            }
        }
        return "";
    }

    private static String resolveLuckPermsPrefix(UUID playerId) {
        if (playerId == null || !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return "";
        }

        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, playerId);
            if (user == null) {
                return "";
            }

            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            return prefix instanceof String value ? value : "";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !value.startsWith("%");
    }
}
