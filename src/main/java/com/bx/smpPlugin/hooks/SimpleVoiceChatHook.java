package com.bx.smpPlugin.hooks;

import com.bx.smpPlugin.SmpPlugin;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

import java.util.UUID;

/**
 * Drops microphone audio from voice-muted players.
 *
 * <p>Simple Voice Chat is optional, so nothing may load this class before the "voicechat" plugin is
 * known to be installed: without the API on the classpath it fails to load.
 */
public class SimpleVoiceChatHook implements VoicechatPlugin {

    private final SmpPlugin plugin;

    public SimpleVoiceChatHook(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns true when Simple Voice Chat accepted the registration. */
    public static boolean register(SmpPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            return false;
        }
        service.registerPlugin(new SimpleVoiceChatHook(plugin));
        return true;
    }

    @Override
    public String getPluginId() {
        return "smpplugin";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    /** Runs on Simple Voice Chat's network thread, so it only reads the mute cache. */
    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!event.isCancellable() || event.isCancelled()) {
            return;
        }
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || connection.getPlayer() == null) {
            return;
        }
        UUID speaker = connection.getPlayer().getUuid();
        if (plugin.getVoiceMuteManager().isVoiceMuted(speaker)) {
            event.cancel();
            plugin.getVoiceMuteManager().remindMuted(speaker);
        }
    }
}
