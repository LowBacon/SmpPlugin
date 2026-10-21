package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShardAdminCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.admin.shards";

    private final SmpPlugin plugin;

    public ShardAdminCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        Mode mode = Mode.fromCommand(command.getName());
        if (mode == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜɴᴋɴᴏᴡɴ ѕʜᴀʀᴅ ᴀᴅᴍɪɴ ᴄᴏᴍᴍᴀɴᴅ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /" + label + " <player> <amount>"));
            return true;
        }

        long amount;
        try {
            amount = NumberUtils.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ColorUtils.toComponent("&cᴀᴍᴏᴜɴᴛ ᴍᴜѕᴛ ʙᴇ ᴀ ᴠᴀʟɪᴅ ɴᴜᴍʙᴇʀ."));
            return true;
        }

        if (mode == Mode.SET) {
            if (amount < 0L) {
                sender.sendMessage(ColorUtils.toComponent("&cᴀᴍᴏᴜɴᴛ ᴍᴜѕᴛ ʙᴇ ᴢᴇʀᴏ ᴏʀ ɢʀᴇᴀᴛᴇʀ."));
                return true;
            }
        } else if (amount <= 0L) {
            sender.sendMessage(ColorUtils.toComponent("&cᴀᴍᴏᴜɴᴛ ᴍᴜѕᴛ ʙᴇ ɢʀᴇᴀᴛᴇʀ ᴛʜᴀɴ ᴢᴇʀᴏ."));
            return true;
        }

        EconomyManager.AccountReference account = plugin.getEconomyManager().resolveAccount(args[0]);
        if (account == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ."));
            return true;
        }

        String reasonNote = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
        EconomyManager.ShardResult result = com.bx.smpPlugin.economy.EconomyLedger.asActor(sender, reasonNote, () -> switch (mode) {
            case ADD -> plugin.getEconomyManager().depositShards(account.uuid(), account.displayName(), amount,
                    com.bx.smpPlugin.models.EconomyReason.ADMIN_ADD);
            case REMOVE -> plugin.getEconomyManager().withdrawShards(account.uuid(), account.displayName(), amount,
                    com.bx.smpPlugin.models.EconomyReason.ADMIN_REMOVE);
            case SET -> plugin.getEconomyManager().setShards(account.uuid(), account.displayName(), amount,
                    com.bx.smpPlugin.models.EconomyReason.ADMIN_SET);
        });
        if (!result.success()) {
            if (result.failure() == com.bx.smpPlugin.models.EconomyFailureReason.INSUFFICIENT_FUNDS) {
                sender.sendMessage(ColorUtils.toComponent("&c" + account.displayName()
                        + " ᴏɴʟʏ ʜᴀѕ " + formatShards(result.before()) + "."));
            } else {
                sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ᴅᴀᴛᴀ ᴄᴏᴜʟᴅ ɴᴏᴛ ʙᴇ ʟᴏᴀᴅᴇᴅ."));
            }
            return true;
        }
        long before = result.before();

        long after = result.after();
        sender.sendMessage(ColorUtils.toComponent("&a" + mode.pastTense() + " ѕʜᴀʀᴅѕ ꜰᴏʀ &f"
                + account.displayName() + "&a. &7ʙᴇꜰᴏʀᴇ: &f" + formatShards(before)
                + " &7ᴀꜰᴛᴇʀ: &f" + formatShards(after)));

        Player target = Bukkit.getPlayer(account.uuid());
        if (target != null && !target.equals(sender)) {
            target.sendMessage(ColorUtils.toComponent("&aʏᴏᴜʀ ѕʜᴀʀᴅѕ ᴡᴇʀᴇ ᴜᴘᴅᴀᴛᴇᴅ ʙʏ &f"
                    + sender.getName() + "&a. &7ʙᴀʟᴀɴᴄᴇ: &f" + formatShards(after)));
        }

        return true;
    }

    private String formatShards(long amount) {
        return plugin.getCurrencyManager().formatShards(amount);
    }

    private enum Mode {
        ADD("addshards", "Added"),
        REMOVE("removeshards", "Removed"),
        SET("setshards", "Set");

        private final String commandName;
        private final String pastTense;

        Mode(String commandName, String pastTense) {
            this.commandName = commandName;
            this.pastTense = pastTense;
        }

        static Mode fromCommand(String commandName) {
            for (Mode mode : values()) {
                if (mode.commandName.equalsIgnoreCase(commandName)) {
                    return mode;
                }
            }
            return null;
        }

        String pastTense() {
            return pastTense;
        }
    }
}
