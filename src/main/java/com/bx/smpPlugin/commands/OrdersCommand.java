// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.utils.PermissionUtils;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.menus.OrdersBrowseMenu;
import com.bx.smpPlugin.menus.OrdersCollectMenu;
import com.bx.smpPlugin.menus.OrdersMyOrdersMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OrdersCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public OrdersCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        String subcommand = args.length == 0 ? "" : args[0].toLowerCase();
        if (subcommand.equals("reload")) {
            if (!PermissionUtils.has(player, "smpplugin.admin.orders")) {
                player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                        "ORDERS.NO_ADMIN_PERMISSION",
                        "&cYou do not have permission to reload orders settings."
                )));
                return true;
            }

            plugin.getConfigManager().reloadOrders();
            plugin.getOrdersManager().reload();
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.RELOADED",
                    "&aOrders config reloaded."
            )));
            return true;
        }

        if (!plugin.getOrdersManager().isEnabled()) {
            player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                    "ORDERS.DISABLED",
                    "&cOrders is currently disabled."
            )));
            plugin.getOrdersManager().playSound(player, "ORDERS.FAIL");
            return true;
        }

        if (args.length == 0) {
            if (plugin.getOrdersBedrockManager() != null
                    && plugin.getOrdersBedrockManager().openMain(player)) {
                plugin.getOrdersManager().playSound(player, "ORDERS.OPEN");
                return true;
            }
            plugin.getOrdersManager().playSound(player, "ORDERS.OPEN");
            new OrdersBrowseMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort(), "ALL").open(player);
            return true;
        }

        switch (subcommand) {
            case "my" -> {
                plugin.getOrdersManager().playSound(player, "ORDERS.OPEN");
                if (plugin.getOrdersBedrockManager() == null
                        || !plugin.getOrdersBedrockManager().openMyOrders(player)) {
                    new OrdersMyOrdersMenu(plugin, 1, plugin.getOrdersManager().getDefaultSort()).open(player);
                }
            }
            case "collect" -> {
                if (!plugin.getOrdersManager().isClaimsEnabled()) {
                    player.sendMessage(ColorUtils.toComponent(plugin.getConfigManager().getMessageOrDefault(
                            "ORDERS.CLAIMS_DISABLED",
                            "&cOrders claims are currently disabled."
                    )));
                    plugin.getOrdersManager().playSound(player, "ORDERS.FAIL");
                    return true;
                }
                plugin.getOrdersManager().playSound(player, "ORDERS.OPEN");
                if (plugin.getOrdersBedrockManager() == null
                        || !plugin.getOrdersBedrockManager().openCollect(player)) {
                    new OrdersCollectMenu(plugin, 1).open(player);
                }
            }
            default -> {
                String query = String.join(" ", args);
                plugin.getOrdersManager().playSound(player, query.isBlank() ? "ORDERS.OPEN" : "ORDERS.SEARCH");
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
