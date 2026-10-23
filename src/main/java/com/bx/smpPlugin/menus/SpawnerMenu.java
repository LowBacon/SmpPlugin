// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.SpawnerManager;
import com.bx.smpPlugin.models.SpawnerInstance;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class SpawnerMenu extends BaseMenu {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_STORAGE = 10;
    private static final int SLOT_SELL = 12;
    private static final int SLOT_XP = 14;
    private static final int SLOT_STACK = 16;
    private static final int SLOT_CLOSE = 22;
    private static final int SLOT_FILTER = 18;

    private final long spawnerId;
    private BukkitTask refreshTask;

    public SpawnerMenu(SmpPlugin plugin, long spawnerId) {
        super(plugin, titleFor(plugin, spawnerId), 27);
        this.spawnerId = spawnerId;
    }

    private static String titleFor(SmpPlugin plugin, long spawnerId) {
        SpawnerInstance instance = plugin.getSpawnerManager().getSpawner(spawnerId);
        return instance == null ? "&8ѕᴘᴀᴡɴᴇʀ" : plugin.getSpawnerManager().getMainMenuTitle(instance);
    }

    @Override
    public void open(Player player) {
        super.open(player);
        sound(player, "OPEN", "minecraft:block.chest.open|0.5|1.4");
        startRefresh(player);
    }

    @Override
    public void onClose(Player player) {
        stopRefresh();
    }

    @Override
    public void build(Player player) {
        SpawnerManager manager = plugin.getSpawnerManager();
        SpawnerInstance instance = manager.getSpawner(spawnerId);
        fill(ItemUtils.parseMaterial(plugin.getConfigManager().getSpawners()
                .getString("GUI.MAIN.FILLER", "GRAY_STAINED_GLASS_PANE")));
        set(SLOT_CLOSE, polish(ItemUtils.createItem(Material.BARRIER, "&cᴄʟᴏѕᴇ", null)));
        if (instance == null) {
            set(SLOT_INFO, ItemUtils.createItem(Material.BARRIER, "&cѕᴘᴀᴡɴᴇʀ ɢᴏɴᴇ",
                    List.of("&7This spawner was broken or removed.")));
            return;
        }

        set(SLOT_INFO, infoItem(instance));

        long stored = instance.getTotalStoredItems();
        set(SLOT_STORAGE, polish(ItemUtils.createItem(Material.CHEST, "&6ѕᴛᴏʀᴀɢᴇ", List.of(
                "&7" + NumberUtils.format(stored) + " items stored",
                "",
                "&e➜ ᴄʟɪᴄᴋ ᴛᴏ ᴏᴘᴇɴ"))));

        double worth = manager.estimateLootWorth(player, instance);
        set(SLOT_SELL, polish(ItemUtils.createItem(Material.GOLD_INGOT, "&eѕᴇʟʟ ᴀʟʟ", List.of(
                "&7Worth &a" + plugin.getCurrencyManager().formatMoneyCompact(worth),
                "",
                worth > 0D ? "&e➜ ᴄʟɪᴄᴋ ᴛᴏ ѕᴇʟʟ" : "&8ɴᴏᴛʜɪɴɢ ᴛᴏ ѕᴇʟʟ ʏᴇᴛ"))));

        if (manager.isXpStorageEnabled()) {
            long xp = instance.getStoredXp();
            set(SLOT_XP, polish(ItemUtils.createItem(xp > 0L ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                    "&aᴄᴏʟʟᴇᴄᴛ xᴘ", List.of(
                            "&7" + NumberUtils.format(xp) + " xp &8(+" + NumberUtils.format(levelsGained(player, xp)) + " levels)",
                            "",
                            xp > 0L ? "&e➜ ᴄʟɪᴄᴋ ᴛᴏ ᴄᴏʟʟᴇᴄᴛ" : "&8ɴᴏ xᴘ ʏᴇᴛ"))));
        }

        List<String> stackLore = new ArrayList<>();
        stackLore.add("&7" + NumberUtils.format(instance.getStackAmount()) + " spawners in this block");
        stackLore.add("");
        stackLore.add("&e➜ ʟᴇꜰᴛ-ᴄʟɪᴄᴋ &7ᴀᴅᴅ ꜰʀᴏᴍ ɪɴᴠᴇɴᴛᴏʀʏ");
        if (manager.canBreak(player, instance)) {
            stackLore.add("&e➜ ʀɪɢʜᴛ-ᴄʟɪᴄᴋ &7ᴛᴀᴋᴇ 1 ᴏᴜᴛ");
        }
        ItemStack stack = ItemUtils.createItem(Material.SPAWNER, "&dѕᴛᴀᴄᴋ", stackLore);
        stack.setAmount((int) Math.max(1L, Math.min(64L, instance.getStackAmount())));
        set(SLOT_STACK, polish(stack));
        int filtered = instance.getDisabledLootKeys().size();
        set(SLOT_FILTER, polish(ItemUtils.createItem(Material.HOPPER, "&bLoot filter", List.of(
                filtered == 0 ? "&7Storing every drop." : "&7" + filtered + " drop" + (filtered == 1 ? "" : "s") + " not stored.",
                "",
                "&eClick to choose what is stored"))));
    }

    private ItemStack infoItem(SpawnerInstance instance) {
        SpawnerManager manager = plugin.getSpawnerManager();
        String paused = manager.getPausedReason(instance);
        List<String> lore = new ArrayList<>();
        lore.add("&7ᴏᴡɴᴇʀ: &f" + instance.getOwnerNameSnapshot());
        lore.add("&7ᴍᴀᴋᴇѕ: &f~" + NumberUtils.formatNice(manager.getItemsPerHour(instance)) + " items/hour");
        lore.add(paused == null
                ? "&7ɴᴇxᴛ ᴅʀᴏᴘ: &f" + NumberUtils.formatCountdown((manager.getMillisUntilNextCycle(instance) + 999L) / 1000L)
                : "&cᴘᴀᴜѕᴇᴅ: &7" + paused);
        return polish(ItemUtils.setGlint(ItemUtils.createItem(Material.SPAWNER,
                manager.getTypeDisplayName(instance.getMobTypeKey()) + " &7x" + NumberUtils.format(instance.getStackAmount()),
                lore), true));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        SpawnerManager manager = plugin.getSpawnerManager();
        if (slot == SLOT_CLOSE) {
            close(player);
            return;
        }
        SpawnerInstance instance = manager.getSpawner(spawnerId);
        if (instance == null || !manager.canOpen(player, instance)) {
            close(player);
            return;
        }

        switch (slot) {
            case SLOT_FILTER -> {
                sound(player, "CLICK", "minecraft:ui.button.click|0.6|1.0");
                new SpawnerFilterMenu(plugin, spawnerId).open(player);
                return;
            }
            case SLOT_STORAGE -> {
                sound(player, "CLICK", "minecraft:ui.button.click|0.6|1.0");
                manager.openStorage(player, instance, 1);
                return;
            }
            case SLOT_SELL -> {
                SpawnerManager.SellLootResult result = manager.sellAllLoot(player, instance);
                feedback(player, result.success(), result.message(), "SELL", "minecraft:entity.player.levelup|0.6|1.8");
            }
            case SLOT_XP -> {
                if (!manager.isXpStorageEnabled()) {
                    return;
                }
                SpawnerManager.ActionResult result = manager.collectXp(player, instance);
                feedback(player, result.success(), result.message(), "XP", "minecraft:entity.experience_orb.pickup|1.0|1.0");
            }
            case SLOT_STACK -> {
                SpawnerManager.ActionResult result = clickType.isRightClick()
                        ? manager.withdrawSpawners(player, instance, 1L)
                        : manager.addSpawnersFromInventory(player, instance);
                feedback(player, result.success(), result.message(), "STACK", "minecraft:block.anvil.use|0.4|1.6");
            }
            default -> {
                return;
            }
        }
        build(player);
    }

    private void feedback(Player player, boolean success, String message, String soundKey, String fallbackSound) {
        player.sendMessage(ColorUtils.toComponent(message));
        if (success) {
            sound(player, soundKey, fallbackSound);
        } else {
            sound(player, "ERROR", "minecraft:entity.villager.no|0.8|1.0");
        }
    }

    private void sound(Player player, String key, String fallback) {
        com.bx.smpPlugin.utils.SoundUtils.play(player,
                plugin.getConfigManager().getSpawners().getString("GUI.MAIN.SOUNDS." + key, fallback));
    }

    private static ItemStack polish(ItemStack item) {
        return ItemUtils.hideMenuNoise(item, true);
    }

    static long levelsGained(Player player, long xp) {
        int level = player.getLevel();
        double target = totalXpAtLevel(level) + player.getExp() * xpToNextLevel(level) + xp;
        double total = totalXpAtLevel(level);
        long gained = 0L;
        int current = level;
        while (current < 1_000_000 && total + xpToNextLevel(current) <= target) {
            total += xpToNextLevel(current);
            current++;
            gained++;
        }
        return gained;
    }

    static int xpToNextLevel(int level) {
        if (level >= 30) {
            return 9 * level - 158;
        }
        if (level >= 15) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }

    static double totalXpAtLevel(int level) {
        if (level <= 16) {
            return level * (double) level + 6D * level;
        }
        if (level <= 31) {
            return 2.5D * level * level - 40.5D * level + 360D;
        }
        return 4.5D * level * level - 162.5D * level + 2220D;
    }

    private void startRefresh(Player player) {
        stopRefresh();
        refreshTask = plugin.getSpigotScheduler().runEntityTimer(player, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) {
                stopRefresh();
                return;
            }
            set(SLOT_INFO, infoItemOrGone());
        }, 20L, 20L);
    }

    private ItemStack infoItemOrGone() {
        SpawnerInstance instance = plugin.getSpawnerManager().getSpawner(spawnerId);
        return instance == null
                ? ItemUtils.createItem(Material.BARRIER, "&cѕᴘᴀᴡɴᴇʀ ɢᴏɴᴇ", List.of("&7This spawner was broken or removed."))
                : infoItem(instance);
    }

    private void stopRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}
