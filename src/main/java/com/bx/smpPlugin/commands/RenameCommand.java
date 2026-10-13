package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RenameCommand implements CommandExecutor {

    private static final String PERMISSION = "smpplugin.staff.rename";

    private final SmpPlugin plugin;

    public RenameCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cᴏɴʟʏ ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ᴜѕᴇ ᴛʜɪѕ ᴄᴏᴍᴍᴀɴᴅ."));
            return true;
        }

        if (!PermissionUtils.has(player, PERMISSION)) {
            player.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: /" + label + " <name...|reset>"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault("RENAME.NO_ITEM", "&cʏᴏᴜ ᴍᴜѕᴛ ʜᴏʟᴅ ᴀɴ ɪᴛᴇᴍ ᴛᴏ ʀᴇɴᴀᴍᴇ ɪᴛ")
            ));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault("RENAME.META_ERROR", "&cᴛʜɪѕ ɪᴛᴇᴍ ᴄᴀɴɴᴏᴛ ʙᴇ ʀᴇɴᴀᴍᴇᴅ")
            ));
            return true;
        }

        if (isStaffModeItemBlocked(player, item)) {
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault(
                            "RENAME.STAFFMODE_BLOCKED",
                            "&cʏᴏᴜ ᴄᴀɴɴᴏᴛ ʀᴇɴᴀᴍᴇ ѕᴛᴀꜰꜰ ᴍᴏᴅᴇ ɪᴛᴇᴍѕ."
                    ),
                    player
            ));
            return true;
        }

        String newName = String.join(" ", args);
        if (isResetRequest(args)) {
            meta.setDisplayName(null);
            item.setItemMeta(meta);
            player.sendMessage(ColorUtils.toComponent(
                    plugin.getConfigManager().getMessageOrDefault(
                            "RENAME.RESET_SUCCESS",
                            "&7ɪᴛᴇᴍ ɴᴀᴍᴇ ʜᴀѕ ʙᴇᴇɴ ʀᴇѕᴇᴛ."
                    ),
                    player
            ));
            return true;
        }

        meta.setDisplayName(ColorUtils.toComponent(newName, player));
        item.setItemMeta(meta);
        player.sendMessage(ColorUtils.toComponent(
                plugin.getConfigManager().getMessageOrDefault("RENAME.SUCCESS", "&7ɴᴇᴡ ɴᴀᴍᴇ: &f%name%", "%name%", newName),
                player
        ));
        return true;
    }

    private boolean isResetRequest(String[] args) {
        if (args.length != 1) {
            return false;
        }

        String value = args[0];
        return value.equalsIgnoreCase("reset")
                || value.equalsIgnoreCase("clear")
                || value.equalsIgnoreCase("remove");
    }

    private boolean isStaffModeItemBlocked(Player player, ItemStack item) {
        if (plugin.getStaffModeManager() == null) {
            return false;
        }

        return plugin.getStaffModeManager().isInStaffMode(player.getUniqueId())
                || plugin.getStaffModeManager().isStaffTool(item);
    }
}
