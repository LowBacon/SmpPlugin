package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.CrateManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CratesMenu extends BaseMenu {

    private final Map<Integer, String> crateSlots = new HashMap<>();
    private final int closeSlot;
    private final int historySlot = 31;

    public CratesMenu(SmpPlugin plugin) {
        super(
                plugin,
                plugin.getCrateManager().getListMenuSettings().title(),
                plugin.getCrateManager().getListMenuSettings().size()
        );
        this.closeSlot = plugin.getCrateManager().getListMenuSettings().closeSlot();
    }

    @Override
    public void build(Player player) {
        clear();
        crateSlots.clear();

        CrateManager.ListMenuSettings settings = plugin.getCrateManager().getListMenuSettings();
        fill(settings.filler());

        List<CrateManager.CrateDefinition> crates = plugin.getCrateManager().getAccessibleCrates(player);
        List<Integer> contentSlots = settings.contentSlots();

        int rendered = 0;
        for (int i = 0; i < contentSlots.size() && rendered < crates.size(); i++) {
            int slot = contentSlots.get(i);
            CrateManager.CrateDefinition crate = crates.get(rendered++);
            set(slot, plugin.getCrateManager().createCrateListItem(player, crate));
            crateSlots.put(slot, crate.id());
        }

        if (crates.isEmpty()) {
            set(settings.emptySlot(), createSimpleItem(settings.emptyItem(), player, null, null));
        }

        set(settings.closeSlot(), createSimpleItem(settings.closeItem(), player, null, null));
        set(historySlot, createHistoryButton(player));
    }

    @Override
    public void handleClick(int slot, Player player) {
        if (slot == closeSlot) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.closeInventory();
            return;
        }

        if (slot == historySlot) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            Deque<CrateManager.CrateHistoryEntry> history = plugin.getCrateManager().getPlayerHistory(player.getUniqueId());
            new CrateRewardHistoryMenu(plugin, history).open(player);
            return;
        }

        String crateId = crateSlots.get(slot);
        if (crateId == null) {
            return;
        }

        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        CrateManager.CrateDefinition definition = plugin.getCrateManager().getCrate(crateId);
        boolean gacha = definition != null && definition.openType() == CrateManager.OpenType.GACHA;
        CrateManager.OpenResult result = plugin.getCrateManager().startOpening(player, crateId, gacha);
        if (!result.success()) {
            player.sendMessage(ColorUtils.toComponent(result.message()));
            build(player);
            return;
        }

        if (result.crate().openType() == CrateManager.OpenType.GACHA) {
            new CrateGachaMenu(plugin, result.crate()).open(player);
            return;
        }

        new CrateRewardMenu(plugin, result.crate(), CrateRewardMenu.OpenContext.COMMAND).open(player);
    }

    private ItemStack createHistoryButton(Player player) {
        Deque<CrateManager.CrateHistoryEntry> history = plugin.getCrateManager().getPlayerHistory(player.getUniqueId());
        int count = history != null ? history.size() : 0;

        List<String> lore = List.of(
                "&7ᴠɪᴇᴡ ʏᴏᴜʀ ʀᴇᴄᴇɴᴛ ᴄʀᴀᴛᴇ ᴄʟᴀɪᴍѕ.",
                "",
                "&7ʀᴇᴄᴇɴᴛ ᴄʟᴀɪᴍѕ: &f" + count,
                "",
                "&eᴄʟɪᴄᴋ ᴛᴏ ᴠɪᴇᴡ ʜɪѕᴛᴏʀʏ."
        );

        return ItemUtils.createItem(
                Material.BOOK,
                "&bʜɪѕᴛᴏʀʏ",
                lore
        );
    }

    private ItemStack createSimpleItem(
            CrateManager.DisplayItem display,
            Player player,
            CrateManager.CrateDefinition crate,
            CrateManager.CrateReward reward
    ) {
        ItemStack item = com.bx.smpPlugin.utils.ItemUtils.createItem(
                display.material(),
                plugin.getCrateManager().applyPlaceholders(display.displayName(), player, crate, reward),
                plugin.getCrateManager().applyPlaceholders(display.lore(), player, crate, reward)
        );
        com.bx.smpPlugin.utils.ItemUtils.addEnchantments(item, display.enchantments());
        item.setAmount(Math.max(1, Math.min(display.amount(), item.getMaxStackSize())));
        return item;
    }
}
