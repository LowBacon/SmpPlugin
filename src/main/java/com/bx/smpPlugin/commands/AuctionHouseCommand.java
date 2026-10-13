package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.AuctionHouseManager;
import com.bx.smpPlugin.models.PlayerPreference;
import com.bx.smpPlugin.menus.AuctionHouseBrowseMenu;
import com.bx.smpPlugin.menus.AuctionHouseClaimsMenu;
import com.bx.smpPlugin.menus.AuctionHouseSounds;
import com.bx.smpPlugin.menus.AuctionYourItemsMenu;
import com.bx.smpPlugin.menus.QuickBuyConfirmListingMenu;

import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.PlayerSettingUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AuctionHouseCommand implements CommandExecutor, TabCompleter {

    private final SmpPlugin plugin;

    public AuctionHouseCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().message(
                    "AUCTION_HOUSE.PLAYERS_ONLY",
                    "Only players can use this command."
            ));
            return true;
        }

        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        String subcommand = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(subcommand)) {
            handleReload(player);
            return true;
        }
        if (!manager.isEnabled()) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.DISABLED", "&cAuction house is currently disabled.");
            return true;
        }

        switch (subcommand) {
            case "" -> {
                if (requirePermission(player, "use")) {
                    openBrowse(player);
                }
            }
            case "sell" -> {
                if (requirePermission(player, "sell")) {
                    handleSell(player, args);
                }
            }
            case "my" -> {
                if (requirePermission(player, "my")) {
                    openPlayerItems(player);
                }
            }
            case "claims" -> {
                if (!requirePermission(player, "claims")) {
                    return true;
                }
                if (!manager.isClaimsEnabled()) {
                    manager.processAutoClaims(player);
                    send(player, "AUCTION_HOUSE.CLAIMS_AUTOMATIC", "&eClaims are collected automatically.");
                } else {
                    AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.OPEN);
                    new AuctionHouseClaimsMenu(plugin, 1).open(player);
                }
            }
            case "cancel" -> {
                if (requirePermission(player, "cancel")) {
                    handleCancel(player, args);
                }
            }
            case "limit" -> {
                if (requirePermission(player, "limit")) {
                    handleLimit(player);
                }
            }
            case "fastbuy" -> togglePreference(player, true);
            case "fastsell" -> togglePreference(player, false);
            case "search" -> {
                if (requirePermission(player, "use")) {
                    handleSearch(player, args);
                }
            }
            default -> {
                if (requirePermission(player, "use")) {
                    String query = clipSearch(String.join(" ", args));
                    if (!query.isEmpty()) {
                        plugin.getAuctionHouseManager().setSearchQuery(player.getUniqueId(), query);
                    }
                    openBrowse(player);
                }
            }
        }
        return true;
    }

    private void handleReload(Player player) {
        if (!PermissionUtils.has(player, "smpplugin.admin.auctionhouse")) {
            send(player, "AUCTION_HOUSE.NO_ADMIN_PERMISSION", "&cYou cannot reload auction house settings.");
            return;
        }
        plugin.getConfigManager().reloadAuctionHouse();
        plugin.getAuctionHouseManager().reload();
        send(player, "AUCTION_HOUSE.RELOADED", "&aAuction house settings reloaded.");
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.SELL_USAGE", "&cUsage: /ah sell <price>");
            return;
        }

        double price;
        try {
            price = NumberUtils.parse(args[1]);
        } catch (NumberFormatException exception) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.INVALID_PRICE", "&cInvalid price.");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.NO_ITEM_IN_HAND", "&cHold the item you want to list.");
            return;
        }

        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        ItemStack escrow = hand.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.updateInventory();
        manager.getPreferenceAsync(player.getUniqueId()).whenComplete((preference, throwable) -> {
            if (!player.isOnline()) {
                manager.returnEscrow(player, escrow);
                return;
            }
            plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (throwable != null || preference == null) {
                        manager.returnEscrow(player, escrow);
                        AuctionHouseSounds.fail(player, plugin);
                        send(player, "AUCTION_HOUSE.CREATE_DATABASE_ERROR",
                                "&cAuction house could not load your listing settings.");
                        return;
                    }
                    boolean fastSell = preference.fastSellEnabled()
                            && hasEither(player, "smpplugin.auctionhouse.fastsell", "donutauction.fastsell");
                    if (fastSell) {
                        com.bx.smpPlugin.models.AuctionCategory category;
                        boolean autoDetect = plugin.getConfigManager().getAuctionHouse()
                                .getBoolean("SETTINGS.AUTO_DETECT_CATEGORY", false);
                        if (autoDetect) {
                            category = com.bx.smpPlugin.models.AuctionCategory.findCategoryForItem(escrow);
                        } else {
                            category = com.bx.smpPlugin.models.AuctionCategory.from(preference.lastCategory());
                        }

                        manager.createListingFromItem(
                                player,
                                escrow,
                                price,
                                preference.lastDurationHours(),
                                category
                        ).thenAccept(result -> plugin.getSpigotScheduler().runEntity(player, () ->
                                handleCreateResult(player, preference, price, result)
                        ));
                        return;
                    }

                    AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.CONFIRM);
                    new QuickBuyConfirmListingMenu(plugin, escrow, price).open(player);
                });
        });
    }

    private void handleCreateResult(
            Player player,
            PlayerPreference preference,
            double price,
            AuctionHouseManager.CreateListingResult result
    ) {
        if (!result.success()) {
            player.sendMessage(ColorUtils.toComponent(resolveCreateFailure(result)));
            AuctionHouseSounds.fail(player, plugin);
            return;
        }
        preference.lastPrice(price);
        plugin.getAuctionHouseManager().savePreference(preference);
        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                "AUCTION_HOUSE.ITEM_LISTED",
                "&fYou listed {amount} {item} for &a$ &f{price}",
                "{amount}", String.valueOf(result.listing().item().getAmount()),
                "{item}", plugin.getAuctionHouseManager().describeItem(result.listing().item()),
                "{price}", NumberUtils.format(result.listing().price()),
                "{price_formatted}", plugin.getCurrencyManager().formatMoney(result.listing().price()),
                "{listing_id}", String.valueOf(result.listing().id()),
                "{fee}", NumberUtils.format(result.listingFee()),
                "{fee_formatted}", plugin.getCurrencyManager().formatMoney(result.listingFee()),
                "{expires}", plugin.getAuctionHouseManager().formatRemaining(
                        result.listing().secondsRemaining(System.currentTimeMillis())
                )
        )));
        String actionBar = plugin.getConfigManager().getMessageOrDefault(
                "AUCTION_HOUSE.ITEM_LISTED_ACTION_BAR",
                "&fYou listed {amount} {item} for &a$ &f{price}",
                "{amount}", String.valueOf(result.listing().item().getAmount()),
                "{item}", plugin.getAuctionHouseManager().describeItem(result.listing().item()),
                "{price}", NumberUtils.format(result.listing().price()),
                "{price_formatted}", plugin.getCurrencyManager().formatMoney(result.listing().price())
        );
        PlayerSettingUtils.sendActionBar(plugin, player, actionBar);
        AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.LIST_ITEM);
        openPlayerItems(player, false);
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.CANCEL_USAGE", "&cUsage: /ah cancel <listingId>");
            return;
        }
        long listingId;
        try {
            listingId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.INVALID_LISTING_ID", "&cInvalid listing ID.");
            return;
        }

        plugin.getAuctionHouseManager().cancelListing(player, listingId)
                .thenAccept(result -> plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (!result.success()) {
                        String key = switch (result.reason()) {
                            case DISABLED -> "AUCTION_HOUSE.DISABLED";
                            case NO_PERMISSION -> "AUCTION_HOUSE.NO_PERMISSION";
                            case LISTING_NOT_FOUND -> "AUCTION_HOUSE.LISTING_NOT_FOUND";
                            case NOT_OWNER -> "AUCTION_HOUSE.NOT_YOUR_LISTING";
                            case NOT_ACTIVE -> "AUCTION_HOUSE.LISTING_NOT_ACTIVE";
                            case INVENTORY_FULL -> "AUCTION_HOUSE.CLAIM_INVENTORY_FULL";
                            case DATABASE_ERROR -> "AUCTION_HOUSE.CANCEL_DATABASE_ERROR";
                        };
                        AuctionHouseSounds.fail(player, plugin);
                        send(player, key, "&cThe listing could not be cancelled.");
                        return;
                    }
                    AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.CANCEL);
                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                            "AUCTION_HOUSE.LISTING_CANCELLED",
                            "{listing_id}", String.valueOf(result.listing().id()),
                            "{item}", plugin.getAuctionHouseManager().describeItem(result.listing().item())
                    )));
                    openPlayerItems(player, false);
                }));
    }

    private void handleLimit(Player player) {
        int active = plugin.getAuctionHouseManager().countActiveListings(player.getUniqueId());
        int limit = plugin.getAuctionHouseManager().getMaxActiveListings(player);
        String displayLimit = limit == Integer.MAX_VALUE
                ? plugin.getAuctionHouseManager().getText("UNLIMITED", "Unlimited")
                : String.valueOf(limit);
        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                "AUCTION_HOUSE.LIMIT",
                "&6Listing limit: &e{active}&7/&e{limit}",
                "{active}", String.valueOf(active),
                "{limit}", displayLimit
        )));
    }

    private void togglePreference(Player player, boolean fastBuy) {
        String permission = fastBuy ? "fastbuy" : "fastsell";
        if (!canUse(player, permission)) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.NO_FAST_PERMISSION", "&cYou cannot use that fast-action setting.");
            return;
        }

        plugin.getAuctionHouseManager().getPreferenceAsync(player.getUniqueId()).thenAccept(preference -> {
            boolean enabled;
            if (fastBuy) {
                preference.fastBuyEnabled(!preference.fastBuyEnabled());
                enabled = preference.fastBuyEnabled();
            } else {
                preference.fastSellEnabled(!preference.fastSellEnabled());
                enabled = preference.fastSellEnabled();
            }
            plugin.getAuctionHouseManager().savePreference(preference);
            boolean finalEnabled = enabled;
            plugin.getSpigotScheduler().runEntity(player, () -> {
                AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.TOGGLE);
                player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault(
                            fastBuy ? "AUCTION_HOUSE.FAST_BUY_TOGGLED" : "AUCTION_HOUSE.FAST_SELL_TOGGLED",
                            "&6&lAuctionHouse &8» &e{setting} is now {state}&e.",
                            "{setting}", fastBuy
                                    ? plugin.getAuctionHouseManager().getText("FAST_BUY", "Fast buy")
                                    : plugin.getAuctionHouseManager().getText("FAST_SELL", "Fast sell"),
                            "{state}", finalEnabled
                                    ? plugin.getAuctionHouseManager().getText("ENABLED", "&aEnabled")
                                    : plugin.getAuctionHouseManager().getText("DISABLED", "&cDisabled")
                    )
                ));
            });
        });
    }

    private void handleSearch(Player player, String[] args) {
        String query = searchArgument(args);
        if (query.isEmpty()) {
            AuctionHouseSounds.fail(player, plugin);
            send(player, "AUCTION_HOUSE.SEARCH_USAGE", "&cUsage: /ah search <item>");
            return;
        }
        plugin.getAuctionHouseManager().setSearchQuery(player.getUniqueId(), query);
        openBrowse(player);
    }

    static String searchArgument(String[] args) {
        if (args == null || args.length < 2) {
            return "";
        }
        String joined = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        return clipSearch(joined);
    }

    static String clipSearch(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.trim();
        if (trimmed.length() > 64) {
            return trimmed.substring(0, 64);
        }
        return trimmed;
    }

    static List<String> searchTabComplete(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        String prefix = token.toLowerCase(Locale.ROOT).replace(' ', '_');
        return Arrays.stream(Material.values())
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .filter(name -> !name.equals("air") && !name.startsWith("legacy_"))
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .limit(40)
                .toList();
    }

    private void openBrowse(Player player) {
        plugin.getAuctionHouseManager().processAutoClaims(player);
        AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.OPEN);
        String category = "ALL";
        boolean autoFilter = plugin.getConfigManager().getAuctionHouse()
                .getBoolean("SETTINGS.AUTO_FILTER_BY_HAND", false);
        if (autoFilter) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && hand.getType() != org.bukkit.Material.AIR) {
                category = com.bx.smpPlugin.models.AuctionCategory.findCategoryForItem(hand).name();
            }
        }
        new AuctionHouseBrowseMenu(
                plugin,
                1,
                plugin.getAuctionHouseManager().getDefaultSort(),
                category
        ).open(player);
    }

    private void openPlayerItems(Player player) {
        openPlayerItems(player, true);
    }

    private void openPlayerItems(Player player, boolean openSound) {
        plugin.getAuctionHouseManager().processAutoClaims(player);
        if (openSound) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.OPEN);
        }
        new AuctionYourItemsMenu(plugin, AuctionYourItemsMenu.Origin.AUCTION).open(player);
    }

    private boolean hasEither(Player player, String first, String second) {
        return PermissionUtils.has(player, first) || PermissionUtils.has(player, second);
    }

    private boolean requirePermission(Player player, String action) {
        if (canUse(player, action)) {
            return true;
        }
        send(player, "AUCTION_HOUSE.NO_PERMISSION", "&cYou cannot use that auction house action.");
        AuctionHouseSounds.fail(player, plugin);
        return false;
    }

    private boolean canUse(Player player, String action) {
        return hasEither(
                player,
                "smpplugin.auctionhouse." + action,
                "donutauction." + action
        ) || PermissionUtils.has(player, "smpplugin.admin.auctionhouse");
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(key, fallback)));
    }

    private String resolveCreateFailure(AuctionHouseManager.CreateListingResult result) {
        return switch (result.reason()) {
            case DISABLED -> plugin.getConfigManager().getMessage("AUCTION_HOUSE.DISABLED");
            case NO_PERMISSION -> plugin.getConfigManager().getMessage("AUCTION_HOUSE.NO_PERMISSION");
            case NO_PLAYER_DATA -> plugin.getLanguageManager().message(
                    "AUCTION_HOUSE.PLAYER_DATA_UNAVAILABLE",
                    "&cYour player data could not be loaded."
            );
            case NO_ITEM -> plugin.getConfigManager().getMessage("AUCTION_HOUSE.NO_ITEM_IN_HAND");
            case INVALID_ITEM -> plugin.getConfigManager().getMessage("AUCTION_HOUSE.ITEM_BLOCKED");
            case UNSAFE_ITEM -> plugin.getConfigManager().getMessageOrDefault(
                    "CRASH_PROTECTION.ITEM_BLOCKED",
                    "&cThat item cannot be used here. &7Reason: &f{reason}",
                    "{context}", "Auction house",
                    "{reason}", result.safetyResult() == null ? "Unsafe item data" : result.safetyResult().reason()
            );
            case INVALID_PRICE -> plugin.getConfigManager().getMessage("AUCTION_HOUSE.PRICE_OUT_OF_RANGE");
            case INVALID_DURATION -> plugin.getConfigManager().getMessageOrDefault(
                    "AUCTION_HOUSE.INVALID_DURATION",
                    "&cThat listing duration is not allowed."
            );
            case NO_MONEY -> plugin.getConfigManager().getMessage(
                    "AUCTION_HOUSE.NO_MONEY_FOR_FEE",
                    "{fee}", NumberUtils.format(result.listingFee()),
                    "{fee_formatted}", plugin.getCurrencyManager().formatMoney(result.listingFee())
            );
            case MAX_LISTINGS_REACHED -> plugin.getConfigManager().getMessage("AUCTION_HOUSE.MAX_LISTINGS_REACHED");
            case DATABASE_ERROR -> plugin.getLanguageManager().message(
                    "AUCTION_HOUSE.CREATE_DATABASE_ERROR",
                    "&cAuction house could not save your listing."
            );
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length >= 2 && "search".equalsIgnoreCase(args[0]) && canUse(player, "use")) {
            return searchTabComplete(args[args.length - 1]);
        }
        if (args.length != 1) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (canUse(player, "use")) {
            values.add("search");
        }
        if (canUse(player, "sell")) {
            values.add("sell");
        }
        if (canUse(player, "my")) {
            values.add("my");
        }
        if (canUse(player, "limit")) {
            values.add("limit");
        }
        if (canUse(player, "fastbuy")) {
            values.add("fastbuy");
        }
        if (canUse(player, "fastsell")) {
            values.add("fastsell");
        }
        if (plugin.getAuctionHouseManager().isClaimsEnabled()
                && canUse(player, "claims")) {
            values.add("claims");
        }
        if (canUse(player, "cancel")) {
            values.add("cancel");
        }
        if (PermissionUtils.has(player, "smpplugin.admin.auctionhouse")) {
            values.add("reload");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
