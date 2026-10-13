package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.dialogs.DialogSupport;
import com.bx.smpPlugin.managers.WorthManager;
import com.bx.smpPlugin.menus.AuctionHouseBrowseMenu;
import com.bx.smpPlugin.menus.AuctionHouseSounds;
import com.bx.smpPlugin.menus.AuctionYourItemsMenu;
import com.bx.smpPlugin.menus.QuickBuyCatalog;
import com.bx.smpPlugin.menus.QuickBuyCatalog.EnchantmentSpec;
import com.bx.smpPlugin.menus.QuickBuyItemSelectMenu;
import com.bx.smpPlugin.menus.QuickBuyMenu;
import com.bx.smpPlugin.menus.QuickBuySounds;
import com.bx.smpPlugin.menus.QuickBuyTransactionsMenu;
import com.bx.smpPlugin.managers.EnchantmentsManager;
import com.bx.smpPlugin.models.QuickBuyEntry;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.bx.smpPlugin.menus.QuickBuyCatalog.*;

public final class QuickBuyItemDialog extends DialogScreen {

    /** Side-by-side Cancel / Add to Quick Buy, matching Design/Quick Buy/Buy Item/3.png. */
    static final int AMOUNT_COLUMNS = 2;
    static final int AMOUNT_BUTTON_WIDTH = 150;
    static final int AMOUNT_INPUT_WIDTH = 200;

    /** Choose Enchantments matching Design/Quick Buy/Buy Item/Choose Enchantments/main.png. */
    public static final int ENCHANT_COLUMNS = 6;
    public static final int ENCHANT_NAME_WIDTH = 145;
    public static final int ENCHANT_LEVEL_WIDTH = 42;
    public static final int ENCHANT_INERT_WIDTH = 1;
    public static final int ENCHANT_BOTTOM_WIDTH = 160;

    public QuickBuyItemDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    public boolean openSelection(Player player, int targetSlot, String query) {
        List<Material> items = QuickBuyCatalog.getSelectableMaterials(plugin, query);
        DialogFactory.Screen screen = screen(player)
                .title("Choose Item")
                .columns(4)
                .input(new DialogConfig.InputSpec("search", "Search", 200, 32, query == null ? "" : query, List.of()))
                .button("&fSearch", null, 120, DialogActions.QUICK_BUY_SEARCH + targetSlot);

        for (Material mat : items) {
            String name = plugin.getWorthManager().prettifyMaterial(mat);
            String spriteTag = getSpriteTag(mat);
            String label = spriteTag.isEmpty() ? ("&f" + name) : (spriteTag + " &f" + name);
            screen.button(
                    label,
                    chooseItemWorthTooltip(plugin, player, mat, name),
                    120,
                    DialogActions.QUICK_BUY_ITEM_SELECT + targetSlot + "_" + mat.name()
            );
        }

        screen.exit("&cCancel", DialogActions.QUICK_BUY_CANCEL, 120);
        return show(player, screen.build());
    }

    public boolean openEnchantments(Player player, int targetSlot, Material material) {
        if (material == null || !isEnchantable(material)) {
            return openAmount(player, targetSlot, material);
        }
        DialogSession session = session(player);
        if (session.getQuickBuySlot() != targetSlot || session.getQuickBuyMaterial() != material) {
            session.startQuickBuy(targetSlot, material);
        }
        Map<Enchantment, Integer> selected = session.getQuickBuyEnchantments();

        ItemStack preview = new ItemStack(material);
        if (!selected.isEmpty()) {
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                if (material == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
                    for (Map.Entry<Enchantment, Integer> entry : selected.entrySet()) {
                        esm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    }
                } else {
                    for (Map.Entry<Enchantment, Integer> entry : selected.entrySet()) {
                        meta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                }
                preview.setItemMeta(meta);
            }
        }

        DialogFactory.Screen screen = screen(player)
                .title("Choose Enchantments")
                .item(preview)
                .columns(ENCHANT_COLUMNS);

        List<EnchantmentSpec> specs = getEnchantmentSpecs(plugin, material);
        for (EnchantmentSpec spec : specs) {
            int currentLevel = 0;
            if (spec.enchantment() != null) {
                currentLevel = selected.getOrDefault(spec.enchantment(), 0);
            } else {
                for (Map.Entry<Enchantment, Integer> e : selected.entrySet()) {
                    if (e.getKey() != null && e.getKey().getKey().getKey().equalsIgnoreCase(spec.key())) {
                        currentLevel = e.getValue();
                        break;
                    }
                }
            }
            String enchKey = spec.key().toLowerCase(Locale.ROOT);

            // Column 0: Enchantment Name Button
            Component nameComp;
            if (spec.isCurse()) {
                NamedTextColor color = (currentLevel > 0) ? NamedTextColor.GREEN : NamedTextColor.RED;
                nameComp = Component.text(spec.displayName(), color).decoration(TextDecoration.ITALIC, false);
            } else {
                NamedTextColor color = (currentLevel > 0) ? NamedTextColor.GREEN : NamedTextColor.WHITE;
                String text = spec.displayName() + (spec.maxLevel() > 1 ? (" " + (currentLevel > 0 ? toRoman(currentLevel) : "I")) : "");
                nameComp = Component.text(text, color).decoration(TextDecoration.ITALIC, false);
            }

            int nameTargetLevel = currentLevel > 0 ? 0 : 1;
            screen.button(
                    nameComp,
                    null,
                    ENCHANT_NAME_WIDTH,
                    DialogActions.QUICK_BUY_ENCHANT_LVL + targetSlot + "/" + material.name() + "/" + enchKey + "/" + nameTargetLevel
            );

            // Columns 1 to 5: Roman numeral level buttons or inert placeholders
            for (int lvl = 1; lvl <= 5; lvl++) {
                if (lvl <= spec.maxLevel()) {
                    String roman = toRoman(lvl);
                    NamedTextColor color = (currentLevel == lvl) ? NamedTextColor.GREEN : NamedTextColor.WHITE;
                    Component lvlComp = Component.text(roman, color).decoration(TextDecoration.ITALIC, false);
                    screen.button(
                            lvlComp,
                            null,
                            ENCHANT_LEVEL_WIDTH,
                            DialogActions.QUICK_BUY_ENCHANT_LVL + targetSlot + "/" + material.name() + "/" + enchKey + "/" + lvl
                    );
                } else {
                    screen.inertButton("", null, ENCHANT_INERT_WIDTH);
                }
            }
        }

        // Bottom row:
        // Centered side-by-side buttons: Back to Items & Skip/Confirm Enchantments
        screen.button(
                Component.text("Back to Items", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                null,
                ENCHANT_BOTTOM_WIDTH,
                DialogActions.QUICK_BUY_ENCHANT_BACK + targetSlot
        );
        if (selected.isEmpty()) {
            screen.button(
                    Component.text("Skip Enchantments", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    null,
                    ENCHANT_BOTTOM_WIDTH,
                    DialogActions.QUICK_BUY_ENCHANT_SKIP + targetSlot + "/" + material.name()
            );
        } else {
            screen.button(
                    Component.text("Confirm Enchantments", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    null,
                    ENCHANT_BOTTOM_WIDTH,
                    DialogActions.QUICK_BUY_ENCHANT_CONFIRM + targetSlot + "/" + material.name()
            );
        }

        return show(player, screen.build());
    }

    public boolean openAmount(Player player, int targetSlot, Material material) {
        if (material == null || isBlacklisted(plugin, material)) {
            return false;
        }
        int max = material.getMaxStackSize();
        ItemStack icon = new ItemStack(material);
        Map<Enchantment, Integer> enchants = session(player).getQuickBuyEnchantments();
        if (!enchants.isEmpty()) {
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (material == Material.ENCHANTED_BOOK && meta instanceof EnchantmentStorageMeta esm) {
                    for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                        esm.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                    }
                } else {
                    for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                        meta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                }
                icon.setItemMeta(meta);
            }
        }
        DialogFactory.Screen screen = screen(player)
                .title("How many to buy?")
                .item(icon)
                .text("&fMax per purchase: " + max)
                .columns(AMOUNT_COLUMNS)
                .input(new DialogConfig.InputSpec(
                        "amount",
                        "Amount",
                        AMOUNT_INPUT_WIDTH,
                        10,
                        "1",
                        List.of()
                ))
                .button("&cCancel", null, AMOUNT_BUTTON_WIDTH, DialogActions.QUICK_BUY_CANCEL)
                .button(
                        "&fAdd to Quick Buy",
                        null,
                        AMOUNT_BUTTON_WIDTH,
                        DialogActions.QUICK_BUY_CONFIRM + targetSlot + "_" + material.name()
                );

        return show(player, screen.build());
    }

    public boolean openAuctionSearch(Player player, String currentQuery, String source) {
        String query = currentQuery == null ? "" : currentQuery;
        DialogFactory.Screen screen = screen(player)
                .title("Search Auction")
                .item(new ItemStack(Material.OAK_SIGN))
                .columns(2)
                .input(new DialogConfig.InputSpec("query", "Search", 300, 32, query, List.of()))
                .button("&cCancel", null, 120, DialogActions.QUICK_BUY_SEARCH_CANCEL + source)
                .button("&aSearch", null, 120, DialogActions.QUICK_BUY_SEARCH_SUBMIT + source);

        return show(player, screen.build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (action.equals(DialogActions.QUICK_BUY_CANCEL)) {
            session(player).clearQuickBuy();
            QuickBuySounds.play(player, plugin, QuickBuySounds.CANCEL);
            DialogSupport.close(player);
            plugin.getSpigotScheduler().runEntity(player, () -> new QuickBuyMenu(plugin).open(player));
            return true;
        }

        if (action.startsWith(DialogActions.QUICK_BUY_SEARCH_CANCEL)) {
            String source = action.substring(DialogActions.QUICK_BUY_SEARCH_CANCEL.length());
            playSearchDialogSound(player, source, false);
            DialogSupport.close(player);
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (isYourItemsSearch(source)) {
                    new AuctionYourItemsMenu(plugin, yourItemsOrigin(source)).open(player);
                } else if ("transactions".equalsIgnoreCase(source)) {
                    new QuickBuyTransactionsMenu(plugin, 0).open(player);
                } else if ("ah_browse".equalsIgnoreCase(source)) {
                    new AuctionHouseBrowseMenu(plugin, 1, com.bx.smpPlugin.managers.AuctionHouseManager.AuctionSort.NEWEST).open(player);
                } else if (source.startsWith("select_")) {
                    try {
                        int targetSlot = Integer.parseInt(source.substring(7));
                        new QuickBuyItemSelectMenu(plugin, targetSlot, 0, "").open(player);
                    } catch (NumberFormatException e) {
                        new QuickBuyMenu(plugin).open(player);
                    }
                } else {
                    new QuickBuyMenu(plugin).open(player);
                }
            });
            return true;
        }

        if (action.startsWith(DialogActions.QUICK_BUY_SEARCH_SUBMIT)) {
            String source = action.substring(DialogActions.QUICK_BUY_SEARCH_SUBMIT.length());
            playSearchDialogSound(player, source, true);
            String query = input(response, "query");
            String trimmedQuery = query == null || query.isBlank() ? "" : query.trim();
            DialogSupport.close(player);
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (isYourItemsSearch(source)) {
                    new AuctionYourItemsMenu(
                            plugin,
                            yourItemsOrigin(source),
                            AuctionYourItemsMenu.YourItemsFilter.DEFAULT,
                            trimmedQuery
                    ).open(player);
                } else if ("transactions".equalsIgnoreCase(source)) {
                    new QuickBuyTransactionsMenu(plugin, 0, trimmedQuery).open(player);
                } else if ("ah_browse".equalsIgnoreCase(source)) {
                    plugin.getAuctionHouseManager().setSearchQuery(player.getUniqueId(), trimmedQuery);
                    new AuctionHouseBrowseMenu(plugin, 1, com.bx.smpPlugin.managers.AuctionHouseManager.AuctionSort.NEWEST, "ALL", trimmedQuery).open(player);
                } else if (source.startsWith("select_")) {
                    try {
                        int targetSlot = Integer.parseInt(source.substring(7));
                        new QuickBuyItemSelectMenu(plugin, targetSlot, 0, trimmedQuery).open(player);
                    } catch (NumberFormatException e) {
                        new QuickBuyMenu(plugin).open(player);
                    }
                } else {
                    new QuickBuyMenu(plugin, QuickBuyMenu.Filter.DEFAULT, false, trimmedQuery).open(player);
                }
            });
            return true;
        }

        if (action.startsWith(DialogActions.QUICK_BUY_SEARCH)) {
            QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
            String slotStr = action.substring(DialogActions.QUICK_BUY_SEARCH.length());
            int slot = parseSlot(slotStr);
            String search = input(response, "search");
            openSelection(player, slot, search == null ? "" : search);
            return true;
        }

        if (action.startsWith(DialogActions.QUICK_BUY_ITEM_SELECT)) {
            click(player);
            String data = action.substring(DialogActions.QUICK_BUY_ITEM_SELECT.length());
            String[] parts = data.split("_", 2);
            if (parts.length == 2) {
                int slot = parseSlot(parts[0]);
                Material mat = Material.matchMaterial(parts[1]);
                if (mat != null) {
                    if (!isBlacklisted(plugin, mat)) {
                        session(player).startQuickBuy(slot, mat);
                        if (isEnchantable(mat)) {
                            openEnchantments(player, slot, mat);
                        } else {
                            openAmount(player, slot, mat);
                        }
                    } else {
                        QuickBuySounds.fail(player, plugin);
                    }
                    return true;
                }
            }
        }

        if (action.startsWith(DialogActions.QUICK_BUY_ENCHANT_LVL)) {
            String rest = action.substring(DialogActions.QUICK_BUY_ENCHANT_LVL.length());
            String[] parts = rest.split("/");
            if (parts.length == 4) {
                int slot = parseSlot(parts[0]);
                Material mat = Material.matchMaterial(parts[1]);
                String enchKey = parts[2];
                int lvl = 1;
                try {
                    lvl = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ignored) {}

                if (mat != null) {
                    Enchantment ench = QuickBuyEntry.findEnchantment(enchKey);
                    if (ench != null) {
                        if (lvl <= 0) {
                            session(player).setQuickBuyEnchantment(ench, 0);
                        } else {
                            session(player).toggleQuickBuyEnchantment(ench, lvl);
                        }
                    }
                    click(player);
                    openEnchantments(player, slot, mat);
                    return true;
                }
            }
        }

        if (action.startsWith(DialogActions.QUICK_BUY_ENCHANT_BACK)) {
            String slotStr = action.substring(DialogActions.QUICK_BUY_ENCHANT_BACK.length());
            int slot = parseSlot(slotStr);
            session(player).clearQuickBuy();
            QuickBuySounds.play(player, plugin, QuickBuySounds.CANCEL);
            openSelection(player, slot, "");
            return true;
        }

        if (action.startsWith(DialogActions.QUICK_BUY_ENCHANT_SKIP)) {
            String rest = action.substring(DialogActions.QUICK_BUY_ENCHANT_SKIP.length());
            String[] parts = rest.split("/");
            if (parts.length == 2) {
                int slot = parseSlot(parts[0]);
                Material mat = Material.matchMaterial(parts[1]);
                if (mat != null) {
                    session(player).clearQuickBuyEnchantments();
                    click(player);
                    openAmount(player, slot, mat);
                    return true;
                }
            }
        }

        if (action.startsWith(DialogActions.QUICK_BUY_ENCHANT_CONFIRM)) {
            String rest = action.substring(DialogActions.QUICK_BUY_ENCHANT_CONFIRM.length());
            String[] parts = rest.split("/");
            if (parts.length == 2) {
                int slot = parseSlot(parts[0]);
                Material mat = Material.matchMaterial(parts[1]);
                if (mat != null) {
                    click(player);
                    openAmount(player, slot, mat);
                    return true;
                }
            }
        }

        if (action.startsWith(DialogActions.QUICK_BUY_CONFIRM)) {
            String data = action.substring(DialogActions.QUICK_BUY_CONFIRM.length());
            String[] parts = data.split("_", 2);
            if (parts.length == 2) {
                int slot = parseSlot(parts[0]);
                Material mat = Material.matchMaterial(parts[1]);
                if (mat != null) {
                    if (!isBlacklisted(plugin, mat)) {
                        String amtStr = input(response, "amount");
                        int amount = 1;
                        if (amtStr != null) {
                            try {
                                amount = Integer.parseInt(amtStr.trim());
                            } catch (NumberFormatException ignored) {}
                        }
                        amount = Math.max(1, Math.min(mat.getMaxStackSize(), amount));
                        Map<Enchantment, Integer> enchants = session(player).getQuickBuyEnchantments();
                        String serializedEnchants = QuickBuyEntry.serializeEnchantments(enchants);
                        QuickBuyEntry entry = new QuickBuyEntry(slot, mat, amount, serializedEnchants, true);
                        plugin.getShopManager().setQuickBuyEntry(player.getUniqueId(), entry);
                        session(player).clearQuickBuy();
                        QuickBuySounds.play(player, plugin, QuickBuySounds.PIN);
                        DialogSupport.close(player);
                        plugin.getSpigotScheduler().runEntity(player, () -> new QuickBuyMenu(plugin).open(player));
                    } else {
                        QuickBuySounds.fail(player, plugin);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private void playSearchDialogSound(Player player, String source, boolean submit) {
        boolean auction = isYourItemsSearch(source) || "ah_browse".equalsIgnoreCase(source);
        if (auction) {
            AuctionHouseSounds.play(player, plugin, submit ? AuctionHouseSounds.SEARCH : AuctionHouseSounds.CANCEL);
            return;
        }
        QuickBuySounds.play(player, plugin, submit ? QuickBuySounds.SEARCH : QuickBuySounds.CANCEL);
    }

    private static boolean isYourItemsSearch(String source) {
        return source != null && source.toLowerCase(Locale.ROOT).startsWith("your_items");
    }

    private static AuctionYourItemsMenu.Origin yourItemsOrigin(String source) {
        return "your_items_shop".equalsIgnoreCase(source)
                ? AuctionYourItemsMenu.Origin.SHOP
                : AuctionYourItemsMenu.Origin.AUCTION;
    }

    private int parseSlot(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Hover text for a Choose Item button: the item name, then {@code Worth:} plus the inventory
     * worth lore line (green {@code $} and compact amount). Unsellable items, a globally disabled
     * worth-lore switch, and a player who turned Worth Display off leave the tooltip off.
     */
    public static String chooseItemWorthTooltip(
            SmpPlugin plugin,
            Player player,
            Material material,
            String itemName
    ) {
        if (plugin == null || plugin.getWorthManager() == null || material == null) {
            return null;
        }
        WorthManager worth = plugin.getWorthManager();
        if (!worth.isWorthLoreEnabled()) {
            return null;
        }
        if (player != null
                && plugin.getPlayerDataManager() != null
                && !worth.isWorthDisplayEnabledFor(player)) {
            return null;
        }
        return formatChooseItemWorthTooltip(itemName, worth.getWorthLoreLine(new ItemStack(material)));
    }

    public static String formatChooseItemWorthTooltip(String itemName, String worthLoreLine) {
        if (worthLoreLine == null || worthLoreLine.isBlank()) {
            return null;
        }
        String lore = "&7Worth: " + worthLoreLine;
        if (itemName == null || itemName.isBlank()) {
            return lore;
        }
        return "&f" + itemName + "\n" + lore;
    }

    public List<Material> getSelectableMaterials(String query) {
        return QuickBuyCatalog.getSelectableMaterials(plugin, query);
    }

    public static String getSpriteTag(Material mat) {
        if (mat == null) {
            return "";
        }
        String head = getHeadTexture(mat);
        if (!head.isEmpty()) {
            return "<head:" + head + ">";
        }
        String path = getSpritePath(mat);
        if (path == null || path.isBlank()) {
            return "";
        }
        return "<item:" + path + ">";
    }

    /**
     * The skin texture that draws a head or a skull, or blank for everything else.
     *
     * <p>No atlas holds a head, but a head can be drawn from any skin texture, and a mob texture
     * lays its face out where a player skin keeps one. So a creeper head is a head wearing
     * creeper.png rather than a stand-in item that merely hints at the mob.
     */
    public static String getHeadTexture(Material mat) {
        if (mat == null) {
            return "";
        }
        String name = mat.name().toLowerCase(Locale.ROOT);
        if (mat.isLegacy() || name.startsWith("legacy_")) {
            return "";
        }
        return switch (name) {
            case "player_head" -> "entity/player/wide/steve";
            case "creeper_head" -> "entity/creeper/creeper";
            case "zombie_head" -> "entity/zombie/zombie";
            case "piglin_head" -> "entity/piglin/piglin";
            case "skeleton_skull" -> "entity/skeleton/skeleton";
            case "wither_skeleton_skull" -> "entity/skeleton/wither_skeleton";
            default -> "";
        };
    }

    public static String getSpritePath(Material mat) {
        if (mat == null) {
            return "";
        }
        String name = mat.name().toLowerCase(Locale.ROOT);
        if (mat.isLegacy() || name.startsWith("legacy_") || isAir(mat)) {
            return "";
        }

        // Heads, skulls and the shield are entity models, so nothing in the item or block atlas
        // draws them. Heads are handled by getHeadTexture instead and have no atlas path at all.
        if (name.equals("shield")) {
            return "item/iron_ingot";
        }
        if (name.endsWith("_skull") || name.endsWith("_head")) {
            // The dragon is the one head whose model does not lay its face out where a player skin
            // does, so a texture override would crop the wrong corner of dragon.png. Its own block
            // stands in for it.
            return name.equals("dragon_head") ? "block/dragon_egg" : "";
        }

        // Beds & Banners -> colored wool
        if (name.endsWith("_bed")) {
            String color = name.substring(0, name.length() - 4);
            return "block/" + color + "_wool";
        }
        if (name.endsWith("_banner")) {
            String color = name.substring(0, name.length() - 7);
            return "block/" + color + "_wool";
        }

        // Carpets
        if (name.endsWith("_carpet")) {
            if (name.equals("moss_carpet")) {
                return "block/moss_block";
            }
            if (name.equals("pale_moss_carpet")) {
                return "block/pale_moss_carpet";
            }
            String color = name.substring(0, name.length() - 7);
            return "block/" + color + "_wool";
        }

        // Harnesses (wolf armor) -> colored wool or lead
        if (name.endsWith("_harness")) {
            String color = name.substring(0, name.length() - 8);
            if (hasTexture("block/" + color + "_wool")) {
                return "block/" + color + "_wool";
            }
            return "item/lead";
        }

        // Chests
        if (name.equals("chest") || name.equals("trapped_chest")) {
            return "block/oak_planks";
        }
        if (name.equals("ender_chest")) {
            return "block/obsidian";
        }
        if (name.contains("copper_chest") || name.contains("copper_golem_statue")) {
            return "block/copper_block";
        }

        // Animated / state items
        if (name.equals("clock")) {
            return "item/clock_00";
        }
        if (name.equals("compass")) {
            return "item/compass_00";
        }
        if (name.equals("recovery_compass")) {
            return "item/recovery_compass_00";
        }
        if (name.equals("crossbow")) {
            return "item/crossbow_standby";
        }
        if (name.equals("enchanted_golden_apple")) {
            return "item/golden_apple";
        }
        if (name.equals("debug_stick")) {
            return "item/stick";
        }
        if (name.equals("bundle")) {
            return "item/bundle";
        }
        if (name.equals("tipped_arrow")) {
            return hasTexture("item/tipped_arrow_base") ? "item/tipped_arrow_base" : "item/arrow";
        }

        // Eggs
        if (name.equals("blue_egg") || name.equals("brown_egg")) {
            return "item/egg";
        }

        // Spears
        if (name.endsWith("_spear")) {
            String base = name.substring(0, name.length() - 6);
            if (hasTexture("item/" + base + "_sword")) {
                return "item/" + base + "_sword";
            }
            return "item/trident";
        }

        // Copper nugget
        if (name.equals("copper_nugget")) {
            return "item/iron_nugget";
        }

        // Goat horn
        if (name.equals("goat_horn")) {
            return "item/goat_horn";
        }

        // Check item/ first if it exists in textures
        if (hasTexture("item/" + name)) {
            return "item/" + name;
        }

        // Clean prefixes (waxed_, infested_)
        String clean = name;
        if (clean.startsWith("waxed_")) {
            clean = clean.substring(6);
        }
        if (clean.startsWith("infested_")) {
            clean = clean.substring(9);
            if (clean.endsWith("_brick")) {
                clean += "s";
            }
        }

        // Check if clean item sprite exists (e.g. waxed_copper_door -> item/copper_door)
        if (hasTexture("item/" + clean)) {
            return "item/" + clean;
        }

        // Direct block check
        if (hasTexture("block/" + clean)) {
            return "block/" + clean;
        }

        // Block overrides
        String override = getBlockSpriteOverride(clean);
        if (override != null) {
            return "block/" + override;
        }

        // Glass panes
        if (clean.equals("glass_pane")) {
            return "block/glass";
        }
        if (clean.endsWith("_stained_glass_pane")) {
            String color = clean.substring(0, clean.length() - 19);
            return "block/" + color + "_stained_glass";
        }

        // Wood logs
        if (clean.endsWith("_wood")) {
            return "block/" + clean.substring(0, clean.length() - 5) + "_log";
        }
        if (clean.endsWith("_hyphae")) {
            return "block/" + clean.substring(0, clean.length() - 7) + "_stem";
        }

        // Slabs, stairs, walls, fences, buttons, pressure plates
        String base = extractBaseBlock(clean);
        if (base != null) {
            return "block/" + resolveBasePlankOrStone(base);
        }

        // Fallback: if isBlock or registry says block, try block/
        if (isBlock(mat)) {
            if (hasTexture("block/" + clean)) {
                return "block/" + clean;
            }
        } else {
            if (hasTexture("item/" + clean)) {
                return "item/" + clean;
            }
        }

        // If known textures is populated and not found, return empty string so no magenta error appears
        if (hasKnownTextures()) {
            if (hasTexture("item/" + name)) {
                return "item/" + name;
            }
            if (hasTexture("block/" + clean)) {
                return "block/" + clean;
            }
            return "";
        }

        return "block/" + clean;
    }

    private static String getBlockSpriteOverride(String name) {
        return switch (name) {
            case "ancient_debris" -> "ancient_debris_side";
            case "barrel" -> "barrel_side";
            case "basalt" -> "basalt_side";
            case "polished_basalt" -> "polished_basalt_side";
            case "beehive" -> "beehive_front";
            case "bee_nest" -> "bee_nest_front";
            case "big_dripleaf" -> "big_dripleaf_top";
            case "small_dripleaf" -> "small_dripleaf_top";
            case "tnt" -> "tnt_side";
            case "crafting_table" -> "crafting_table_front";
            case "furnace" -> "furnace_front";
            case "blast_furnace" -> "blast_furnace_front";
            case "smoker" -> "smoker_front";
            case "fletching_table" -> "fletching_table_front";
            case "smithing_table" -> "smithing_table_front";
            case "cartography_table" -> "cartography_table_side1";
            case "loom" -> "loom_front";
            case "hay_block" -> "hay_block_side";
            case "piston" -> "piston_side";
            case "sticky_piston" -> "piston_top_sticky";
            case "dispenser" -> "dispenser_front";
            case "dropper" -> "dropper_front";
            case "observer" -> "observer_front";
            case "daylight_detector" -> "daylight_detector_top";
            case "chiseled_bookshelf" -> "chiseled_bookshelf_empty";
            case "crafter" -> "crafter_top";
            case "lectern" -> "lectern_top";
            case "bone_block" -> "bone_block_side";
            case "azalea" -> "azalea_side";
            case "flowering_azalea" -> "flowering_azalea_side";
            case "grass_block" -> "grass_block_side";
            case "dirt_path" -> "dirt_path_side";
            case "podzol" -> "podzol_side";
            case "mycelium" -> "mycelium_side";
            case "cactus" -> "cactus_side";
            case "melon" -> "melon_side";
            case "pumpkin" -> "pumpkin_side";
            case "jukebox" -> "jukebox_top";
            case "enchanting_table" -> "enchanting_table_top";
            case "end_portal_frame" -> "end_portal_frame_top";
            case "composter" -> "composter_side";
            case "grindstone" -> "grindstone_side";
            case "stonecutter" -> "stonecutter_top";
            case "lodestone" -> "lodestone_top";
            case "respawn_anchor" -> "respawn_anchor_top";
            case "sculk_sensor" -> "sculk_sensor_top";
            case "calibrated_sculk_sensor" -> "calibrated_sculk_sensor_top";
            case "sculk_catalyst" -> "sculk_catalyst_top";
            case "sculk_shrieker" -> "sculk_shrieker_top";
            case "sniffer_egg" -> "sniffer_egg";
            case "trial_spawner" -> "trial_spawner_top_inactive";
            case "vault" -> "vault_top";
            case "honey_block" -> "honey_block_side";
            case "dried_kelp_block" -> "dried_kelp_side";
            case "dried_ghast" -> "dried_ghast_hydration_0_top";
            case "scaffolding" -> "scaffolding_top";
            case "magma_block" -> "magma";
            case "quartz_block" -> "quartz_block_side";
            case "smooth_quartz" -> "quartz_block_bottom";
            case "sandstone" -> "sandstone";
            case "smooth_sandstone" -> "sandstone_top";
            case "red_sandstone" -> "red_sandstone";
            case "smooth_red_sandstone" -> "red_sandstone_top";
            case "light_weighted_pressure_plate" -> "gold_block";
            case "heavy_weighted_pressure_plate" -> "iron_block";
            case "mangrove_roots" -> "mangrove_roots_side";
            case "muddy_mangrove_roots" -> "muddy_mangrove_roots_side";
            case "suspicious_sand" -> "suspicious_sand_0";
            case "suspicious_gravel" -> "suspicious_gravel_0";
            case "pointed_dripstone" -> "pointed_dripstone_down_tip";
            case "conduit" -> "conduit";
            case "chipped_anvil" -> "chipped_anvil_top";
            case "damaged_anvil" -> "damaged_anvil_top";
            case "sunflower" -> "sunflower_front";
            case "lilac" -> "lilac_top";
            case "rose_bush" -> "rose_bush_top";
            case "peony" -> "peony_top";
            case "tall_grass" -> "tall_grass_top";
            case "large_fern" -> "large_fern_top";
            case "pitcher_plant" -> "pitcher_plant_top";
            case "snow_block" -> "snow";
            case "target" -> "target_top";
            case "command_block" -> "command_block_front";
            case "repeating_command_block" -> "repeating_command_block_front";
            case "chain_command_block" -> "chain_command_block_front";
            case "jigsaw" -> "jigsaw_side";
            case "decorated_pot" -> "terracotta";
            case "reinforced_deepslate" -> "reinforced_deepslate_top";
            case "copper_bars" -> "copper_grate";
            case "copper_chain" -> "chain";
            case "copper_lantern" -> "lantern";
            case "copper_bulb" -> "copper_bulb";
            case "ochre_froglight" -> "ochre_froglight_side";
            case "verdant_froglight" -> "verdant_froglight_side";
            case "pearlescent_froglight" -> "pearlescent_froglight_side";
            default -> null;
        };
    }

    private static String extractBaseBlock(String clean) {
        if (clean.endsWith("_slab")) {
            return clean.substring(0, clean.length() - 5);
        } else if (clean.endsWith("_stairs")) {
            return clean.substring(0, clean.length() - 7);
        } else if (clean.endsWith("_fence_gate")) {
            return clean.substring(0, clean.length() - 11);
        } else if (clean.endsWith("_fence")) {
            return clean.substring(0, clean.length() - 6);
        } else if (clean.endsWith("_wall")) {
            return clean.substring(0, clean.length() - 5);
        } else if (clean.endsWith("_button")) {
            return clean.substring(0, clean.length() - 7);
        } else if (clean.endsWith("_pressure_plate")) {
            return clean.substring(0, clean.length() - 15);
        }
        return null;
    }

    private static String resolveBasePlankOrStone(String base) {
        return switch (base) {
            case "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                 "mangrove", "cherry", "crimson", "warped", "pale_oak" -> base + "_planks";
            case "petrified_oak" -> "oak_planks";
            case "bamboo" -> "bamboo_planks";
            case "bamboo_mosaic" -> "bamboo_mosaic";
            case "brick" -> "bricks";
            case "purpur" -> "purpur_block";
            case "quartz" -> "quartz_block_side";
            case "smooth_quartz" -> "quartz_block_bottom";
            case "sandstone" -> "sandstone";
            case "smooth_sandstone" -> "sandstone_top";
            case "red_sandstone" -> "red_sandstone";
            case "smooth_red_sandstone" -> "red_sandstone_top";
            default -> {
                if (base.endsWith("_brick")) {
                    yield base + "s";
                }
                if (base.endsWith("_tile")) {
                    yield base + "s";
                }
                yield base;
            }
        };
    }

}
