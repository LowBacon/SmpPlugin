package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.entity.Player;

public final class AuctionHouseClaimsMenu extends BaseMenu {

    private final int page;

    public AuctionHouseClaimsMenu(SmpPlugin plugin, int page) {
        super(plugin, plugin.getAuctionHouseManager().getClaimsTitle(), 54);
        this.page = Math.max(1, page);
    }

    @Override
    public void build(Player player) {
    }

    @Override
    public void open(Player player) {
        if (!plugin.getAuctionHouseManager().isClaimsEnabled()) {
            plugin.getAuctionHouseManager().processAutoClaims(player);
        }
        new PlayerAuctionGui(plugin, page).open(player);
    }
}
