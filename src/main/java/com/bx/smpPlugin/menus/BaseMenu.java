// SmpPlugin by LowBacon
package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class BaseMenu implements InventoryHolder {

    protected final SmpPlugin plugin;
    protected Inventory inventory;
    private int renderVersion;

    public BaseMenu(SmpPlugin plugin, String title, int size) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, size, ColorUtils.toComponent(title));
    }

    public BaseMenu(SmpPlugin plugin, String title, org.bukkit.event.inventory.InventoryType type) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, type, ColorUtils.toComponent(title));
    }

    public abstract void build(Player player);

    public void handleClick(int slot, Player player, ClickType clickType) {
        handleClick(slot, player); // default: delegate to simple version
    }

    public void handleClick(int slot, Player player) {}

    public void onClose(Player player) {}

    public void open(Player player) {
        boolean fromGame = !(player.getOpenInventory().getTopInventory().getHolder() instanceof BaseMenu);
        build(player);
        player.openInventory(inventory);
        if (fromGame && animatesOpening()) {
            MenuReveal.play(plugin, player, this);
        }
    }

    // Menus players put items into, and menus with an animation of their own, open without the
    // shared reveal.
    protected boolean animatesOpening() {
        return !(this instanceof com.bx.smpPlugin.core.InteractiveMenu
                || this instanceof SellMenu
                || this instanceof CrateEditorMenu
                || this instanceof OrdersDepositMenu
                || this instanceof OrdersInventoryItemMenu
                || this instanceof RTPMenu
                || this instanceof CrateGachaMenu
                || this instanceof com.bx.smpPlugin.coinflip.bukkit.CoinFlipMenu);
    }

    int renderVersion() {
        return renderVersion;
    }

    public void close(Player player) {
        if (player != null) {
            player.closeInventory();
        }
    }

    public boolean isEscBackTarget() {
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected void fill(Material material) {
        renderVersion++;
        ItemUtils.fillInventory(inventory, material);
    }

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    protected void clear() {
        renderVersion++;
        inventory.clear();
    }

    protected boolean isPlaceholder(ItemStack item) {
        if (item == null) return true;
        return item.getType() == Material.GRAY_STAINED_GLASS_PANE
                || item.getType() == Material.BLACK_STAINED_GLASS_PANE;
    }
}
