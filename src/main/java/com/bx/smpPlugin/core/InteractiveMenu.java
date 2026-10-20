package com.bx.smpPlugin.core;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * A menu that handles raw inventory events itself, for screens where items may be placed
 * (the prize editors). Ordinary menus cancel every click; these decide per slot.
 */
public interface InteractiveMenu {

    void handleInventoryClick(InventoryClickEvent event);

    void handleInventoryDrag(InventoryDragEvent event);
}
