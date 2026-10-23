// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PunishmentRecord;
import com.bx.smpPlugin.models.PunishmentType;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.entity.Player;

final class PunishmentMessages {

    private final SmpPlugin plugin;

    PunishmentMessages(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    void applyRuntimeEffect(PunishmentType type, Player onlineTarget, PunishmentRecord record) {
        if (onlineTarget == null) {
            return;
        }

        switch (type) {
            case BAN, BLACKLIST, KICK -> onlineTarget.kickPlayer(ColorUtils.toComponent(build(record)));
            case WARN -> onlineTarget.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault(
                            "PUNISHMENTS.WARN-RECEIVED",
                            "&cᴡᴀʀɴɪɴɢ: &f{reason}",
                            "{reason}", record.getReason()
                    )
            ));
            case MUTE, VOICE_MUTE -> onlineTarget.sendMessage(ColorUtils.toComponent(build(record)));
        }
    }

    String build(PunishmentRecord record) {
        return plugin.getConfigManager().getMessageOrDefault(
                messagePath(record.getType()),
                defaultMessage(record.getType()),
                placeholders(record)
        );
    }

    static String messagePath(PunishmentType type) {
        return switch (type) {
            case BAN -> "PUNISHMENTS.BAN";
            case KICK -> "PUNISHMENTS.KICK";
            case MUTE -> "PUNISHMENTS.MUTE";
            case VOICE_MUTE -> "PUNISHMENTS.VOICE-MUTE";
            case BLACKLIST -> "PUNISHMENTS.BLACKLIST";
            case WARN -> "PUNISHMENTS.WARN-RECEIVED";
        };
    }

    static String defaultMessage(PunishmentType type) {
        return switch (type) {
            case BAN -> "&c&lyou have been banned!\n&8&m----------------------------\n&7reason: &f%reason%\n&7expires: &f%nicest_expiration%\n&7banned by: &f%issuer%\n&8&m----------------------------\n&7appeal at: &fdiscord.example.space";
            case KICK -> "&c&lyou have been kicked!\n&8&m----------------------------\n&7reason: &f%reason%\n&7kicked by: &f%issuer%\n&8&m----------------------------\n&7you may reconnect";
            case MUTE -> "&c&lyou have been muted!\n&8&m----------------------------\n&7reason: &f%reason%\n&7expires: &f%nicest_expiration%\n&7muted by: &f%issuer%\n&8&m----------------------------\n&7you cannot speak in chat";
            case VOICE_MUTE -> "&c&lyou have been voice muted!\n&8&m----------------------------\n&7reason: &f%reason%\n&7expires: &f%nicest_expiration%\n&7muted by: &f%issuer%\n&8&m----------------------------\n&7nobody can hear you in voice chat";
            case BLACKLIST -> "&4&lyou have been blacklisted!\n&8&m----------------------------\n&7reason: &f%reason%\n&7blacklisted by: &f%issuer%\n&8&m----------------------------\n&4you cannot join the server";
            case WARN -> "&cwarning: &f{reason}";
        };
    }

    private String[] placeholders(PunishmentRecord record) {
        String expires = formatExpires(record);
        String issuer = formatIssuer(record);
        return new String[]{
                "%reason%", record.getReason(),
                "%nicest_expiration%", expires,
                "%issuer%", issuer,
                "{reason}", record.getReason(),
                "{expires}", expires,
                "{issuer}", issuer
        };
    }

    static String formatExpires(PunishmentRecord record) {
        if (record.getExpiresAt() == null) {
            return "Never";
        }
        long remainingSeconds = Math.max(0L, (record.getExpiresAt() - System.currentTimeMillis()) / 1000L);
        return NumberUtils.formatCountdown(remainingSeconds);
    }

    private static String formatIssuer(PunishmentRecord record) {
        String issuer = record.getIssuerNameSnapshot();
        return issuer == null || issuer.isBlank() ? "unknown" : issuer;
    }
}
