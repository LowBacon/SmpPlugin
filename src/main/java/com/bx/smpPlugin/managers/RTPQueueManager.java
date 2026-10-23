// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RTPQueueManager {

    private static final int DEFAULT_MATCH_SIZE = 2;
    private static final int MIN_MATCH_SIZE = 2;
    private static final int MAX_MATCH_SIZE = 32;
    private static final int DEFAULT_SPREAD_RADIUS = 16;
    private static final int MAX_SPREAD_RADIUS = 512;
    private static final long ACTIONBAR_REFRESH_TICKS = 20L;

    private final SmpPlugin plugin;
    private final LinkedHashSet<UUID> waiting = new LinkedHashSet<>();
    private final Set<UUID> zoneJoined = ConcurrentHashMap.newKeySet();
    private BukkitTask waitingActionBarTask;

    public RTPQueueManager(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        clear();
    }

    public void shutdown() {
        stopWaitingActionBarTask();
    }

    public boolean isEnabled() {
        FileConfiguration rtp = rtpConfig();
        return rtp != null
                && plugin.getRtpManager() != null
                && plugin.getRtpManager().isEnabled()
                && rtp.getBoolean("QUEUE.ENABLED", true);
    }

    public int getMatchSize() {
        FileConfiguration rtp = rtpConfig();
        int configured = rtp == null ? DEFAULT_MATCH_SIZE : rtp.getInt("QUEUE.MATCH-SIZE", DEFAULT_MATCH_SIZE);
        return Math.max(MIN_MATCH_SIZE, Math.min(MAX_MATCH_SIZE, configured));
    }

    public int getSpreadRadius() {
        FileConfiguration rtp = rtpConfig();
        int configured = rtp == null ? DEFAULT_SPREAD_RADIUS : rtp.getInt("QUEUE.SPREAD-RADIUS", DEFAULT_SPREAD_RADIUS);
        return Math.max(0, Math.min(MAX_SPREAD_RADIUS, configured));
    }

    public String getWorldName() {
        FileConfiguration rtp = rtpConfig();
        String configured = rtp == null ? null : rtp.getString("QUEUE.WORLD", "world");
        if (configured == null || configured.isBlank() || plugin.getRtpManager() == null) {
            return null;
        }
        return plugin.getRtpManager().resolveWorldSelector(configured);
    }

    public String getCuboidName() {
        FileConfiguration rtp = rtpConfig();
        String configured = rtp == null ? null : rtp.getString("QUEUE.CUBOID", "");
        return configured == null || configured.isBlank() ? null : configured;
    }

    public boolean isZoneActive() {
        return plugin != null
                && plugin.getCuboidManager() != null
                && getCuboidName() != null
                && isEnabled();
    }

    public boolean isInQueue(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        synchronized (waiting) {
            return waiting.contains(playerId);
        }
    }

    public int getQueuePosition(UUID playerId) {
        if (playerId == null) {
            return -1;
        }
        synchronized (waiting) {
            int position = 1;
            for (UUID queued : waiting) {
                if (queued.equals(playerId)) {
                    return position;
                }
                position++;
            }
        }
        return -1;
    }

    public int getQueueSize() {
        synchronized (waiting) {
            return waiting.size();
        }
    }

    public void clear() {
        List<UUID> queued;
        synchronized (waiting) {
            queued = new ArrayList<>(waiting);
            waiting.clear();
        }
        zoneJoined.clear();
        stopWaitingActionBarTask();
        for (UUID playerId : queued) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clearQueueActionBar(player);
            }
        }
    }

    public boolean join(Player player) {
        return join(player, true);
    }

    private boolean join(Player player, boolean notifyRefusal) {
        if (player == null) {
            return false;
        }

        if (!isEnabled()) {
            if (notifyRefusal) {
                sendMessage(player, "DISABLED", "&cThe RTP queue is currently disabled.");
            }
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(playerId)) {
            if (notifyRefusal) {
                player.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            }
            return false;
        }

        if (isInQueue(playerId)) {
            if (notifyRefusal) {
                sendMessage(
                        player,
                        "ALREADY-QUEUED",
                        "&cYou are already in the RTP queue at position #{position}.",
                        "{position}", String.valueOf(getQueuePosition(playerId))
                );
            }
            return false;
        }

        if (getSearchSettings() == null) {
            if (notifyRefusal) {
                sendMessage(player, "UNAVAILABLE", "&cThe RTP queue is not available right now.");
            }
            return false;
        }

        if (plugin.getRtpManager().hasActiveRtpFlow(playerId)
                || plugin.getTeleportManager().hasPendingType(playerId, "RTP")) {
            if (notifyRefusal) {
                sendMessage(player, "BUSY", "&cFinish the teleport you already started before joining the RTP queue.");
            }
            return false;
        }

        int needed = getMatchSize();
        int waitingCount;
        synchronized (waiting) {
            waiting.add(playerId);
            waitingCount = waiting.size();
        }

        broadcastToQueue(
                playerId,
                "PLAYER-JOINED",
                "&e[RTP Queue] &f{player} has joined the queue. &7({waiting}/{needed})",
                "{player}", player.getName(),
                "{waiting}", String.valueOf(waitingCount),
                "{needed}", String.valueOf(needed)
        );
        sendMessage(
                player,
                "JOINED",
                "&e[RTP Queue] &fYou have joined the queue. Waiting for another player... &7({waiting}/{needed})",
                "{waiting}", String.valueOf(waitingCount),
                "{needed}", String.valueOf(needed)
        );
        SoundUtils.play(player, plugin.getConfigManager().getSound("RTP.SEARCH-START"));

        startMatchIfReady();
        refreshWaitingActionBars();
        if (isInQueue(playerId)) {
            ensureWaitingActionBarTask();
        }
        return true;
    }

    public void tickZone(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String cuboidName = getCuboidName();
        if (cuboidName == null || !isEnabled() || plugin.getCuboidManager() == null) {
            zoneJoined.remove(playerId);
            return;
        }

        CuboidManager.Cuboid cuboid = plugin.getCuboidManager().getCuboid(cuboidName);
        if (cuboid == null) {
            zoneJoined.remove(playerId);
            return;
        }

        if (cuboid.contains(player.getLocation())) {
            if (!isInQueue(playerId) && join(player, false)) {
                zoneJoined.add(playerId);
            }
            return;
        }

        // A matched player has already been dropped from the waiting list and teleported out of
        // the region, so the marker goes without a second "you have left the queue".
        if (zoneJoined.remove(playerId) && isInQueue(playerId)) {
            leave(player);
        }
    }

    public boolean leave(Player player) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (!remove(playerId)) {
            sendMessage(player, "NOT-QUEUED", "&cYou are not in the RTP queue.");
            return false;
        }

        sendMessage(player, "LEFT", "&e[RTP Queue] &fYou have left the queue.");
        broadcastToQueue(
                playerId,
                "PLAYER-LEFT",
                "&e[RTP Queue] &f{player} has left the queue. &7({waiting}/{needed})",
                "{player}", player.getName(),
                "{waiting}", String.valueOf(getQueueSize()),
                "{needed}", String.valueOf(getMatchSize())
        );
        clearQueueActionBar(player);
        refreshWaitingActionBars();
        if (getQueueSize() == 0) {
            stopWaitingActionBarTask();
        }
        return true;
    }

    public void handleQuit(UUID playerId) {
        zoneJoined.remove(playerId);
        if (!remove(playerId)) {
            return;
        }
        refreshWaitingActionBars();
        if (getQueueSize() == 0) {
            stopWaitingActionBarTask();
        }
    }

    public boolean remove(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        synchronized (waiting) {
            return waiting.remove(playerId);
        }
    }

    List<UUID> pollMatchParty() {
        int needed = getMatchSize();
        synchronized (waiting) {
            if (waiting.size() < needed) {
                return List.of();
            }
            List<UUID> party = new ArrayList<>(needed);
            for (UUID queued : waiting) {
                party.add(queued);
                if (party.size() == needed) {
                    break;
                }
            }
            waiting.removeAll(party);
            return party;
        }
    }

    private void startMatchIfReady() {
        List<UUID> party = pollMatchParty();
        if (party.isEmpty()) {
            return;
        }

        List<Player> matched = onlinePlayers(party);
        if (matched.size() < MIN_MATCH_SIZE) {
            requeue(
                    matched,
                    "MATCH-ABANDONED",
                    "&c[RTP Queue] &fThe other players left before the match started, so you are back in the queue."
            );
            return;
        }

        RTPManager.SearchSettings settings = getSearchSettings();
        if (settings == null) {
            requeue(matched, "UNAVAILABLE", "&cThe RTP queue is not available right now.");
            return;
        }

        for (Player player : matched) {
            sendMessage(player, "MATCH-FOUND", "&a[RTP Queue] &fMatch found! Teleporting you to the same area...");
            sendActionBar(
                    player,
                    "MATCH-FOUND-ACTIONBAR",
                    "&aMatch found! Teleporting you to the same area..."
            );
            SoundUtils.play(player, plugin.getConfigManager().getSound("RTP.SEARCH-FOUND"));
        }
        refreshWaitingActionBars();
        if (getQueueSize() == 0) {
            stopWaitingActionBarTask();
        }

        plugin.getRtpManager().findSafeLocationAsync(settings).whenComplete((meetingPoint, throwable) -> {
            if (throwable != null || meetingPoint == null) {
                requeue(
                        matched,
                        "MATCH-FAILED",
                        "&c[RTP Queue] &fNo safe location was found for the match, so you are back in the queue."
                );
                return;
            }
            teleportParty(matched, meetingPoint, settings);
        });
    }

    private void teleportParty(List<Player> matched, Location meetingPoint, RTPManager.SearchSettings settings) {
        int spreadRadius = getSpreadRadius();
        for (int index = 0; index < matched.size(); index++) {
            Player player = matched.get(index);
            if (index == 0 || spreadRadius <= 0) {
                teleport(player, meetingPoint);
                continue;
            }

            RTPManager.SearchSettings spread = new RTPManager.SearchSettings(
                    settings.worldName(),
                    0,
                    spreadRadius,
                    meetingPoint.getBlockX(),
                    meetingPoint.getBlockZ(),
                    settings.maxAttempts(),
                    settings.maxChunkSamples(),
                    settings.attemptIntervalTicks()
            );
            plugin.getRtpManager().findSafeLocationAsync(spread).whenComplete((nearby, throwable) ->
                    teleport(player, throwable == null && nearby != null ? nearby : meetingPoint));
        }
    }

    private void teleport(Player player, Location destination) {
        plugin.getSpigotScheduler().runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getSpigotScheduler().teleport(player, destination).thenAccept(success ->
                    plugin.getSpigotScheduler().runEntity(player, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (!Boolean.TRUE.equals(success)) {
                            sendMessage(
                                    player,
                                    "MATCH-FAILED",
                                    "&c[RTP Queue] &fNo safe location was found for the match, so you are back in the queue."
                            );
                            rejoin(player.getUniqueId());
                            refreshWaitingActionBars();
                            ensureWaitingActionBarTask();
                            return;
                        }
                        SoundUtils.play(player, plugin.getConfigManager().getSound("TELEPORT.SUCCESS"));
                    }));
        });
    }

    private void requeue(List<Player> matched, String messageKey, String fallback) {
        for (Player player : matched) {
            if (!player.isOnline()) {
                continue;
            }
            rejoin(player.getUniqueId());
            sendMessage(player, messageKey, fallback);
        }
        startMatchIfReady();
        refreshWaitingActionBars();
        if (getQueueSize() > 0) {
            ensureWaitingActionBarTask();
        }
    }

    private void rejoin(UUID playerId) {
        synchronized (waiting) {
            waiting.add(playerId);
        }
    }

    private List<Player> onlinePlayers(List<UUID> party) {
        List<Player> matched = new ArrayList<>(party.size());
        for (UUID playerId : party) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                matched.add(player);
            }
        }
        return matched;
    }

    private RTPManager.SearchSettings getSearchSettings() {
        String worldName = getWorldName();
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return plugin.getRtpManager().getWorldSearchSettings(worldName);
    }

    private void broadcastToQueue(UUID excluded, String messageKey, String fallback, String... replacements) {
        List<UUID> queued;
        synchronized (waiting) {
            queued = new ArrayList<>(waiting);
        }
        for (UUID playerId : queued) {
            if (playerId.equals(excluded)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendMessage(player, messageKey, fallback, replacements);
            }
        }
    }

    private void sendMessage(Player player, String messageKey, String fallback, String... replacements) {
        String message = formatQueueMessage(messageKey, fallback, replacements);
        if (message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(ColorUtils.toComponent(message));
    }

    private void sendActionBar(Player player, String messageKey, String fallback, String... replacements) {
        if (player == null || !player.isOnline() || !canSendActionBar()) {
            return;
        }
        String message = formatQueueMessage(messageKey, fallback, replacements);
        if (message == null || message.isBlank()) {
            return;
        }
        try {
            PlayerSettingUtils.sendActionBar(plugin, player, ColorUtils.toComponent(message));
        } catch (Throwable ignored) {
        }
    }

    private void clearQueueActionBar(Player player) {
        if (player == null) {
            return;
        }
        try {
            PlayerSettingUtils.clearActionBar(player);
        } catch (Throwable ignored) {
        }
    }

    private void refreshWaitingActionBars() {
        int waitingCount = getQueueSize();
        int needed = getMatchSize();
        List<UUID> queued;
        synchronized (waiting) {
            queued = new ArrayList<>(waiting);
        }
        if (queued.isEmpty()) {
            stopWaitingActionBarTask();
            return;
        }
        for (UUID playerId : queued) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                sendWaitingActionBar(player, waitingCount, needed);
            }
        }
    }

    private void sendWaitingActionBar(Player player, int waitingCount, int needed) {
        sendActionBar(
                player,
                "WAITING-ACTIONBAR",
                "&eWaiting for another player... &7({waiting}/{needed})",
                "{waiting}", String.valueOf(waitingCount),
                "{needed}", String.valueOf(needed)
        );
    }

    String formatWaitingActionBar(int waitingCount, int needed) {
        String message = formatQueueMessage(
                "WAITING-ACTIONBAR",
                "&eWaiting for another player... &7({waiting}/{needed})",
                "{waiting}", String.valueOf(waitingCount),
                "{needed}", String.valueOf(needed)
        );
        return message == null ? "" : message;
    }

    private void ensureWaitingActionBarTask() {
        if (waitingActionBarTask != null
                || plugin == null
                || plugin.getSpigotScheduler() == null) {
            return;
        }
        waitingActionBarTask = plugin.getSpigotScheduler().runGlobalTimer(
                this::refreshWaitingActionBars,
                ACTIONBAR_REFRESH_TICKS,
                ACTIONBAR_REFRESH_TICKS
        );
    }

    private void stopWaitingActionBarTask() {
        if (waitingActionBarTask != null) {
            waitingActionBarTask.cancel();
            waitingActionBarTask = null;
        }
    }

    private boolean canSendActionBar() {
        return plugin != null && plugin.getPlayerDataManager() != null;
    }

    private String formatQueueMessage(String messageKey, String fallback, String... replacements) {
        FileConfiguration rtp = rtpConfig();
        String message = rtp == null ? fallback : rtp.getString("QUEUE.MESSAGES." + messageKey, fallback);
        if (message == null || message.isBlank()) {
            return message;
        }
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    private FileConfiguration rtpConfig() {
        if (plugin == null || plugin.getConfigManager() == null) {
            return null;
        }
        return plugin.getConfigManager().getRtp();
    }
}
