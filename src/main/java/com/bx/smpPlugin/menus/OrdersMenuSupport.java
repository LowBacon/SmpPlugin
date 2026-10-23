// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.models.ItemKey;
import com.bx.smpPlugin.models.Order;
import com.bx.smpPlugin.models.OrderCollectionClaim;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class OrdersMenuSupport {

    static final int PREV_SLOT = 45;
    static final int FILTER_SLOT = 47;
    static final int SHARD_SHOP_SLOT = 48;
    static final int INFO_SLOT = 49;
    static final int SEARCH_SLOT = 50;
    static final int MY_ORDERS_SLOT = 51;
    static final int NEXT_SLOT = 53;

    private OrdersMenuSupport() {
    }

    static int slot(SmpPlugin plugin, String path, int fallback) {
        return plugin.getConfigManager().getOrders().getInt(path, fallback);
    }

    static Material material(SmpPlugin plugin, String path, Material fallback) {
        String raw = plugin.getConfigManager().getOrders().getString(path, fallback.name());
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    static String text(SmpPlugin plugin, String path, String fallback, String... placeholders) {
        return plugin.getLanguageManager().text(path, null, fallback, placeholders);
    }

    static List<String> list(SmpPlugin plugin, String path, List<String> fallback, String... placeholders) {
        return plugin.getLanguageManager().list(path, fallback, placeholders);
    }

    static ItemStack button(
            SmpPlugin plugin,
            String configPath,
            String languagePath,
            Material fallbackMaterial,
            String fallbackName,
            List<String> fallbackLore,
            String... placeholders
    ) {
        return ItemUtils.createItem(
                material(plugin, configPath + ".MATERIAL", fallbackMaterial),
                text(plugin, languagePath + ".NAME", fallbackName, placeholders),
                list(plugin, languagePath + ".LORE", fallbackLore, placeholders)
        );
    }

    static String publicOwnerName(SmpPlugin plugin, Order order) {
        if (order == null) {
            return "";
        }
        return plugin != null && plugin.getHideManager() != null
                ? plugin.getHideManager().publicName(order.ownerUuid(), order.ownerName())
                : (order.ownerName() != null ? order.ownerName() : "");
    }

    static ItemStack createOrderDisplay(
            SmpPlugin plugin,
            OrdersManager manager,
            Order order,
            boolean ownedByViewer
    ) {
        String owner = plugin != null && plugin.getHideManager() != null
                ? plugin.getHideManager().publicName(order.ownerUuid(), order.ownerName())
                : (order.ownerName() != null ? order.ownerName() : "");
        List<String> lore = list(
                plugin,
                "ORDERS.GUI.ORDER_ITEM.LORE",
                List.of(
                        "&a{requested} &frequested",
                        "",
                        "&a{price_each} &feach",
                        "&a{delivered}/{requested} &fDelivered",
                        "",
                        "&7{time} Until Order expires"
                ),
                "{item}", manager.describeItem(order.requestedItem()),
                "{owner}", owner,
                "{price_each}", plugin.getCurrencyManager().formatMoney(order.priceEach()),
                "{delivered}", String.valueOf(order.deliveredQuantity()),
                "{requested}", String.valueOf(order.requestedQuantity()),
                "{paid}", plugin.getCurrencyManager().formatMoney(order.paidAmount()),
                "{total}", plugin.getCurrencyManager().formatMoney(order.totalBudget()),
                "{remaining}", String.valueOf(order.remainingQuantity()),
                "{time}", manager.formatRemaining(order.secondsRemaining(System.currentTimeMillis())),
                "{status}", plugin.getLanguageManager().display("ORDER_STATUSES", order.status().name(), order.status().name()),
                "{order_id}", String.valueOf(order.id())
        );
        String name = text(
                plugin,
                "ORDERS.GUI.ORDER_ITEM.NAME",
                "&f{item}",
                "{owner}", owner,
                "{item}", manager.describeItem(order.requestedItem())
        );
        ItemStack display = decorateItem(plugin, order.requestedItem(), name, lore, false);
        int shown = Math.max(1, Math.min(64, order.remainingQuantity() > 0
                ? order.remainingQuantity()
                : order.requestedQuantity()));
        display.setAmount(shown);
        return display;
    }

    static ItemStack createClaimDisplay(
            SmpPlugin plugin,
            OrdersManager manager,
            OrderCollectionClaim claim
    ) {
        if (claim.refundClaim()) {
            return ItemUtils.createItem(
                    Material.SUNFLOWER,
                    text(plugin, "ORDERS.GUI.CLAIM.REFUND_NAME", "&aEscrow refund"),
                    list(
                            plugin,
                            "ORDERS.GUI.CLAIM.REFUND_LORE",
                            List.of("&7Amount: &f{amount}", "&7Order: &f#{order_id}", "", "&eClick to collect"),
                            "{amount}", plugin.getCurrencyManager().formatMoney(claim.moneyAmount()),
                            "{order_id}", String.valueOf(claim.orderId())
                    )
            );
        }

        List<String> lore = list(
                plugin,
                "ORDERS.GUI.CLAIM.ITEM_LORE",
                List.of(
                        "&7Order: &f#{order_id}",
                        "&7Created: &f{age} ago",
                        "",
                        "&eClick to collect"
                ),
                "{order_id}", String.valueOf(claim.orderId()),
                "{age}", NumberUtils.formatTimeLong(Math.max(0L,
                        (System.currentTimeMillis() - claim.createdAt()) / 1000L))
        );
        return decorateItem(
                plugin,
                claim.item(),
                "&b" + manager.describeItem(claim.item()),
                lore,
                true
        );
    }

    static ItemStack decorateItem(
            SmpPlugin plugin,
            ItemStack source,
            String displayName,
            List<String> extraLore,
            boolean preserveOriginalLore
    ) {
        if (source == null || source.getType().isAir()) {
            return ItemUtils.createItem(Material.BARRIER, "&cMissing item", List.of("&7Stored item data is unavailable."));
        }

        ItemStack display = source.clone();
        display.setAmount(Math.max(1, source.getAmount()));
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }

        List<String> combinedLore = new ArrayList<>();
        List<String> enchantLines = ItemKey.fromStack(source).enchantLoreLines("&7- &e");
        if (!enchantLines.isEmpty()) {
            combinedLore.add(text(plugin, "ORDERS.GUI.REQUIRED_ENCHANTMENTS", "&bRequired enchantments:"));
            combinedLore.addAll(enchantLines);
            combinedLore.add("");
        }
        if (preserveOriginalLore && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                combinedLore.add(ColorUtils.toLegacyString(line));
            }
        }
        combinedLore.addAll(extraLore);
        meta.setDisplayName(ColorUtils.toComponent(displayName));
        meta.setLore(ColorUtils.toComponentList(combinedLore));
        display.setItemMeta(meta);
        return display;
    }

    static ItemStack decorateItem(
            SmpPlugin plugin,
            ItemStack source,
            String displayName,
            List<String> extraLore
    ) {
        return decorateItem(plugin, source, displayName, extraLore, true);
    }

    static void placeNavigation(
            BaseMenu menu,
            Player player,
            boolean includeSearch,
            boolean alwaysShowNext,
            int page,
            boolean hasPrev,
            boolean hasNext,
            OrderSort sort
    ) {
        SmpPlugin plugin = menu.plugin;
        int prevSlot = slot(plugin, "GUI.MAIN.BUTTONS.PREV.SLOT", PREV_SLOT);
        int filterSlot = slot(plugin, "GUI.MAIN.BUTTONS.FILTER.SLOT", FILTER_SLOT);
        int shardSlot = slot(plugin, "GUI.MAIN.BUTTONS.SHARD_SHOP.SLOT", SHARD_SHOP_SLOT);
        int infoSlot = slot(plugin, "GUI.MAIN.BUTTONS.INFO.SLOT", INFO_SLOT);
        int searchSlot = slot(plugin, "GUI.MAIN.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        int myOrdersSlot = slot(plugin, "GUI.MAIN.BUTTONS.MY_ORDERS.SLOT", MY_ORDERS_SLOT);
        int nextSlot = slot(plugin, "GUI.MAIN.BUTTONS.NEXT.SLOT", NEXT_SLOT);

        if (hasPrev) {
            menu.set(prevSlot, button(
                    plugin, "GUI.MAIN.BUTTONS.PREV", "ORDERS.GUI.MAIN.PREV",
                    Material.ARROW, "&fPrevious page",
                    List.of("&f&oClick to view previous page")
            ));
        }

        List<String> filterLore = new ArrayList<>(list(
                plugin, "ORDERS.GUI.MAIN.FILTER.LORE", List.of("&7Click to change")
        ));
        filterLore.add("");
        OrderSort current = sort == null ? plugin.getOrdersManager().getDefaultSort() : sort;
        for (OrderSort value : plugin.getOrdersManager().getAllowedSorts()) {
            String prefix = value == current ? "&f• " : "&7• ";
            filterLore.add(prefix + plugin.getLanguageManager().display(
                    "ORDER_SORTS", value.name(), value.displayName()
            ));
        }
        menu.set(filterSlot, ItemUtils.createItem(
                material(plugin, "GUI.MAIN.BUTTONS.FILTER.MATERIAL", Material.HOPPER),
                text(plugin, "ORDERS.GUI.MAIN.FILTER.NAME", "&fFilter"),
                filterLore
        ));
        menu.set(shardSlot, button(
                plugin, "GUI.MAIN.BUTTONS.SHARD_SHOP", "ORDERS.GUI.MAIN.SHARD_SHOP",
                Material.AMETHYST_SHARD, "&fShard Shop", List.of("&7Click to view")
        ));
        menu.set(infoSlot, button(
                plugin, "GUI.MAIN.BUTTONS.INFO", "ORDERS.GUI.MAIN.INFO",
                Material.BOOK, "&fOrders", List.of("&7Request and deliver items")
        ));
        if (includeSearch) {
            menu.set(searchSlot, button(
                    plugin, "GUI.MAIN.BUTTONS.SEARCH", "ORDERS.GUI.MAIN.SEARCH",
                    Material.OAK_SIGN, "&fSearch", List.of("&7Click to search")
            ));
        }
        menu.set(myOrdersSlot, button(
                plugin, "GUI.MAIN.BUTTONS.MY_ORDERS", "ORDERS.GUI.MAIN.MY_ORDERS",
                Material.CHEST, "&fYour Orders", List.of("&7Click to view")
        ));
        if (hasNext || alwaysShowNext) {
            menu.set(nextSlot, button(
                    plugin, "GUI.MAIN.BUTTONS.NEXT", "ORDERS.GUI.MAIN.NEXT",
                    Material.ARROW, "&fNext page",
                    List.of("&f&oClick to view next page")
            ));
        }
    }

    static boolean handleNavigationClick(
            BaseMenu menu,
            Player player,
            int slot,
            ClickType clickType,
            int page,
            boolean hasPrev,
            boolean hasNext,
            OrderSort sort,
            String categoryFilter,
            String query,
            boolean fromMyOrders
    ) {
        SmpPlugin plugin = menu.plugin;
        OrdersManager manager = plugin.getOrdersManager();
        int prevSlot = slot(plugin, "GUI.MAIN.BUTTONS.PREV.SLOT", PREV_SLOT);
        int filterSlot = slot(plugin, "GUI.MAIN.BUTTONS.FILTER.SLOT", FILTER_SLOT);
        int shardSlot = slot(plugin, "GUI.MAIN.BUTTONS.SHARD_SHOP.SLOT", SHARD_SHOP_SLOT);
        int infoSlot = slot(plugin, "GUI.MAIN.BUTTONS.INFO.SLOT", INFO_SLOT);
        int searchSlot = slot(plugin, "GUI.MAIN.BUTTONS.SEARCH.SLOT", SEARCH_SLOT);
        int myOrdersSlot = slot(plugin, "GUI.MAIN.BUTTONS.MY_ORDERS.SLOT", MY_ORDERS_SLOT);
        int nextSlot = slot(plugin, "GUI.MAIN.BUTTONS.NEXT.SLOT", NEXT_SLOT);

        if (slot == prevSlot && hasPrev) {
            pageTurn(player, plugin);
            openCurrent(plugin, player, page - 1, sort, categoryFilter, query, fromMyOrders);
            return true;
        }
        if (slot == nextSlot && hasNext) {
            pageTurn(player, plugin);
            openCurrent(plugin, player, page + 1, sort, categoryFilter, query, fromMyOrders);
            return true;
        }
        if (slot == filterSlot) {
            click(player, plugin);
            OrderSort next = nextSort(plugin, sort);
            manager.getUiState(player.getUniqueId()).sort(next);
            openCurrent(plugin, player, 1, next, categoryFilter, query, fromMyOrders);
            return true;
        }
        if (slot == shardSlot) {
            click(player, plugin);
            player.performCommand("shardshop");
            return true;
        }
        if (slot == infoSlot) {
            click(player, plugin);
            if (fromMyOrders) {
                new OrdersBrowseMenu(plugin, 1, sort, "ALL", "").open(player);
            }
            return true;
        }
        if (slot == searchSlot && !fromMyOrders) {
            click(player, plugin);
            if (clickType != null && clickType.isRightClick()) {
                new OrdersBrowseMenu(plugin, 1, sort, categoryFilter, "").open(player);
                return true;
            }
            openSearch(plugin, player, sort, categoryFilter, query);
            return true;
        }
        if (slot == myOrdersSlot) {
            click(player, plugin);
            new OrdersMyOrdersMenu(plugin, 1, sort, query).open(player);
            return true;
        }
        return false;
    }

    static void openSearch(
            SmpPlugin plugin,
            Player player,
            OrderSort sort,
            String categoryFilter,
            String query
    ) {
        if (plugin.getDialogManager() != null
                && plugin.getDialogManager().openOrdersSearch(player, sort, categoryFilter, query)) {
            return;
        }
        plugin.getOrdersManager().promptOrdersMenuSearch(player, sort, categoryFilter, false);
    }

    static void click(Player player, SmpPlugin plugin) {
        play(player, plugin, "ORDERS.CLICK");
    }

    static void pageTurn(Player player, SmpPlugin plugin) {
        play(player, plugin, "ORDERS.PAGE-TURN");
    }

    static void play(Player player, SmpPlugin plugin, String path) {
        if (player == null || plugin == null || plugin.getConfigManager() == null) {
            return;
        }
        SoundUtils.play(player, plugin.getConfigManager().getSound(path));
    }

    static OrderSort nextSort(SmpPlugin plugin, OrderSort current) {
        List<OrderSort> sorts = plugin.getOrdersManager().getAllowedSorts();
        int index = sorts.indexOf(current);
        return index < 0 ? plugin.getOrdersManager().getDefaultSort() : sorts.get((index + 1) % sorts.size());
    }

    private static void openCurrent(
            SmpPlugin plugin,
            Player player,
            int page,
            OrderSort sort,
            String categoryFilter,
            String query,
            boolean fromMyOrders
    ) {
        if (fromMyOrders) {
            new OrdersMyOrdersMenu(plugin, page, sort, query).open(player);
        } else {
            new OrdersBrowseMenu(plugin, page, sort, categoryFilter, query).open(player);
        }
    }
}
