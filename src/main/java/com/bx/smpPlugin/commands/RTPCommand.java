// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.RTPMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RTPCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public RTPCommand(SmpPlugin plugin) {
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

        if (!plugin.getRtpManager().isEnabled()) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getRtp().getString("MESSAGES.DISABLED", "&cRTP is disabled.")));
            return true;
        }

        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player.getUniqueId())) {
            player.sendMessage(ColorUtils.toComponent(plugin.getCombatManager().getBlockMessage()));
            return true;
        }

        if (args.length == 0) {
            // The world picker; RTP-MENU.OPEN-ON-COMMAND false keeps the old "RTP in this world".
            if (plugin.getConfigManager().getRtp().getBoolean("RTP-MENU.OPEN-ON-COMMAND", true)
                    && plugin.getRtpManager().hasMenuDestinations()) {
                if (plugin.getRtpManager().isDeniedWorld(player.getWorld().getName())) {
                    player.sendMessage(ColorUtils.toComponent("&cYou cannot RTP in this world."));
                    return true;
                }
                new RTPMenu(plugin).open(player);
                return true;
            }
            plugin.getRtpManager().queueCommandTeleport(player, player.getWorld().getName());
            return true;
        }

        plugin.getRtpManager().queueCommandTeleport(player, args[0]);
        return true;
    }
}
