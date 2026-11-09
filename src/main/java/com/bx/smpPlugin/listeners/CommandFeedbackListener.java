// SmpPlugin by LowBacon
package com.bx.smpPlugin.listeners;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Locale;

/** Consistent command feedback and the vanilla unemployed-villager rejection sound. */
public final class CommandFeedbackListener implements Listener {

    private static final String CONFIG = "COMMAND-FEEDBACK.";
    private final SmpPlugin plugin;
    private final CommandMap commandMap;

    public CommandFeedbackListener(SmpPlugin plugin) {
        this.plugin = plugin;
        this.commandMap = findCommandMap();
        registerUnknownCommandEvent();
    }

    /** Handles permission checks before Bukkit sends its default chat-only denial. */
    @org.bukkit.event.EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled() || commandMap == null) return;
        String label = commandLabel(event.getMessage());
        if (label.isEmpty()) return;

        Command command = commandMap.getCommand(label);
        if (command == null || command.testPermissionSilent(event.getPlayer())) return;

        event.setCancelled(true);
        showCommandError(event.getPlayer());
    }

    /** Emits the normal villager refusal sound for an adult villager with no profession. */
    @org.bukkit.event.EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !plugin.getConfigManager().getConfig().getBoolean(CONFIG + "UNEMPLOYED-VILLAGER.ENABLED", true)
                || !(event.getRightClicked() instanceof Villager villager)
                || !villager.isAdult()) return;

        Villager.Profession profession = villager.getProfession();
        if (profession != Villager.Profession.NONE && profession != Villager.Profession.NITWIT) return;

        String soundName = plugin.getConfigManager().getConfig()
                .getString(CONFIG + "UNEMPLOYED-VILLAGER.SOUND", "ENTITY_VILLAGER_NO");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            sound = Sound.ENTITY_VILLAGER_NO;
        }
        float volume = (float) plugin.getConfigManager().getConfig()
                .getDouble(CONFIG + "UNEMPLOYED-VILLAGER.VOLUME", 0.8);
        float pitch = (float) plugin.getConfigManager().getConfig()
                .getDouble(CONFIG + "UNEMPLOYED-VILLAGER.PITCH", 1.0);
        event.getPlayer().playSound(event.getPlayer().getLocation(), sound, volume, pitch);
    }

    private boolean enabled() {
        return plugin.getConfigManager().getConfig().getBoolean(CONFIG + "ENABLED", true);
    }

    private void showCommandError(Player player) {
        String message = plugin.getConfigManager().getConfig().getString(CONFIG + "MESSAGE",
                "&cYou don't have permission to use this command, or this command doesn't exist!");
        Text.actionBar(player, message);
    }

    private static String commandLabel(String raw) {
        if (raw == null) return "";
        String command = raw.stripLeading();
        if (!command.startsWith("/")) return "";
        command = command.substring(1).stripLeading();
        int end = 0;
        while (end < command.length() && !Character.isWhitespace(command.charAt(end))) end++;
        return command.substring(0, end).toLowerCase(Locale.ROOT);
    }

    private CommandMap findCommandMap() {
        try {
            Method getter = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object result = getter.invoke(Bukkit.getServer());
            if (result instanceof CommandMap map) return map;
        } catch (ReflectiveOperationException ignored) {
            // Spigot-compatible fallback: CraftServer keeps the Bukkit command map in a field.
        }

        for (Class<?> type = Bukkit.getServer().getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("commandMap");
                field.setAccessible(true);
                Object result = field.get(Bukkit.getServer());
                if (result instanceof CommandMap map) return map;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Continue up the implementation hierarchy.
            }
        }
        plugin.getLogger().warning("Could not access the server command map; permission error feedback is disabled.");
        return null;
    }

    @SuppressWarnings("unchecked")
    private void registerUnknownCommandEvent() {
        try {
            Class<?> rawType = Class.forName("org.bukkit.event.command.UnknownCommandEvent");
            if (!Event.class.isAssignableFrom(rawType)) return;
            Class<? extends Event> eventType = (Class<? extends Event>) rawType;
            EventExecutor executor = (listener, event) -> handleUnknownCommand(event);
            plugin.getServer().getPluginManager().registerEvent(eventType, this, EventPriority.HIGHEST,
                    executor, plugin, true);
        } catch (ClassNotFoundException ignored) {
            // Older Spigot builds do not provide Paper's UnknownCommandEvent.
        }
    }

    private void handleUnknownCommand(Event event) throws EventException {
        try {
            Object sender = event.getClass().getMethod("getSender").invoke(event);
            if (!(sender instanceof Player player)) return;

            if (!enabled()) return;
            event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
            showCommandError(player);
        } catch (ReflectiveOperationException exception) {
            throw new EventException(exception);
        }
    }
}
