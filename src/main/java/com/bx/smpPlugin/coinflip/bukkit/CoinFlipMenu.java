// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipPreferences;
import com.bx.smpPlugin.coinflip.CoinFlipService;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.PermissionUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class CoinFlipMenu extends BaseMenu {

    protected final CoinFlipModule module;
    protected final UUID viewer;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private long lastClickMillis;
    private boolean busy;

    protected CoinFlipMenu(CoinFlipModule module, UUID viewer, String title, int size) {
        super(module.plugin(), module.config().titlePrefix() + " &8» &r" + title, size);
        this.module = module;
        this.viewer = viewer;
    }

    protected abstract void render(Player player);

    public boolean live() {
        return false;
    }

    @Override
    public final void build(Player player) {
        actions.clear();
        inventory.clear();
        Gui.frame(inventory, Gui.material(config().borderMaterial(), Material.BLACK_STAINED_GLASS_PANE),
                Gui.material(config().accentMaterial(), Material.PURPLE_STAINED_GLASS_PANE), readable());
        render(player);
    }

    public void refresh() {
        Player player = Bukkit.getPlayer(viewer);
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() == this) {
            build(player);
        }
    }

    @Override
    public final void handleClick(int slot, Player player, ClickType clickType) {
        if (!player.getUniqueId().equals(viewer)) {
            return;
        }
        Consumer<ClickType> action = actions.get(slot);
        if (action == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClickMillis < config().clickCooldownMillis()) {
            return;
        }
        lastClickMillis = now;
        if (busy) {
            return;
        }
        sound("CLICK");
        action.accept(clickType);
    }

    protected void button(int slot, ItemStack item, Consumer<ClickType> action) {
        set(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void button(int slot, ItemStack item, Runnable action) {
        button(slot, item, click -> action.run());
    }

    protected void back(Runnable action) {
        button(inventory.getSize() - 9, Gui.item(Material.ARROW, Gui.INFO + "Back", Gui.MUTED + "Return to the previous menu."), action);
    }

    protected void gated(int slot, boolean available, boolean permitted, String reasonKey, ItemStack item, Runnable action) {
        if (available && permitted) {
            button(slot, item, action);
            return;
        }
        String name = item.getItemMeta() == null ? "" : item.getItemMeta().getDisplayName();
        String key = permitted ? reasonKey : "no-permission";
        button(slot, Gui.item(Material.GRAY_DYE, name, Gui.BAD + com.bx.smpPlugin.utils.ColorUtils.strip(
                module.messages().raw(key, Map.of()))), () -> {
            message(key, Map.of());
            sound("ERROR");
        });
    }

    protected void close() {
        Player player = Bukkit.getPlayer(viewer);
        if (player != null) {
            player.closeInventory();
        }
    }

    protected void setBusy(boolean value) {
        this.busy = value;
    }

    protected boolean busy() {
        return busy;
    }

    protected boolean can(String permission) {
        Player player = Bukkit.getPlayer(viewer);
        return player != null && PermissionUtils.has(player, permission);
    }

    protected void report(CoinFlipService.Outcome outcome) {
        module.presenter().sendOutcome(viewer, outcome);
    }

    protected void message(String key, Map<String, String> placeholders) {
        module.presenter().send(viewer, key, placeholders);
    }

    protected void sound(String key) {
        Player player = Bukkit.getPlayer(viewer);
        if (player != null && config().soundsAllowed() && prefs().sounds()) {
            SoundUtils.play(player, config().sound(key));
        }
    }

    protected CoinFlipConfig config() {
        return module.config();
    }

    protected CoinFlipPreferences prefs() {
        return module.service().preferences(viewer);
    }

    protected boolean readable() {
        return prefs().readableMode();
    }

    protected String money(long cents) {
        return module.money(cents);
    }

    protected void openMenu(CoinFlipMenu menu) {
        Player player = Bukkit.getPlayer(viewer);
        if (player != null) {
            menu.open(player);
        }
    }
}
