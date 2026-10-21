package com.bx.smpPlugin.core;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.DatabaseManager;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.managers.OptimizationManager;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The text of /smp diagnostics: server load, the database's health, the plugin's caches and
 * queues, the slowest commands and anything that failed to start. Every number is measured on
 * this server since it started; nothing is estimated.
 */
public final class DiagnosticsReport {

    private DiagnosticsReport() {
    }

    public static List<String> build(SmpPlugin plugin) {
        List<String> lines = new ArrayList<>();
        lines.add("&8&m----------&r &bSmpPlugin " + plugin.getDescription().getVersion() + " diagnostics &8&m----------");

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;
        long maxMb = runtime.maxMemory() / 1_048_576L;
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        OptimizationManager optimization = plugin.getOptimizationManager();
        String load = optimization == null || optimization.getLastSampleMillis() <= 0L ? "&8not sampled yet"
                : String.format(Locale.ROOT, "&f%.1f TPS&7, &f%.1f ms&7/tick &8(%s)", optimization.getLastTps(),
                optimization.getLastMspt(), optimization.getLoadState().display());
        lines.add("&7Server: &f" + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + " &8| &7Java &f"
                + System.getProperty("java.version"));
        lines.add("&7Load: " + load + " &8| &7Memory: &f" + usedMb + "&7/&f" + maxMb + " MB &8| &7Uptime: &f"
                + Text.duration(uptimeSeconds));
        lines.add("&7Players online: &f" + Bukkit.getOnlinePlayers().size() + " &8| &7Brand: &f" + Branding.name());

        DatabaseManager database = plugin.getDatabaseManager();
        if (database != null) {
            String probe = Diagnostics.lastDatabaseProbeAt() <= 0L ? "&8not measured yet (every minute)"
                    : "&fquery " + Diagnostics.millis(Diagnostics.lastDatabaseMillis()) + "&7, waited "
                    + Diagnostics.millis(Diagnostics.lastDatabaseQueueMillis()) + " in the queue"
                    + (Diagnostics.lastDatabaseError() == null ? "" : " &c" + Diagnostics.lastDatabaseError());
            lines.add("&7Database: &f" + database.getDatabaseType() + (database.isConnected() ? " &aconnected" : " &cnot connected")
                    + " &8| &7schema v&f" + (plugin.getV4Module() == null ? "?" : plugin.getV4Module().schemaVersion()));
            lines.add("&7  last check: " + probe);
            lines.add("&7  writes waiting: &f" + database.getPendingWriteCount() + " &8| &7written since start: &f"
                    + database.getCompletedWriteCount());
        }

        Map<String, Long> gauges = Diagnostics.gauges();
        if (!gauges.isEmpty()) {
            StringBuilder caches = new StringBuilder("&7Sizes: ");
            gauges.forEach((name, value) -> caches.append("&f").append(name).append("&7=&f").append(value).append("  "));
            lines.add(caches.toString().trim());
        }

        List<Diagnostics.TimerView> slow = Diagnostics.slowest("command:", 5);
        lines.add("&7Commands run since start: &f" + Diagnostics.totalSamples("command:"));
        if (!slow.isEmpty()) {
            lines.add("&7Slowest commands (average / slowest single run):");
            for (Diagnostics.TimerView view : slow) {
                lines.add("&8  " + view.name().substring("command:".length()) + " &f"
                        + Diagnostics.millis(view.averageMillis()) + " &7/ &f" + Diagnostics.millis(view.maxMillis())
                        + " &8(" + view.count() + "x)");
            }
        }

        int enabled = 0;
        int disabled = 0;
        for (FeatureManager.Feature feature : FeatureManager.Feature.values()) {
            if (plugin.getFeatureManager().isEnabled(feature)) {
                enabled++;
            } else {
                disabled++;
            }
        }
        lines.add("&7Modules: &a" + enabled + " on&7, &c" + disabled + " off &8(/smp modules)");

        List<String> problems = new ArrayList<>(plugin.getFailedListeners());
        if (plugin.getV4Module() != null) {
            plugin.getV4Module().partStatus().forEach((part, status) -> {
                // "off: <reason>" is a part that failed to start; one switched off by staff is not a problem.
                if (status.startsWith("off:")) {
                    problems.add(part + " (" + status + ")");
                }
            });
        }
        lines.add(problems.isEmpty() ? "&7Failed to start: &anothing"
                : "&7Failed to start: &c" + String.join("&7, &c", problems));
        return lines;
    }
}
