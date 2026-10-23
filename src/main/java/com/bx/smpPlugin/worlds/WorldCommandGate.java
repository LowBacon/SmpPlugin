// SmpPlugin by LowBacon
package com.bx.smpPlugin.worlds;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class WorldCommandGate implements Listener {

    private final SmpPlugin plugin;
    private final WorldSystem worlds;
    private CommandMap commandMap;
    private boolean commandMapLooked;

    public WorldCommandGate(SmpPlugin plugin, WorldSystem worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        WorldRules rules = worlds.rulesFor(player.getWorld());
        if (rules == null || !rules.commandAllowlist() || worlds.bypassesCommands(player)) {
            return;
        }
        String message = event.getMessage();
        String label = message.startsWith("/") ? message.substring(1) : message;
        int space = label.indexOf(' ');
        if (space >= 0) {
            label = label.substring(0, space);
        }
        if (!isAllowed(rules.allowedCommands(), label)) {
            event.setCancelled(true);
            String denied = plugin.getConfigManager().getWorlds().getString("MESSAGES.COMMAND-BLOCKED",
                    "&cThat command can't be used in the {world}.");
            player.sendMessage(ColorUtils.toComponent(denied.replace("{world}", rules.key()), player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandList(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        WorldRules rules = worlds.rulesFor(player.getWorld());
        if (rules == null || !rules.commandAllowlist() || worlds.bypassesCommands(player)) {
            return;
        }
        event.getCommands().removeIf(label -> !isAllowed(rules.allowedCommands(), label));
    }

    boolean isAllowed(Set<String> allowed, String typedLabel) {
        String bare = WorldRules.normaliseCommand(typedLabel);
        if (bare.isEmpty()) {
            return false;
        }
        CommandMap map = commandMap();
        Command command = map == null ? null : map.getCommand(typedLabel.toLowerCase(Locale.ROOT));
        if (map != null && command == null) {
            return false;
        }
        return isAllowed(allowed, bare, command == null ? null : command.getName());
    }

    static boolean isAllowed(Set<String> allowed, String bareLabel, String commandName) {
        if (allowed.contains(bareLabel)) {
            return true;
        }
        return commandName != null && allowed.contains(WorldRules.normaliseCommand(commandName));
    }

    private CommandMap commandMap() {
        if (!commandMapLooked) {
            commandMapLooked = true;
            try {
                // Paper and Spigot both have it; the Spigot API does not declare it, so it is looked up once.
                commandMap = (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read the server's command list; the lobby command"
                        + " allowlist matches typed names only.", exception);
            }
        }
        return commandMap;
    }
}
