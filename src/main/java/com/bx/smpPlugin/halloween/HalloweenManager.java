// SmpPlugin by LowBacon
package com.bx.smpPlugin.halloween;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.managers.FeatureManager.Feature;
import com.bx.smpPlugin.seasonal.CalendarTheme;
import com.bx.smpPlugin.seasonal.EventId;
import com.bx.smpPlugin.seasonal.EventStatus;
import com.bx.smpPlugin.seasonal.SeasonCalendar;
import com.bx.smpPlugin.seasonal.SeasonalEvents;
import com.bx.smpPlugin.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HalloweenManager extends SeasonCalendar {

    public static final String USE_PERMISSION = "smpplugin.halloween";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.halloween";
    private static final int TRICK_SLOT = 51;
    private static final int HUB_SLOT = 45;
    private volatile HalloweenHub hub;
    private volatile HalloweenMenus menus;

    public HalloweenManager(SmpPlugin plugin, PlayerStore store, SeasonalEvents events) {
        super(plugin, store, events, EventId.HALLOWEEN, "halloween.yml", () -> plugin.getConfigManager().getHalloween(),
                Feature.HALLOWEEN, CalendarRules.of(10, 1, 31, "DAILY", true, 0), CalendarTheme.HALLOWEEN,
                USE_PERMISSION, ADMIN_PERMISSION);
        events.register(EventId.TRICK_OR_TREAT, new SeasonalEvents.Source() {
            @Override
            public boolean configEnabled() {
                return config().getBoolean("ENABLED", true) && config().getBoolean("TRICK-OR-TREAT.ENABLED", true);
            }

            @Override
            public boolean featureEnabled() {
                return featureOn();
            }

            @Override
            public CalendarRules rules() {
                return trickOrTreatRules();
            }

            @Override
            public ZonedDateTime now() {
                return HalloweenManager.this.now();
            }

            @Override
            public String command() {
                return "trickortreat";
            }
        });
    }

    public void attach(HalloweenHub hub, HalloweenMenus menus) {
        this.hub = hub;
        this.menus = menus;
    }

    @Override
    public String calendarCommand() {
        return "halloween calendar";
    }

    @Override
    public void open(Player player) {
        HalloweenMenus current = menus;
        if (current == null) {
            super.open(player);
        } else {
            current.openHub(player);
        }
    }

    @Override
    protected void onDoorOpened(Player player, Granted granted) {
        HalloweenHub current = hub;
        if (current != null) {
            current.track(player, QuestType.OPEN_DOORS, null, 1);
        }
    }

    public CalendarRules trickOrTreatRules() {
        CalendarRules season = rules();
        FileConfiguration c = config();
        int first = c.getInt("TRICK-OR-TREAT.FIRST-DAY", season.firstDay());
        int last = c.getInt("TRICK-OR-TREAT.LAST-DAY", season.lastDay());
        return new CalendarRules(season.month(), first, last, season.mode(), season.catchUp(), season.graceDays());
    }

    public boolean trickOrTreatShown() {
        return events.status(EventId.TRICK_OR_TREAT) != EventStatus.DISABLED;
    }

    @Override
    public Map<Integer, MenuButton> extraButtons(Player viewer) {
        Map<Integer, MenuButton> buttons = new HashMap<>();
        if (menus != null) {
            buttons.put(HUB_SLOT, new MenuButton(ItemUtils.createItem(Material.ARROW, "&6&lHalloween hub",
                    List.of("&7Quests, the candy shop and more.", "", "&e▶ Click to go back")), player -> menus.openHub(player)));
        }
        if (trickOrTreatShown()) {
            buttons.put(TRICK_SLOT, new MenuButton(ItemUtils.createItem(Material.CAULDRON, "&5&lTrick or Treat!",
                    List.of("&7Knock on a door and hope for", "&7candy... or a trick.", "",
                            "&e▶ Click or use /trickortreat")), player -> {
                player.closeInventory();
                plugin.getSpigotScheduler().dispatchPlayerCommand(player, "trickortreat");
            }));
        }
        return buttons;
    }
}
