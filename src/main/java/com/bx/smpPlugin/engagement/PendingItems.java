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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The reward vault: items owed to a player that could not be handed over (a full inventory, or they were offline).
 * Kept in the player store until they make room, and never dropped on the ground where anyone could pick them up.
 *
 * <p>Every batch is its own entry ({@code pending.items.<id>}), so adding never reads and rewrites what is already
 * there: a batch added while the player logs in is at worst handed over on the next login, never lost. The single
 * {@code pending.items} entry written by 3.0 is read as one more batch.
 */
public final class PendingItems {

    static final String SECTION = "pending.items";
    private static final AtomicLong COUNTER = new AtomicLong();

    private PendingItems() {
    }

    private static PlayerStore store(SmpPlugin plugin) {
        return plugin.getV3Module() == null ? null : plugin.getV3Module().store();
    }

    /** Keeps one item for a player, online or not. */
    public static boolean add(SmpPlugin plugin, UUID owner, ItemStack item) {
        return item != null && keep(plugin, owner, List.of(item));
    }

    /**
     * Keeps items for a player, online or not. Any thread. Returns false (and keeps nothing) when the vault is not
     * available, so the caller can fall back.
     */
    public static boolean keep(SmpPlugin plugin, UUID owner, List<ItemStack> items) {
        PlayerStore store = store(plugin);
        if (store == null || !store.isReady() || owner == null || items == null || items.isEmpty()) {
            return false;
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                copies.add(item.clone());
            }
        }
        if (copies.isEmpty()) {
            return true;
        }
        store.set(owner, newEntry(), write(copies));
        return true;
    }

    /**
     * Gives items to an online player; what does not fit goes to the vault (with a message), or, only when the
     * vault cannot be used, drops at their feet. Call on the player's thread.
     *
     * @return the stacks that went to the vault
     */
    public static int giveOrKeep(SmpPlugin plugin, Player player, List<ItemStack> items) {
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                rest.addAll(player.getInventory().addItem(item.clone()).values());
            }
        }
        if (rest.isEmpty()) {
            return 0;
        }
        if (keep(plugin, player.getUniqueId(), rest)) {
            Text.line(player, Text.prefix(), Text.get("PENDING.KEPT",
                            "&7Your inventory is full: &e{count} &7stack(s) went to your reward vault. ",
                            "{count}", Integer.toString(rest.size())),
                    Text.button(Text.get("PENDING.WAITING-BUTTON", "&a&l[Collect]"), "claimitems",
                            Text.get("PENDING.WAITING-HOVER", "&7Click to run /claimitems")));
            return rest.size();
        }
        plugin.getLogger().warning("The reward vault is unavailable; dropping " + rest.size() + " stack(s) at "
                + player.getName() + "'s feet.");
        for (ItemStack item : rest) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        return 0;
    }

    /** Stacks waiting for a loaded player. */
    public static int waiting(PlayerStore store, UUID owner) {
        int count = 0;
        for (String stored : store.sections(owner, SECTION).values()) {
            count += read(stored).size();
        }
        return count;
    }

    /**
     * Hands over what fits. What does not fit stays in the vault. Call on the player's thread.
     *
     * @param quiet true to say nothing when the vault is empty
     * @return the stacks still waiting
     */
    public static int deliver(SmpPlugin plugin, PlayerStore store, Player player, boolean quiet) {
        UUID uuid = player.getUniqueId();
        int given = 0;
        int left = 0;
        for (Map.Entry<String, String> entry : store.sections(uuid, SECTION).entrySet()) {
            List<ItemStack> items = read(entry.getValue());
            if (items.isEmpty()) {
                // Unreadable (or empty): left alone so staff can still look at it.
                continue;
            }
            List<ItemStack> rest = new ArrayList<>();
            boolean changed = false;
            for (ItemStack item : items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                int notGiven = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                if (notGiven < item.getAmount()) {
                    given++;
                    changed = true;
                }
                rest.addAll(leftover.values());
            }
            if (changed) {
                store.set(uuid, entry.getKey(), rest.isEmpty() ? null : write(rest));
            }
            left += rest.size();
        }
        if (given > 0) {
            Text.send(player, "PENDING.DELIVERED", "&aYou got &f{count} &aitem stack(s) from your reward vault.",
                    "{count}", Integer.toString(given));
        }
        if (left > 0) {
            Text.line(player, Text.prefix(), Text.get("PENDING.WAITING",
                            "&e{count} &7stack(s) are still waiting in your reward vault. Make room, then ",
                            "{count}", Integer.toString(left)),
                    Text.button(Text.get("PENDING.WAITING-BUTTON", "&a&l[Collect]"), "claimitems",
                            Text.get("PENDING.WAITING-HOVER", "&7Click to run /claimitems")));
        } else if (given == 0 && !quiet) {
            Text.send(player, "PENDING.EMPTY", "&7Your reward vault is empty.");
        }
        return left;
    }

    private static String newEntry() {
        return SECTION + "." + Long.toString(System.currentTimeMillis(), 36) + "-"
                + Long.toString(COUNTER.incrementAndGet(), 36) + "-"
                + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36), 36);
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
