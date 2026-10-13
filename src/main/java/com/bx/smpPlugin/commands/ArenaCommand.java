package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.DuelArena;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ArenaCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public ArenaCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.duels")) {
            sender.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴍᴀɴᴀɢᴇ ᴅᴜᴇʟ ᴀʀᴇɴᴀѕ."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.toComponent("&e/arena ᴄʀᴇᴀᴛᴇ <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ᴅᴇʟᴇᴛᴇ <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ѕᴇᴛᴘᴏѕ1 <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ѕᴇᴛᴘᴏѕ2 <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ѕᴇᴛʀᴇᴛᴜʀɴ <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ѕᴇᴛᴅɪѕᴘʟᴀʏ <id> <name>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ᴇɴᴀʙʟᴇ <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ᴅɪѕᴀʙʟᴇ <id>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ǫᴜᴇᴜᴇ <id> <true|false>"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ʟɪѕᴛ"));
            sender.sendMessage(ColorUtils.toComponent("&e/arena ʀᴇʟᴏᴀᴅ"));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("list")) {
            if (plugin.getDuelManager().getArenas().isEmpty()) {
                sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴅᴜᴇʟ ᴀʀᴇɴᴀѕ ᴄᴏɴꜰɪɢᴜʀᴇᴅ."));
                return true;
            }
            sender.sendMessage(ColorUtils.toComponent("&eᴅᴜᴇʟ ᴀʀᴇɴᴀѕ:"));
            for (DuelArena arena : plugin.getDuelManager().getArenas()) {
                sender.sendMessage(ColorUtils.toComponent(
                        "&7- &f" + arena.getId()
                                + " &8(" + arena.getDisplayName() + "&8)"
                                + " &7ʀᴇᴀᴅʏ=&f" + arena.isReady()
                                + " &7ʀᴏʟʟʙᴀᴄᴋ=&f" + arena.hasRollbackRegion()
                                + " &7ᴘᴏѕ1=&f" + (arena.getSpawn1() != null)
                                + " &7ᴘᴏѕ2=&f" + (arena.getSpawn2() != null)
                                + " &7ʀᴇᴛᴜʀɴ=&f" + (arena.getReturnLocation() != null)
                                + " &7ᴇɴᴀʙʟᴇᴅ=&f" + arena.isEnabled()
                                + " &7ǫᴜᴇᴜᴇ=&f" + arena.isQueueEnabled()
                ));
            }
            return true;
        }
        if (subcommand.equals("reload")) {
            plugin.getConfigManager().reloadDuels();
            plugin.getDuelManager().reload();
            sender.sendMessage(ColorUtils.toComponent("&aʀᴇʟᴏᴀᴅᴇᴅ ᴅᴜᴇʟ ᴀʀᴇɴᴀѕ ᴀɴᴅ ᴄᴏɴꜰɪɢ."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴍᴜѕᴛ ѕᴘᴇᴄɪꜰʏ ᴀɴ ᴀʀᴇɴᴀ ɪᴅ."));
            return true;
        }

        String id = args[1];
        if (subcommand.equals("create")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().createArena(id)
                            ? "&aᴄʀᴇᴀᴛᴇᴅ ᴅᴜᴇʟ ᴀʀᴇɴᴀ &f" + id + "&a."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴄʀᴇᴀᴛᴇ ᴛʜᴀᴛ ᴅᴜᴇʟ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("delete")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().deleteArena(id)
                            ? "&aᴅᴇʟᴇᴛᴇᴅ ᴅᴜᴇʟ ᴀʀᴇɴᴀ &f" + id + "&a."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴅᴇʟᴇᴛᴇ ᴛʜᴀᴛ ᴅᴜᴇʟ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("enable")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaEnabled(id, true)
                            ? "&aᴇɴᴀʙʟᴇᴅ ᴅᴜᴇʟ ᴀʀᴇɴᴀ &f" + id + "&a."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴇɴᴀʙʟᴇ ᴛʜᴀᴛ ᴅᴜᴇʟ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("disable")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaEnabled(id, false)
                            ? "&eᴅɪѕᴀʙʟᴇᴅ ᴅᴜᴇʟ ᴀʀᴇɴᴀ &f" + id + "&e."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴅɪѕᴀʙʟᴇ ᴛʜᴀᴛ ᴅᴜᴇʟ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("queue")) {
            if (args.length < 3) {
                sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /arena ǫᴜᴇᴜᴇ <id> <true|false>"));
                return true;
            }
            boolean enabled = Boolean.parseBoolean(args[2]);
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaQueueEnabled(id, enabled)
                            ? "&aᴜᴘᴅᴀᴛᴇᴅ ǫᴜᴇᴜᴇ ѕᴛᴀᴛᴜѕ ꜰᴏʀ &f" + id + "&a."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴜᴘᴅᴀᴛᴇ ǫᴜᴇᴜᴇ ѕᴛᴀᴛᴜѕ ꜰᴏʀ ᴛʜᴀᴛ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("setdisplay")) {
            if (args.length < 3) {
                sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /arena ѕᴇᴛᴅɪѕᴘʟᴀʏ <id> <name>"));
                return true;
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) {
                    builder.append(' ');
                }
                builder.append(args[i]);
            }
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaDisplayName(id, builder.toString())
                            ? "&aᴜᴘᴅᴀᴛᴇᴅ ᴅɪѕᴘʟᴀʏ ɴᴀᴍᴇ ꜰᴏʀ ᴀʀᴇɴᴀ &f" + id + "&a."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ᴜᴘᴅᴀᴛᴇ ᴛʜᴀᴛ ᴀʀᴇɴᴀ ᴅɪѕᴘʟᴀʏ ɴᴀᴍᴇ."
            ));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cᴛʜɪѕ ѕᴜʙᴄᴏᴍᴍᴀɴᴅ ʀᴇǫᴜɪʀᴇѕ ᴀ ᴘʟᴀʏᴇʀ ѕᴇɴᴅᴇʀ."));
            return true;
        }
        if (subcommand.equals("setpos1")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaRegionPos(id, 1, player.getLocation())
                            ? "&aѕᴇᴛ ᴘᴏѕ 1 ꜰᴏʀ ᴀʀᴇɴᴀ &f" + id + "&a. ᴛʜɪѕ ɴᴏᴡ ᴀᴄᴛѕ ᴀѕ ѕᴘᴀᴡɴ 1 ᴀɴᴅ ʀᴏʟʟʙᴀᴄᴋ ᴀɴᴄʜᴏʀ."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ѕᴇᴛ ᴘᴏѕ 1 ꜰᴏʀ ᴛʜᴀᴛ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("setpos2")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaRegionPos(id, 2, player.getLocation())
                            ? "&aѕᴇᴛ ᴘᴏѕ 2 ꜰᴏʀ ᴀʀᴇɴᴀ &f" + id + "&a. ᴛʜɪѕ ɴᴏᴡ ᴀᴄᴛѕ ᴀѕ ѕᴘᴀᴡɴ 2 ᴀɴᴅ ʀᴏʟʟʙᴀᴄᴋ ᴀɴᴄʜᴏʀ."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ѕᴇᴛ ᴘᴏѕ 2 ꜰᴏʀ ᴛʜᴀᴛ ᴀʀᴇɴᴀ."
            ));
            return true;
        }
        if (subcommand.equals("setreturn")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getDuelManager().setArenaReturn(id, player.getLocation())
                            ? "&aѕᴇᴛ ʀᴇᴛᴜʀɴ ʟᴏᴄᴀᴛɪᴏɴ ꜰᴏʀ ᴀʀᴇɴᴀ &f" + id + "&a."
                            : "&cᴄᴏᴜʟᴅ ɴᴏᴛ ѕᴇᴛ ʀᴇᴛᴜʀɴ ʟᴏᴄᴀᴛɪᴏɴ ꜰᴏʀ ᴛʜᴀᴛ ᴀʀᴇɴᴀ."
            ));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent("&cᴜɴᴋɴᴏᴡɴ ᴀʀᴇɴᴀ ѕᴜʙᴄᴏᴍᴍᴀɴᴅ."));
        return true;
    }
}
