// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.DialogGateway;
import com.bx.smpPlugin.models.AuctionListing;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuickBuyTransactionsMenu extends BaseMenu {

    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int BACK_SLOT = 46;
    private static final int STATS_SLOT = 48;
    private static final int REFRESH_SLOT = 49;
    private static final int SEARCH_SLOT = 50;
    private static final int NEXT_PAGE_SLOT = 53;

    private int page;
    private String searchQuery;
    private final AuctionYourItemsMenu.Origin origin;

    public QuickBuyTransactionsMenu(SmpPlugin plugin, int page) {
        this(plugin, page, "", AuctionYourItemsMenu.Origin.AUCTION);
    }

    public QuickBuyTransactionsMenu(SmpPlugin plugin, int page, String searchQuery) {
        this(plugin, page, searchQuery, AuctionYourItemsMenu.Origin.AUCTION);
    }

    public QuickBuyTransactionsMenu(SmpPlugin plugin, int page, AuctionYourItemsMenu.Origin origin) {
        this(plugin, page, "", origin);
    }

    public QuickBuyTransactionsMenu(SmpPlugin plugin, int page, String searchQuery, AuctionYourItemsMenu.Origin origin) {
        super(
                plugin,
                plugin.getConfigManager().getShop().getString("TRANSACTIONS.TITLE",
                        plugin.getConfigManager().getMessageOrDefault(
                                "QUICK_BUY.TRANSACTIONS_TITLE",
                                "Transactions (Page {page})"
                        )).replace("{page}", String.valueOf(page)),
                plugin.getConfigManager().getShop().getInt("TRANSACTIONS.SIZE", SIZE)
        );
        this.page = Math.max(0, page);
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim();
        this.origin = origin == null ? AuctionYourItemsMenu.Origin.AUCTION : origin;
    }

    @Override
    public void build(Player player) {
        clear();

        List<AuctionListing> transactions = plugin.getAuctionHouseManager().getPlayerTransactions(player.getUniqueId(), searchQuery);
        int totalItems = transactions.size();
        int maxPage = Math.max(0, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE) - 1);
        if (page > maxPage) {
            page = maxPage;
        }

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            AuctionListing listing = transactions.get(i);
            boolean sold = listing.sellerUuid().equals(player.getUniqueId());

            ItemStack display = listing.item() != null ? listing.item().clone() : new ItemStack(Material.CHEST);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                String itemName = plugin.getAuctionHouseManager().describeItem(listing.item());
                String formattedPrice = NumberUtils.formatNice(listing.price());
                if (sold) {
                    String buyerName = "Unknown";
                    if (listing.buyerUuid() != null) {
                        OfflinePlayer buyer = plugin.getServer().getOfflinePlayer(listing.buyerUuid());
                        String rawName = buyer.getName() != null ? buyer.getName() : "Unknown";
                        buyerName = plugin.getHideManager() != null
                                ? plugin.getHideManager().publicName(listing.buyerUuid(), rawName)
                                : rawName;
                    }
                    lore.add(ColorUtils.colorize("&a" + buyerName + " &fbought your " + itemName + " for &a$ &f" + formattedPrice));
                } else {
                    String sellerName = plugin.getHideManager() != null
                            ? plugin.getHideManager().publicName(listing.sellerUuid(), listing.sellerName())
                            : listing.sellerName();
                    lore.add(ColorUtils.colorize("&fYou bought &f" + itemName + " from &a" + sellerName + " for &a$ &f" + formattedPrice));
                }

                long soldTime = listing.soldAt() > 0 ? listing.soldAt() : listing.createdAt();
                long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - soldTime) / 1000L);
                lore.add(ColorUtils.colorize("&a" + NumberUtils.formatTime(elapsedSeconds) + " ago"));

                meta.setLore(lore);
                display.setItemMeta(meta);
            }

            set(slot++, display);
        }

        var shopCfg = plugin.getConfigManager().getShop();

        // Place paper indicator only when there are no transactions at all
        if (totalItems == 0) {
            Material endMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.END-OF-LIST.MATERIAL", "PAPER"));
            String endName = shopCfg.getString("TRANSACTIONS.END-OF-LIST.NAME", "&cYou've reached the end");
            List<String> endLore = shopCfg.getStringList("TRANSACTIONS.END-OF-LIST.LORE");
            if (endLore == null || endLore.isEmpty()) {
                endLore = List.of("&fTransactions only last 90d");
            }
            set(0, ItemUtils.createItem(endMat, endName, endLore));
        }

        // Controls row
        int prevSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.PREV-PAGE.SLOT", PREV_PAGE_SLOT);
        Material prevMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.BUTTONS.PREV-PAGE.MATERIAL", "ARROW"));
        String prevName = shopCfg.getString("TRANSACTIONS.BUTTONS.PREV-PAGE.NAME", "&fPrevious Page");
        List<String> prevLore = ItemUtils.readLore(shopCfg, "TRANSACTIONS.BUTTONS.PREV-PAGE.LORE", List.of("&7Page {page}"));

        if (page > 0) {
            List<String> formattedPrevLore = new ArrayList<>();
            for (String l : prevLore) {
                formattedPrevLore.add(l.replace("{page}", String.valueOf(page - 1)));
            }
            set(prevSlot, ItemUtils.createItem(
                    prevMat,
                    prevName,
                    formattedPrevLore
            ));
        }

        int backSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.BACK.SLOT", -1);
        if (backSlot >= 0 && backSlot < 54) {
            Material backMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.BUTTONS.BACK.MATERIAL", "ARROW"));
            String backName = shopCfg.getString("TRANSACTIONS.BUTTONS.BACK.NAME", "&fBack");
            List<String> backLore = ItemUtils.readLore(shopCfg, "TRANSACTIONS.BUTTONS.BACK.LORE", List.of("&7Return to Your Items"));
            set(backSlot, ItemUtils.createItem(backMat, backName, backLore));
        }

        // Stats
        int statsSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.STATS.SLOT", STATS_SLOT);
        Material statsMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.BUTTONS.STATS.MATERIAL", "BOOK"));
        String statsName = shopCfg.getString("TRANSACTIONS.BUTTONS.STATS.NAME", "&fStats");
        String spentFmt = shopCfg.getString("TRANSACTIONS.BUTTONS.STATS.LORE-SPENT", "&fTotal Spent: &a${spent}");
        String madeFmt = shopCfg.getString("TRANSACTIONS.BUTTONS.STATS.LORE-MADE", "&fTotal Made: &a${made}");
        double totalSpent = plugin.getAuctionHouseManager().getPlayerTotalSpent(player.getUniqueId());
        double totalMade = plugin.getAuctionHouseManager().getPlayerTotalMade(player.getUniqueId());
        set(statsSlot, ItemUtils.createItem(
                statsMat,
                statsName,
                List.of(
                        spentFmt.replace("{spent}", NumberUtils.formatNice(totalSpent)),
                        madeFmt.replace("{made}", NumberUtils.formatNice(totalMade))
                )
        ));

        // Refresh
        int refreshSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.REFRESH.SLOT", REFRESH_SLOT);
        Material refreshMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.BUTTONS.REFRESH.MATERIAL", "WRITABLE_BOOK"));
        String refreshName = shopCfg.getString("TRANSACTIONS.BUTTONS.REFRESH.NAME", "&fTransactions");
        List<String> refreshLore = ItemUtils.readLore(shopCfg, "TRANSACTIONS.BUTTONS.REFRESH.LORE", List.of("&o&7Click to refresh"));
        set(refreshSlot, ItemUtils.createItem(refreshMat, refreshName, refreshLore));

        // Search
        int searchSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        Material searchMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.BUTTONS.SEARCH.MATERIAL", "OAK_SIGN"));
        String searchName = shopCfg.getString("TRANSACTIONS.BUTTONS.SEARCH.NAME", "&fSearch");
        List<String> searchLore = new ArrayList<>(ItemUtils.readLore(shopCfg, "TRANSACTIONS.BUTTONS.SEARCH.LORE", List.of("&o&7Click to search")));
        if (!searchQuery.isBlank()) {
            List<String> activeLore = ItemUtils.readLore(shopCfg, "TRANSACTIONS.BUTTONS.SEARCH.ACTIVE-QUERY-LORE", List.of("&7Current: &e{query}", "&8Right-click to clear"));
            for (String l : activeLore) {
                searchLore.add(l.replace("{query}", searchQuery));
            }
        }
        set(searchSlot, ItemUtils.createItem(searchMat, searchName, searchLore));

        // Next page
        int nextSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.NEXT-PAGE.SLOT", NEXT_PAGE_SLOT);
        Material nextMat = ItemUtils.parseMaterial(shopCfg.getString("TRANSACTIONS.BUTTONS.NEXT-PAGE.MATERIAL", "ARROW"));
        String nextName = shopCfg.getString("TRANSACTIONS.BUTTONS.NEXT-PAGE.NAME", "&fNext Page");
        List<String> nextLore = ItemUtils.readLore(shopCfg, "TRANSACTIONS.BUTTONS.NEXT-PAGE.LORE", List.of("&7Page {page}"));

        if (endIndex < totalItems) {
            List<String> formattedNextLore = new ArrayList<>();
            for (String l : nextLore) {
                formattedNextLore.add(l.replace("{page}", String.valueOf(page + 1)));
            }
            set(nextSlot, ItemUtils.createItem(
                    nextMat,
                    nextName,
                    formattedNextLore
            ));
        }
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        var shopCfg = plugin.getConfigManager().getShop();
        int prevSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.PREV-PAGE.SLOT", PREV_PAGE_SLOT);
        int nextSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.NEXT-PAGE.SLOT", NEXT_PAGE_SLOT);
        int backSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.BACK.SLOT", -1);
        int refreshSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.REFRESH.SLOT", REFRESH_SLOT);
        int searchSlot = shopCfg.getInt("TRANSACTIONS.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);

        if (slot == prevSlot && page > 0) {
            QuickBuySounds.pageTurn(player, plugin);
            page--;
            build(player);
            return;
        }

        if (slot == nextSlot) {
            QuickBuySounds.pageTurn(player, plugin);
            page++;
            build(player);
            return;
        }

        if (backSlot >= 0 && slot == backSlot) {
            click(player);
            new AuctionYourItemsMenu(plugin, origin).open(player);
            return;
        }

        if (slot == refreshSlot) {
            QuickBuySounds.play(player, plugin, QuickBuySounds.REFRESH);
            plugin.getAuctionHouseManager().refreshCache().thenRun(() ->
                    plugin.getSpigotScheduler().runEntity(player, () -> build(player)));
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
            DialogGateway dialog = plugin.getDialogManager();
            if (dialog != null) {
                player.closeInventory();
                if (dialog.openQuickBuySearch(player, searchQuery, "transactions")) {
                    return;
                }
            }
            SignInputUtil.open(plugin, player, List.of("", "↑↑↑↑↑↑↑↑↑↑↑↑↑↑", "Search", ""), 0, input -> {
                searchQuery = input == null || input.isBlank() ? "" : input.trim();
                page = 0;
                QuickBuySounds.play(player, plugin, QuickBuySounds.SEARCH);
                new QuickBuyTransactionsMenu(plugin, page, searchQuery, origin).open(player);
            });
        }
    }

    private void click(Player player) {
        QuickBuySounds.click(player, plugin);
    }
}
