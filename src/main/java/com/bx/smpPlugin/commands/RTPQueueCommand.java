// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class RTPQueueCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("join", "leave");

    private final SmpPlugin plugin;

    public RTPQueueCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        if (!plugin.getConfigManager().isCommandEnabled("RTP")) {
            player.sendMessage(ColorUtils.toComponent("&cRTP command is currently disabled."));
            return true;
        }

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            return true;
        }

        String subcommand = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "leave", "quit" -> plugin.getRtpQueueManager().leave(player);
            default -> {
                // The dialog asks for confirmation first; joining outright stays the fallback.
                if (args.length > 0 || plugin.getDialogManager() == null
                        || !plugin.getDialogManager().openRtpQueue(player)) {
                    plugin.getRtpQueueManager().join(player);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
