package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Christmas presents: while the event runs, monsters killed by players sometimes drop a wrapped
 * present. Right-click it to unwrap a reward picked by weight from events.yml.
 *
 * <p>A present is recognised by a tag only the plugin sets, never by its name, so a renamed chest
 * cannot be opened. Each player can find at most DAILY-LIMIT presents a day; the count is kept in
 * the player store and survives restarts. Presents can be unwrapped while the event runs; one kept
 * until next year still works then.
 */
final class ChristmasEvent extends SeasonalEvent implements Listener {

    private final NamespacedKey presentKey;

    ChristmasEvent(SmpPlugin plugin, EventManager manager) {
        super(plugin, manager, "christmas");
        this.presentKey = new NamespacedKey(plugin, "event_present");
    }

    @Override
    protected String defaultName() {
        return "&cChristmas";
    }

    @Override
    protected String defaultDescription() {
        return "Monsters drop wrapped presents. Right-click one to unwrap a gift.";
    }

    @Override
    protected Material defaultIcon() {
        return Material.CHEST;
    }

    @Override
    protected void onStart() {
        listen(this);
    }

    @Override
    public List<String> progress(Player player) {
        PlayerStore store = store();
        int found = store == null ? 0 : parse(store.get(player.getUniqueId(), storeKey("found." + today())));
        return List.of("&7Presents found today: &c" + found + "&7/&c" + dailyLimit());
    }

    private int dailyLimit() {
        return Math.max(0, config().getInt("PRESENTS.DAILY-LIMIT", 10));
    }

    ItemStack present() {
        Material material = Material.matchMaterial(config().getString("PRESENTS.ITEM", "CHEST").toUpperCase(Locale.ROOT));
        ItemStack item = ItemUtils.createItem(material == null || !material.isItem() ? Material.CHEST : material,
                config().getString("PRESENTS.ITEM-NAME", "&c&lChristmas Present"),
                config().getStringList("PRESENTS.ITEM-LORE").isEmpty()
                        ? List.of("&7A wrapped gift.", "", "&e▶ Right-click to unwrap")
                        : config().getStringList("PRESENTS.ITEM-LORE"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(presentKey, PersistentDataType.STRING, id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isPresent(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(presentKey, PersistentDataType.STRING);
        return id().equals(tag);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (!(entity instanceof Monster) || killer == null) {
            return;
        }
        PlayerStore store = store();
        if (store == null || !store.isLoaded(killer.getUniqueId())) {
            return;
        }
        double chance = config().getDouble("PRESENTS.CHANCE-PERCENT", 5D);
        if (ThreadLocalRandom.current().nextDouble(100D) >= chance) {
            return;
        }
        String key = storeKey("found." + today());
        int found = parse(store.get(killer.getUniqueId(), key));
        if (found >= dailyLimit()) {
            return;
        }
        store.set(killer.getUniqueId(), key, Integer.toString(found + 1));
        event.getDrops().add(present());
        Text.actionBar(killer, config().getString("PRESENTS.DROP-MESSAGE", "&cA present fell out! &7Pick it up."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack item = event.getItem();
        if (!isPresent(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        WeightedRewards table = WeightedRewards.read(config().getConfigurationSection("PRESENTS.REWARDS"));
        WeightedRewards.Entry entry = table.roll(ThreadLocalRandom.current());
        if (entry == null) {
            Text.send(player, "EVENTS.PRESENT-EMPTY", "&7This present is empty. Ask staff to set PRESENTS.REWARDS.");
            return;
        }
        // Take the present first, so a second click in the same tick cannot open it twice.
        item.setAmount(item.getAmount() - 1);
        entry.reward().give(plugin, player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1.2, 0), 30, 0.4, 0.5, 0.4, 0.02);
        String gift = String.join("&7, ", entry.reward().describe(plugin));
        Text.send(player, "EVENTS.PRESENT-OPENED", "&cYou unwrapped: &f{gift}", "{gift}", gift);
        if (entry.announce()) {
            Text.broadcast(Text.prefix() + Text.get("EVENTS.PRESENT-ANNOUNCE", "&c{player} &7unwrapped &f{gift}&7!",
                    "{player}", player.getName(), "{gift}", gift));
        }
    }

    /** A present is an item, not a block. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isPresent(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    private static int parse(String raw) {
        try {
            return raw == null ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
