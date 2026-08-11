package com.bx.finnishSmp.listeners;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.models.PlayerData;
import com.bx.finnishSmp.models.ThreeChoice;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.RolePrefixResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class PlayerAdvancementDoneListener implements Listener {

    private final FinnishSmp plugin;

    public PlayerAdvancementDoneListener(FinnishSmp plugin) {
        this.plugin = plugin;
        disableVanillaAnnouncements();
    }

    private void disableVanillaAnnouncements() {
        for (World world : Bukkit.getWorlds()) {
            try {
                world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        try {
            event.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Advancement adv = event.getAdvancement();
        if (adv.getDisplay() == null) {
            return;
        }

        Player player = event.getPlayer();
        String title = adv.getDisplay().getTitle();
        if (title == null || title.isEmpty()) {
            return;
        }

        String titleColor = "&a";
        String frameText = "made the advancement";
        if (adv.getDisplay().getType() != null) {
            switch (adv.getDisplay().getType()) {
                case GOAL -> {
                    titleColor = "&6";
                    frameText = "reached the goal";
                }
                case CHALLENGE -> {
                    titleColor = "&5";
                    frameText = "completed the challenge";
                }
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
