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

/**
 * Small player heads drawn inside dialog text and buttons.
 *
 * <p>The head carries the skin texture itself and no uuid or name. A head that only names a
 * player is drawn by the client as a static default skin (Steve, Alex, Sunny...), and a dialog
 * never redraws, so the wrong face would stay. Sending the texture avoids that.
 *
 * <p>Only skins the server already knows are used: the player's own profile when they are online,
 * or what the tab list cached. Nothing here waits on the network, so a list of heads costs a few
 * map lookups. A player with no known skin is drawn without a head.
 */
final class DialogPlayerHeads {

    private DialogPlayerHeads() {
    }

    /** The head for this player, or {@link Component#empty()} when no skin is known. */
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

    /** The head followed by a space and the label, or just the label when there is no head. */
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
