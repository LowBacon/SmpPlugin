package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.StaffModeManager;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffModeCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public StaffModeCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StaffModeManager manager = plugin.getStaffModeManager();

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!manager.canAdmin(sender)) {
                sender.sendMessage(ColorUtils.toComponent(
                        manager.getMessage("NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ.")
                ));
                return true;
            }

            plugin.getConfigManager().reload();
            manager.reload();
            if (plugin.getFakePlayerManager() != null) {
                plugin.getFakePlayerManager().reload();
            }
            sender.sendMessage(ColorUtils.toComponent(
                    manager.getMessage("RELOAD-SUCCESS", "&aѕᴛᴀꜰꜰ ᴍᴏᴅᴇ ᴄᴏɴꜰɪɢ ʀᴇʟᴏᴀᴅᴇᴅ.")
            ));
            return true;
        }

        if (args.length > 0) {
            if (sender instanceof Player player && args[0].equalsIgnoreCase(player.getName())) {
                return toggleSelf(player, manager);
            }

            if (!manager.canManageOthers(sender)) {
                sender.sendMessage(ColorUtils.toComponent(
                        manager.getStaffMessage(
                                "NO_PERMISSION_OTHERS",
                                "&c&lᴇʀʀᴏʀ &7>> &cʏᴏᴜ ᴅᴏɴ'ᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴍᴀɴᴀɢᴇ ᴏᴛʜᴇʀ ᴘʟᴀʏᴇʀѕ' ѕᴛᴀꜰꜰ ᴍᴏᴅᴇ!"
                        )
                ));
                return true;
            }

            Player target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ᴏɴʟɪɴᴇ."));
                return true;
            }

            if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
                return toggleSelf(player, manager);
            }

            StaffModeManager.StaffModeToggleResult result = manager.isInStaffMode(target.getUniqueId())
                    ? manager.disable(target, false)
                    : manager.enable(target, false);
            if (!result.success()) {
                sender.sendMessage(ColorUtils.toComponent(
                        manager.getStaffMessage("TOGGLE_ERROR", "&c&lᴇʀʀᴏʀ &7>> &cꜰᴀɪʟᴇᴅ ᴛᴏ ᴛᴏɢɢʟᴇ ѕᴛᴀꜰꜰ ᴍᴏᴅᴇ!")
                ));
                return true;
            }

            manager.notifyExternalToggle(sender, target, result.active());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent(
                    manager.getMessage("PLAYER-ONLY", "&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ.")
            ));
            return true;
        }

        return toggleSelf(player, manager);
    }

    private boolean toggleSelf(Player player, StaffModeManager manager) {
        if (!manager.isInStaffMode(player.getUniqueId()) && !manager.isEnabled()) {
            player.sendMessage(ColorUtils.toComponent(
                    manager.getMessage("FEATURE-DISABLED", "&cѕᴛᴀꜰꜰ ᴍᴏᴅᴇ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ.")
            ));
            return true;
        }

        if (!manager.canUse(player)) {
            player.sendMessage(ColorUtils.toComponent(
                    manager.getMessage("NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ.")
            ));
            return true;
        }

        StaffModeManager.StaffModeToggleResult result = manager.toggle(player);
        if (!result.message().isBlank() && !result.success()) {
            player.sendMessage(ColorUtils.toComponent(result.message(), player));
        }
        return true;
    }
}
