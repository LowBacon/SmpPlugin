package com.bx.smpPlugin.afk;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code /afk} when there is more than one zone: one button per zone, click to go. */
public class AfkZoneMenu extends BaseMenu {

    private static final int[] SLOTS = {13, 11, 15, 12, 14, 10, 16, 22, 20, 24, 21, 23, 19, 25};

    private final Map<Integer, String> slotZones = new HashMap<>();

    public AfkZoneMenu(SmpPlugin plugin) {
        super(plugin, plugin.getConfigManager().getAfk().getString("MENU.TITLE", "&8AFK Zones"), 36);
    }

    @Override
    public void build(Player player) {
        clear();
        slotZones.clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        AfkManager afk = plugin.getAfkManager();
        List<AfkZone> zones = afk.destinationsFor(player);
        for (int index = 0; index < zones.size() && index < SLOTS.length; index++) {
            AfkZone zone = zones.get(index);
            List<String> lore = new ArrayList<>();
            lore.add("&7ᴘʟᴀʏᴇʀѕ ʜᴇʀᴇ: &f" + afk.countPlayersIn(zone));
            lore.add(zone.paysRewards()
                    ? "&7ʀᴇᴡᴀʀᴅ: &b" + zone.rewardAmount() + " shard &7every &f" + NumberUtils.formatCountdown(zone.rewardSeconds())
                    : "&7ʀᴇᴡᴀʀᴅ: &8none");
            lore.add("");
            lore.add("&e➜ ᴄʟɪᴄᴋ ᴛᴏ ɢᴏ");
            ItemStack item = ItemUtils.createItem(zone.paysRewards() ? Material.AMETHYST_CLUSTER : Material.ENDER_PEARL,
                    zone.displayName(), lore);
            set(SLOTS[index], ItemUtils.hideMenuNoise(item, true));
            slotZones.put(SLOTS[index], zone.id());
        }
    }

    @Override
    public void handleClick(int slot, Player player) {
        String id = slotZones.get(slot);
        AfkZone zone = id == null ? null : plugin.getAfkManager().getZone(id);
        if (zone == null) {
            return;
        }
        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        close(player);
        plugin.getAfkManager().teleportWithWarmup(player, zone);
    }
}
