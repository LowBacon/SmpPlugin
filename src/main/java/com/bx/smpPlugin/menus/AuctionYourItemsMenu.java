// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.AuctionHouseManager;
import com.bx.smpPlugin.models.AuctionListing;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuctionYourItemsMenu extends BaseMenu {

    public enum Origin {
        AUCTION,
        SHOP
    }

    public enum YourItemsFilter {
        DEFAULT("Default"),
        LOWEST_PRICE("Lowest Price"),
        HIGHEST_PRICE("Highest Price"),
        RECENTLY_LISTED("Recently Listed");

        private final String displayName;

        YourItemsFilter(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public YourItemsFilter next() {
            YourItemsFilter[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    static final int GRID_SLOTS = 45;
    static final int DEFAULT_UNLOCKED_SLOTS = 18;
    static final int DEFAULT_DONUT_PLUS_SLOTS = 45;
    static final int DEFAULT_PLUS_PAGE_SLOTS = 45;
    static final int PREV_SLOT = 45;
    static final int FILTER_SLOT = 47;
    static final int QUICK_BUY_SLOT = 48;
    static final int AUCTION_SLOT = 49;
    static final int SEARCH_SLOT = 50;
    static final int TRANSACTIONS_SLOT = 51;
    static final int NEXT_SLOT = 53;

    private final Origin origin;
    private final int page;
    private YourItemsFilter filter = YourItemsFilter.DEFAULT;
    private String searchQuery = "";
    private final Map<Integer, AuctionListing> slotListings = new HashMap<>();
    private int unlockedSlots = DEFAULT_UNLOCKED_SLOTS;
    private int plusPageSlots = DEFAULT_PLUS_PAGE_SLOTS;
    private int plusUnlockedSlots = 0;

    public AuctionYourItemsMenu(SmpPlugin plugin) {
        this(plugin, Origin.AUCTION, YourItemsFilter.DEFAULT, "", 1);
    }

    public AuctionYourItemsMenu(SmpPlugin plugin, Origin origin) {
        this(plugin, origin, YourItemsFilter.DEFAULT, "", 1);
    }

    public AuctionYourItemsMenu(SmpPlugin plugin, YourItemsFilter filter, String searchQuery) {
        this(plugin, Origin.AUCTION, filter, searchQuery, 1);
    }

    public AuctionYourItemsMenu(SmpPlugin plugin, Origin origin, YourItemsFilter filter, String searchQuery) {
        this(plugin, origin, filter, searchQuery, 1);
    }

    public AuctionYourItemsMenu(
            SmpPlugin plugin,
            Origin origin,
            YourItemsFilter filter,
            String searchQuery,
            int page
    ) {
        super(plugin, getMenuTitle(plugin), getMenuSize(plugin));
        this.origin = origin == null ? Origin.AUCTION : origin;
        this.filter = filter == null ? YourItemsFilter.DEFAULT : filter;
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        this.page = Math.max(1, Math.min(2, page));
    }

    public static int getMenuSize(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return 54;
        }
        int configured = plugin.getConfigManager().getShop().getInt("YOUR-ITEMS.SIZE", 54);
        return normalizeSize(configured);
    }

    public static int getPlusPageSlots(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return DEFAULT_PLUS_PAGE_SLOTS;
        }
        int configured = plugin.getConfigManager().getShop().getInt("YOUR-ITEMS.PLUS-PAGE-SLOTS", DEFAULT_PLUS_PAGE_SLOTS);
        return Math.max(0, Math.min(GRID_SLOTS, configured));
    }

    public static int getSellButtonSlot(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return 0;
        }
        return plugin.getConfigManager().getShop().getInt("YOUR-ITEMS.SELL-BUTTON.SLOT", 0);
    }

    private static String getMenuTitle(SmpPlugin plugin) {
        if (plugin == null || plugin.getConfigManager() == null || plugin.getConfigManager().getShop() == null) {
            return "Auction -> Your Items";
        }
        return plugin.getConfigManager().getShop().getString("YOUR-ITEMS.TITLE", "Auction -> Your Items");
    }

    private static int normalizeSize(int configured) {
        int size = Math.max(9, Math.min(54, configured));
        return size - (size % 9);
    }

    @Override
    public void build(Player player) {
        clear();
        slotListings.clear();
        var shopCfg = plugin.getConfigManager().getShop();

        unlockedSlots = page1UnlockedSlots(player);
        plusPageSlots = getPlusPageSlots(plugin);
        plusUnlockedSlots = page2UnlockedSlots(player);
        int sellButtonSlot = getSellButtonSlot(plugin);

        List<AuctionListing> listings = sortedListings(player);

        if (page > 1) {
            buildPlusPage(listings);
            buildBottomRow();
            return;
        }

        String priceFmt = shopCfg.getString("YOUR-ITEMS.ACTIVE-ITEM.PRICE-FORMAT", "&7Price: &a${price}");
        String expiresFmt = shopCfg.getString("YOUR-ITEMS.ACTIVE-ITEM.EXPIRES-FORMAT", "&7Expires in: &e{time}");
        String cancelLore = shopCfg.getString("YOUR-ITEMS.ACTIVE-ITEM.CANCEL-LORE", "&cClick to cancel listing");

        int contentSlots = Math.min(GRID_SLOTS, Math.max(0, (inventory != null ? inventory.getSize() : getMenuSize(plugin)) - 9));
        for (int slot = 0; slot < contentSlots; slot++) {
            if (slot < listings.size()) {
                set(slot, listingDisplay(listings.get(slot), priceFmt, expiresFmt, cancelLore));
                slotListings.put(slot, listings.get(slot));
            } else if (slot < unlockedSlots) {
                set(slot, listPane(slot == sellButtonSlot));
            } else {
                set(slot, lockedPane(false));
            }
        }

        buildBottomRow();
    }

    private void buildPlusPage(List<AuctionListing> listings) {
        var shopCfg = plugin.getConfigManager().getShop();
        String priceFmt = shopCfg.getString("YOUR-ITEMS.ACTIVE-ITEM.PRICE-FORMAT", "&7Price: &a${price}");
        String expiresFmt = shopCfg.getString("YOUR-ITEMS.ACTIVE-ITEM.EXPIRES-FORMAT", "&7Expires in: &e{time}");
        String cancelLore = shopCfg.getString("YOUR-ITEMS.ACTIVE-ITEM.CANCEL-LORE", "&cClick to cancel listing");
        int contentSlots = Math.min(plusPageSlots, Math.max(0, (inventory != null ? inventory.getSize() : getMenuSize(plugin)) - 9));
        int start = GRID_SLOTS;
        for (int slot = 0; slot < contentSlots; slot++) {
            int listingIndex = start + slot;
            if (listingIndex < listings.size()) {
                set(slot, listingDisplay(listings.get(listingIndex), priceFmt, expiresFmt, cancelLore));
                slotListings.put(slot, listings.get(listingIndex));
            } else if (slot < plusUnlockedSlots) {
                set(slot, listPane());
            } else {
                set(slot, lockedPane(true));
            }
        }
    }

    private ItemStack listingDisplay(AuctionListing listing, String priceFmt, String expiresFmt, String cancelLore) {
        ItemStack display = listing.item().clone();
        String name = plugin.getAuctionHouseManager().describeItem(listing.item());
        long remainingSec = Math.max(0, (listing.expiresAt() - System.currentTimeMillis()) / 1000);
        List<String> lore = List.of(
                priceFmt.replace("{price}", NumberUtils.format(listing.price())),
                expiresFmt.replace("{time}", NumberUtils.formatTime(remainingSec)),
                "",
                cancelLore
        );
        return ItemUtils.withDisplay(display, "&f" + name, lore);
    }

    private void buildBottomRow() {
        var shopCfg = plugin.getConfigManager().getShop();

        if (page > 1) {
            int prevSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.PREV.SLOT", PREV_SLOT);
            set(prevSlot, chrome(
                    ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.PREV.MATERIAL", "ARROW")),
                    shopCfg.getString("YOUR-ITEMS.BUTTONS.PREV.NAME", "&fPrevious page"),
                    ItemUtils.readLore(shopCfg, "YOUR-ITEMS.BUTTONS.PREV.LORE",
                            List.of("&o&7Click to view previous page"))
            ));
        }

        int filterSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.FILTER.SLOT", FILTER_SLOT);
        List<String> filterLore = new ArrayList<>(ItemUtils.readLore(
                shopCfg, "YOUR-ITEMS.BUTTONS.FILTER.LORE", List.of("&o&7Click to change")));
        filterLore.add("");
        for (YourItemsFilter f : YourItemsFilter.values()) {
            filterLore.add((f == filter ? "&f▪ " : "&8▪ ") + f.getDisplayName());
        }
        set(filterSlot, chrome(
                ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.FILTER.MATERIAL", "HOPPER")),
                shopCfg.getString("YOUR-ITEMS.BUTTONS.FILTER.NAME", "&fFilter"),
                filterLore
        ));

        int quickBuySlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.QUICK-BUY.SLOT", QUICK_BUY_SLOT);
        set(quickBuySlot, chrome(
                ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.QUICK-BUY.MATERIAL", "ENDER_CHEST")),
                shopCfg.getString("YOUR-ITEMS.BUTTONS.QUICK-BUY.NAME", "&fQuick Buy"),
                ItemUtils.readLore(shopCfg, "YOUR-ITEMS.BUTTONS.QUICK-BUY.LORE", List.of("&o&7Click to view"))
        ));

        int auctionSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.AUCTION.SLOT", AUCTION_SLOT);
        set(auctionSlot, chrome(
                ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.AUCTION.MATERIAL", "ANVIL")),
                shopCfg.getString("YOUR-ITEMS.BUTTONS.AUCTION.NAME", "&fAuction"),
                ItemUtils.readLore(shopCfg, "YOUR-ITEMS.BUTTONS.AUCTION.LORE", List.of("&o&7Click to view"))
        ));

        int searchSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        List<String> searchLore = new ArrayList<>(ItemUtils.readLore(
                shopCfg, "YOUR-ITEMS.BUTTONS.SEARCH.LORE", List.of("&o&7Click to search")));
        if (!searchQuery.isBlank()) {
            for (String line : ItemUtils.readLore(shopCfg, "YOUR-ITEMS.BUTTONS.SEARCH.ACTIVE-QUERY-LORE",
                    List.of("&7Current: &e{query}", "&8Right-click to clear"))) {
                searchLore.add(line.replace("{query}", searchQuery));
            }
        }
        set(searchSlot, chrome(
                ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.SEARCH.MATERIAL", "OAK_SIGN")),
                shopCfg.getString("YOUR-ITEMS.BUTTONS.SEARCH.NAME", "&fSearch"),
                searchLore
        ));

        int transSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.TRANSACTIONS.SLOT", TRANSACTIONS_SLOT);
        set(transSlot, chrome(
                ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.TRANSACTIONS.MATERIAL", "WRITABLE_BOOK")),
                shopCfg.getString("YOUR-ITEMS.BUTTONS.TRANSACTIONS.NAME", "&fTransactions"),
                ItemUtils.readLore(shopCfg, "YOUR-ITEMS.BUTTONS.TRANSACTIONS.LORE",
                        List.of("&o&7Click to view your transactions"))
        ));

        if (page == 1) {
            int nextSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.NEXT.SLOT", NEXT_SLOT);
            set(nextSlot, chrome(
                    ItemUtils.parseMaterial(shopCfg.getString("YOUR-ITEMS.BUTTONS.NEXT.MATERIAL", "ARROW")),
                    shopCfg.getString("YOUR-ITEMS.BUTTONS.NEXT.NAME", "&fNext page"),
                    ItemUtils.readLore(shopCfg, "YOUR-ITEMS.BUTTONS.NEXT.LORE",
                            List.of("&o&7Click to view next page"))
            ));
        }
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        var shopCfg = plugin.getConfigManager().getShop();
        int prevSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.PREV.SLOT", PREV_SLOT);
        int filterSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.FILTER.SLOT", FILTER_SLOT);
        int quickBuySlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.QUICK-BUY.SLOT", QUICK_BUY_SLOT);
        int auctionSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.AUCTION.SLOT", AUCTION_SLOT);
        int searchSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        int transSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.TRANSACTIONS.SLOT", TRANSACTIONS_SLOT);
        int nextSlot = shopCfg.getInt("YOUR-ITEMS.BUTTONS.NEXT.SLOT", NEXT_SLOT);

        int contentSlots = Math.min(page > 1 ? plusPageSlots : GRID_SLOTS, Math.max(0, (inventory != null ? inventory.getSize() : getMenuSize(plugin)) - 9));
        if (slot >= 0 && slot < contentSlots) {
            AuctionListing listing = slotListings.get(slot);
            if (listing != null) {
                click(player);
                plugin.getAuctionHouseManager().cancelListing(player, listing.id()).thenAccept(result ->
                        plugin.getSpigotScheduler().runEntity(player, () -> {
                            if (result.success()) {
                                AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.CANCEL);
                                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                        "AUCTION_HOUSE.LISTING_CANCELLED",
                                        "&eListing #{listing_id} &f({item}) &ehas been returned to your inventory.",
                                        "{listing_id}", String.valueOf(result.listing().id()),
                                        "{item}", plugin.getAuctionHouseManager().describeItem(result.listing().item())
                                )));
                            } else {
                                AuctionHouseSounds.fail(player, plugin);
                                if (result.reason() == AuctionHouseManager.CancelFailureReason.INVENTORY_FULL) {
                                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                            "AUCTION_HOUSE.CLAIM_INVENTORY_FULL",
                                            "&cYou need a free inventory slot to claim that item."
                                    )));
                                } else {
                                    player.sendMessage(ColorUtils.toComponent("&cFailed to cancel listing."));
                                }
                            }
                            reopen(player, page);
                        }));
                return;
            }

            int sellButtonSlot = getSellButtonSlot(plugin);
            if ((page == 1 && (slot == sellButtonSlot || slot < unlockedSlots)) || (page > 1 && slot < plusUnlockedSlots)) {
                click(player);
                new QuickBuyInsertItemMenu(plugin).open(player);
                return;
            }

            click(player);
            return;
        }

        if (slot == filterSlot) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.FILTER);
            filter = filter.next();
            reopen(player, 1);
            return;
        }

        if (slot == quickBuySlot) {
            QuickBuySounds.play(player, plugin, QuickBuySounds.OPEN);
            new QuickBuyMenu(plugin).open(player);
            return;
        }

        if (slot == auctionSlot) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.OPEN);
            new AuctionHouseBrowseMenu(plugin, 1, AuctionHouseManager.AuctionSort.PRICE_LOWEST).open(player);
            return;
        }

        if (slot == searchSlot) {
            if (clickType.isRightClick() && !searchQuery.isBlank()) {
                AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.SEARCH);
                searchQuery = "";
                reopen(player, 1);
                return;
            }
            click(player);
            DialogGateway dialog = plugin.getDialogManager();
            if (dialog != null) {
                player.closeInventory();
                if (dialog.openQuickBuySearch(player, searchQuery, searchSource())) {
                    return;
                }
            }
            SignInputUtil.open(plugin, player, List.of("", "↑↑↑↑↑", "Search", ""), 0, input -> {
                searchQuery = input == null || input.isBlank() ? "" : input.trim();
                AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.SEARCH);
                reopen(player, 1);
            });
            return;
        }

        if (slot == transSlot) {
            click(player);
            new QuickBuyTransactionsMenu(plugin, 0, origin).open(player);
            return;
        }

        if (slot == nextSlot && page == 1) {
            AuctionHouseSounds.pageTurn(player, plugin);
            reopen(player, 2);
            return;
        }

        if (slot == prevSlot && page > 1) {
            AuctionHouseSounds.pageTurn(player, plugin);
            reopen(player, 1);
        }
    }

    private List<AuctionListing> sortedListings(Player player) {
        List<AuctionListing> listings = new ArrayList<>(plugin.getAuctionHouseManager().getPlayerListings(player.getUniqueId()));
        listings.removeIf(l -> !l.active() || l.expiresAt() <= System.currentTimeMillis());

        if (!searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase(Locale.ROOT);
            listings.removeIf(l -> !plugin.getAuctionHouseManager().describeItem(l.item()).toLowerCase(Locale.ROOT).contains(q));
        }

        if (filter == YourItemsFilter.LOWEST_PRICE) {
            listings.sort(Comparator.comparingDouble(AuctionListing::price));
        } else if (filter == YourItemsFilter.HIGHEST_PRICE) {
            listings.sort(Comparator.comparingDouble(AuctionListing::price).reversed());
        } else if (filter == YourItemsFilter.RECENTLY_LISTED) {
            listings.sort(Comparator.comparingLong(AuctionListing::createdAt).reversed());
        }
        return listings;
    }

    private int page1UnlockedSlots(Player player) {
        var shopCfg = plugin.getConfigManager().getShop();
        int freeSlots = Math.max(1, Math.min(GRID_SLOTS,
                shopCfg.getInt("YOUR-ITEMS.UNLOCKED-SLOTS", DEFAULT_UNLOCKED_SLOTS)));
        int donutPlusSlots = Math.max(freeSlots, Math.min(GRID_SLOTS,
                shopCfg.getInt("YOUR-ITEMS.DONUT-PLUS-SLOTS", DEFAULT_DONUT_PLUS_SLOTS)));
        if (hasDonutPlus(player) || hasDonutPlusPlus(player)) {
            return donutPlusSlots;
        }
        if (player != null && plugin.getAuctionHouseManager() != null) {
            int limit = plugin.getAuctionHouseManager().getMaxActiveListings(player);
            freeSlots = Math.max(freeSlots, Math.min(GRID_SLOTS, limit));
        }
        return freeSlots;
    }

    private int page2UnlockedSlots(Player player) {
        return hasDonutPlusPlus(player) ? plusPageSlots : 0;
    }

    private boolean hasDonutPlus(Player player) {
        return PermissionUtils.hasExact(player, "smpplugin.donutplus")
                || PermissionUtils.hasExact(player, "donutplus");
    }

    private boolean hasDonutPlusPlus(Player player) {
        return PermissionUtils.hasExact(player, "smpplugin.donutplusplus")
                || PermissionUtils.hasExact(player, "donutplusplus")
                || PermissionUtils.hasExact(player, "smpplugin.donutplusplusplus")
                || PermissionUtils.hasExact(player, "donutplusplusplus");
    }

    private ItemStack listPane() {
        return listPane(false);
    }

    private ItemStack listPane(boolean isSellButton) {
        var shopCfg = plugin.getConfigManager().getShop();
        String primary = isSellButton ? "YOUR-ITEMS.SELL-BUTTON" : "YOUR-ITEMS.EMPTY-SLOT";
        String fallback = isSellButton ? "YOUR-ITEMS.EMPTY-SLOT" : "YOUR-ITEMS.SELL-BUTTON";
        String rawEmptyMat = shopCfg.getString(primary + ".MATERIAL",
                shopCfg.getString(fallback + ".MATERIAL", "GRAY_STAINED_GLASS_PANE"));
        if ("HOPPER".equalsIgnoreCase(rawEmptyMat)) {
            rawEmptyMat = "GRAY_STAINED_GLASS_PANE";
        }
        return chrome(
                ItemUtils.parseMaterial(rawEmptyMat),
                shopCfg.getString(primary + ".NAME",
                        shopCfg.getString(fallback + ".NAME", "&fList")),
                ItemUtils.readLore(shopCfg, primary + ".LORE",
                        ItemUtils.readLore(shopCfg, fallback + ".LORE",
                                List.of("&o&7Click to sell an item")))
        );
    }

    private ItemStack lockedPane(boolean plusPlus) {
        var shopCfg = plugin.getConfigManager().getShop();
        String path = plusPlus ? "YOUR-ITEMS.LOCKED-PLUS-SLOT" : "YOUR-ITEMS.LOCKED-SLOT";
        String loreFallback = plusPlus
                ? "&fBuy Donut&b++&f for even more auction slots"
                : "&fBuy Donut&b+&f for more auction slots";
        return chrome(
                ItemUtils.parseMaterial(shopCfg.getString(path + ".MATERIAL",
                        shopCfg.getString("YOUR-ITEMS.LOCKED-SLOT.MATERIAL", "RED_STAINED_GLASS_PANE"))),
                shopCfg.getString(path + ".NAME", "&cLocked"),
                ItemUtils.readLore(shopCfg, path + ".LORE", List.of(loreFallback))
        );
    }

    private ItemStack chrome(Material material, String name, List<String> lore) {
        return ItemUtils.hideMenuNoise(ItemUtils.createItem(material, name, lore), true);
    }

    private void reopen(Player player, int nextPage) {
        new AuctionYourItemsMenu(plugin, origin, filter, searchQuery, nextPage).open(player);
    }

    private String searchSource() {
        return origin == Origin.SHOP ? "your_items_shop" : "your_items";
    }

    private void click(Player player) {
        AuctionHouseSounds.click(player, plugin);
    }
}
