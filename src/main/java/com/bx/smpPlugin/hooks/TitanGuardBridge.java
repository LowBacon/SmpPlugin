// SmpPlugin by LowBacon
package com.bx.smpPlugin.hooks;

import com.bx.smpPlugin.SmpPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

// SmpPlugin's side of TitanGuard, the anti-cheat. Every TitanGuard alert lands in the player's
// SmpPlugin logs (category AntiCheat), next to their trades and punishments. TitanGuard's bans
// already run SmpPlugin's /tempban and /ban, so they show in /history and on the ban screen.
// Read by reflection: neither plugin needs the other to build or to run.
public final class TitanGuardBridge implements Listener {

    private static final String ALERT_EVENT = "com.bx.titanguard.api.event.TitanGuardAlertEvent";

    private final SmpPlugin plugin;
    private Plugin titanGuard;
    private boolean linked;

    public TitanGuardBridge(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Plugin found = plugin.getServer().getPluginManager().getPlugin("TitanGuard");
        if (found == null || !found.isEnabled()) {
            return;
        }
        titanGuard = found;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(ALERT_EVENT, true,
                    found.getClass().getClassLoader());
            Method uuid = eventClass.getMethod("getUuid");
            Method name = eventClass.getMethod("getPlayerName");
            Method check = eventClass.getMethod("getCheck");
            Method vl = eventClass.getMethod("getViolationLevel");
            Method confidence = eventClass.getMethod("getConfidence");
            Method explanation = eventClass.getMethod("getExplanation");
            Method evidence = eventClass.getMethod("getEvidenceId");
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    record((UUID) uuid.invoke(event), (String) name.invoke(event), (String) check.invoke(event),
                            (double) vl.invoke(event), (double) confidence.invoke(event),
                            (String) explanation.invoke(event), (String) evidence.invoke(event));
                } catch (ReflectiveOperationException exception) {
                    plugin.getLogger().log(Level.FINE, "Could not read a TitanGuard alert", exception);
                }
            }, plugin, true);
            linked = true;
            plugin.getLogger().info("Linked with TitanGuard " + found.getDescription().getVersion()
                    + ": anti-cheat alerts go to player logs, bans to /history.");
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            plugin.getLogger().warning("TitanGuard " + found.getDescription().getVersion()
                    + " is too old to link with (needs 1.2.0); its own alerts and bans still work.");
        }
    }

    private void record(UUID uuid, String name, String check, double vl, double confidence,
                        String explanation, String evidenceId) {
        if (plugin.getPlayerLogsManager() == null || uuid == null) {
            return;
        }
        String details = String.format(Locale.ROOT, "%s (vl %.1f, %d%%): %s #%s", check, vl,
                Math.round(confidence * 100D), explanation, evidenceId);
        plugin.getPlayerLogsManager().log(uuid, name, "AntiCheat", "TITANGUARD_FLAG", details);
    }

    public boolean linked() {
        return linked;
    }

    public String version() {
        return titanGuard == null ? null : titanGuard.getDescription().getVersion();
    }
}
