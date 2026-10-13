package com.bx.smpPlugin.anticheat;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.BaseMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

/** Inventory, totem and client checks: INVENTORY, AUTO_TOTEM and BAD_CLIENT. Main thread only. */
public final class AntiCheatPlayerListener implements Listener {

    private final SmpPlugin plugin;
    private final AntiCheatManager manager;

    public AntiCheatPlayerListener(SmpPlugin plugin, AntiCheatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ── INVENTORY ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !manager.isActive()) {
            return;
        }
        // The plugin's own menus are buttons, not item handling; fast clicking there is harmless.
        if (event.getView().getTopInventory().getHolder() instanceof BaseMenu) {
            return;
        }
        long now = System.currentTimeMillis();
        PlayerState state = manager.state(player);
        int perSecond = state.inventoryClicks.record(now);
        int perTick = state.inventoryClicks.countInSameTick(now);

        if (manager.shouldCheck(player, CheckType.INVENTORY, now)) {
            CheckSettings settings = manager.config().settings(CheckType.INVENTORY);
            double maxPerTick = settings.param("MAX-CLICKS-PER-TICK", 6D);
            double maxPerSecond = settings.param("MAX-CLICKS-PER-SECOND", 30D);
            AntiCheatManager.FlagResult result = null;
            if (perTick > maxPerTick) {
                result = manager.flag(player, CheckType.INVENTORY, perTick - maxPerTick,
                        perTick + " inventory clicks in one tick", now);
            } else if (perSecond > maxPerSecond) {
                result = manager.flag(player, CheckType.INVENTORY, (perSecond - maxPerSecond) / 5D,
                        perSecond + " inventory clicks/s", now);
            }
            if (result != null && result.act()) {
                event.setCancelled(true);
            }

            // Arm the movement check: the next updates must show the player slowing down.
            boolean movingFast = now - state.lastMoveMillis < 100L && state.lastMoveOnSolidGround
                    && state.lastHorizontal >= settings.param("MIN-MOVE-SPEED", 0.2D);
            state.inventoryReferenceSpeed = movingFast ? state.lastHorizontal : 0D;
            state.inventoryCheckUntilMillis = now + 150L;
        }

        if (state.lastTotemPopMillis > 0L && now - state.lastTotemPopMillis < 2_000L) {
            long popped = state.lastTotemPopMillis;
            plugin.getSpigotScheduler().runEntity(player, () -> checkAutoTotem(player, popped));
        }
    }

    // ── AUTO_TOTEM ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            manager.state(player).lastTotemPopMillis = System.currentTimeMillis();
        }
    }

    private void checkAutoTotem(Player player, long poppedAtMillis) {
        if (!player.isOnline()) {
            return;
        }
        PlayerState state = manager.state(player);
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (state.lastTotemPopMillis != poppedAtMillis || offHand.getType() != Material.TOTEM_OF_UNDYING) {
            return;
        }
        state.lastTotemPopMillis = 0L;
        long now = System.currentTimeMillis();
        long reaction = now - poppedAtMillis;
        double minimum = manager.config().settings(CheckType.AUTO_TOTEM).param("MIN-REACTION-MS", 100D);
        if (reaction < minimum && manager.shouldCheck(player, CheckType.AUTO_TOTEM, now)) {
            manager.flag(player, CheckType.AUTO_TOTEM, 1D, "new totem in the off-hand " + reaction + " ms after one was used", now);
        }
    }

    // ── BAD_CLIENT ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!manager.config().clientScanEnabled()) {
            return;
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> scanClient(player), manager.config().clientScanDelayTicks());
    }

    private void scanClient(Player player) {
        if (!player.isOnline() || !manager.isActive()) {
            return;
        }
        String brand = clientBrand(player);
        manager.state(player).clientBrand = brand;
        long now = System.currentTimeMillis();
        if (!manager.shouldCheck(player, CheckType.BAD_CLIENT, now)) {
            return;
        }
        String match = ClientSignature.matchBrand(brand, manager.config().blockedBrands());
        if (match != null) {
            manager.flag(player, CheckType.BAD_CLIENT, 1D, "client brand \"" + brand + "\" matches \"" + match + "\"", now);
            return;
        }
        Set<String> channels = player.getListeningPluginChannels();
        String channel = ClientSignature.matchChannel(channels, manager.config().blockedChannels());
        if (channel != null) {
            manager.flag(player, CheckType.BAD_CLIENT, 1D, "registered cheat-client channel " + channel, now);
        }
    }

    /** Paper exposes the brand the client sent; Spigot does not, so this returns null there. */
    private static String clientBrand(Player player) {
        try {
            Method method = player.getClass().getMethod("getClientBrandName");
            Object value = method.invoke(player);
            return value == null ? null : value.toString().toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
