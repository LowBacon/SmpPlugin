package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.LocationUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public class AFKLoungeCommand implements CommandExecutor, TabCompleter {

    private final SmpPlugin plugin;

    public AFKLoungeCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getAFKLoungeManager().isEnabled()) {
            sender.sendMessage(ColorUtils.toComponent("&cᴀꜰᴋ ʟᴏᴜɴɢᴇ ɪѕ ᴅɪѕᴀʙʟᴇᴅ."));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "setlocation":
                return setLocation(sender);
            case "border":
                return border(sender, args);
            case "info":
                return info(sender);
            case "help":
                showHelp(sender);
                return true;
            default:
                sender.sendMessage(ColorUtils.toComponent("&cᴜɴᴋɴᴏᴡɴ ѕᴜʙᴄᴏᴍᴍᴀɴᴅ. ᴛʏᴘᴇ /ᴀꜰᴋʟᴏᴜɴɢᴇ ʜᴇʟᴘ"));
                return true;
        }
    }

    private boolean setLocation(CommandSender sender) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.afklounge")) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ᴏɴʟʏ."));
            return true;
        }

        plugin.getAFKLoungeManager().setAFKLoungeLocation(player.getLocation());
        sender.sendMessage(ColorUtils.toComponent("&aᴀꜰᴋ ʟᴏᴜɴɢᴇ ʟᴏᴄᴀᴛɪᴏɴ ѕᴇᴛ ᴀᴛ: "
                + player.getLocation().getBlockX() + ", "
                + player.getLocation().getBlockY() + ", "
                + player.getLocation().getBlockZ()));
        return true;
    }

    private boolean border(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.afklounge")) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (plugin.getAFKLoungeManager().getAFKLoungeLocation() == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴀꜰᴋ ʟᴏᴜɴɢᴇ ʟᴏᴄᴀᴛɪᴏɴ ɴᴏᴛ ѕᴇᴛ. ᴜѕᴇ /ᴀꜰᴋʟᴏᴜɴɢᴇ ѕᴇᴛʟᴏᴄᴀᴛɪᴏɴ"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /ᴀꜰᴋʟᴏᴜɴɢᴇ ʙᴏʀᴅᴇʀ <ʀᴀᴅɪᴜѕ|ᴘᴏѕ1|ᴘᴏѕ2>"));
            return true;
        }

        String borderSubcommand = args[1].toLowerCase();

        if ("pos1".equalsIgnoreCase(borderSubcommand)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ᴏɴʟʏ."));
                return true;
            }
            plugin.getAFKLoungeManager().setPendingBorderCorner1(player.getUniqueId(), player.getLocation());
            sender.sendMessage(ColorUtils.toComponent("&aꜰɪʀѕᴛ ʙᴏʀᴅᴇʀ ᴄᴏʀɴᴇʀ ѕᴇᴛ. ɴᴏᴡ ᴍᴏᴠᴇ ᴛᴏ ᴛʜᴇ ᴏᴘᴘᴏѕɪᴛᴇ ᴄᴏʀɴᴇʀ ᴀɴᴅ ᴜѕᴇ /ᴀꜰᴋʟᴏᴜɴɢᴇ ʙᴏʀᴅᴇʀ ᴘᴏѕ2"));
            return true;
        } else if ("pos2".equalsIgnoreCase(borderSubcommand)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ᴏɴʟʏ."));
                return true;
            }
            Location corner1 = plugin.getAFKLoungeManager().getPendingBorderCorner1(player.getUniqueId());
            if (corner1 == null) {
                sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴇᴀѕᴇ ѕᴇᴛ ᴘᴏѕ1 ꜰɪʀѕᴛ."));
                return true;
            }

            Location corner2 = player.getLocation();
            plugin.getAFKLoungeManager().setBorderCorners(
                    corner1.getX(), corner1.getZ(),
                    corner2.getX(), corner2.getZ()
            );
            plugin.getAFKLoungeManager().clearPendingBorderCorner1(player.getUniqueId());
            sender.sendMessage(ColorUtils.toComponent("&aʙᴏʀᴅᴇʀ ѕᴇᴛ!"));
            return true;
        } else {
            // Try to parse as radius
            try {
                double radius = Double.parseDouble(borderSubcommand);
                plugin.getAFKLoungeManager().setBorderRadius(radius);
                sender.sendMessage(ColorUtils.toComponent("&aʙᴏʀᴅᴇʀ ѕᴇᴛ ᴡɪᴛʜ ʀᴀᴅɪᴜѕ: " + radius));
                return true;
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.toComponent("&cɪɴᴠᴀʟɪᴅ ʀᴀᴅɪᴜѕ."));
                return true;
            }
        }
    }

    private boolean info(CommandSender sender) {
        if (plugin.getAFKLoungeManager().getAFKLoungeLocation() == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴀꜰᴋ ʟᴏᴜɴɢᴇ ɴᴏᴛ ѕᴇᴛ ᴜᴘ ʏᴇᴛ."));
            return true;
        }

        Location loc = plugin.getAFKLoungeManager().getAFKLoungeLocation();
        sender.sendMessage(ColorUtils.toComponent("&6ᴀꜰᴋ ʟᴏᴜɴɢᴇ ɪɴꜰᴏ:"));
        sender.sendMessage(ColorUtils.toComponent("&7ʟᴏᴄᴀᴛɪᴏɴ: &ᶠ"
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        sender.sendMessage(ColorUtils.toComponent("&7ʙᴏʀᴅᴇʀ ᴇɴᴀʙʟᴇᴅ: &ᶠ"
                + (plugin.getAFKLoungeManager().isBorderEnabled() ? "&aʏᴇѕ" : "&cɴᴏ")));
        sender.sendMessage(ColorUtils.toComponent("&7ѕʜᴀʀᴅ ʀᴇᴡᴀʀᴅ: &ᶠ"
                + plugin.getAFKLoungeManager().getShardRewardAmount()
                + " ᴇᴠᴇʀʏ " + plugin.getAFKLoungeManager().getShardRewardIntervalSeconds() + "ѕ"));
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.toComponent("&6&lᴀꜰᴋ ʟᴏᴜɴɢᴇ ᴄᴏᴍᴍᴀɴᴅѕ"));
        sender.sendMessage(ColorUtils.toComponent("&e/ᴀꜰᴋʟᴏᴜɴɢᴇ ѕᴇᴛʟᴏᴄᴀᴛɪᴏɴ &7- ѕᴇᴛ ᴀꜰᴋ ʟᴏᴜɴɢᴇ ʟᴏᴄᴀᴛɪᴏɴ"));
        sender.sendMessage(ColorUtils.toComponent("&e/ᴀꜰᴋʟᴏᴜɴɢᴇ ʙᴏʀᴅᴇʀ <ʀᴀᴅɪᴜѕ> &7- ѕᴇᴛ ʙᴏʀᴅᴇʀ ʙʏ ʀᴀᴅɪᴜѕ"));
        sender.sendMessage(ColorUtils.toComponent("&e/ᴀꜰᴋʟᴏᴜɴɢᴇ ʙᴏʀᴅᴇʀ ᴘᴏѕ1 &7- ѕᴇᴛ ꜰɪʀѕᴛ ʙᴏʀᴅᴇʀ ᴄᴏʀɴᴇʀ"));
        sender.sendMessage(ColorUtils.toComponent("&e/ᴀꜰᴋʟᴏᴜɴɢᴇ ʙᴏʀᴅᴇʀ ᴘᴏѕ2 &7- ѕᴇᴛ ѕᴇᴄᴏɴᴅ ʙᴏʀᴅᴇʀ ᴄᴏʀɴᴇʀ"));
        sender.sendMessage(ColorUtils.toComponent("&e/ᴀꜰᴋʟᴏᴜɴɢᴇ ɪɴꜰᴏ &7- ѕʜᴏᴡ ᴀꜰᴋ ʟᴏᴜɴɢᴇ ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ"));
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("setlocation");
            completions.add("border");
            completions.add("info");
            completions.add("help");
        } else if (args.length == 2 && "border".equalsIgnoreCase(args[0])) {
            completions.add("radius");
            completions.add("pos1");
            completions.add("pos2");
        }
        return completions;
    }
}
