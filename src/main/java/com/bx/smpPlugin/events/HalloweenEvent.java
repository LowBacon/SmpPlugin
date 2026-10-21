package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.halloween.HalloweenManager;
import com.bx.smpPlugin.managers.FeatureManager.Feature;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * The Halloween season (3.0) as seen from /event. It keeps its own dates in halloween.yml and is
 * switched with the HALLOWEEN feature, so /event enable|disable halloween flips that feature.
 */
final class HalloweenEvent extends SeasonalEvent {

    private final HalloweenManager halloween;

    HalloweenEvent(SmpPlugin plugin, EventManager manager, HalloweenManager halloween) {
        super(plugin, manager, "halloween");
        this.halloween = halloween;
    }

    @Override
    protected String defaultName() {
        return "&6Halloween";
    }

    @Override
    protected String defaultDescription() {
        return "The Spooky Calendar, Trick or Treat, candy drops and pumpkin heads (halloween.yml).";
    }

    @Override
    protected Material defaultIcon() {
        return Material.JACK_O_LANTERN;
    }

    @Override
    public boolean isManaged() {
        return false;
    }

    @Override
    public boolean isConfigured() {
        return halloween.config().getBoolean("ENABLED", true);
    }

    @Override
    public boolean isRunning() {
        return halloween.isEnabled() && halloween.inSeason();
    }

    @Override
    public Optional<EventSchedule> schedule() {
        var rules = halloween.rules();
        int year = halloween.seasonYear();
        return EventSchedule.parse(rules.seasonStart(year).toString(), rules.claimDeadline(year).plusDays(1).toString());
    }

    /** Staff switch: turns the HALLOWEEN feature on or off. */
    void switchFeature(boolean on) {
        plugin.getFeatureManager().setEnabled(Feature.HALLOWEEN, on);
    }

    boolean featureOn() {
        return plugin.getFeatureManager().isEnabled(Feature.HALLOWEEN);
    }

    @Override
    public void open(Player player) {
        plugin.getSpigotScheduler().dispatchPlayerCommand(player, "halloween");
    }

    @Override
    public List<String> progress(Player player) {
        return List.of("&7Doors opened: &6" + halloween.openedCount(player.getUniqueId()),
                "&7Doors ready: &6" + halloween.claimableCount(player.getUniqueId()));
    }
}
