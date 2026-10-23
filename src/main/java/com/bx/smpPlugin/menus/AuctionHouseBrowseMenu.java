// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.AuctionHouseManager;
import com.bx.smpPlugin.models.AuctionBrowseRequest;
import com.bx.smpPlugin.models.AuctionCategory;
import com.bx.smpPlugin.models.AuctionListing;
import com.bx.smpPlugin.models.AuctionPage;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.ShulkerBoxSupport;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AuctionHouseBrowseMenu extends BaseMenu {

    private final AuctionBrowseRequest request;
    private final Map<Integer, AuctionListing> slotMappings = new HashMap<>();
    private AuctionPage renderedPage = new AuctionPage(List.of(), 1, 1, 0);

    public AuctionHouseBrowseMenu(
            SmpPlugin plugin,
            int page,
            AuctionHouseManager.AuctionSort sort
    ) {
        this(plugin, page, sort, "ALL");
    }

    public AuctionHouseBrowseMenu(
            SmpPlugin plugin,
            int page,
            AuctionHouseManager.AuctionSort sort,
            String category
    ) {
        super(plugin, plugin.getAuctionHouseManager().getBrowseTitle(page), plugin.getAuctionHouseManager().getBrowseSize());
        this.request = new AuctionBrowseRequest(page, sort, AuctionCategory.from(category), "");
    }

    public AuctionHouseBrowseMenu(
            SmpPlugin plugin,
            int page,
            AuctionHouseManager.AuctionSort sort,
            String category,
            String search
    ) {
        super(plugin, plugin.getAuctionHouseManager().getBrowseTitle(page), plugin.getAuctionHouseManager().getBrowseSize());
        this.request = new AuctionBrowseRequest(page, sort, AuctionCategory.from(category), search == null ? "" : search);
    }

    public AuctionHouseBrowseMenu(SmpPlugin plugin, AuctionBrowseRequest request) {
        super(plugin, plugin.getAuctionHouseManager().getBrowseTitle(request.page()), plugin.getAuctionHouseManager().getBrowseSize());
        this.request = request;
    }

    @Override
    public void build(Player player) {
        clear();
        slotMappings.clear();

        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        AuctionBrowseRequest effective = new AuctionBrowseRequest(
                request.page(),
                request.sort(),
                request.category(),
                manager.getSearchQuery(player.getUniqueId())
        );
        manager.updateSession(player.getUniqueId(), effective);
        renderedPage = manager.browse(effective);

        int slot = 0;
        for (AuctionListing listing : renderedPage.listings()) {
            ItemStack display = AuctionHouseMenuSupport.createListingDisplay(
                    plugin,
                    manager,
                    listing,
                    listing.sellerUuid().equals(player.getUniqueId())
            );
            if (ShulkerBoxSupport.isShulkerBox(display)) {
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() && meta.getLore() != null
                            ? new ArrayList<>(meta.getLore())
                            : new ArrayList<>();
                    lore.add(ColorUtils.colorize(AuctionHouseMenuSupport.configText(
                            plugin,
                            "GUI.BROWSE.SHULKER.ITEM_COUNT_LORE",
                            "&bItems inside: &3{count}",
                            "{count}", String.valueOf(ShulkerBoxSupport.getItemCount(display))
                    )));
                    lore.add(ColorUtils.colorize(AuctionHouseMenuSupport.configText(
                            plugin,
                            "GUI.BROWSE.SHULKER.PREVIEW_LORE",
                            "&8Right-click to preview"
                    )));
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
            }
            set(slot, display);
            slotMappings.put(slot, listing);
            slot++;
        }

        // Slot 45: Previous page Arrow (only if hasPrevious())
        if (renderedPage.hasPrevious()) {
            set(controlSlot("PREVIOUS", 45), control("PREVIOUS", Material.ARROW, "&fPrevious page",
                    List.of("&7Go to page &f{page}"), "{page}", String.valueOf(renderedPage.page() - 1)));
        }

        // Slot 47: Filter (Hopper). Rows follow SORTING.ALLOWED. The design
        // screenshot is the three-sort case: Lowest / Highest / Recently Listed.
        List<String> filterLore = AuctionHouseMenuSupport.browseFilterLore(
                effective.sort(),
                plugin.getAuctionHouseManager().getAllowedSorts(),
                AuctionHouseMenuSupport.configList(
                        plugin,
                        "GUI.BROWSE.CONTROLS.FILTER.LORE",
                        List.of("&o&7Click to change")
                )
        );
        set(controlSlot("FILTER", 47), AuctionHouseMenuSupport.controlUsingLore(
                plugin,
                "GUI.BROWSE.CONTROLS.FILTER",
                Material.HOPPER,
                "&fFilter",
                filterLore
        ));

        // Slot 48: Quick Buy (Ender Chest) matching Design/Auction/Main Gui or Menu/2.png
        set(controlSlot("QUICK_BUY", 48), control("QUICK_BUY", Material.ENDER_CHEST, "&fQuick Buy", List.of("&o&7Click to view")));

        // Slot 49: Auction (Anvil) matching Design/Auction/Main Gui or Menu/3.png
        set(controlSlot("REFRESH", 49), control("REFRESH", Material.ANVIL, "&fAuction", List.of("&o&7Buy and sell items")));

        // Slot 50: Search (Oak Sign) matching Design/Auction/Main Gui or Menu/4.png
        List<String> searchLore = new ArrayList<>(List.of("&o&7Click to search"));
        if (!effective.search().isBlank()) {
            searchLore.add("&7Current: &e" + effective.search());
            searchLore.add("&8Right-click to clear");
        }
        set(controlSlot("SEARCH", 50), control("SEARCH", Material.OAK_SIGN, "&fSearch", searchLore));

        // Slot 51: Your Items (Chest) matching Design/Auction/Main Gui or Menu/5.png
        set(controlSlot("PLAYER_ITEMS", 51), control("PLAYER_ITEMS", Material.CHEST, "&fYour Items", List.of("&o&7Click to view")));

        // Slot 53: Next page Arrow (only if hasNext())
        if (renderedPage.hasNext()) {
            set(controlSlot("NEXT", 53), control("NEXT", Material.ARROW, "&fNext page",
                    List.of("&7Go to page &f{page}"), "{page}", String.valueOf(renderedPage.page() + 1)));
        }
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        AuctionBrowseRequest current = manager.session(player.getUniqueId()).request();
        AuctionListing listing = slotMappings.get(slot);
        if (listing != null) {
            if (manager.isOnClickCooldown(player.getUniqueId())) {
                return;
            }
            manager.updateClickCooldown(player.getUniqueId());

            if (clickType.isRightClick() && ShulkerBoxSupport.isShulkerBox(listing.item())) {
                AuctionHouseSounds.click(player, plugin);
                navigate(player, () -> new ShulkerPreviewGui(plugin, listing.item(), current).open(player));
                return;
            }
            manager.getPreferenceAsync(player.getUniqueId()).thenAccept(preference ->
                    plugin.getSpigotScheduler().runEntity(player, () -> {
                        boolean fastBuy = preference.fastBuyEnabled()
                                && (PermissionUtils.has(player, "smpplugin.auctionhouse.fastbuy")
                                || PermissionUtils.has(player, "donutauction.fastbuy"));
                        if (fastBuy) {
                            purchase(player, listing, current);
                        } else {
                            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.CONFIRM);
                            if (plugin.getDialogManager() != null
                                    && plugin.getDialogManager().openAuctionPurchase(player, listing.id())) {
                                return;
                            }
                            navigate(player, () -> new ConfirmPurchaseGui(plugin, listing, current).open(player));
                        }
                    })
            );
            return;
        }

        if (slot == controlSlot("PREVIOUS", 45) && renderedPage.hasPrevious()) {
            AuctionHouseSounds.pageTurn(player, plugin);
            open(player, current.withPage(renderedPage.page() - 1), false);
        } else if (slot == controlSlot("FILTER", 47)) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.FILTER);
            open(player, current.withSort(nextSort(current.sort())), false);
        } else if (slot == controlSlot("QUICK_BUY", 48)) {
            QuickBuySounds.play(player, plugin, QuickBuySounds.OPEN);
            navigate(player, () -> new QuickBuyMenu(plugin).open(player));
        } else if (slot == controlSlot("REFRESH", 49)) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.REFRESH);
            manager.refreshCache().thenRun(() -> plugin.getSpigotScheduler().runEntity(
                    player,
                    () -> open(player, current, false)
            ));
        } else if (slot == controlSlot("SEARCH", 50)) {
            if (clickType.isRightClick()) {
                AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.SEARCH);
                manager.clearSearchQuery(player.getUniqueId());
                open(player, current.withSearch(""), false);
                return;
            }
            AuctionHouseSounds.click(player, plugin);
            DialogGateway dialog = plugin.getDialogManager();
            if (dialog != null) {
                player.closeInventory();
                manager.startNavigating(player.getUniqueId());
                if (dialog.openQuickBuySearch(player, current.search(), "ah_browse")) {
                    return;
                }
            }
            var signConfig = plugin.getConfigManager().getAuctionHouse()
                    .getConfigurationSection("GUI.BROWSE.SEARCH_SIGN");
            manager.startNavigating(player.getUniqueId());
            SignInputUtil.openFromConfig(plugin, player, signConfig, text -> {
                String search = text == null || text.isBlank() || text.equalsIgnoreCase("cancel")
                        ? current.search()
                        : text.trim();
                if (search.length() > 64) {
                    search = search.substring(0, 64);
                }
                manager.setSearchQuery(player.getUniqueId(), search);
                AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.SEARCH);
                open(player, current.withSearch(search), false);
            });
        } else if (slot == controlSlot("PLAYER_ITEMS", 51)) {
            AuctionHouseSounds.click(player, plugin);
            navigate(player, () -> new AuctionYourItemsMenu(plugin, AuctionYourItemsMenu.Origin.AUCTION).open(player));
        } else if (slot == controlSlot("NEXT", 53) && renderedPage.hasNext()) {
            AuctionHouseSounds.pageTurn(player, plugin);
            open(player, current.withPage(renderedPage.page() + 1), false);
        }
    }

    private void purchase(Player player, AuctionListing listing, AuctionBrowseRequest current) {
        plugin.getAuctionHouseManager().purchaseListing(player, listing.id())
                .thenAccept(result -> plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (result.success()) {
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                                "AUCTION_HOUSE.PURCHASE_SUCCESS",
                                "{item}", plugin.getAuctionHouseManager().describeItem(listing.item()),
                                "{price}", NumberUtils.format(listing.price()),
                                "{price_formatted}", plugin.getCurrencyManager().formatMoney(listing.price()),
                                "{seller}", plugin.getHideManager().publicName(listing.sellerUuid(), listing.sellerName())
                        )));
                        AuctionHouseSounds.success(player, plugin);
                        plugin.getShopManager().sendPurchaseActionBar(
                                player,
                                plugin.getAuctionHouseManager().describeItem(listing.item()),
                                listing.item() != null ? listing.item().getAmount() : 1,
                                listing.price()
                        );
                    } else {
                        String key = switch (result.reason()) {
                            case DISABLED -> "AUCTION_HOUSE.DISABLED";
                            case NO_PERMISSION -> "AUCTION_HOUSE.NO_PERMISSION";
                            case LISTING_NOT_FOUND -> "AUCTION_HOUSE.LISTING_NOT_FOUND";
                            case NOT_ACTIVE -> "AUCTION_HOUSE.LISTING_NOT_ACTIVE";
                            case OWN_LISTING -> "AUCTION_HOUSE.CANNOT_BUY_OWN";
                            case NO_MONEY -> "AUCTION_HOUSE.NOT_ENOUGH_MONEY";
                            case INVENTORY_FULL -> "AUCTION_HOUSE.FULL_INVENTORY";
                            case NO_PLAYER_DATA, DATABASE_ERROR -> "AUCTION_HOUSE.PURCHASE_DATABASE_ERROR";
                        };
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(key)));
                        AuctionHouseSounds.fail(player, plugin);
                    }
                    open(player, current, true);
                }));
    }

    private void open(Player player, AuctionBrowseRequest next, boolean sound) {
        if (sound) {
            AuctionHouseSounds.click(player, plugin);
        }
        plugin.getAuctionHouseManager().updateSession(player.getUniqueId(), next);
        navigate(player, () -> new AuctionHouseBrowseMenu(plugin, next).open(player));
    }

    private void navigate(Player player, Runnable action) {
        plugin.getAuctionHouseManager().startNavigating(player.getUniqueId());
        action.run();
    }

    private AuctionHouseManager.AuctionSort nextSort(AuctionHouseManager.AuctionSort current) {
        return nextAllowedSort(
                current,
                plugin.getAuctionHouseManager().getAllowedSorts(),
                plugin.getAuctionHouseManager().getDefaultSort()
        );
    }

    static AuctionHouseManager.AuctionSort nextAllowedSort(
            AuctionHouseManager.AuctionSort current,
            List<AuctionHouseManager.AuctionSort> sorts,
            AuctionHouseManager.AuctionSort fallback
    ) {
        if (sorts == null || sorts.isEmpty()) {
            return fallback != null ? fallback : AuctionHouseManager.AuctionSort.NEWEST;
        }
        int index = sorts.indexOf(current);
        return index < 0
                ? (fallback != null ? fallback : sorts.get(0))
                : sorts.get((index + 1) % sorts.size());
    }

    private int controlSlot(String key, int fallback) {
        return AuctionHouseMenuSupport.slot(plugin, "GUI.BROWSE.CONTROLS." + key, fallback);
    }

    private ItemStack control(
            String key,
            Material material,
            String name,
            List<String> lore,
            String... replacements
    ) {
        return AuctionHouseMenuSupport.control(
                plugin,
                "GUI.BROWSE.CONTROLS." + key,
                material,
                name,
                lore,
                replacements
        );
    }

    @Override
    public void onClose(Player player) {
        plugin.getAuctionHouseManager().stopNavigating(player.getUniqueId());
    }
}
