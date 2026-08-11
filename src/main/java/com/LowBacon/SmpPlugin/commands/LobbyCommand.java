package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles both /lobby [survival] and /smpplugin &lt;lobby|survival&gt; (registered as the same
 * executor for both command names, matching plugin.yml's documented usage for each), plus admin
 * subcommands under /lobby only: border [radius], reload, debug, help.
 */
public final class LobbyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> LOBBY_SUBCOMMANDS = List.of("survival", "border", "reload", "debug", "help");
    private static final List<String> SMPPLUGIN_SUBCOMMANDS = List.of("lobby", "survival");

    private final FinnishSmp plugin;

    public LobbyCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isSmppluginAlias = "smpplugin".equalsIgnoreCase(label);

        if (isSmppluginAlias) {
            if (args.length == 0) {
                sender.sendMessage(ColorUtils.toComponent("&cUsage: /smpplugin <lobby|survival>"));
                return true;
            }
            String choice = args[0].toLowerCase(Locale.ROOT);
            if (choice.equals("lobby")) {
                return goToLobby(sender);
            } else if (choice.equals("survival")) {
                return goToSurvival(sender);
            }
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /smpplugin <lobby|survival>"));
            return true;
        }

        // /lobby [survival|border|reload|debug|help]
        if (args.length == 0) {
            return goToLobby(sender);
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "survival" -> goToSurvival(sender);
            case "border" -> border(sender, args);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            case "help" -> help(sender);
            default -> {
                sender.sendMessage(ColorUtils.toComponent("&cUnknown subcommand. Use /lobby help for a list of commands."));
                yield true;
            }
        };
    }

    private boolean goToLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cOnly a player can use this command."));
            return true;
        }
        if (!PermissionUtils.has(player, "finnishsmp.lobby")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }

        long remainingCooldown = plugin.getLobbyManager().checkAndStartCommandCooldown(player);
        if (remainingCooldown > 0) {
            sender.sendMessage(ColorUtils.toComponent("&cPlease wait " + remainingCooldown + " more second(s)."));
            return true;
        }

        if (plugin.getLobbyManager().sendToLobby(player, false)) {
            sender.sendMessage(ColorUtils.toComponent("&aTeleported to the lobby.", player));
        } else {
            sender.sendMessage(ColorUtils.toComponent("&cThe lobby has not been set up yet. Ask an admin to run /setlobby."));
        }
        return true;
    }

    private boolean goToSurvival(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cOnly a player can use this command."));
            return true;
        }
        if (!PermissionUtils.has(player, "finnishsmp.lobby")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }
        if (plugin.getLobbyManager().isBlockedByCombat(player)) {
            sender.sendMessage(ColorUtils.toComponent(plugin.getLobbyManager().getCombatBlockedMessage(), player));
            return true;
        }

        long remainingCooldown = plugin.getLobbyManager().checkAndStartCommandCooldown(player);
        if (remainingCooldown > 0) {
            sender.sendMessage(ColorUtils.toComponent("&cPlease wait " + remainingCooldown + " more second(s)."));
            return true;
        }

        plugin.getLobbyManager().sendToSurvival(player);
        return true;
    }

    private boolean border(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, "finnishsmp.admin.lobby.border")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }

        if (args.length >= 2 && ("pos1".equalsIgnoreCase(args[1]) || "pos2".equalsIgnoreCase(args[1]))) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.toComponent("&cOnly a player can set a border corner."));
                return true;
            }
            if ("pos1".equalsIgnoreCase(args[1])) {
                plugin.getLobbyManager().setBorderCorner1(player, player.getLocation());
                sender.sendMessage(ColorUtils.toComponent(
                        "&aFirst border corner set at your position. Now go to the opposite corner and run &f/lobby border pos2&a."));
            } else {
                boolean success = plugin.getLobbyManager().setBorderCorner2(player, player.getLocation());
                if (success) {
                    sender.sendMessage(ColorUtils.toComponent("&aBorder set to &f"
                            + plugin.getLobbyManager().describeBorderSize() + " &ablocks."));
                } else {
                    sender.sendMessage(ColorUtils.toComponent(
                            "&cSet the first corner first with &f/lobby border pos1&c."));
                }
            }
            return true;
        }

        if (args.length >= 2) {
            double radius;
            try {
                radius = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ColorUtils.toComponent(
                        "&cUsage: /lobby border <radius> | pos1 | pos2 - radius must be a number of at least 1."));
                return true;
            }
            if (radius < 1.0) {
                sender.sendMessage(ColorUtils.toComponent(
                        "&cUsage: /lobby border <radius> | pos1 | pos2 - radius must be a number of at least 1."));
                return true;
            }
            plugin.getLobbyManager().setBorderRadius(radius);
            sender.sendMessage(ColorUtils.toComponent("&aLobby border set to &f"
                    + plugin.getLobbyManager().describeBorderSize() + " &ablocks."));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent("&7Border: &f"
                + (plugin.getLobbyManager().isBorderEnabled() ? "enabled" : "disabled")
                + " &7size &f" + plugin.getLobbyManager().describeBorderSize() + " &7blocks"));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!PermissionUtils.has(sender, "finnishsmp.admin.lobby.reload")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }
        plugin.getConfigManager().reload();
        plugin.getLobbyManager().reload();
        sender.sendMessage(ColorUtils.toComponent("&aLobby configuration reloaded."));
        return true;
    }

    private boolean debug(CommandSender sender) {
        if (!PermissionUtils.has(sender, "finnishsmp.admin.lobby.debug")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }
        var lobby = plugin.getConfigManager().getLobby();
        boolean newValue = !lobby.getBoolean("DEBUG", false);
        lobby.set("DEBUG", newValue);
        plugin.getConfigManager().saveLobby();

        sender.sendMessage(ColorUtils.toComponent("&eLobby debug mode " + (newValue ? "enabled" : "disabled") + "."));
        if (newValue) {
            sender.sendMessage("Lobby location set: " + plugin.getLobbyManager().hasLobbyLocation());
            sender.sendMessage("Border: enabled=" + plugin.getLobbyManager().isBorderEnabled()
                    + " size=" + plugin.getLobbyManager().describeBorderSize());
        }
        return true;
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(ColorUtils.toComponent("&6&lLOBBY COMMANDS"));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby &7- Teleport to the lobby."));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby survival &7- Enter the SMP."));
        sender.sendMessage(ColorUtils.toComponent("&e/setlobby &7- Set the lobby location."));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby border [radius] &7- View, or set a square border by radius."));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby border pos1 &7- Set the first corner of an exact border at your position."));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby border pos2 &7- Set the opposite corner, completing the border."));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby reload &7- Reload lobby.yml."));
        sender.sendMessage(ColorUtils.toComponent("&e/lobby debug &7- Toggle debug output."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            if (args.length == 2 && "border".equalsIgnoreCase(args[0]) && !"smpplugin".equalsIgnoreCase(alias)) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return Stream.of("50", "100", "150", "200", "300", "500", "pos1", "pos2")
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }
        List<String> options = "smpplugin".equalsIgnoreCase(alias) ? SMPPLUGIN_SUBCOMMANDS : LOBBY_SUBCOMMANDS;
        String partial = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
    }
}
