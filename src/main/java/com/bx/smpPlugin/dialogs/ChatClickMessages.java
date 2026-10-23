// SmpPlugin by LowBacon
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

public final class ChatClickMessages {

    private static final Duration CLICK_LIFETIME = Duration.ofMinutes(15);

    private ChatClickMessages() {
    }

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
