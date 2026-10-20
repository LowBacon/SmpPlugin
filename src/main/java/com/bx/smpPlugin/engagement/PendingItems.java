package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Items owed to a player who was offline when they were due (a giveaway prize handed back to a
 * host who left). Kept in the player store and given out the next time they join.
 */
public final class PendingItems {

    private static final String SECTION = "pending.items";

    private PendingItems() {
    }

    /** Adds an item for a player who is offline. Works entirely on the database thread. */
    public static void add(SmpPlugin plugin, UUID owner, ItemStack item) {
        PlayerStore store = plugin.getV3Module() == null ? null : plugin.getV3Module().store();
        if (store == null || owner == null || item == null) {
            return;
        }
        ItemStack copy = item.clone();
        plugin.getDatabaseManager().executeAsync(() -> {
            List<ItemStack> items = read(store.readBlocking(owner, SECTION));
            items.add(copy);
            store.set(owner, SECTION, write(items));
        });
    }

    /** Hands over anything owed. Call on the player's thread after they join. */
    public static void deliver(SmpPlugin plugin, PlayerStore store, Player player) {
        List<ItemStack> items = read(store.get(player.getUniqueId(), SECTION));
        if (items.isEmpty()) {
            return;
        }
        store.set(player.getUniqueId(), SECTION, null);
        for (ItemStack item : items) {
            for (ItemStack rest : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        Text.send(player, "PENDING.DELIVERED", "&aYou got back &f{count} &aitem stack(s) that were waiting for you.",
                "{count}", Integer.toString(items.size()));
    }

    static List<ItemStack> read(String stored) {
        List<ItemStack> items = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return items;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(stored);
        } catch (InvalidConfigurationException ignored) {
            return items;
        }
        List<?> list = yaml.getList("items");
        if (list != null) {
            for (Object entry : list) {
                if (entry instanceof ItemStack stack) {
                    items.add(stack);
                }
            }
        }
        return items;
    }

    static String write(List<ItemStack> items) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("items", items);
        return yaml.saveToString();
    }
}
