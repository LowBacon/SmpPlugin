// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import com.bx.smpPlugin.utils.SignInputUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class OrdersNewMenu extends BaseMenu {

    public OrdersNewMenu(SmpPlugin plugin) {
        super(plugin, plugin.getOrdersManager().getNewOrderTitle(), plugin.getOrdersManager().getNewOrderSize());
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);

        OrdersManager manager = plugin.getOrdersManager();
        OrdersManager.NewOrderSession session = manager.getOrCreateNewOrderSession(player.getUniqueId());

        // Slot 10: Cancel
        set(10, ItemUtils.createItem(
                Material.RED_STAINED_GLASS_PANE,
                "&cCancel",
                List.of("&7Click to cancel an item and return")
        ));

        // Slot 12: Item
        ItemStack itemDisplay;
        if (session.getChosenItem() == null) {
            itemDisplay = ItemUtils.createItem(
                    Material.BARRIER,
                    "&bChoose item",
                    List.of("&7Click to select the item you want to order.")
            );
        } else {
            itemDisplay = OrdersMenuSupport.decorateItem(
                    plugin,
                    session.getChosenItem(),
                    manager.describeItem(session.getChosenItem()),
                    List.of(
                            "",
                            "&7Category: &f" + manager.prettifyCategory(session.getCategoryKey()),
                            "",
                            "&eClick to change item"
                    )
            );
        }
        set(12, itemDisplay);

        // Slot 13: Amount
        int amount = session.getAmount();
        ItemStack amountDisplay = ItemUtils.createItem(
                Material.PAPER,
                "&bOrder quantity",
                List.of(
                        "&7Current quantity: &e" + (amount <= 0 ? "Not set" : amount),
                        "",
                        "&eClick to set quantity"
                )
        );
        if (amount > 0) {
            amountDisplay.setAmount(Math.max(1, Math.min(64, amount)));
        }
        set(13, amountDisplay);

        // Slot 14: Price
        double priceEach = session.getPriceEach();
        ItemStack priceDisplay = ItemUtils.createItem(
                Material.SUNFLOWER,
                "&bPrice each",
                List.of(
                        "&7Current price: &e" + (priceEach <= 0D ? "Not set" : plugin.getCurrencyManager().formatMoney(priceEach)),
                        "",
                        "&eClick to s..."
                )
        );
        // Let's refine description
        List<String> priceLore = new ArrayList<>();
        priceLore.add("&7Current price: &e" + (priceEach <= 0D ? "Not set" : plugin.getCurrencyManager().formatMoney(priceEach)));
        priceLore.add("");
        priceLore.add("&eClick to set price");
        priceDisplay = ItemUtils.createItem(Material.SUNFLOWER, "&bPrice each", priceLore);
        set(14, priceDisplay);

        // Slot 16: Confirm
        double totalBudget = manager.roundCurrency(amount * priceEach);
        double creationFee = plugin.getConfigManager().getOrders().getDouble("PRICING.ORDER_CREATION_FEE", 0D);
        double requiredTotal = totalBudget + creationFee;
        boolean canConfirm = session.getChosenItem() != null && amount > 0 && priceEach > 0D;

        List<String> confirmLore = new ArrayList<>();
        if (!canConfirm) {
            confirmLore.add("&cPlease set item, quantity, and price first.");
        } else {
            confirmLore.add("&7Total budget: &e" + plugin.getCurrencyManager().formatMoney(totalBudget));
            confirmLore.add("&7Creation fee: &e" + plugin.getCurrencyManager().formatMoney(creationFee));
            confirmLore.add("&7Required total: &e" + plugin.getCurrencyManager().formatMoney(requiredTotal));
            confirmLore.add("");
            confirmLore.add("&7Current balance: " + plugin.getCurrencyManager().formatMoney(plugin.getEconomyManager().getBalance(player)));
            confirmLore.add("");
            confirmLore.add("&aClick to confirm &7(locks budget in escrow)");
        }

        ItemStack confirmDisplay = ItemUtils.createItem(
                canConfirm ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                "&aConfirm order",
                confirmLore
        );
        set(16, confirmDisplay);
    }

    @Override
    public void handleClick(int slot, Player player) {
        OrdersManager manager = plugin.getOrdersManager();
        OrdersManager.NewOrderSession session = manager.getOrCreateNewOrderSession(player.getUniqueId());

        if (slot == 10) {
            OrdersMenuSupport.click(player, plugin);
            manager.clearPendingCreation(player.getUniqueId());
            new OrdersBrowseMenu(plugin, 1, manager.getDefaultSort(), "ALL").open(player);
            return;
        }

        if (slot == 12) {
            OrdersMenuSupport.click(player, plugin);
            manager.openNewOrderItemSelection(player);
            return;
        }

        if (slot == 13) {
            OrdersMenuSupport.click(player, plugin);
            ConfigurationSection config = manager.getSignConfig("AMOUNT_SIGN");
            SignInputUtil.openFromConfig(plugin, player, config, text -> {
                if (text != null && !text.isBlank()) {
                    try {
                        int quantity = Math.toIntExact(NumberUtils.parseLong(text));
                        if (quantity > 0 && quantity <= manager.getMaxQuantityPerOrder()) {
                            session.setAmount(quantity);
                        } else {
                            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                    "ORDERS.QUANTITY_OUT_OF_RANGE",
                                    "&cQuantity must be between 1 and {max}.",
                                    "{max}", String.valueOf(manager.getMaxQuantityPerOrder())
                            )));
                            OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                        }
                    } catch (Exception e) {
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                "ORDERS.INVALID_QUANTITY",
                                "&cInvalid quantity. Use a whole number greater than 0."
                        )));
                        OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                    }
                }
                new OrdersNewMenu(plugin).open(player);
            });
            return;
        }

        if (slot == 14) {
            OrdersMenuSupport.click(player, plugin);
            ConfigurationSection config = manager.getSignConfig("PRICE_SIGN");
            SignInputUtil.openFromConfig(plugin, player, config, text -> {
                if (text != null && !text.isBlank()) {
                    try {
                        double priceEach = NumberUtils.parse(text);
                        double normalizedPrice = manager.roundCurrency(priceEach);
                        if (normalizedPrice >= manager.getMinPriceEach() && normalizedPrice <= manager.getMaxPriceEach()) {
                            session.setPriceEach(normalizedPrice);
                        } else {
                            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                    "ORDERS.PRICE_OUT_OF_RANGE",
                                    "&cPrice each must be between &f{min_formatted}&c and &f{max_formatted}&c.",
                                    "{min}", NumberUtils.format(manager.getMinPriceEach()),
                                    "{min_formatted}", plugin.getCurrencyManager().formatMoney(manager.getMinPriceEach()),
                                    "${min}", plugin.getCurrencyManager().formatMoney(manager.getMinPriceEach()),
                                    "{max}", NumberUtils.format(manager.getMaxPriceEach()),
                                    "{max_formatted}", plugin.getCurrencyManager().formatMoney(manager.getMaxPriceEach()),
                                    "${max}", plugin.getCurrencyManager().formatMoney(manager.getMaxPriceEach())
                            )));
                            OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                        }
                    } catch (Exception e) {
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                                "ORDERS.INVALID_PRICE",
                                "&cInvalid price format. Use numbers like 100, 5k, or 1.5M."
                        )));
                        OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                    }
                }
                new OrdersNewMenu(plugin).open(player);
            });
            return;
        }

        if (slot == 16) {
            boolean canConfirm = session.getChosenItem() != null && session.getAmount() > 0 && session.getPriceEach() > 0D;
            if (!canConfirm) {
                OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                return;
            }

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

                OrdersManager.CreateOrderResult result = manager.createOrder(player);
                if (!result.success()) {
                    player.sendMessage(ColorUtils.toComponent(resolveFailureMessage(result)));
                    OrdersMenuSupport.play(player, plugin, "ORDERS.FAIL");
                    return;
                }

                PlayerSettingUtils.sendActionBar(
                        plugin,
                        player,
                        plugin.getConfigManager().getMessageOrDefault(
                                "ORDERS.CREATED",
                                "&7You ordered &a{quantity} {item}",
                                "{order_id}", String.valueOf(result.order().id()),
                                "{quantity}", String.valueOf(result.order().requestedQuantity()),
                                "{item}", manager.describeItem(result.order().requestedItem())
                        )
                );
                OrdersMenuSupport.play(player, plugin, "ORDERS.CREATE");
                new OrdersMyOrdersMenu(plugin, 1, manager.getDefaultSort()).open(player);
            } finally {
                manager.endAction(player.getUniqueId());
            }
        }
    }

    private String resolveFailureMessage(OrdersManager.CreateOrderResult result) {
        return switch (result.reason()) {
            case DISABLED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.DISABLED", "&cOrders is currently disabled.");
            case NO_PENDING_ORDER -> plugin.getConfigManager().getMessageOrDefault("ORDERS.NO_PENDING_ORDER", "&cThere is no pending order draft to confirm.");
            case NO_PLAYER_DATA -> "&cYour player data could not be loaded.";
            case INVALID_ITEM -> plugin.getConfigManager().getMessageOrDefault("ORDERS.ITEM_BLOCKED", "&cThat item cannot be ordered.");
            case UNSAFE_ITEM -> plugin.getConfigManager().getMessageOrDefault(
                    "CRASH_PROTECTION.ITEM_BLOCKED",
                    "&cThat item cannot be used here because its data looks unsafe. &7Context: &f{context}&7. Reason: &f{reason}",
                    "{context}", "Orders",
                    "{reason}", result.safetyResult() == null ? "Unsafe item data" : result.safetyResult().reason()
            );
            case INVALID_QUANTITY -> plugin.getConfigManager().getMessageOrDefault("ORDERS.INVALID_QUANTITY", "&cInvalid quantity.");
            case INVALID_PRICE -> plugin.getConfigManager().getMessageOrDefault("ORDERS.INVALID_PRICE", "&cInvalid price.");
            case TOTAL_TOO_HIGH -> plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.TOTAL_TOO_HIGH",
                    "&cTotal order budget cannot exceed &f{max_formatted}&c.",
                    "{max}", NumberUtils.format(
                            plugin.getConfigManager().getOrders().getDouble("PRICING.MAX_TOTAL_BUDGET", 250_000_000D)
                    ),
                    "{max_formatted}", plugin.getCurrencyManager().formatMoney(
                            plugin.getConfigManager().getOrders().getDouble("PRICING.MAX_TOTAL_BUDGET", 250_000_000D)
                    ),
                    "${max}", plugin.getCurrencyManager().formatMoney(
                            plugin.getConfigManager().getOrders().getDouble("PRICING.MAX_TOTAL_BUDGET", 250_000_000D)
                    )
            );
            case NO_MONEY -> plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.NOT_ENOUGH_MONEY",
                    "&cYou do not have enough money for that order."
            );
            case MAX_ORDERS_REACHED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.MAX_ACTIVE_REACHED", "&cYou have reached your active order limit.");
            case DATABASE_ERROR -> "&cOrders could not save your order right now. Try again.";
        };
    }
}
