// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.RTPManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RTPMenu extends BaseMenu {

    private enum Status { READY, COOLDOWN, LOCKED, RUNNING }

    private BukkitTask refreshTask;

    public RTPMenu(SmpPlugin plugin) {
        super(plugin,
                plugin.getConfigManager().getRtp().getString("RTP-MENU.TITLE", "&8ʀᴀɴᴅᴏᴍ ᴛᴇʟᴇᴘᴏʀᴛ"),
                normalizeSize(plugin.getConfigManager().getRtp().getInt("RTP-MENU.SIZE", 27)));
    }

    @Override
    public void open(Player player) {
        super.open(player);
        plugin.getRtpManager().prefetchMenuDestinations();
        startRefresh(player);
    }

    @Override
    public void onClose(Player player) {
        stopRefresh();
    }

    @Override
    public void build(Player player) {
        clear();
        if (plugin.getConfigManager().getRtp().getBoolean("RTP-MENU.PLACEHOLDER", true)) {
            fill(Material.GRAY_STAINED_GLASS_PANE);
        }
        List<RTPManager.RTPDestination> destinations = plugin.getRtpManager().getMenuDestinations();
        if (destinations.isEmpty()) {
            set(inventory.getSize() / 2, ItemUtils.createItem(Material.BARRIER, "&cɴᴏ ʀᴛᴘ ᴅᴇѕᴛɪɴᴀᴛɪᴏɴѕ",
                    List.of("&7No valid RTP destinations in config.")));
            return;
        }
        for (RTPManager.RTPDestination destination : destinations) {
            List<String> lore = new ArrayList<>();
            for (String line : destination.lore()) {
                lore.add(fill(player, destination, line));
            }
            lore.add("");
            lore.add(fill(player, destination, statusLine(statusFor(player, destination))));
            set(destination.slot(), ItemUtils.hideMenuNoise(ItemUtils.createItem(
                    destination.material(), fill(player, destination, destination.displayName()), lore), true));
        }
    }

    @Override
    public void handleClick(int slot, Player player) {
        RTPManager.RTPDestination destination = plugin.getRtpManager().getDestinationBySlot(slot);
        if (destination == null) {
            return;
        }
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            close(player);
            return;
        }
        if (statusFor(player, destination) != Status.READY) {
            // Stay open so the player can watch the cooldown or pick another world.
            SoundUtils.play(player, "minecraft:entity.villager.no|0.8|1.0");
            return;
        }
        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        close(player);
        plugin.getRtpManager().queueMenuTeleport(player, destination);
    }

    private Status statusFor(Player player, RTPManager.RTPDestination destination) {
        RTPManager rtp = plugin.getRtpManager();
        if (rtp.hasActiveRtpFlow(player.getUniqueId()) || rtp.isInQueue(player.getUniqueId())) {
            return Status.RUNNING;
        }
        double required = rtp.getWorldRequiredPlaytimeHours(destination.worldName());
        if (required > 0.0D && rtp.getPlaytimeHours(player) < required) {
            return Status.LOCKED;
        }
        return rtp.getCooldownRemainingSeconds(player, destination.worldName()) > 0L ? Status.COOLDOWN : Status.READY;
    }

    private String statusLine(Status status) {
        FileConfiguration rtp = plugin.getConfigManager().getRtp();
        return switch (status) {
            case READY -> rtp.getString("RTP-MENU.STATUS.READY", "&a➜ ᴄʟɪᴄᴋ ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ");
            case COOLDOWN -> rtp.getString("RTP-MENU.STATUS.COOLDOWN", "&c⌛ ᴄᴏᴏʟᴅᴏᴡɴ: &f{remaining}");
            case LOCKED -> rtp.getString("RTP-MENU.STATUS.LOCKED", "&c✖ ɴᴇᴇᴅѕ &f{required_hours}h &cᴘʟᴀʏᴛɪᴍᴇ &7({current_hours}h)");
            case RUNNING -> rtp.getString("RTP-MENU.STATUS.RUNNING", "&e⌛ ᴛᴇʟᴇᴘᴏʀᴛ ɪɴ ᴘʀᴏɢʀᴇѕѕ");
        };
    }

    private String fill(Player player, RTPManager.RTPDestination destination, String text) {
        RTPManager rtp = plugin.getRtpManager();
        RTPManager.SearchSettings settings = rtp.getWorldSearchSettings(destination.worldName());
        double required = rtp.getWorldRequiredPlaytimeHours(destination.worldName());
        return text
                .replace("{players}", String.valueOf(rtp.getPlayersInWorld(destination.worldName())))
                .replace("{world}", rtp.describeWorld(destination.worldName()))
                .replace("{min_radius}", settings == null ? "0" : NumberUtils.format(settings.minRadius()))
                .replace("{max_radius}", settings == null ? "0" : NumberUtils.format(settings.maxRadius()))
                .replace("{cooldown}", String.valueOf(rtp.getPlayerCooldownSeconds(player, destination.worldName())))
                .replace("{remaining}", NumberUtils.formatCountdown(rtp.getCooldownRemainingSeconds(player, destination.worldName())))
                .replace("{required_playtime}", required > 0.0 ? String.format(Locale.ROOT, "%.1f hours", required) : "None")
                .replace("{required_hours}", String.format(Locale.ROOT, "%.1f", required))
                .replace("{current_hours}", String.format(Locale.ROOT, "%.1f", Math.floor(rtp.getPlaytimeHours(player) * 10D) / 10D))
                .replace("{ping}", String.valueOf(player.getPing()))
                .replace("{status}", destination.enabled() ? "&aᴇɴᴀʙʟᴇᴅ" : "&cᴅɪѕᴀʙʟᴇᴅ");
    }

    private void startRefresh(Player player) {
        stopRefresh();
        refreshTask = plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) {
                stopRefresh();
                return;
            }
            build(player);
        }, 20L, 20L);
    }

    private void stopRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private static int normalizeSize(int size) {
        int normalized = Math.max(9, ((size + 8) / 9) * 9);
        return Math.min(54, normalized);
    }
}
