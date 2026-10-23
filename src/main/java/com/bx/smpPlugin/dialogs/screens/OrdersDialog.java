// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.dialogs.DialogSupport;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.OrdersManager;
import com.bx.smpPlugin.menus.OrdersBrowseMenu;
import com.bx.smpPlugin.menus.OrdersMyOrdersMenu;
import com.bx.smpPlugin.models.OrderSort;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class OrdersDialog extends DialogScreen {

    private static final int BUTTON_WIDTH = 150;
    private static final int CHOOSE_INPUT_WIDTH = 220;
    private static final int CHOOSE_SEARCH_WIDTH = 100;
    private static final int CHOOSE_ITEM_WIDTH = 130;
    private static final int CHOOSE_CANCEL_WIDTH = 100;
    private static final int AMOUNT_INPUT_WIDTH = 200;
    private static final int AMOUNT_BUTTON_WIDTH = 150;
    private static final int PRICE_INPUT_WIDTH = 200;
    private static final int PRICE_BUTTON_WIDTH = 150;
    private static final int REVIEW_BUTTON_WIDTH = 130;
    private static final int REVIEW_CREATE_WIDTH = 150;

    public OrdersDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    public boolean openSearch(Player player, OrderSort sort, String categoryFilter, String query) {
        plugin.getOrdersManager().getUiState(player.getUniqueId()).sort(sort);
        plugin.getOrdersManager().getUiState(player.getUniqueId()).filter(categoryFilter);
        String current = query == null ? "" : query;
        return show(player, screen(player)
                .title(text("ORDERS.GUI.DIALOG.SEARCH.TITLE", "Search Orders"))
                .item(new ItemStack(Material.OAK_SIGN))
                .columns(2)
                .input(new DialogConfig.InputSpec(
                        "query",
                        text("ORDERS.GUI.DIALOG.SEARCH.INPUT", "Search"),
                        300,
                        32,
                        current,
                        List.of()
                ))
                .button(text("ORDERS.GUI.DIALOG.SEARCH.CANCEL", "&cCancel"), null, BUTTON_WIDTH, DialogActions.ORD_SEARCH_CAN)
                .button(text("ORDERS.GUI.DIALOG.SEARCH.SUBMIT", "&aSearch"), null, BUTTON_WIDTH, DialogActions.ORD_SEARCH_GO)
                .build());
    }

    public boolean openChooseItem(Player player) {
        return openChooseItem(player, "");
    }

    public boolean openChooseItem(Player player, String query) {
        plugin.getOrdersManager().getOrCreateNewOrderSession(player.getUniqueId());
        String q = query == null ? "" : query.trim();
        DialogFactory.Screen screen = screen(player)
                .title(text("ORDERS.GUI.DIALOG.CHOOSE.TITLE", "Choose Item"))
                .columns(4)
                .input(new DialogConfig.InputSpec(
                        "search",
                        text("ORDERS.GUI.DIALOG.CHOOSE.INPUT", "Search"),
                        CHOOSE_INPUT_WIDTH,
                        32,
                        q,
                        List.of()
                ));

        screen.canCloseWithEscape(true);
        screen.button(text("ORDERS.GUI.DIALOG.CHOOSE.SEARCH", "&fSearch"), null, CHOOSE_SEARCH_WIDTH, DialogActions.ORD_PICK_SRCH);

        OrdersManager manager = plugin.getOrdersManager();
        // shop.yml QUICK-BUY.CHOOSE-ITEM.BLACKLIST is Quick Buy only.
        for (Material mat : QuickBuyItemDialog.getSelectableMaterials(plugin, q, List.of())) {
            if (!manager.canOrderMaterial(mat)) {
                continue;
            }
            String name = manager.describeMaterial(mat);
            String sprite = QuickBuyItemDialog.getSpriteTag(mat);
            String label = sprite.isEmpty() ? ("&f" + name) : (sprite + " &f" + name);
            screen.button(
                    label,
                    QuickBuyItemDialog.chooseItemWorthTooltip(plugin, player, mat, name),
                    CHOOSE_ITEM_WIDTH,
                    DialogActions.ORD_ITEM + mat.name().toLowerCase(Locale.ROOT)
            );
        }

        screen.exit(text("ORDERS.GUI.DIALOG.CHOOSE.CANCEL", "&cCancel"), DialogActions.ORD_PICK_CAN, CHOOSE_CANCEL_WIDTH);
        return show(player, screen.build());
    }

    public boolean openAmount(Player player) {
        OrdersManager.NewOrderSession session = plugin.getOrdersManager().getOrCreateNewOrderSession(player.getUniqueId());
        ItemStack item = session.getChosenItem();
        if (item == null) {
            return openChooseItem(player);
        }
        int amount = session.getAmount() > 0 ? session.getAmount() : 1;
        return show(player, screen(player)
                .title(text("ORDERS.GUI.DIALOG.AMOUNT.TITLE", "How many?"))
                .item(item.clone())
                .columns(2)
                .canCloseWithEscape(true)
                .input(new DialogConfig.InputSpec(
                        "amount",
                        text("ORDERS.GUI.DIALOG.AMOUNT.INPUT", "Amount"),
                        AMOUNT_INPUT_WIDTH,
                        10,
                        String.valueOf(amount),
                        List.of()
                ))
                .button(text("ORDERS.GUI.DIALOG.AMOUNT.CANCEL", "&cCancel"), null, AMOUNT_BUTTON_WIDTH, DialogActions.ORD_AMT_CAN)
                .button(text("ORDERS.GUI.DIALOG.AMOUNT.NEXT", "&aNext"), null, AMOUNT_BUTTON_WIDTH, DialogActions.ORD_AMT_NEXT)
                .build());
    }

    public boolean openPrice(Player player, boolean invalid) {
        OrdersManager manager = plugin.getOrdersManager();
        OrdersManager.NewOrderSession session = manager.getOrCreateNewOrderSession(player.getUniqueId());
        ItemStack item = session.getChosenItem();
        if (item == null) {
            return openChooseItem(player);
        }
        int amount = Math.max(1, session.getAmount());
        DialogFactory.Screen screen = screen(player)
                .title(text("ORDERS.GUI.DIALOG.PRICE.TITLE", "Price per item?"));
        if (invalid) {
            screen.text(text("ORDERS.GUI.DIALOG.PRICE.INVALID", "&cInvalid price."));
        }
        screen.item(item.clone())
                .text(formatAmountLine("ORDERS.GUI.DIALOG.PRICE.AMOUNT", "&7Amount: &f{amount}", amount))
                .text(formatPriceLine(
                        "Minimum:",
                        stripNumber(manager.getMinPriceEach()),
                        text(
                                "ORDERS.GUI.DIALOG.PRICE.MINIMUM",
                                "&7Minimum: &a$ &f{min}",
                                "{min}", stripNumber(manager.getMinPriceEach()),
                                "{symbol}", plugin.getCurrencyManager().symbol(CurrencyManager.CurrencyType.MONEY),
                                "{symbol_colored}", plugin.getCurrencyManager().coloredSymbol(CurrencyManager.CurrencyType.MONEY)
                        )
                ))
                .columns(2)
                .canCloseWithEscape(true)
                .input(new DialogConfig.InputSpec(
                        "price",
                        text("ORDERS.GUI.DIALOG.PRICE.INPUT", "Price"),
                        PRICE_INPUT_WIDTH,
                        16,
                        session.getPriceEach() > 0D ? stripNumber(session.getPriceEach()) : "",
                        List.of()
                ))
                .button(text("ORDERS.GUI.DIALOG.PRICE.CANCEL", "&cCancel"), null, PRICE_BUTTON_WIDTH, DialogActions.ORD_PRICE_CAN)
                .button(text("ORDERS.GUI.DIALOG.PRICE.NEXT", "&aReview Order"), null, PRICE_BUTTON_WIDTH, DialogActions.ORD_PRICE_GO);
        return show(player, screen.build());
    }

    public boolean openReview(Player player) {
        OrdersManager manager = plugin.getOrdersManager();
        OrdersManager.NewOrderSession session = manager.getOrCreateNewOrderSession(player.getUniqueId());
        ItemStack item = session.getChosenItem();
        if (item == null || session.getAmount() <= 0 || session.getPriceEach() <= 0D) {
            return openChooseItem(player);
        }
        String itemName = manager.describeItem(item);
        String price = stripNumber(session.getPriceEach());
        String total = stripNumber(manager.roundCurrency(session.getAmount() * session.getPriceEach()));
        return show(player, screen(player)
                .title(text("ORDERS.GUI.DIALOG.REVIEW.TITLE", "Review Order"))
                .item(item.clone())
                .text(formatItemLine(text("ORDERS.GUI.DIALOG.REVIEW.ITEM", "&7Item: &f{item}", "{item}", itemName), itemName))
                .text(formatAmountLine("ORDERS.GUI.DIALOG.REVIEW.AMOUNT", "&7Amount: &f{amount}", session.getAmount()))
                .text(formatPriceLine(
                        "Price:",
                        price + " each",
                        text(
                                "ORDERS.GUI.DIALOG.REVIEW.PRICE",
                                "&7Price: &a$ &f{price} each",
                                "{price}", price,
                                "{symbol}", plugin.getCurrencyManager().symbol(CurrencyManager.CurrencyType.MONEY),
                                "{symbol_colored}", plugin.getCurrencyManager().coloredSymbol(CurrencyManager.CurrencyType.MONEY)
                        )
                ))
                .text(formatPriceLine(
                        "Total:",
                        total,
                        text(
                                "ORDERS.GUI.DIALOG.REVIEW.TOTAL",
                                "&7Total: &a$ &f{total}",
                                "{total}", total,
                                "{symbol}", plugin.getCurrencyManager().symbol(CurrencyManager.CurrencyType.MONEY),
                                "{symbol_colored}", plugin.getCurrencyManager().coloredSymbol(CurrencyManager.CurrencyType.MONEY)
                        )
                ))
                .columns(2)
                .canCloseWithEscape(true)
                .button(text("ORDERS.GUI.DIALOG.REVIEW.CANCEL", "&cCancel"), null, REVIEW_BUTTON_WIDTH, DialogActions.ORD_REV_CAN)
                .button(text("ORDERS.GUI.DIALOG.REVIEW.CHANGE_ITEM", "Change Item"), null, REVIEW_BUTTON_WIDTH, DialogActions.ORD_REV_ITEM)
                .button(text("ORDERS.GUI.DIALOG.REVIEW.CHANGE_AMOUNT", "Change Amount"), null, REVIEW_BUTTON_WIDTH, DialogActions.ORD_REV_AMT)
                .button(text("ORDERS.GUI.DIALOG.REVIEW.CHANGE_PRICE", "Change Price"), null, REVIEW_BUTTON_WIDTH, DialogActions.ORD_REV_PRICE)
                .button(text("ORDERS.GUI.DIALOG.REVIEW.CREATE", "&aCreate Order"), null, REVIEW_CREATE_WIDTH, DialogActions.ORD_REV_CREATE)
                .build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.ORD_SEARCH_CAN.equals(action)) {
            click(player);
            reopenBrowse(player);
            return true;
        }
        if (DialogActions.ORD_SEARCH_GO.equals(action)) {
            click(player);
            String query = input(response, "query");
            var state = plugin.getOrdersManager().getUiState(player.getUniqueId());
            plugin.getOrdersManager().playSound(player, "ORDERS.SEARCH");
            DialogSupport.close(player);
            plugin.getSpigotScheduler().runEntity(player, () -> new OrdersBrowseMenu(
                    plugin,
                    1,
                    state.sort(),
                    state.filter(),
                    query == null ? "" : query
            ).open(player));
            return true;
        }
        if (DialogActions.ORD_PICK_CAN.equals(action)) {
            click(player);
            if (backToReviewIfDraft(player)) {
                return true;
            }
            plugin.getOrdersManager().clearPendingCreation(player.getUniqueId());
            reopenMyOrders(player);
            return true;
        }
        if (DialogActions.ORD_REV_CAN.equals(action)) {
            click(player);
            plugin.getOrdersManager().clearPendingCreation(player.getUniqueId());
            reopenMyOrders(player);
            return true;
        }
        if (DialogActions.ORD_PICK_SRCH.equals(action)) {
            click(player);
            String search = input(response, "search");
            openChooseItem(player, search == null ? "" : search);
            return true;
        }
        if (action.startsWith(DialogActions.ORD_ITEM)) {
            click(player);
            String raw = DialogActions.argument(action, DialogActions.ORD_ITEM);
            Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
            if (material == null
                    || ChooseItemBlacklist.isBlacklisted(material, List.of())
                    || !plugin.getOrdersManager().canOrderMaterial(material)) {
                plugin.getOrdersManager().playSound(player, "ORDERS.FAIL");
                return true;
            }
            OrdersManager.NewOrderSession session = plugin.getOrdersManager().getOrCreateNewOrderSession(player.getUniqueId());
            boolean editingDraft = hasReviewDraft(session);
            session.setChosenItem(new ItemStack(material));
            session.setCategoryKey(plugin.getOrdersManager().resolveCategoryForMaterial(material));
            if (session.getAmount() <= 0) {
                session.setAmount(1);
            }
            if (editingDraft) {
                openReview(player);
            } else {
                openAmount(player);
            }
            return true;
        }
        if (DialogActions.ORD_AMT_CAN.equals(action)) {
            click(player);
            if (backToReviewIfDraft(player)) {
                return true;
            }
            openChooseItem(player);
            return true;
        }
        if (DialogActions.ORD_AMT_NEXT.equals(action)) {
            click(player);
            if (!applyAmount(player, response)) {
                plugin.getOrdersManager().playSound(player, "ORDERS.FAIL");
                openAmount(player);
                return true;
            }
            if (hasReviewDraft(player)) {
                openReview(player);
            } else {
                openPrice(player, false);
            }
            return true;
        }
        if (DialogActions.ORD_PRICE_CAN.equals(action)) {
            click(player);
            if (backToReviewIfDraft(player)) {
                return true;
            }
            openAmount(player);
            return true;
        }
        if (DialogActions.ORD_PRICE_GO.equals(action)) {
            click(player);
            if (!applyPrice(player, response)) {
                plugin.getOrdersManager().playSound(player, "ORDERS.FAIL");
                openPrice(player, true);
                return true;
            }
            openReview(player);
            return true;
        }
        if (DialogActions.ORD_REV_ITEM.equals(action)) {
            click(player);
            openChooseItem(player);
            return true;
        }
        if (DialogActions.ORD_REV_AMT.equals(action)) {
            click(player);
            openAmount(player);
            return true;
        }
        if (DialogActions.ORD_REV_PRICE.equals(action)) {
            click(player);
            openPrice(player, false);
            return true;
        }
        if (DialogActions.ORD_REV_CREATE.equals(action)) {
            click(player);
            create(player);
            return true;
        }
        return false;
    }

    private boolean applyAmount(Player player, DialogResponseView response) {
        OrdersManager manager = plugin.getOrdersManager();
        OrdersManager.NewOrderSession session = manager.getOrCreateNewOrderSession(player.getUniqueId());
        String raw = input(response, "amount");
        try {
            int amount = Math.toIntExact(NumberUtils.parseLong(raw == null ? "1" : raw));
            if (amount <= 0 || amount > manager.getMaxQuantityPerOrder()) {
                return false;
            }
            session.setAmount(amount);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean applyPrice(Player player, DialogResponseView response) {
        OrdersManager manager = plugin.getOrdersManager();
        OrdersManager.NewOrderSession session = manager.getOrCreateNewOrderSession(player.getUniqueId());
        String raw = input(response, "price");
        try {
            double price = manager.roundCurrency(NumberUtils.parse(raw == null ? "" : raw));
            if (price < manager.getMinPriceEach() || price > manager.getMaxPriceEach()) {
                return false;
            }
            session.setPriceEach(price);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void create(Player player) {
        OrdersManager manager = plugin.getOrdersManager();
        if (!manager.beginAction(player.getUniqueId())) {
            return;
        }
        try {
            OrdersManager.CreateOrderResult result = manager.createOrder(player);
            if (!result.success() || result.order() == null) {
                if (result.reason() != null) {
                    player.sendMessage(ColorUtils.toComponent(createFailureMessage(result)));
                    plugin.getOrdersManager().playSound(player, "ORDERS.FAIL");
                }
                reopenMyOrders(player);
                return;
            }
            PlayerSettingUtils.sendActionBar(
                    plugin,
                    player,
                    plugin.getConfigManager().getMessageOrDefault(
                            "ORDERS.CREATED",
                            "&7You ordered &a{quantity} {item}",
                            "{quantity}", String.valueOf(result.order().requestedQuantity()),
                            "{item}", manager.describeItem(result.order().requestedItem()),
                            "{order_id}", String.valueOf(result.order().id())
                    )
            );
            plugin.getOrdersManager().playSound(player, "ORDERS.CREATE");
            DialogSupport.close(player);
            plugin.getSpigotScheduler().runEntity(player, () ->
                    new OrdersMyOrdersMenu(plugin, 1, manager.getDefaultSort()).open(player));
        } finally {
            manager.endAction(player.getUniqueId());
        }
    }

    private String createFailureMessage(OrdersManager.CreateOrderResult result) {
        return switch (result.reason()) {
            case DISABLED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.DISABLED", "&cOrders is currently disabled.");
            case NO_PENDING_ORDER -> plugin.getConfigManager().getMessageOrDefault("ORDERS.NO_PENDING_ORDER", "&cThere is no pending order draft to confirm.");
            case NO_PLAYER_DATA -> "&cYour player data could not be loaded.";
            case INVALID_ITEM -> plugin.getConfigManager().getMessageOrDefault("ORDERS.ITEM_BLOCKED", "&cThat item cannot be ordered.");
            case UNSAFE_ITEM -> plugin.getConfigManager().getMessageOrDefault(
                    "CRASH_PROTECTION.ITEM_BLOCKED",
                    "&cThat item cannot be used here because its data looks unsafe.",
                    "{context}", "Orders",
                    "{reason}", result.safetyResult() == null ? "Unsafe item data" : result.safetyResult().reason()
            );
            case INVALID_QUANTITY -> plugin.getConfigManager().getMessageOrDefault("ORDERS.INVALID_QUANTITY", "&cInvalid quantity.");
            case INVALID_PRICE -> plugin.getConfigManager().getMessageOrDefault("ORDERS.INVALID_PRICE", "&cInvalid price.");
            case TOTAL_TOO_HIGH -> plugin.getConfigManager().getMessageOrDefault("ORDERS.TOTAL_TOO_HIGH", "&cTotal order budget is too high.");
            case NO_MONEY -> plugin.getConfigManager().getMessageOrDefault("ORDERS.NOT_ENOUGH_MONEY", "&cYou do not have enough money for that order.");
            case MAX_ORDERS_REACHED -> plugin.getConfigManager().getMessageOrDefault("ORDERS.MAX_ACTIVE_REACHED", "&cYou have reached your active order limit.");
            case DATABASE_ERROR -> "&cOrders could not save your order right now. Try again.";
        };
    }

    private void reopenBrowse(Player player) {
        var state = plugin.getOrdersManager().getUiState(player.getUniqueId());
        DialogSupport.close(player);
        plugin.getSpigotScheduler().runEntity(player, () -> new OrdersBrowseMenu(
                plugin, state.page() + 1, state.sort(), state.filter(), state.search()
        ).open(player));
    }

    private void reopenMyOrders(Player player) {
        DialogSupport.close(player);
        plugin.getSpigotScheduler().runEntity(player, () ->
                new OrdersMyOrdersMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort()).open(player));
    }

    private boolean backToReviewIfDraft(Player player) {
        if (!hasReviewDraft(player)) {
            return false;
        }
        openReview(player);
        return true;
    }

    private boolean hasReviewDraft(Player player) {
        return hasReviewDraft(plugin.getOrdersManager().getOrCreateNewOrderSession(player.getUniqueId()));
    }

    private static boolean hasReviewDraft(OrdersManager.NewOrderSession session) {
        return session != null
                && session.getChosenItem() != null
                && session.getAmount() > 0
                && session.getPriceEach() > 0D;
    }

    private String text(String path, String fallback, String... placeholders) {
        return plugin.getLanguageManager().text(path, null, fallback, placeholders);
    }

    private String formatPriceLine(String prefix, String valueWithSuffix, String raw) {
        String symbol = plugin.getCurrencyManager().symbol(CurrencyManager.CurrencyType.MONEY);
        String symbolColored = plugin.getCurrencyManager().coloredSymbol(CurrencyManager.CurrencyType.MONEY);
        return sanitizePriceLine(raw, prefix, valueWithSuffix, symbol, symbolColored);
    }

    private String formatAmountLine(String path, String fallback, int amount) {
        String val = String.valueOf(amount);
        String raw = text(path, fallback, "{amount}", val);
        if ("Amount: {amount}".equals(raw) || ("Amount: " + val).equals(raw)) {
            return "&7Amount: &f" + val;
        }
        return raw;
    }

    private static String formatItemLine(String raw, String itemName) {
        if ("Item: {item}".equals(raw) || ("Item: " + itemName).equals(raw)) {
            return "&7Item: &f" + itemName;
        }
        return raw;
    }

    static String sanitizePriceLine(String raw, String prefix, String valueWithSuffix, String symbol, String symbolColored) {
        if (raw == null || raw.isBlank()) {
            return "&7" + prefix + " " + symbolColored + " &f" + valueWithSuffix;
        }
        if (raw.contains("$") || (symbol != null && !symbol.isBlank() && raw.contains(symbol))) {
            return raw;
        }
        String stripped = raw.trim();
        if (stripped.startsWith(prefix)) {
            String remainder = stripped.substring(prefix.length()).trim();
            return "&7" + prefix + " " + symbolColored + " &f" + remainder;
        }
        if (stripped.startsWith("&7" + prefix)) {
            String remainder = stripped.substring(("&7" + prefix).length()).trim();
            if (remainder.startsWith("&f")) {
                remainder = remainder.substring(2).trim();
            }
            return "&7" + prefix + " " + symbolColored + " &f" + remainder;
        }
        return "&7" + prefix + " " + symbolColored + " &f" + valueWithSuffix;
    }

    private static String stripNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
