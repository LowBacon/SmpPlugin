package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.hooks.SimpleVoiceChatHook;
import com.bx.smpPlugin.models.PunishmentRecord;
import com.bx.smpPlugin.models.PunishmentType;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Voice mutes from /vcmute, enforced through Simple Voice Chat.
 *
 * <p>The record lives in the punishment history like any other punishment. Microphone packets arrive
 * on Simple Voice Chat's own thread many times a second, so the gate answers from a cache of expiry
 * times instead of the database. The cache is filled on join and whenever a voice mute is issued,
 * lifted or deleted.
 */
public class VoiceMuteManager {

    private static final String VOICECHAT_PLUGIN = "voicechat";
    /** How often a muted player who keeps talking is reminded, in milliseconds. */
    private static final long REMINDER_INTERVAL_MS = 4_000L;

    private final SmpPlugin plugin;
    private final Map<UUID, Long> muteExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReminder = new ConcurrentHashMap<>();
    private boolean hooked;

    public VoiceMuteManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVoicechatInstalled() {
        return Bukkit.getPluginManager().getPlugin(VOICECHAT_PLUGIN) != null;
    }

    public boolean isHooked() {
        return hooked;
    }

    /** Hooks into Simple Voice Chat when it is installed. The hook class is only touched after the check. */
    public void registerHook() {
        if (hooked || !isVoicechatInstalled()) {
            return;
        }
        try {
            hooked = SimpleVoiceChatHook.register(plugin);
            if (hooked) {
                plugin.getLogger().info("Hooked into Simple Voice Chat: /vcmute is active.");
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Could not hook into Simple Voice Chat; /vcmute will only be recorded.", throwable);
        }
    }

    /** Safe off the main thread. */
    public boolean isVoiceMuted(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Long expiresAt = muteExpiry.get(uuid);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            muteExpiry.remove(uuid);
            return false;
        }
        return true;
    }

    /** Puts a fresh record (or its absence) into the cache the microphone gate reads. */
    public void apply(UUID uuid, PunishmentRecord record) {
        if (uuid == null) {
            return;
        }
        if (record == null || record.getType() != PunishmentType.VOICE_MUTE) {
            muteExpiry.remove(uuid);
            return;
        }
        muteExpiry.put(uuid, record.getExpiresAt() == null ? Long.MAX_VALUE : record.getExpiresAt());
    }

    /** Re-reads the player's active voice mute from the database, on the database thread. */
    public void refresh(UUID uuid) {
        if (uuid == null) {
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> apply(uuid,
                plugin.getPunishmentManager().getActiveRecord(uuid, PunishmentType.VOICE_MUTE).orElse(null)));
    }

    public void handleJoin(Player player) {
        if (player != null) {
            refresh(player.getUniqueId());
        }
    }

    public void handleQuit(UUID uuid) {
        if (uuid != null) {
            muteExpiry.remove(uuid);
            lastReminder.remove(uuid);
        }
    }

    /** Called from the microphone thread; the message itself goes out on the player's thread. */
    public void remindMuted(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastReminder.get(uuid);
        if (last != null && now - last < REMINDER_INTERVAL_MS) {
            return;
        }
        lastReminder.put(uuid, now);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        Long expiresAt = muteExpiry.get(uuid);
        String until = expiresAt == null || expiresAt == Long.MAX_VALUE
                ? "permanently"
                : "for " + NumberUtils.formatCountdown(Math.max(1L, (expiresAt - now) / 1000L));
        String text = plugin.getConfigManager().getMessageOrDefault(
                "PUNISHMENTS.VOICE-MUTE-REMINDER",
                "&cYou are voice muted {until}. Nobody can hear you.",
                "{until}", until);
        plugin.getSpigotScheduler().runEntity(player, () -> PlayerSettingUtils.sendActionBar(plugin, player, text));
    }
}
