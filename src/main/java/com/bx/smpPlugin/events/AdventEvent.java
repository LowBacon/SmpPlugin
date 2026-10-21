package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarMenu;
import com.bx.smpPlugin.halloween.CalendarTheme;
import com.bx.smpPlugin.halloween.HalloweenManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * The Advent Calendar: one door a day in December (advent.yml), the same calendar as the Spooky
 * Calendar with a Christmas theme. /advent opens it while the event runs.
 */
final class AdventEvent extends SeasonalEvent implements Listener {

    private final HalloweenManager calendar;

    AdventEvent(SmpPlugin plugin, EventManager manager, PlayerStore store) {
        super(plugin, manager, "advent");
        this.calendar = new HalloweenManager(plugin, store, CalendarTheme.ADVENT,
                () -> plugin.getConfigManager().getAdvent(), this::isRunning);
    }

    HalloweenManager calendar() {
        return calendar;
    }

    @Override
    protected String defaultName() {
        return "&cAdvent Calendar";
    }

    @Override
    protected String defaultDescription() {
        return "Open one door every day of December for a gift (advent.yml).";
    }

    @Override
    protected Material defaultIcon() {
        return Material.CAKE;
    }

    @Override
    protected void onStart() {
        listen(this);
    }

    @Override
    protected void onReload() {
        calendar.loadOverrides();
    }

    @Override
    public void open(Player player) {
        if (!calendar.isEnabled()) {
            Text.send(player, "ADVENT.DISABLED", CalendarTheme.ADVENT.closedText());
            return;
        }
        new CalendarMenu(plugin, calendar).open(player);
    }

    @Override
    public List<String> progress(Player player) {
        return List.of("&7Doors opened: &c" + calendar.openedCount(player.getUniqueId()),
                "&7Doors ready: &c" + calendar.claimableCount(player.getUniqueId()));
    }

    /** A reminder about unopened doors, a few seconds after joining. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!config().getBoolean("JOIN-REMINDER", true)) {
            return;
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline() || !calendar.inSeason()) {
                return;
            }
            int ready = calendar.claimableCount(player.getUniqueId());
            if (ready > 0) {
                Text.line(player, Text.prefix() + Text.get("ADVENT.REMINDER",
                                "&7You have &c{count} &7unopened door{s} in the Advent Calendar! ",
                                "{count}", Integer.toString(ready), "{s}", ready == 1 ? "" : "s"),
                        Text.button(Text.get("ADVENT.REMINDER-BUTTON", "&c&l[Open]"), "advent",
                                Text.get("ADVENT.REMINDER-HOVER", "&7Click to open the calendar")));
            }
        }, 100L);
    }
}
