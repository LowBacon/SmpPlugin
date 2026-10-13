package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.AuctionHouseManager;
import com.bx.smpPlugin.models.AuctionClaim;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AuctionHouseClaimsMenu extends BaseMenu {

    private int page;
    private final Map<Integer, AuctionClaim> claimsBySlot = new HashMap<>();
    private int totalPages = 1;

    public AuctionHouseClaimsMenu(SmpPlugin plugin, int page) {
        super(plugin, plugin.getAuctionHouseManager().getClaimsTitle(), plugin.getAuctionHouseManager().getClaimsSize());
        this.page = Math.max(1, page);
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        claimsBySlot.clear();

        AuctionHouseManager manager = plugin.getAuctionHouseManager();
        List<AuctionClaim> claims = manager.getUnclaimedClaims(player.getUniqueId());
        int itemsPerPage = itemsPerPage();
        totalPages = Math.max(1, (int) Math.ceil(claims.size() / (double) itemsPerPage));
        page = Math.min(page, totalPages);
        int from = (page - 1) * itemsPerPage;
        int to = Math.min(claims.size(), from + itemsPerPage);
        for (int index = from; index < to; index++) {
            int slot = index - from;
            AuctionClaim claim = claims.get(index);
            set(slot, AuctionHouseMenuSupport.createClaimDisplay(plugin, manager, claim));
            claimsBySlot.put(slot, claim);
        }

        int lastRow = inventory.getSize() - 9;
        set(lastRow, page > 1
                ? control("PREVIOUS", Material.ARROW, "&fPrevious page",
                List.of("&7Go to page &f{page}"), "{page}", String.valueOf(page - 1))
                : control("FILLER", Material.BLACK_STAINED_GLASS_PANE, "&7 ", List.of()));
        set(lastRow + 3, control(
                "REFRESH",
                Material.ANVIL,
                "&fRefresh",
                List.of("&7Reload your pending claims")
        ));
        set(lastRow + 4, control(
                "BACK",
                Material.CHEST,
                "&fBack to auction",
                List.of("&7Return to the auction browser")
        ));
        set(lastRow + 5, control(
                "PAGE",
                Material.BOOK,
                "&fPage {page}/{pages}",
                List.of("&7Claims: &f{count}"),
                "{page}", String.valueOf(page),
                "{pages}", String.valueOf(totalPages),
                "{count}", String.valueOf(claims.size())
        ));
        set(lastRow + 8, page < totalPages
                ? control("NEXT", Material.ARROW, "&fNext page",
                List.of("&7Go to page &f{page}"), "{page}", String.valueOf(page + 1))
                : control("FILLER", Material.BLACK_STAINED_GLASS_PANE, "&7 ", List.of()));

        if (claims.isEmpty()) {
            set(Math.max(0, inventory.getSize() / 2), control(
                    "NO_CLAIMS",
                    Material.BARRIER,
                    "&cNo pending claims",
                    List.of("&7Sold payouts and returned items show up here.")
            ));
        }
    }

    @Override
    public void open(Player player) {
        if (!plugin.getAuctionHouseManager().isClaimsEnabled()) {
            plugin.getAuctionHouseManager().processAutoClaims(player);
            return;
        }
        super.open(player);
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        int lastRow = inventory.getSize() - 9;
        if (slot == lastRow && page > 1) {
            AuctionHouseSounds.pageTurn(player, plugin);
            navigate(player, () -> new AuctionHouseClaimsMenu(plugin, page - 1).open(player), false);
            return;
        }
        if (slot == lastRow + 3) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.REFRESH);
            plugin.getAuctionHouseManager().refreshCache().thenRun(() ->
                    plugin.getSpigotScheduler().runEntity(
                            player,
                            () -> navigate(player, () -> new AuctionHouseClaimsMenu(plugin, page).open(player), false)
                    )
            );
            return;
        }
        if (slot == lastRow + 4) {
            AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.OPEN);
            var request = plugin.getAuctionHouseManager().session(player.getUniqueId()).request();
            navigate(player, () -> new AuctionHouseBrowseMenu(
                    plugin,
                    request.page(),
                    request.sort(),
                    request.category().name()
            ).open(player), false);
            return;
        }
        if (slot == lastRow + 8 && page < totalPages) {
            AuctionHouseSounds.pageTurn(player, plugin);
            navigate(player, () -> new AuctionHouseClaimsMenu(plugin, page + 1).open(player), false);
            return;
        }

        AuctionClaim claim = claimsBySlot.get(slot);
        if (claim == null || !plugin.getAuctionHouseManager().isClaimsEnabled()) {
            return;
        }
        plugin.getAuctionHouseManager().claim(player, claim.id())
                .thenAccept(result -> plugin.getSpigotScheduler().runEntity(player, () -> {
                    if (result.success()) {
                        String key = claim.moneyClaim()
                                ? "AUCTION_HOUSE.CLAIMED_MONEY"
                                : "AUCTION_HOUSE.CLAIMED_ITEM";
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(
                                key,
                                "{amount}", NumberUtils.format(claim.moneyAmount()),
                                "{amount_formatted}", plugin.getCurrencyManager().formatMoney(claim.moneyAmount()),
                                "{item}", plugin.getAuctionHouseManager().describeItem(claim.item())
                        )));
                        AuctionHouseSounds.play(player, plugin, AuctionHouseSounds.CLAIM);
                    } else {
                        String key = switch (result.reason()) {
                            case DISABLED -> "AUCTION_HOUSE.DISABLED";
                            case NO_PERMISSION -> "AUCTION_HOUSE.NO_PERMISSION";
                            case CLAIM_NOT_FOUND -> "AUCTION_HOUSE.CLAIM_NOT_FOUND";
                            case NOT_OWNER -> "AUCTION_HOUSE.NOT_YOUR_CLAIM";
                            case ALREADY_CLAIMED -> "AUCTION_HOUSE.CLAIM_ALREADY_CLAIMED";
                            case INVENTORY_FULL -> "AUCTION_HOUSE.CLAIM_INVENTORY_FULL";
                            case NO_PLAYER_DATA, DATABASE_ERROR -> "AUCTION_HOUSE.CLAIM_DATABASE_ERROR";
                        };
                        player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessage(key)));
                        AuctionHouseSounds.fail(player, plugin);
                    }
                    navigate(player, () -> new AuctionHouseClaimsMenu(plugin, page).open(player), false);
                }));
    }

    private int itemsPerPage() {
        int configured = plugin.getAuctionHouseManager().getClaimsItemsPerPage();
        int contentSlots = Math.max(1, inventory.getSize() - 9);
        return Math.max(1, Math.min(contentSlots, configured));
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
                "GUI.CLAIMS.CONTROLS." + key,
                material,
                name,
                lore,
                replacements
        );
    }

    private void navigate(Player player, Runnable action, boolean click) {
        if (click) {
            AuctionHouseSounds.click(player, plugin);
        }
        plugin.getAuctionHouseManager().startNavigating(player.getUniqueId());
        action.run();
    }

    @Override
    public void onClose(Player player) {
        plugin.getAuctionHouseManager().stopNavigating(player.getUniqueId());
    }
}
