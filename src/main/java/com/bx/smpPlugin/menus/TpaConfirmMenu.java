// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class TpaConfirmMenu extends BaseMenu {

    private final String requesterName;
    private final boolean tpaHere;

    public TpaConfirmMenu(SmpPlugin plugin, String requesterName, boolean tpaHere) {
        super(
                plugin,
                plugin.getConfigManager().getMenus()
                        .getString("TPA-CONFIRM-MENU.TITLE", "&8Confirm TPA {here}")
                        .replace("{here}", tpaHere ? "Here" : ""),
                plugin.getConfigManager().getMenus().getInt("TPA-CONFIRM-MENU.SIZE", 27)
        );
        this.requesterName = requesterName;
        this.tpaHere = tpaHere;
    }

    public int getCancelSlot() {
        return getMenus() != null ? getMenus().getInt("TPA-CONFIRM-MENU.BUTTONS.CANCEL.SLOT", 11) : 11;
    }

    public int getLocationSlot() {
        return getMenus() != null ? getMenus().getInt("TPA-CONFIRM-MENU.BUTTONS.LOCATION.SLOT", 12) : 12;
    }

    public int getPlayerSlot() {
        return getMenus() != null ? getMenus().getInt("TPA-CONFIRM-MENU.BUTTONS.PLAYER.SLOT", 13) : 13;
    }

    public int getRegionSlot() {
        return getMenus() != null ? getMenus().getInt("TPA-CONFIRM-MENU.BUTTONS.REGION.SLOT", 14) : 14;
    }

    public int getConfirmSlot() {
        return getMenus() != null ? getMenus().getInt("TPA-CONFIRM-MENU.BUTTONS.CONFIRM.SLOT", 15) : 15;
    }

    private FileConfiguration getMenus() {
        return plugin != null && plugin.getConfigManager() != null ? plugin.getConfigManager().getMenus() : null;
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);

        FileConfiguration menus = getMenus();

        Player requester = Bukkit.getPlayerExact(requesterName);
        String worldName = requester != null && requester.getWorld() != null
                ? requester.getWorld().getName()
                : (player != null && player.getWorld() != null ? player.getWorld().getName() : "world");
        int ping = requester != null ? requester.getPing() : (player != null ? player.getPing() : 0);

        // Cancel button
        String cancelMatStr = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.CANCEL.MATERIAL", "RED_STAINED_GLASS_PANE")
                : "RED_STAINED_GLASS_PANE";
        Material cancelMaterial = ItemUtils.parseMaterial(cancelMatStr);
        if (cancelMaterial == null) cancelMaterial = Material.RED_STAINED_GLASS_PANE;
        String cancelName = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.CANCEL.NAME", menus.getString("TPA-CONFIRM-MENU.BUTTONS.CANCEL.DISPLAY-NAME", "&cCancel"))
                : "&cCancel";
        List<String> cancelLore = menus != null
                ? ItemUtils.readLore(menus, "TPA-CONFIRM-MENU.BUTTONS.CANCEL.LORE", List.of("&fCLICK TO CANCEL"))
                : List.of("&fCLICK TO CANCEL");
        set(getCancelSlot(), ItemUtils.createItem(
                cancelMaterial,
                applyPlaceholders(cancelName, worldName, ping),
                applyPlaceholders(cancelLore, worldName, ping)
        ));

        // Location button
        String locationMatStr = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.LOCATION.MATERIAL", "GRASS_BLOCK")
                : "GRASS_BLOCK";
        Material locationMaterial = ItemUtils.parseMaterial(locationMatStr);
        if (locationMaterial == null) locationMaterial = Material.GRASS_BLOCK;
        String locationName = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.LOCATION.NAME", menus.getString("TPA-CONFIRM-MENU.BUTTONS.LOCATION.DISPLAY-NAME", "&#6BF18DLocation"))
                : "&#6BF18DLocation";
        List<String> locationLore = menus != null
                ? ItemUtils.readLore(menus, "TPA-CONFIRM-MENU.BUTTONS.LOCATION.LORE", List.of("&7{world}"))
                : List.of("&7{world}");
        set(getLocationSlot(), ItemUtils.createItem(
                locationMaterial,
                applyPlaceholders(locationName, worldName, ping),
                applyPlaceholders(locationLore, worldName, ping)
        ));

        // Player button
        String playerMatStr = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.PLAYER.MATERIAL", "PLAYER_HEAD")
                : "PLAYER_HEAD";
        Material playerMaterial = ItemUtils.parseMaterial(playerMatStr);
        if (playerMaterial == null) playerMaterial = Material.PLAYER_HEAD;
        String playerName = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.PLAYER.NAME", menus.getString("TPA-CONFIRM-MENU.BUTTONS.PLAYER.DISPLAY-NAME", "&#00FC00Player"))
                : "&#00FC00Player";
        List<String> playerLore = menus != null
                ? ItemUtils.readLore(menus, "TPA-CONFIRM-MENU.BUTTONS.PLAYER.LORE", List.of("&7{player}"))
                : List.of("&7{player}");
        set(getPlayerSlot(), createRequesterItem(
                playerMaterial,
                applyPlaceholders(playerName, worldName, ping),
                applyPlaceholders(playerLore, worldName, ping)
        ));

        // Region button
        String regionMatStr = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.REGION.MATERIAL", "FEATHER")
                : "FEATHER";
        Material regionMaterial = ItemUtils.parseMaterial(regionMatStr);
        if (regionMaterial == null) regionMaterial = Material.FEATHER;
        String regionName = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.REGION.NAME", menus.getString("TPA-CONFIRM-MENU.BUTTONS.REGION.DISPLAY-NAME", "&#6BF18DRegion"))
                : "&#6BF18DRegion";
        List<String> regionLore = menus != null
                ? ItemUtils.readLore(menus, "TPA-CONFIRM-MENU.BUTTONS.REGION.LORE", List.of("&7NA East (&#0069D6${ping}ms&7)"))
                : List.of("&7NA East (&#0069D6${ping}ms&7)");
        set(getRegionSlot(), ItemUtils.createItem(
                regionMaterial,
                applyPlaceholders(regionName, worldName, ping),
                applyPlaceholders(regionLore, worldName, ping)
        ));

        // Confirm button
        String confirmMatStr = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.CONFIRM.MATERIAL", "LIME_STAINED_GLASS_PANE")
                : "LIME_STAINED_GLASS_PANE";
        Material confirmMaterial = ItemUtils.parseMaterial(confirmMatStr);
        if (confirmMaterial == null) confirmMaterial = Material.LIME_STAINED_GLASS_PANE;
        String confirmName = menus != null
                ? menus.getString("TPA-CONFIRM-MENU.BUTTONS.CONFIRM.NAME", menus.getString("TPA-CONFIRM-MENU.BUTTONS.CONFIRM.DISPLAY-NAME", "&aConfirm"))
                : "&aConfirm";
        List<String> confirmLore = menus != null
                ? ItemUtils.readLore(menus, "TPA-CONFIRM-MENU.BUTTONS.CONFIRM.LORE", List.of("&fCLICK TO CONFIRM"))
                : List.of("&fCLICK TO CONFIRM");
        set(getConfirmSlot(), ItemUtils.createItem(
                confirmMaterial,
                applyPlaceholders(confirmName, worldName, ping),
                applyPlaceholders(confirmLore, worldName, ping)
        ));
    }

    @Override
    public void handleClick(int slot, Player player) {
        int cancelSlot = getCancelSlot();
        int confirmSlot = getConfirmSlot();
        if (slot != cancelSlot && slot != confirmSlot) return;

        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        player.closeInventory();

        if (slot == cancelSlot) {
            player.performCommand("tpadeny " + requesterName);
            return;
        }

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            return;
        }

        player.performCommand("tpaccept " + requesterName);
    }

    private String applyPlaceholders(String text, String worldName, int ping) {
        if (text == null) return "";
        return text.replace("{player}", requesterName != null ? requesterName : "")
                   .replace("{world}", worldName != null ? worldName : "world")
                   .replace("${ping}", String.valueOf(ping))
                   .replace("{ping}", String.valueOf(ping))
                   .replace("{here}", tpaHere ? "Here" : "");
    }

    private List<String> applyPlaceholders(List<String> lines, String worldName, int ping) {
        if (lines == null) return List.of();
        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            formatted.add(applyPlaceholders(line, worldName, ping));
        }
        return formatted;
    }

    private ItemStack createRequesterItem(Material material, String name, List<String> lore) {
        ItemStack item = ItemUtils.createItem(material != null ? material : Material.PLAYER_HEAD, name, lore);
        if (!(item.getItemMeta() instanceof SkullMeta meta)) {
            return item;
        }

        OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterName);
        meta.setOwningPlayer(requester);
        item.setItemMeta(meta);
        return item;
    }
}
