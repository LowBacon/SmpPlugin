package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.ThreeChoice;
import com.bx.smpPlugin.platform.ServerApiCompat;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.RolePrefixResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Optional;

public class PlayerAdvancementDoneListener implements Listener {

    private final SmpPlugin plugin;

    public PlayerAdvancementDoneListener(SmpPlugin plugin) {
        this.plugin = plugin;
        disableVanillaAnnouncements();
    }

    private void disableVanillaAnnouncements() {
        for (World world : Bukkit.getWorlds()) {
            disableVanillaAnnouncements(world);
        }
    }

    private void disableVanillaAnnouncements(World world) {
        // The rule is SHOW_ADVANCEMENT_MESSAGES on Spigot 26.3 but ANNOUNCE_ADVANCEMENTS on Paper/Purpur,
        // so it is looked up at runtime; referencing either constant directly crashes on the other server.
        Optional<GameRule<Boolean>> rule = ServerApiCompat.advancementMessagesRule();
        if (rule.isEmpty()) {
            return;
        }
        try {
            world.setGameRule(rule.get(), false);
        } catch (RuntimeException ignored) {
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        disableVanillaAnnouncements(event.getWorld());
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Optional<ServerApiCompat.AdvancementInfo> info = ServerApiCompat.describe(event.getAdvancement());
        if (info.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        String title = info.get().title();
        if (title == null || title.isEmpty()) {
            return;
        }

        String titleColor = "&a";
        String frameText = "made the advancement";
        switch (info.get().frame()) {
            case "GOAL" -> {
                titleColor = "&6";
                frameText = "reached the goal";
            }
            case "CHALLENGE" -> {
                titleColor = "&5";
                frameText = "completed the challenge";
            }
            default -> {
            }
        }

        String announcement = ColorUtils.colorize(" &7has " + frameText + " " + titleColor + "[" + title + "]");
        var finalAnnouncement = plugin.getHoverStatsManager().buildChatComponent(
                player,
                RolePrefixResolver.resolve(player),
                announcement,
                "%player%%message%"
        );

        plugin.getSpigotScheduler().forEachOnlinePlayer(p -> {
            if (shouldReceiveAdvancement(p, player)) {
                p.spigot().sendMessage(finalAnnouncement);
            }
        });
    }

    private boolean shouldReceiveAdvancement(Player receiver, Player victim) {
        PlayerData receiverData = plugin.getPlayerDataManager().get(receiver);
        if (receiverData == null) {
            return true;
        }
        ThreeChoice choice = receiverData.getAdvancementMessagesChoice();
        if (choice == ThreeChoice.OFF) {
            return false;
        }
        if (choice == ThreeChoice.FRIENDS_FOLLOWED) {
            return plugin.getFriendsManager() != null && plugin.getFriendsManager().isFollowing(receiver.getUniqueId(), victim.getUniqueId());
        }
        return true;
    }
}
