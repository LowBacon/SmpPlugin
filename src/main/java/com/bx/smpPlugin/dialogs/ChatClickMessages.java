package com.bx.smpPlugin.dialogs;

import com.bx.smpPlugin.SmpPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Clickable chat that runs on the server instead of asking the client to confirm a command.
 *
 * <p>A {@code run_command} click in 1.21.6+ opens Minecraft's command-confirm dialog. That
 * screen is gone from this flow, so a callback is used. For TPA / TPAHERE the click only
 * accepts the request; the 5s stand-still warmup still runs afterwards. RTP teleports at once.
 */
public final class ChatClickMessages {

    private static final Duration CLICK_LIFETIME = Duration.ofMinutes(15);

    private ChatClickMessages() {
    }

    /**
     * Sends {@code legacy} as a clickable line. Returns false when this server cannot attach a
     * server-side click, so the caller can fall back to a Bungee {@code RUN_COMMAND}.
     */
    public static boolean sendInstantAction(
            SmpPlugin plugin,
            Player viewer,
            String legacy,
            String hoverLegacy,
            Consumer<Player> onClick
    ) {
        if (!DialogSupport.isAvailable()
                || plugin == null
                || viewer == null
                || onClick == null
                || !(viewer instanceof Audience audience)) {
            return false;
        }
        try {
            Component component = DialogText.of(legacy, viewer)
                    .clickEvent(ClickEvent.callback(clicked -> {
                        if (!(clicked instanceof Player player)) {
                            return;
                        }
                        plugin.getSpigotScheduler().runEntity(player, () -> {
                            if (player.isOnline()) {
                                onClick.accept(player);
                            }
                        });
                    }, options -> options
                            .uses(ClickCallback.UNLIMITED_USES)
                            .lifetime(CLICK_LIFETIME)));
            if (hoverLegacy != null && !hoverLegacy.isBlank()) {
                component = component.hoverEvent(HoverEvent.showText(DialogText.of(hoverLegacy, viewer)));
            }
            audience.sendMessage(component);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
