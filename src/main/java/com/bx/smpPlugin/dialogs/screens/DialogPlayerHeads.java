// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.TablistManager;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

final class DialogPlayerHeads {

    private DialogPlayerHeads() {
    }

    static Component component(SmpPlugin plugin, UUID uuid) {
        String texture = textureOf(plugin, uuid);
        if (texture == null) {
            return Component.empty();
        }
        try {
            return Component.object(ObjectContents.playerHead()
                    .hat(true)
                    .profileProperty(PlayerHeadObjectContents.property("textures", texture))
                    .build());
        } catch (Throwable unsupported) {
            // A server whose Adventure predates head objects: draw the name alone.
            return Component.empty();
        }
    }

    static Component withLabel(SmpPlugin plugin, UUID uuid, Component label) {
        Component head = component(plugin, uuid);
        return Component.empty().equals(head)
                ? label
                : Component.empty().append(head).append(Component.space()).append(label);
    }

    private static String textureOf(SmpPlugin plugin, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            String worn = profileTexture(online);
            if (worn != null) {
                return worn;
            }
        }
        TablistManager tablist = plugin == null ? null : plugin.getTablistManager();
        if (tablist == null) {
            return null;
        }
        TablistManager.SkinTexture known = tablist.knownSkinTexture(uuid);
        return known != null && known.isValid() ? known.value() : null;
    }

    private static String profileTexture(Player player) {
        try {
            PlayerProfile profile = player.getPlayerProfile();
            for (ProfileProperty property : profile.getProperties()) {
                if ("textures".equalsIgnoreCase(property.getName())
                        && property.getValue() != null && !property.getValue().isBlank()) {
                    return property.getValue();
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the tab list's copy.
        }
        return null;
    }
}
