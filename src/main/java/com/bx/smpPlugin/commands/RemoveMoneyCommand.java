package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

/** /removemoney: kept for existing staff habits; the same change as /eco take. */
public class RemoveMoneyCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.admin.removemoney";

    private final EconomyAdmin admin;

    public RemoveMoneyCommand(SmpPlugin plugin) {
        this.admin = new EconomyAdmin(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /removemoney <player> <amount> [note]"));
            return true;
        }
        String note = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        admin.run(sender, EconomyAdmin.Action.TAKE, args[0], args[1], note);
        return true;
    }
}
