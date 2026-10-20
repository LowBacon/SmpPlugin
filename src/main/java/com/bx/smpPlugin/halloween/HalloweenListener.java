package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Halloween while the season runs: a greeting and door reminder on join, candy dropped by monsters (eat it for a
 * short boost), candy for the shop from monster kills, quest progress, and zombies and skeletons in pumpkin masks.
 * Each handler starts with a season test that is worked out at most once a second, so outside October they return
 * at once.
 */
public final class HalloweenListener implements Listener {

    private static final String GREETED = "halloween.greeted";
    private static final List<PotionEffectType> CANDY_EFFECTS = List.of(
            PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.NIGHT_VISION,
            PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.GLOWING,
            PotionEffectType.SLOW_FALLING, PotionEffectType.FIRE_RESISTANCE);

    private final SmpPlugin plugin;
    private final HalloweenManager halloween;
    private final HalloweenHub hub;
    private final PlayerStore store;
    private final NamespacedKey candyKey;

    public HalloweenListener(SmpPlugin plugin, HalloweenManager halloween, HalloweenHub hub, PlayerStore store) {
        this.plugin = plugin;
        this.halloween = halloween;
        this.hub = hub;
        this.store = store;
        this.candyKey = new NamespacedKey(plugin, "halloween_candy");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!halloween.inSeason() || !halloween.config().getBoolean("AMBIENCE.JOIN-REMINDER", true)) {
            return;
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline() || !halloween.inSeason()) {
                return;
            }
            String today = halloween.now().toLocalDate().toString();
            if (!today.equals(store.get(player.getUniqueId(), GREETED))) {
                store.set(player.getUniqueId(), GREETED, today);
                long untilHalloween = halloween.rules().secondsUntilLastDay(halloween.now());
                Text.title(player, Text.get("HALLOWEEN.GREETING-TITLE", "&6&l🎃 Happy Halloween! 🎃"),
                        untilHalloween > 0
                                ? Text.get("HALLOWEEN.GREETING-COUNTDOWN", "&7Halloween in &f{time}", "{time}", Text.duration(untilHalloween))
                                : Text.get("HALLOWEEN.GREETING-TODAY", "&5Tonight is the night..."),
                        10, 60, 20);
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 0.6F, 0.9F);
            }
            int ready = halloween.claimableCount(player.getUniqueId());
            if (ready > 0) {
                Text.line(player, Text.prefix(),
                        Text.get("HALLOWEEN.REMINDER", "&7You have &6{count} &7unopened door{s} in the Spooky Calendar! ",
                                "{count}", Integer.toString(ready), "{s}", ready == 1 ? "" : "s"),
                        Text.button(Text.get("HALLOWEEN.REMINDER-BUTTON", "&6&l[Open]"), "halloween",
                                Text.get("HALLOWEEN.REMINDER-HOVER", "&7Click to open the calendar")));
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMonsterDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !hub.running()) {
            return;
        }
        hub.track(killer, QuestType.KILL_MOB, entity.getType().name(), 1);
        if (!(entity instanceof Monster)) {
            return;
        }
        hub.track(killer, QuestType.KILL_MONSTERS, null, 1);
        double candyChance = halloween.config().getDouble("CANDY.MONSTER-KILL-CHANCE-PERCENT", 15D);
        if (ThreadLocalRandom.current().nextDouble(100D) < candyChance) {
            hub.earn(killer, Math.max(0L, halloween.config().getLong("CANDY.MONSTER-KILL-AMOUNT", 1L)), true,
                    Text.get("HALLOWEEN.KILL-REASON", "monster"));
        }
        if (!halloween.config().getBoolean("AMBIENCE.CANDY-DROPS.ENABLED", true)) {
            return;
        }
        double chance = halloween.config().getDouble("AMBIENCE.CANDY-DROPS.CHANCE-PERCENT", 8D);
        if (ThreadLocalRandom.current().nextDouble(100D) < chance) {
            event.getDrops().add(candy());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (hub.running()) {
            hub.track(event.getPlayer(), QuestType.BREAK_BLOCK, event.getBlock().getType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(candyKey, PersistentDataType.BYTE)) {
            return;
        }
        Player player = event.getPlayer();
        hub.track(player, QuestType.EAT_CANDY, null, 1);
        PotionEffectType type = CANDY_EFFECTS.get(ThreadLocalRandom.current().nextInt(CANDY_EFFECTS.size()));
        player.addPotionEffect(new PotionEffect(type, 20 * 20, 0, false, true, true));
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1.2, 0), 15, 0.3, 0.4, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.7F, 1.4F);
        Text.actionBar(player, Text.get("HALLOWEEN.CANDY-EATEN", "&6Sweet! &7You feel a little spooky..."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        EntityType type = event.getEntityType();
        if (type != EntityType.ZOMBIE && type != EntityType.SKELETON && type != EntityType.HUSK
                && type != EntityType.STRAY && type != EntityType.ZOMBIE_VILLAGER) {
            return;
        }
        if (!hub.running() || !halloween.config().getBoolean("AMBIENCE.PUMPKIN-HEADS.ENABLED", true)) {
            return;
        }
        double chance = halloween.config().getDouble("AMBIENCE.PUMPKIN-HEADS.CHANCE-PERCENT", 25D);
        EntityEquipment equipment = event.getEntity().getEquipment();
        if (equipment == null || ThreadLocalRandom.current().nextDouble(100D) >= chance
                || (equipment.getHelmet() != null && !equipment.getHelmet().getType().isAir())) {
            return;
        }
        boolean lit = ThreadLocalRandom.current().nextInt(10) == 0;
        equipment.setHelmet(new ItemStack(lit ? Material.JACK_O_LANTERN : Material.CARVED_PUMPKIN));
        equipment.setHelmetDropChance(0F);
    }

    /** One Halloween Candy: a cookie marked so eating it gives a boost. */
    public ItemStack candy() {
        ItemStack candy = ItemUtils.createItem(Material.COOKIE, Text.get("HALLOWEEN.CANDY-NAME", "&6&lHalloween Candy"),
                List.of(Text.get("HALLOWEEN.CANDY-LORE", "&7Eat it for a spooky surprise!")));
        ItemMeta meta = candy.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(candyKey, PersistentDataType.BYTE, (byte) 1);
            candy.setItemMeta(meta);
        }
        return candy;
    }
}
