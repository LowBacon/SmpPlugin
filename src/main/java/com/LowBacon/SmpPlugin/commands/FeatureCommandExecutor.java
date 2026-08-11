package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.managers.FeatureManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class FeatureCommandExecutor implements CommandExecutor, TabCompleter {

    private final FinnishSmp plugin;
    private final CommandExecutor delegate;
    private final FeatureManager.Feature[] requiredFeatures;

    public FeatureCommandExecutor(
            FinnishSmp plugin,
            CommandExecutor delegate,
            FeatureManager.Feature... requiredFeatures
    ) {
        this.plugin = plugin;
        this.delegate = delegate;
        this.requiredFeatures = requiredFeatures == null ? new FeatureManager.Feature[0] : requiredFeatures;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        for (FeatureManager.Feature feature : requiredFeatures) {
            if (feature != null && !plugin.getFeatureManager().isEnabled(feature)) {
                plugin.getFeatureManager().sendDisabledMessage(sender, feature, label);
                return true;
            }
        }
        return delegate.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        for (FeatureManager.Feature feature : requiredFeatures) {
            if (feature != null && !plugin.getFeatureManager().isEnabled(feature)) {
                return Collections.emptyList();
            }
        }
        if (delegate instanceof TabCompleter tabCompleter) {
            return tabCompleter.onTabComplete(sender, command, alias, args);
        }
        return Collections.emptyList();
    }
}
