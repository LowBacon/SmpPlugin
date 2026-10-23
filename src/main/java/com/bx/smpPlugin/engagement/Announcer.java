// SmpPlugin by LowBacon
package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class Announcer implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.admin.announcer";

    private final SmpPlugin plugin;
    private int next;
    private long lastSent = System.currentTimeMillis();
    private boolean paused;

    public Announcer(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    List<List<String>> messages() {
        List<List<String>> out = new ArrayList<>();
        List<?> raw = config().getList("ANNOUNCER.MESSAGES");
        if (raw == null) {
            return out;
        }
        for (Object entry : raw) {
            if (entry instanceof List<?> lines) {
                List<String> block = new ArrayList<>();
                lines.forEach(line -> block.add(String.valueOf(line)));
                out.add(block);
            } else if (entry != null) {
                out.add(List.of(String.valueOf(entry)));
            }
        }
        return out;
    }

    public void tick() {
        if (paused || !config().getBoolean("ANNOUNCER.ENABLED", true)
                || !plugin.getFeatureManager().isEnabled(FeatureManager.Feature.ANNOUNCER)
                || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        long interval = Math.max(30L, config().getLong("ANNOUNCER.INTERVAL-SECONDS", 420L)) * 1000L;
        if (System.currentTimeMillis() - lastSent < interval) {
            return;
        }
        List<List<String>> messages = messages();
        if (messages.isEmpty()) {
            return;
        }
        int index = config().getBoolean("ANNOUNCER.RANDOM", false)
                ? ThreadLocalRandom.current().nextInt(messages.size())
                : Math.floorMod(next++, messages.size());
        send(messages.get(index));
    }

    private void send(List<String> lines) {
        lastSent = System.currentTimeMillis();
        Sound sound = Text.sound(config().getString("ANNOUNCER.SOUND", "BLOCK_NOTE_BLOCK_CHIME"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            Text.raw(player, "");
            for (String line : lines) {
                Text.raw(player, line);
            }
            Text.raw(player, "");
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 0.5F, 1.2F);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        List<List<String>> messages = messages();
        switch (action) {
            case "send" -> {
                int index;
                try {
                    index = Integer.parseInt(args.length > 1 ? args[1] : "") - 1;
                } catch (NumberFormatException ignored) {
                    index = -1;
                }
                if (index < 0 || index >= messages.size()) {
                    Text.send(sender, "ANNOUNCER.USAGE", "&7/announcer send <1-{count}>", "{count}", Integer.toString(messages.size()));
                } else {
                    send(messages.get(index));
                }
            }
            case "toggle" -> {
                paused = !paused;
                Text.send(sender, "ANNOUNCER.TOGGLED", paused ? "&7Announcements paused." : "&aAnnouncements resumed.");
            }
            default -> {
                Text.send(sender, "ANNOUNCER.LIST", "&6Announcements &7({count}, every {interval}s){paused}:",
                        "{count}", Integer.toString(messages.size()),
                        "{interval}", Long.toString(config().getLong("ANNOUNCER.INTERVAL-SECONDS", 420L)),
                        "{paused}", paused ? " &c(paused)" : "");
                for (int i = 0; i < messages.size(); i++) {
                    Text.line(sender, Text.button("&e[" + (i + 1) + "] ", "announcer send " + (i + 1), "&7Send this one now"),
                            messages.get(i).isEmpty() ? "" : messages.get(i).get(0));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && PermissionUtils.has(sender, PERMISSION)) {
            return List.of("list", "send", "toggle").stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
