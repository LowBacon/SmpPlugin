package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.DuelQueueMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QueueCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public QueueCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        if (!plugin.getDuelManager().isEnabled()) {
            player.sendMessage(com.bx.smpPlugin.utils.ColorUtils.toComponent("&cᴅᴜᴇʟѕ ᴀʀᴇ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (args.length == 0) {
            new DuelQueueMenu(plugin).open(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "join" -> plugin.getDuelManager().joinQueue(
                    player,
                    args.length > 1 ? plugin.getDuelManager().parseMapSelection(args[1]) : null
            );
            case "leave" -> plugin.getDuelManager().leaveState(player);
            default -> new DuelQueueMenu(plugin).open(player);
        }
        return true;
    }
}
