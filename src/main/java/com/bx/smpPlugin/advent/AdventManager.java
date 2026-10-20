package com.bx.smpPlugin.advent;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.halloween.CalendarRules;
import com.bx.smpPlugin.managers.FeatureManager.Feature;
import com.bx.smpPlugin.seasonal.CalendarTheme;
import com.bx.smpPlugin.seasonal.EventId;
import com.bx.smpPlugin.seasonal.SeasonCalendar;
import com.bx.smpPlugin.seasonal.SeasonalEvents;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Advent Calendar (/advent): a door a day from 1 to 24 December, read from advent.yml. On top of the shared
 * calendar it keeps a streak of days in a row, with STREAK.BONUSES at set lengths and a COMPLETION prize for
 * opening every door. A short reminder on join points to doors that are ready.
 */
public final class AdventManager extends SeasonCalendar implements Listener {

    public static final String USE_PERMISSION = "smpplugin.advent";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.advent";
    private static final int STREAK_SLOT = 51;

    public AdventManager(SmpPlugin plugin, PlayerStore store, SeasonalEvents events) {
        super(plugin, store, events, EventId.ADVENT, "advent.yml", () -> plugin.getConfigManager().getAdvent(),
                Feature.ADVENT, CalendarRules.of(12, 1, 24, "DAILY", true, 0), CalendarTheme.ADVENT,
                USE_PERMISSION, ADMIN_PERMISSION);
    }

    @Override
    protected boolean streaksByDefault() {
        return true;
    }

    @Override
    public Map<Integer, MenuButton> extraButtons(Player viewer) {
        if (!streaksEnabled()) {
            return Map.of();
        }
        List<String> lore = new ArrayList<>();
        lore.add("&7Open a door on days in a row");
        lore.add("&7to build your streak.");
        lore.add("");
        lore.add("&7Current streak: &b" + currentStreak(viewer) + " day(s)");
        lore.add("&7Best this season: &b" + streak(viewer.getUniqueId()).best());
        int grace = streakGrace(viewer);
        if (grace > 0) {
            lore.add("&7Your rank keeps it through &b" + grace + " &7missed day(s).");
        }
        ConfigurationSection bonuses = config().getConfigurationSection("STREAK.BONUSES");
        if (bonuses != null && !bonuses.getKeys(false).isEmpty()) {
            lore.add("");
            lore.add("&7Streak bonuses:");
            for (String key : bonuses.getKeys(false)) {
                ConfigurationSection bonus = bonuses.getConfigurationSection(key);
                if (bonus == null) {
                    continue;
                }
                boolean got;
                try {
                    got = streak(viewer.getUniqueId()).hasMilestone(Integer.parseInt(key.trim()));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                lore.add((got ? "&a✔ " : "&8• ") + "&f" + key + " days&7: "
                        + String.join("&7, ", com.bx.smpPlugin.core.Reward.read(bonus).describe(plugin)));
            }
        }
        return Map.of(STREAK_SLOT, new MenuButton(ItemUtils.createItem(Material.CAMPFIRE, "&b&lYour streak", lore),
                player -> { }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!inSeason() || !config().getBoolean("JOIN-REMINDER", true) || !PermissionUtils.has(player, USE_PERMISSION)) {
            return;
        }
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (!player.isOnline() || !inSeason() || !store.isLoaded(player.getUniqueId())) {
                return;
            }
            int ready = claimableCount(player.getUniqueId());
            if (ready > 0) {
                Text.line(player, Text.prefix(),
                        Text.get("ADVENT.REMINDER", "&7You have &b{count} &7unopened door{s} in the Advent Calendar! ",
                                "{count}", Integer.toString(ready), "{s}", ready == 1 ? "" : "s"),
                        Text.button(Text.get("ADVENT.REMINDER-BUTTON", "&b&l[Open]"), "advent",
                                Text.get("ADVENT.REMINDER-HOVER", "&7Click to open the calendar")));
            }
        }, 70L);
    }
}
