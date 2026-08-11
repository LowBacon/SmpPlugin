package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StaffListMenu extends BaseMenu {

    private static final String MENU_KEY = "STAFF-LIST";

    private final Map<Integer, UUID> slotTargets = new HashMap<>();
    private BukkitTask refreshTask;

    public StaffListMenu(SmpPlugin plugin) {
        super(
                plugin,
                plugin.getStaffModeManager().getMenuTitle(MENU_KEY, "&8ᴏɴʟɪɴᴇ ѕᴛᴀꜰꜰ"),
                plugin.getStaffModeManager().getMenuSize(MENU_KEY)
        );
    }

    @Override
    public void open(Player player) {
        build(player);
        player.openInventory(inventory);
        startRefresh(player);
    }

    @Override
    public void build(Player player) {
        cancelRefresh();
        render(player);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        int refreshSlot = plugin.getStaffModeManager().getMenuRefreshSlot(MENU_KEY);
        if (slot == refreshSlot) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            render(player);
            return;
        }

        UUID targetUuid = slotTargets.get(slot);
        if (targetUuid == null) {
            return;
        }

        Player target = plugin.getServer().getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ColorUtils.toComponent("&cᴛʜᴀᴛ ѕᴛᴀꜰꜰ ᴍᴇᴍʙᴇʀ ɪѕ ɴᴏ ʟᴏɴɢᴇʀ ᴏɴʟɪɴᴇ."));
            render(player);
            return;
        }

        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        player.closeInventory();
        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent("&7ʏᴏᴜ ᴀʀᴇ ᴀʟʀᴇᴀᴅʏ ᴠɪᴇᴡɪɴɢ ʏᴏᴜʀѕᴇʟꜰ."));
            return;
        }

        plugin.getSpigotScheduler().teleport(player, target.getLocation()).thenAccept(success ->
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (Boolean.TRUE.equals(success) && player.isOnline()) {
                        player.sendMessage(ColorUtils.toComponent("&eᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ ѕᴛᴀꜰꜰ ᴍᴇᴍʙᴇʀ &f" + target.getName() + "&e."));
                    }
                }));
    }

    @Override
    public void onClose(Player player) {
        cancelRefresh();
    }

    private void render(Player viewer) {
        clear();
        fill(plugin.getStaffModeManager().getMenuPlaceholderMaterial(MENU_KEY, Material.GRAY_STAINED_GLASS_PANE));
        slotTargets.clear();

        int refreshSlot = plugin.getStaffModeManager().getMenuRefreshSlot(MENU_KEY);
        set(refreshSlot, plugin.getStaffModeManager().createRefreshItem(MENU_KEY));

        List<Integer> contentSlots = plugin.getStaffModeManager().getMenuContentSlots(MENU_KEY, inventory.getSize());
        List<Player> staffMembers = plugin.getStaffModeManager().getOnlineStaffMembers();
        boolean canSeeVanished = PermissionUtils.has(viewer, plugin.getStaffModeManager().getSeeVanishedPermission());

        int rendered = 0;
        for (Player staff : staffMembers) {
            if (rendered >= contentSlots.size()) {
                break;
            }
            if (plugin.getStaffModeManager().isVanished(staff.getUniqueId())
                    && !canSeeVanished
                    && !viewer.getUniqueId().equals(staff.getUniqueId())) {
                continue;
            }

            int slot = contentSlots.get(rendered++);
            set(slot, createStaffItem(viewer, staff));
            slotTargets.put(slot, staff.getUniqueId());
        }

        if (rendered == 0) {
            set(inventory.getSize() / 2, plugin.getStaffModeManager().createMenuEmptyItem(MENU_KEY));
        }
    }

    private ItemStack createStaffItem(Player viewer, Player staff) {
        List<String> lore = List.of(
                "&7ѕᴛᴀᴛᴜѕ: &f" + plugin.getStaffModeManager().getPlayerStatusSummary(staff),
                "&7ᴡᴏʀʟᴅ: &f" + staff.getWorld().getName(),
                "&7ѕᴇʀᴠᴇʀ: &f" + plugin.getStaffModeManager().getLocalServerDisplayName(),
                "&7ᴠᴀɴɪѕʜᴇᴅ: " + (plugin.getStaffModeManager().isVanished(staff.getUniqueId()) ? "&aʏᴇѕ" : "&cɴᴏ"),
                "&7ʙᴇᴛᴛᴇʀ ᴠɪᴇᴡ: " + (plugin.getStaffModeManager().isBetterViewEnabled(staff.getUniqueId()) ? "&aᴏɴ" : "&cᴏꜰꜰ"),
                viewer.getUniqueId().equals(staff.getUniqueId())
                        ? "&7ᴛʜɪѕ ɪѕ ʏᴏᴜ."
                        : "&eᴄʟɪᴄᴋ ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ"
        );
        return ItemUtils.createPlayerHead(staff, "&e" + staff.getName(), lore);
    }

    private void startRefresh(Player player) {
        refreshTask = plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                cancelRefresh();
                return;
            }
            render(player);
        }, 40L, 40L);
    }

    private void cancelRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}
