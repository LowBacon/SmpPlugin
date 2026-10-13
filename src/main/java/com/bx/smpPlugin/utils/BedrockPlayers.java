package com.bx.smpPlugin.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects a Bedrock connection without loading Floodgate or Geyser until one of them is installed.
 */
public final class BedrockPlayers {

    private static volatile boolean floodgateAbsent;
    private static volatile boolean geyserAbsent;
    private static volatile Method floodgateGetInstance;
    private static volatile Method floodgateIsPlayer;
    private static volatile Method geyserApi;
    private static volatile Method geyserConnection;

    private BedrockPlayers() {
    }

    public static boolean isBedrock(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        return isFloodgate(uuid) || isGeyser(uuid);
    }

    private static boolean isFloodgate(UUID uuid) {
        if (uuid == null || floodgateAbsent) {
            return false;
        }
        try {
            if (floodgateGetInstance == null || floodgateIsPlayer == null) {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                floodgateGetInstance = api.getMethod("getInstance");
                floodgateIsPlayer = api.getMethod("isFloodgatePlayer", UUID.class);
            }
            Object instance = floodgateGetInstance.invoke(null);
            if (instance == null) {
                return false;
            }
            Object result = floodgateIsPlayer.invoke(instance, uuid);
            return result instanceof Boolean bedrock && bedrock;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError absent) {
            floodgateAbsent = true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isGeyser(UUID uuid) {
        if (uuid == null || geyserAbsent) {
            return false;
        }
        try {
            if (geyserApi == null || geyserConnection == null) {
                Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = apiClass.getMethod("api");
                geyserConnection = apiClass.getMethod("connectionByUuid", UUID.class);
            }
            Object api = geyserApi.invoke(null);
            if (api == null) {
                return false;
            }
            return geyserConnection.invoke(api, uuid) != null;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError absent) {
            geyserAbsent = true;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
