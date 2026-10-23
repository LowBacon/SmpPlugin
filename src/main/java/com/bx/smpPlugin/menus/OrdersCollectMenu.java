// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.models.OrderBatchClaimResult;
import com.bx.smpPlugin.models.OrderCollectionClaim;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class OrdersCollectMenu extends BaseMenu {

    private final int page;
    private final long orderId;

    public OrdersCollectMenu(SmpPlugin plugin, int page) {
        this(plugin, page, 0L);
    }

    public OrdersCollectMenu(SmpPlugin plugin, int page, long orderId) {
        super(plugin, plugin.getOrdersManager().getCollectTitle(), plugin.getOrdersManager().getCollectSize());
        this.page = Math.max(1, page);
        this.orderId = Math.max(0L, orderId);
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);

        List<OrderCollectionClaim> claims = getClaims(player);
        int itemsPerPage = plugin.getOrdersManager().getCollectItemsPerPage();
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(claims.size(), startIndex + itemsPerPage);

        for (int slot = 0; slot < itemsPerPage && slot < inventory.getSize() - 9; slot++) {
            int claimIndex = startIndex + slot;
            if (claimIndex >= endIndex) {
                break;
            }
            set(slot, OrdersMenuSupport.createClaimDisplay(
                    plugin,
                    plugin.getOrdersManager(),
                    claims.get(claimIndex)
            ));
        }

        int lastRow = inventory.getSize() - 9;
        set(lastRow, ItemUtils.createItem(Material.COMPASS, "&bBack to board", List.of("&7Return to active orders")));
        set(lastRow + 1, page > 1
                ? ItemUtils.createItem(Material.ARROW, "&aPrevious page", List.of("&7Go to page &f" + (page - 1)))
                : ItemUtils.createPlaceholder(Material.BLACK_STAINED_GLASS_PANE));
        set(lastRow + 2, ItemUtils.createItem(Material.WRITABLE_BOOK, "&bMy orders", List.of("&7View your orders")));
        set(lastRow + 3, ItemUtils.createItem(Material.CLOCK, "&eRefresh", List.of("&7Reload your collect queue")));
        set(lastRow + 4, OrdersMenuSupport.button(
                plugin, "GUI.COLLECT.BUTTONS.COLLECT_PAGE", "ORDERS.GUI.COLLECT.COLLECT_PAGE",
                Material.HOPPER, "&aCollect page", List.of("&fCollect every claim shown on this page")
        ));
        set(lastRow + 5, ItemUtils.createItem(
                Material.BOOK,
                "&ePage " + page + "&7/&e" + getTotalPages(claims.size(), itemsPerPage),
                List.of("&7Pending claims: &f" + claims.size())
        ));
        set(lastRow + 6, OrdersMenuSupport.button(
                plugin, "GUI.COLLECT.BUTTONS.DROP_ALL", "ORDERS.GUI.COLLECT.DROP_ALL",
                Material.DROPPER, "&eDrop all", List.of("&fDrop every pending item claim where you're looking")
        ));
        set(lastRow + 7, hasNextPage(claims.size(), itemsPerPage)
                ? ItemUtils.createItem(Material.ARROW, "&aNext page", List.of("&7Go to page &f" + (page + 1)))
                : ItemUtils.createPlaceholder(Material.BLACK_STAINED_GLASS_PANE));
        set(lastRow + 8, OrdersMenuSupport.button(
                plugin, "GUI.COLLECT.BUTTONS.DROP_PAGE", "ORDERS.GUI.COLLECT.DROP_PAGE",
                Material.DROPPER, "&eDrop page", List.of("&fDrop item claims where you're looking")
        ));

        if (claims.isEmpty()) {
            set(inventory.getSize() / 2, ItemUtils.createItem(
                    Material.BARRIER,
                    "&cNothing to collect",
                    List.of("&7Delivered items and refunds will appear here.")
            ));
        }
    }

    @Override
    public void handleClick(int slot, Player player) {
        int lastRow = inventory.getSize() - 9;
        List<OrderCollectionClaim> claims = getClaims(player);
        int itemsPerPage = plugin.getOrdersManager().getCollectItemsPerPage();

        if (slot == lastRow) {
            OrdersMenuSupport.click(player, plugin);
            new OrdersBrowseMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort(), "ALL").open(player);
            return;
        }
        if (slot == lastRow + 1) {
            if (page > 1) {
                OrdersMenuSupport.pageTurn(player, plugin);
                new OrdersCollectMenu(plugin, page - 1, orderId).open(player);
            }
            return;
        }
        if (slot == lastRow + 2) {
            OrdersMenuSupport.click(player, plugin);
            new OrdersMyOrdersMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort()).open(player);
            return;
        }
        if (slot == lastRow + 3) {
            OrdersMenuSupport.click(player, plugin);
            new OrdersCollectMenu(plugin, page, orderId).open(player);
            return;
        }
        if (slot == lastRow + 4) {
            collectPage(player, false, claims, itemsPerPage);
            return;
        }
        if (slot == lastRow + 6) {
            collectRange(player, true, claims, 0, claims.size());
            return;
        }
        if (slot == lastRow + 7) {
            if (hasNextPage(claims.size(), itemsPerPage)) {
                OrdersMenuSupport.pageTurn(player, plugin);
                new OrdersCollectMenu(plugin, page + 1, orderId).open(player);
            }
            return;
        }
        if (slot == lastRow + 8) {
            collectPage(player, true, claims, itemsPerPage);
            return;
        }

        if (slot < 0 || slot >= itemsPerPage) {
            return;
        }

        int claimIndex = ((page - 1) * itemsPerPage) + slot;
        if (claimIndex >= claims.size()) {
            return;
        }

        OrdersManager manager = plugin.getOrdersManager();
        if (!manager.beginAction(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent("&cOrders is still processing your previous action."));
            return;
        }

        try {
            if (manager.isOnClickCooldown(player.getUniqueId())) {
                player.sendMessage(ColorUtils.toComponent("&cSlow down for a moment."));
                return;
            }
            manager.updateClickCooldown(player.getUniqueId());

            OrderCollectionClaim claim = claims.get(claimIndex);
            OrdersManager.ClaimResult result = manager.claim(player, claim.id());
            if (!result.success()) {
                player.sendMessage(ColorUtils.toComponent(resolveFailureMessage(result)));
                OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                return;
            }

            if (claim.refundClaim()) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "ORDERS.CLAIMED_REFUND",
                        "&aClaimed escrow refund of {amount_formatted}&a.",
                        "{amount}", NumberUtils.format(claim.moneyAmount()),
                        "{amount_formatted}", plugin.getCurrencyManager().formatMoney(claim.moneyAmount())
                )));
                OrdersMenuSupport.play(player, plugin, "ORDERS.REFUND");
            } else {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "ORDERS.CLAIMED_ITEM",
                        "&aClaimed delivered item: &f{item}&a.",
                        "{item}", manager.describeItem(claim.item())
                )));
                OrdersMenuSupport.play(player, plugin, "ORDERS.COLLECT");
            }
            new OrdersCollectMenu(plugin, page, orderId).open(player);
        } finally {
            manager.endAction(player.getUniqueId());
        }
    }

    private List<OrderCollectionClaim> getClaims(Player player) {
        return plugin.getOrdersManager().getUnclaimedClaims(player.getUniqueId(), orderId);
    }

    private void collectPage(
            Player player,
            boolean dropItems,
            List<OrderCollectionClaim> claims,
            int itemsPerPage
    ) {
        int from = Math.min(claims.size(), (page - 1) * itemsPerPage);
        int to = Math.min(claims.size(), from + itemsPerPage);
        collectRange(player, dropItems, claims, from, to);
    }

    private void collectRange(
            Player player,
            boolean dropItems,
            List<OrderCollectionClaim> claims,
            int from,
            int to
    ) {
        List<Long> claimIds = claimIds(claims, from, to);
        OrderBatchClaimResult result = plugin.getOrdersManager().claimBatch(player, claimIds, dropItems);
        player.sendMessage(ColorUtils.toComponent(OrdersMenuSupport.text(
                plugin,
                "ORDERS.BATCH_COLLECTED",
                "&aCollected {claims} claims ({items} items, {refund} refund). &c{failed} failed.",
                "{claims}", String.valueOf(result.itemClaims() + result.refundClaims()),
                "{items}", String.valueOf(result.itemAmount()),
                "{refund}", plugin.getCurrencyManager().formatMoney(result.refundAmount()),
                "{failed}", String.valueOf(result.failedClaims())
        )));
        OrdersMenuSupport.play(player, plugin, collectPageSound(result, dropItems));
        new OrdersCollectMenu(plugin, page, orderId).open(player);
    }

    static List<Long> claimIds(List<OrderCollectionClaim> claims, int from, int to) {
        if (claims == null || claims.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, Math.min(from, claims.size()));
        int end = Math.max(start, Math.min(to, claims.size()));
        return claims.subList(start, end).stream().map(OrderCollectionClaim::id).toList();
    }

    private static String collectPageSound(OrderBatchClaimResult result, boolean dropItems) {
        if (result.itemClaims() <= 0 && result.refundClaims() <= 0) {
            return "ORDERS.FAIL";
        }
        if (dropItems && result.itemClaims() > 0) {
            return "ORDERS.DROP";
        }
        if (result.itemClaims() <= 0 && result.refundClaims() > 0) {
            return "ORDERS.REFUND";
        }
        return "ORDERS.COLLECT";
    }

    private int getTotalPages(int totalItems, int itemsPerPage) {
        return Math.max(1, (int) Math.ceil(totalItems / (double) itemsPerPage));
    }

    private boolean hasNextPage(int totalItems, int itemsPerPage) {
        return page < getTotalPages(totalItems, itemsPerPage);
    }

    private String resolveFailureMessage(OrdersManager.ClaimResult result) {
        return switch (result.reason()) {
            case DISABLED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.DISABLED", "&cOrders is currently disabled.");
            case CLAIMS_DISABLED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.CLAIMS_DISABLED", "&cOrders claims are currently disabled.");
            case CLAIM_NOT_FOUND -> plugin.getConfigManager().getMessageOrDefault("ORDERS.CLAIM_NOT_FOUND", "&cThat claim no longer exists.");
            case NOT_OWNER -> plugin.getConfigManager().getMessageOrDefault("ORDERS.NOT_YOUR_CLAIM", "&cThat claim does not belong to you.");
            case ALREADY_CLAIMED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.CLAIM_ALREADY_CLAIMED", "&cThat claim was already collected.");
            case INVENTORY_FULL -> plugin.getConfigManager().getMessageOrDefault("ORDERS.CLAIM_INVENTORY_FULL", "&cYou need a free inventory slot to claim that item.");
            case NO_PLAYER_DATA -> "&cYour player data could not be loaded.";
            case DATABASE_ERROR -> "&cOrders could not complete that claim right now.";
        };
    }
}
