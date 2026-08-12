package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CrateManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class CrateRewardHistoryMenu extends BaseMenu {

    private static final int HISTORY_LIMIT = 45;
    private static final String DATE_FORMAT = "MM/dd/yy HH:mm";
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(DATE_FORMAT, Locale.US);

    private final Deque<CrateManager.CrateHistoryEntry> history;
    private final int backSlot;

    public CrateRewardHistoryMenu(SmpPlugin plugin) {
        this(plugin, null);
    }

    public CrateRewardHistoryMenu(SmpPlugin plugin, Deque<CrateManager.CrateHistoryEntry> history) {
        super(plugin, "&8Crate History", 45);
        this.history = history != null ? new ArrayDeque<>(history) : new ArrayDeque<>();
        this.backSlot = 36;
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);

        set(backSlot, ItemUtils.createItem(
                Material.BARRIER,
                "&cʙᴀᴄᴋ",
                List.of("&7ʀᴇᴛᴜʀɴ ᴛᴏ ᴄʀᴀᴛᴇ ᴍᴇɴᴜ.")
        ));

        if (history.isEmpty()) {
            set(22, ItemUtils.createItem(
                    Material.BARRIER,
                    "&cɴᴏ ʜɪѕᴛᴏʀʏ",
                    List.of("&7ʏᴏᴜ ʜᴀᴠᴇɴ'ᴛ ᴏᴘᴇɴᴇᴅ ᴀɴʏ ᴄʀᴀᴛᴇѕ ʏᴇᴛ.")
            ));
            return;
        }

        int slot = 10;
        int rendered = 0;
        for (CrateManager.CrateHistoryEntry entry : history) {
            if (slot >= 35 || slot == backSlot) {
                slot++;
                continue;
            }
            if (slot % 9 == 8) {
                slot += 2;
                continue;
            }

            String timeStr = DATE_FORMATTER.format(new Date(entry.claimedAtMillis()));
            ItemStack item = ItemUtils.createItem(
                    Material.CHEST_MINECART,
                    "&f" + entry.crateDisplayName(),
                    List.of(
                            "&7ʀᴇᴡᴀʀᴅ: &f" + entry.rewardDisplayName(),
                            "&7ᴛɪᴍᴇ: &f" + timeStr
                    )
            );
            set(slot, item);
            slot++;
            rendered++;
            if (rendered >= HISTORY_LIMIT) {
                break;
            }
        }
    }

    @Override
    public void handleClick(int slot, Player player) {
        if (slot == backSlot) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            new CratesMenu(plugin).open(player);
        }
    }
}
