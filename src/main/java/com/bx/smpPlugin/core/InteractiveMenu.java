// SmpPlugin by LowBacon
package com.bx.smpPlugin.core;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public interface InteractiveMenu {

    void handleInventoryClick(InventoryClickEvent event);

    void handleInventoryDrag(InventoryDragEvent event);
}
