package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Handles right-clicks on the configured SMP-entrance FancyNpcs NPC. Deliberately kept in its
 * own class, separate from {@link LobbyListener}: this class references FancyNpcs API classes
 * directly, and Bukkit resolves an @EventHandler method's parameter type when a Listener is
 * registered. If this code lived inside LobbyListener, registering LobbyListener on a server
 * that doesn't have FancyNpcs installed would throw NoClassDefFoundError and take the entire
 * lobby feature down with it. Keeping it separate means SmpPlugin only ever instantiates/
 * registers this class when FancyNpcs is actually present (see SmpPlugin#registerListeners).
 */
public final class LobbyNpcListener implements Listener {

    private final SmpPlugin plugin;

    public LobbyNpcListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        FileConfiguration lobby = plugin.getConfigManager().getLobby();
        if (!lobby.getBoolean("NPC.ENABLED", true)) {
            return;
        }

        Npc npc = event.getNpc();
        Player player = event.getPlayer();
        if (npc == null || player == null || npc.getData() == null) {
            return;
        }

        String configuredName = lobby.getString("NPC.FANCYNPCS-NAME", "");
        if (configuredName == null || configuredName.isBlank() || !configuredName.equals(npc.getData().getName())) {
            return;
        }

        if (!plugin.getLobbyManager().isInLobby(player)) {
            return;
        }

        if (plugin.getLobbyManager().isBlockedByCombat(player)) {
            player.sendMessage(ColorUtils.toComponent(plugin.getLobbyManager().getCombatBlockedMessage(), player));
            return;
        }

        long remainingCooldown = plugin.getLobbyManager().checkAndStartNpcCooldown(player);
        if (remainingCooldown > 0) {
            String message = lobby.getString("NPC.COOLDOWN-MESSAGE", "").replace("%seconds%", String.valueOf(remainingCooldown));
            player.sendMessage(ColorUtils.toComponent(message, player));
            SoundUtils.play(player, lobby.getString("NPC.COOLDOWN-SOUND", ""));
            return;
        }

        playInteractEffects(player, lobby);

        long delayTicks = Math.max(0, lobby.getLong("NPC.TELEPORT-DELAY-TICKS", 20));
        boolean showCountdown = lobby.getBoolean("NPC.SHOW-COUNTDOWN-ACTIONBAR", true) && delayTicks >= 20;
        if (showCountdown) {
            runCountdown(player, lobby, delayTicks);
        } else {
            scheduleTeleport(player, lobby, delayTicks);
        }
    }

    /** Ticks an actionbar "Teleporting in N..." countdown once per second for the delay period, then teleports. */
    private void runCountdown(Player player, FileConfiguration lobby, long delayTicks) {
        String template = lobby.getString("NPC.COUNTDOWN-ACTIONBAR", "");
        int totalSeconds = (int) (delayTicks / 20L);

        for (int second = totalSeconds; second >= 1; second--) {
            long tickOffset = (long) (totalSeconds - second) * 20L;
            int secondsRemaining = second;
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                if (player.isOnline()) {
                    PlayerSettingUtils.sendActionBar(plugin, player,
                            template.replace("%seconds%", String.valueOf(secondsRemaining)));
                }
            }, tickOffset);
        }

        scheduleTeleport(player, lobby, delayTicks);
    }

    private void scheduleTeleport(Player player, FileConfiguration lobby, long delayTicks) {
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                SoundUtils.play(player, lobby.getString("NPC.TELEPORT-SOUND", ""));
                plugin.getLobbyManager().sendToSurvival(player);
            }
        }, delayTicks);
    }

    private void playInteractEffects(Player player, FileConfiguration lobby) {
        if (lobby.getBoolean("NPC.PLAY-SOUND", true)) {
            SoundUtils.play(player, lobby.getString("NPC.SOUND", ""));
        }
        if (lobby.getBoolean("NPC.SHOW-TITLE", true)) {
            String title = lobby.getString("NPC.TITLE", "");
            String subtitle = lobby.getString("NPC.SUBTITLE", "");
            int fadeIn = lobby.getInt("NPC.TITLE-FADE-IN", 5);
            int stay = lobby.getInt("NPC.TITLE-STAY", 30);
            int fadeOut = lobby.getInt("NPC.TITLE-FADE-OUT", 10);
            player.sendTitle(ColorUtils.colorize(title, player), ColorUtils.colorize(subtitle, player), fadeIn, stay, fadeOut);
        }
    }
}
