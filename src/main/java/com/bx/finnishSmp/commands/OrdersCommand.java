package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.utils.PermissionUtils;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.menus.OrdersBrowseMenu;
import com.bx.finnishSmp.menus.OrdersCollectMenu;
import com.bx.finnishSmp.menus.OrdersMyOrdersMenu;
import com.bx.finnishSmp.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OrdersCommand implements CommandExecutor {

    private final FinnishSmp plugin;

    public OrdersCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ.");
            return true;
        }

        String subcommand = args.length == 0 ? "" : args[0].toLowerCase();
        if (subcommand.equals("reload")) {
            if (!PermissionUtils.has(player, "finnishsmp.admin.orders")) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "ORDERS.NO_ADMIN_PERMISSION",
                        "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ʀᴇʟᴏᴀᴅ ᴏʀᴅᴇʀѕ ѕᴇᴛᴛɪɴɢѕ."
                )));
                return true;
            }

            plugin.getConfigManager().reloadOrders();
            plugin.getOrdersManager().reload();
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.RELOADED",
                    "&aᴏʀᴅᴇʀѕ ᴄᴏɴꜰɪɢ ʀᴇʟᴏᴀᴅᴇᴅ."
            )));
            return true;
        }

        if (!plugin.getOrdersManager().isEnabled()) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.DISABLED",
                    "&cᴏʀᴅᴇʀѕ ɪѕ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."
            )));
            return true;
        }

        if (args.length == 0) {
            if (plugin.getOrdersBedrockManager() != null
                    && plugin.getOrdersBedrockManager().openMain(player)) {
                return true;
            }
            new OrdersBrowseMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort(), "ALL").open(player);
            return true;
        }

        switch (subcommand) {
            case "my" -> {
                if (plugin.getOrdersBedrockManager() == null
                        || !plugin.getOrdersBedrockManager().openMyOrders(player)) {
                    new OrdersMyOrdersMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort()).open(player);
                }
            }
            case "collect" -> {
                if (!plugin.getOrdersManager().isClaimsEnabled()) {
                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                            "ORDERS.CLAIMS_DISABLED",
                            "&cᴏʀᴅᴇʀѕ ᴄʟᴀɪᴍѕ ᴀʀᴇ ᴄᴜʀʀᴇɴᴛʟʏ ᴅɪѕᴀʙʟᴇᴅ."
                    )));
                    return true;
                }
                if (plugin.getOrdersBedrockManager() == null
                        || !plugin.getOrdersBedrockManager().openCollect(player)) {
                    new OrdersCollectMenu(plugin, 1).open(player);
                }
            }
            default -> {
                String query = String.join(" ", args);
                if (plugin.getOrdersBedrockManager() == null
                        || !plugin.getOrdersBedrockManager().openMain(player, query)) {
                    new OrdersBrowseMenu(
                            plugin,
                            1,
                            plugin.getOrdersManager().getUiState(player.getUniqueId()).sort(),
                            plugin.getOrdersManager().getUiState(player.getUniqueId()).filter(),
                            query
                    ).open(player);
                }
            }
        }

        return true;
    }
}
