package com.bx.smpPlugin.tasks;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OptimizationManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AFKLoungeTask implements Runnable {

    private final SmpPlugin plugin;
    private final Set<UUID> previouslyInLounge = new HashSet<>();

    public AFKLoungeTask(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getOptimizationManager() != null
                && !plugin.getOptimizationManager().shouldRun(OptimizationManager.OptimizedTask.SCOREBOARD)) {
            return;
        }

        if (!plugin.getAFKLoungeManager().isEnabled()) {
            return;
        }

        Set<UUID> currentlyInLounge = new HashSet<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (!plugin.getAFKLoungeManager().isPlayerInLounge(player)) {
                if (previouslyInLounge.contains(uuid)) {
                    plugin.getAFKLoungeManager().playerLeftLounge(uuid);
                    player.sendMessage(ColorUtils.toComponent(plugin.getAFKLoungeManager().getLeftBorderMessage()));
                    PlayerSettingUtils.clearActionBar(player);
                    if (plugin.getTablistManager() != null) {
                        plugin.getTablistManager().updateTablistName(player);
                    }
                }
                continue;
            }

            if (!previouslyInLounge.contains(uuid)) {
                plugin.getAFKLoungeManager().playerEnteredLounge(uuid);
                player.sendMessage(ColorUtils.toComponent(plugin.getAFKLoungeManager().getEnteredBorderMessage()));
                if (plugin.getTablistManager() != null) {
                    plugin.getTablistManager().updateTablistName(player);
                }
            }

            currentlyInLounge.add(uuid);

            if (plugin.getAFKLoungeManager().canReceiveShard(uuid)) {
                PlayerData data = plugin.getPlayerDataManager().get(player);
                if (data != null) {
                    int rewardAmount = plugin.getAFKLoungeManager().getShardRewardAmount();
                    data.addShards(rewardAmount);
                    plugin.getDatabaseManager().savePlayer(data);
                    plugin.getAFKLoungeManager().recordShardReward(uuid);
                    player.sendMessage(ColorUtils.toComponent(plugin.getAFKLoungeManager().getShardRewardMessage()));
                }
            }

            if (plugin.getAFKLoungeManager().isCooldownActionBarEnabled()) {
                long remaining = plugin.getAFKLoungeManager().getRemainingShardCooldownSeconds(uuid);
                String actionBarText = plugin.getAFKLoungeManager().getNextShardTimerMessage(remaining);
                PlayerSettingUtils.sendActionBar(plugin, player, ColorUtils.toComponent(actionBarText));
            }
        }

        previouslyInLounge.clear();
        previouslyInLounge.addAll(currentlyInLounge);
    }

    public static void start(SmpPlugin plugin) {
        BukkitTask task = plugin.getSpigotScheduler().runGlobalTimer(new AFKLoungeTask(plugin), 0L, 20L);
        if (task != null) {
            plugin.getLogger().info("AFKLoungeTask started.");
        }
    }
}
