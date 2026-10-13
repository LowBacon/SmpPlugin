package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.AuctionHouseManager;
import com.bx.smpPlugin.managers.QuickBuyBedrockManager;
import com.bx.smpPlugin.managers.QuickBuySearchPrompt;
import com.bx.smpPlugin.managers.ShopManager;
import com.bx.smpPlugin.models.QuickBuyEntry;
import com.bx.smpPlugin.utils.BedrockPlayers;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class QuickBuyMenu extends BaseMenu {

    public enum Filter {
        DEFAULT("Default"),
        CHEAPEST("Cheapest"),
        MOST_EXPENSIVE("Most Expensive"),
        NAME("Name");

        private final String displayName;

        Filter(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Filter next() {
            Filter[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private static final int DEFAULT_SIZE = 54;
    private static final int FILTER_SLOT = 47;
    private static final int REFRESH_SLOT = 48;
    private static final int AUCTION_SLOT = 49;
    private static final int SEARCH_SLOT = 50;
    private static final int YOUR_ITEMS_SLOT = 51;
    private static final int EDIT_SLOT = 53;
    private static final int QUICK_BUY_SLOTS = 45;

    private Filter filter = Filter.DEFAULT;
    private boolean editMode = false;
    private String searchQuery = "";
    private final Set<Integer> markedForRemoval = new HashSet<>();
    private final Map<Integer, QuickBuyEntry> currentEntries = new HashMap<>();

    public QuickBuyMenu(SmpPlugin plugin) {
        super(plugin, getMenuTitle(plugin), getMenuSize(plugin));
    }

    public QuickBuyMenu(SmpPlugin plugin, Filter filter, boolean editMode, String searchQuery) {
        super(plugin, getMenuTitle(plugin), getMenuSize(plugin));
        this.filter = filter == null ? Filter.DEFAULT : filter;
        this.editMode = editMode;
        this.searchQuery = searchQuery == null ? "" : searchQuery;
    }

    public static int getMenuSize(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return DEFAULT_SIZE;
        }
        int configured = plugin.getConfigManager().getShop().getInt("QUICK-BUY.SIZE", DEFAULT_SIZE);
        return normalizeSize(configured);
    }

    private static String getMenuTitle(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return "&8Quick Buy";
        }
        return plugin.getConfigManager().getShop().getString("QUICK-BUY.TITLE", "&8Quick Buy");
    }

    private static int normalizeSize(int configured) {
        int size = Math.max(9, Math.min(54, configured));
        return size - (size % 9);
    }

    private int quickBuySlots() {
        return Math.max(0, Math.min(QUICK_BUY_SLOTS, (inventory != null ? inventory.getSize() : getMenuSize(plugin)) - 9));
    }

    @Override
    public void build(Player player) {
        clear();
        currentEntries.clear();
        Map<Integer, QuickBuyEntry> stored = plugin.getShopManager().getQuickBuyEntries(player.getUniqueId());

        List<QuickBuyEntry> displayList = new ArrayList<>();
        int itemSlots = quickBuySlots();
        for (int slot = 0; slot < itemSlots; slot++) {
            QuickBuyEntry entry = stored.get(slot);
            if (entry != null && !entry.isEmpty()) {
                if (searchQuery.isBlank() || itemName(entry.material()).toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT))) {
                    displayList.add(entry);
                }
            }
        }

        if (filter == Filter.CHEAPEST) {
            displayList.sort(Comparator.comparingDouble(e -> {
                var q = plugin.getShopManager().resolveQuickBuyQuote(player, e);
                return q.outOfStock() ? Double.MAX_VALUE : ShopManager.pricePaidFor(q);
            }));
        } else if (filter == Filter.MOST_EXPENSIVE) {
            displayList.sort(Comparator.comparingDouble((QuickBuyEntry e) -> {
                var q = plugin.getShopManager().resolveQuickBuyQuote(player, e);
                return q.outOfStock() ? -1D : ShopManager.pricePaidFor(q);
            }).reversed());
        } else if (filter == Filter.NAME) {
            displayList.sort(Comparator.comparing(e -> itemName(e.material()), String.CASE_INSENSITIVE_ORDER));
        }

        if (filter == Filter.DEFAULT) {
            for (int slot = 0; slot < itemSlots; slot++) {
                QuickBuyEntry entry = stored.get(slot);
                if (entry != null && !entry.isEmpty()
                        && (searchQuery.isBlank() || itemName(entry.material()).toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT)))) {
                    renderSlot(player, slot, entry);
                } else {
                    renderEmptySlot(slot);
                }
            }
        } else {
            for (int i = 0; i < itemSlots; i++) {
                if (i < displayList.size()) {
                    renderSlot(player, i, displayList.get(i));
                } else {
                    renderEmptySlot(i);
                }
            }
        }

        buildBottomRow(player);
    }

    private void renderSlot(Player player, int slot, QuickBuyEntry entry) {
        currentEntries.put(slot, entry);
        var quote = plugin.getShopManager().resolveQuickBuyQuote(player, entry);
        var shopCfg = plugin.getConfigManager().getShop();
        String priceFormat = shopCfg.getString("QUICK-BUY.ITEM.PRICE-FORMAT", "&a$ &f{price}");
        String outOfStockText = shopCfg.getString("QUICK-BUY.ITEM.OUT-OF-STOCK", "&cOut of stock");
        String removeText = shopCfg.getString("QUICK-BUY.ITEM.EDIT-REMOVE-LORE", "&o&7Click to remove");
        String restoreText = shopCfg.getString("QUICK-BUY.ITEM.EDIT-RESTORE-LORE", "&o&7Click to add back");

        String priceText;
        if (quote.outOfStock()) {
            priceText = ColorUtils.colorize(outOfStockText);
        } else {
            priceText = ColorUtils.colorize(priceFormat.replace("{price}", NumberUtils.formatNice(ShopManager.pricePaidFor(quote))));
        }

        List<String> lore = new ArrayList<>();
        lore.add(priceText);
        if (editMode) {
            if (markedForRemoval.contains(entry.slot())) {
                lore.add(ColorUtils.colorize(restoreText));
            } else {
                lore.add(ColorUtils.colorize(removeText));
            }
        }

        int displayAmount = Math.max(1, Math.min(entry.material().getMaxStackSize(), entry.buyAmount()));
        ItemStack item = entry.createItem(displayAmount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize("&f" + itemName(entry.material())));
            List<String> combinedLore = new ArrayList<>();
            if (meta.hasLore() && meta.getLore() != null) {
                combinedLore.addAll(meta.getLore());
            }
            combinedLore.addAll(lore);
            meta.setLore(combinedLore);
            item.setItemMeta(meta);
        }
        set(slot, item);
    }

    private void renderEmptySlot(int slot) {
        Material emptyMat = ItemUtils.parseMaterial(plugin.getConfigManager().getShop().getString("QUICK-BUY.EMPTY-SLOT.MATERIAL", "GRAY_STAINED_GLASS_PANE"));
        String name = plugin.getConfigManager().getShop().getString("QUICK-BUY.EMPTY-SLOT.NAME", "&fEmpty");
        List<String> lore = plugin.getConfigManager().getShop().getStringList("QUICK-BUY.EMPTY-SLOT.LORE");
        if (lore == null || lore.isEmpty()) {
            lore = List.of("&o&7Click to choose", "&o&7an item to buy");
        }
        set(slot, ItemUtils.createItem(emptyMat, name, lore));
    }

    private void buildBottomRow(Player player) {
        var shopCfg = plugin.getConfigManager().getShop();

        // Filter button
        int filterSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.FILTER.SLOT", FILTER_SLOT);
        Material filterMat = ItemUtils.parseMaterial(shopCfg.getString("QUICK-BUY.BUTTONS.FILTER.MATERIAL", "HOPPER"));
        String filterName = shopCfg.getString("QUICK-BUY.BUTTONS.FILTER.NAME", "&fFilter");
        List<String> filterLore = new ArrayList<>(ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.FILTER.LORE", List.of("&o&7Click to change")));
        filterLore.add("");
        for (Filter f : Filter.values()) {
            if (f == filter) {
                filterLore.add("&f• " + f.getDisplayName());
            } else {
                filterLore.add("&8• " + f.getDisplayName());
            }
        }
        set(filterSlot, ItemUtils.createItem(filterMat, filterName, filterLore));

        // Ender Chest (Quick Buy / Refresh)
        int refreshSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.REFRESH.SLOT", REFRESH_SLOT);
        Material refreshMat = ItemUtils.parseMaterial(shopCfg.getString("QUICK-BUY.BUTTONS.REFRESH.MATERIAL", "ENDER_CHEST"));
        String refreshName = shopCfg.getString("QUICK-BUY.BUTTONS.REFRESH.NAME", "&fQuick Buy");
        List<String> refreshLore = ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.REFRESH.LORE", List.of("&o&7Click to refresh prices"));
        set(refreshSlot, ItemUtils.createItem(refreshMat, refreshName, refreshLore));

        // Anvil (Auction)
        int auctionSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.AUCTION.SLOT", AUCTION_SLOT);
        Material auctionMat = ItemUtils.parseMaterial(shopCfg.getString("QUICK-BUY.BUTTONS.AUCTION.MATERIAL", "ANVIL"));
        String auctionName = shopCfg.getString("QUICK-BUY.BUTTONS.AUCTION.NAME", "&fAuction");
        List<String> auctionLore = ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.AUCTION.LORE", List.of("&o&7Click to view"));
        set(auctionSlot, ItemUtils.createItem(auctionMat, auctionName, auctionLore));

        // Sign (Search)
        int searchSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        Material searchMat = ItemUtils.parseMaterial(shopCfg.getString("QUICK-BUY.BUTTONS.SEARCH.MATERIAL", "OAK_SIGN"));
        String searchName = shopCfg.getString("QUICK-BUY.BUTTONS.SEARCH.NAME", "&fSearch");
        List<String> searchLore = new ArrayList<>(ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.SEARCH.LORE", List.of("&o&7Click to search")));
        if (!searchQuery.isBlank()) {
            List<String> activeLore = ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.SEARCH.ACTIVE-QUERY-LORE", List.of("&7Current: &e{query}", "&8Right-click to clear"));
            for (String l : activeLore) {
                searchLore.add(l.replace("{query}", searchQuery));
            }
        }
        set(searchSlot, ItemUtils.createItem(searchMat, searchName, searchLore));

        // Chest (Your Items)
        int yourItemsSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.YOUR-ITEMS.SLOT", YOUR_ITEMS_SLOT);
        Material yourItemsMat = ItemUtils.parseMaterial(shopCfg.getString("QUICK-BUY.BUTTONS.YOUR-ITEMS.MATERIAL", "CHEST"));
        String yourItemsName = shopCfg.getString("QUICK-BUY.BUTTONS.YOUR-ITEMS.NAME", "&fYour Items");
        List<String> yourItemsLore = ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.YOUR-ITEMS.LORE", List.of("&o&7Click to view"));
        set(yourItemsSlot, ItemUtils.createItem(yourItemsMat, yourItemsName, yourItemsLore));

        // Stick (Edit)
        int editSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.EDIT.SLOT", EDIT_SLOT);
        Material editMat = ItemUtils.parseMaterial(shopCfg.getString("QUICK-BUY.BUTTONS.EDIT.MATERIAL", "STICK"));
        String editName = shopCfg.getString("QUICK-BUY.BUTTONS.EDIT.NAME", "&fEdit");
        List<String> editNormal = ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.EDIT.LORE-NORMAL", List.of("&o&7Click to edit"));
        List<String> editEditing = ItemUtils.readLore(shopCfg, "QUICK-BUY.BUTTONS.EDIT.LORE-EDITING", List.of("&o&7Click to save changes"));
        ItemStack stick = ItemUtils.createItem(
                editMat,
                editName,
                editMode ? editEditing : editNormal
        );
        ItemUtils.setGlint(stick, true);
        set(editSlot, stick);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        var shopCfg = plugin.getConfigManager().getShop();
        int filterSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.FILTER.SLOT", FILTER_SLOT);
        int refreshSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.REFRESH.SLOT", REFRESH_SLOT);
        int auctionSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.AUCTION.SLOT", AUCTION_SLOT);
        int searchSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        int yourItemsSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.YOUR-ITEMS.SLOT", YOUR_ITEMS_SLOT);
        int editSlot = shopCfg.getInt("QUICK-BUY.BUTTONS.EDIT.SLOT", EDIT_SLOT);

        if (slot == filterSlot) {
            QuickBuySounds.play(player, plugin, QuickBuySounds.FILTER);
            filter = filter.next();
            build(player);
            return;
        }

        if (slot == refreshSlot) {
            QuickBuySounds.play(player, plugin, QuickBuySounds.REFRESH);
            if (plugin.getAuctionHouseManager() != null) {
                plugin.getAuctionHouseManager().refreshCache().thenRun(() ->
                        plugin.getSpigotScheduler().runEntity(player, () -> build(player)));
            } else {
                build(player);
            }
            return;
        }

        if (slot == auctionSlot) {
            click(player);
            new AuctionHouseBrowseMenu(plugin, 1, AuctionHouseManager.AuctionSort.NEWEST).open(player);
            return;
        }

        if (slot == searchSlot) {
            if (clickType.isRightClick() && !searchQuery.isBlank()) {
                QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
                searchQuery = "";
                build(player);
                return;
            }
            click(player);
            if (BedrockPlayers.isBedrock(player)) {
                QuickBuyBedrockManager bedrock = plugin.getQuickBuyBedrockManager();
                if (bedrock != null && bedrock.openListingSearch(player, filter, editMode, searchQuery)) {
                    return;
                }
                QuickBuySearchPrompt prompt = plugin.getQuickBuySearchPrompt();
                if (prompt != null) {
                    prompt.askListing(player, filter, editMode, searchQuery);
                }
                return;
            }
            DialogGateway dialog = plugin.getDialogManager();
            if (dialog != null) {
                player.closeInventory();
                if (dialog.openQuickBuySearch(player, searchQuery, "main")) {
                    return;
                }
            }
            SignInputUtil.open(plugin, player, List.of("", "↑↑↑↑↑", "Search", ""), 0, input -> {
                searchQuery = input == null || input.isBlank() ? "" : input.trim();
                QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
                new QuickBuyMenu(plugin, filter, editMode, searchQuery).open(player);
            });
            return;
        }

        if (slot == yourItemsSlot) {
            click(player);
            new AuctionYourItemsMenu(plugin, AuctionYourItemsMenu.Origin.SHOP).open(player);
            return;
        }

        if (slot == editSlot) {
            if (editMode) {
                QuickBuySounds.play(player, plugin, QuickBuySounds.SAVE);
                for (int removedSlot : markedForRemoval) {
                    plugin.getShopManager().removeQuickBuyEntry(player.getUniqueId(), removedSlot);
                }
                markedForRemoval.clear();
                editMode = false;
            } else {
                QuickBuySounds.play(player, plugin, QuickBuySounds.EDIT);
                editMode = true;
            }
            build(player);
            return;
        }

        if (slot >= 0 && slot < quickBuySlots()) {
            QuickBuyEntry entry = currentEntries.get(slot);
            if (editMode) {
                if (entry != null && !entry.isEmpty()) {
                    if (markedForRemoval.contains(entry.slot())) {
                        QuickBuySounds.play(player, plugin, QuickBuySounds.PIN);
                        markedForRemoval.remove(entry.slot());
                    } else {
                        QuickBuySounds.play(player, plugin, QuickBuySounds.REMOVE);
                        markedForRemoval.add(entry.slot());
                    }
                    build(player);
                } else {
                    click(player);
                    openItemPicker(player, slot);
                }
                return;
            }

            // Normal mode
            if (entry == null || entry.isEmpty()) {
                click(player);
                openItemPicker(player, slot);
            } else {
                executeBuy(player, entry);
            }
        }
    }

    private void executeBuy(Player player, QuickBuyEntry entry) {
        plugin.getShopManager().executeQuickBuy(player, entry).thenAccept(result ->
                plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (result.success()) {
                        QuickBuySounds.playPurchase(player, plugin);
                        String successMsg = plugin.getConfigManager().getMessageOrDefault(
                                "QUICK_BUY.BOUGHT",
                                "&aPurchased &f{amount}x {item}&a for &f${price}&a!",
                                "{amount}", String.valueOf(result.amountBought()),
                                "{item}", itemName(entry.material()),
                                "{price}", NumberUtils.format(result.pricePaid())
                        );
                        player.sendMessage(ColorUtils.toComponent(successMsg));
                        plugin.getShopManager().sendPurchaseActionBar(
                                player,
                                itemName(entry.material()),
                                result.amountBought(),
                                result.pricePaid()
                        );
                    } else {
                        QuickBuySounds.fail(player, plugin);
                        if (!result.message().isBlank()) {
                            player.sendMessage(ColorUtils.toComponent(result.message()));
                        }
                    }
                    build(player);
                }));
    }

    private void openItemPicker(Player player, int targetSlot) {
        if (BedrockPlayers.isBedrock(player)) {
            if (plugin.openQuickBuyBedrockCatalogue(player, targetSlot)) {
                return;
            }
            new QuickBuyItemSelectMenu(plugin, targetSlot, 0, "").open(player);
            return;
        }
        DialogGateway dialog = plugin.getDialogManager();
        if (dialog != null && dialog.openQuickBuySelection(player, targetSlot, "")) {
            return;
        }
        new QuickBuyItemSelectMenu(plugin, targetSlot, 0, "").open(player);
    }

    private void click(Player player) {
        QuickBuySounds.click(player, plugin);
    }

    private String itemName(Material material) {
        return plugin.getWorthManager().prettifyMaterial(material);
    }
}
