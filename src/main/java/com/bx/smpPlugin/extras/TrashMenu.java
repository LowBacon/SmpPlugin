package com.bx.smpPlugin.extras;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * /trash: an empty chest whose contents are destroyed when it closes. It is not a plugin menu,
 * so items move in and out freely while it is open.
 */
public final class TrashMenu implements InventoryHolder {

    private final Inventory inventory;

    public TrashMenu(SmpPlugin plugin) {
        this.inventory = Bukkit.createInventory(this, 36, Text.color(Text.get("UTIL.TRASH-TITLE", "&8🗑 Trash &7(items are deleted on close)")));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
