// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.FarmingMetaManager;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MetaCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public MetaCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FarmingMetaManager farmingMetaManager = plugin.getFarmingMetaManager();

        if (!farmingMetaManager.isActive()) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessage("WORTH.META-INACTIVE")));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent(
                farmingMetaManager.formatMetaMessage("WORTH.META-CURRENT")));
        return true;
    }
}
