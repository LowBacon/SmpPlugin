package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
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
 * subcommands under /lobby only: delete, border (passed on to /border lobby), reload, debug, help.
 * /dellobby is an alias for /lobby delete.
 */
public final class LobbyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> LOBBY_SUBCOMMANDS = List.of("survival", "help");
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("delete", "border", "reload", "debug");
    private static final List<String> SMPPLUGIN_SUBCOMMANDS = List.of("lobby", "survival");

    private final SmpPlugin plugin;

    public LobbyCommand(SmpPlugin plugin) {
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

        if ("dellobby".equalsIgnoreCase(label)) {
            return delete(sender, args, 0);
        }

        // /lobby [survival|delete|border|reload|debug|help]
        if (args.length == 0) {
            return goToLobby(sender);
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "survival" -> goToSurvival(sender);
            case "border" -> border(sender, args);
            case "delete", "remove", "unset" -> delete(sender, args, 1);
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
        if (!PermissionUtils.has(player, "smpplugin.lobby")) {
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
        if (!PermissionUtils.has(player, "smpplugin.lobby")) {
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

    /**
     * The lobby border is set with /border lobby since 3.1 (a real Minecraft border that is also the
     * lobby area). /lobby border passes straight on to it, so old habits and docs keep working:
     * {@code /lobby border 50} (a radius) becomes {@code /border lobby set 100}.
     */
    private boolean border(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.border")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }
        String forwarded;
        if (args.length == 1) {
            forwarded = "border lobby";
        } else if (args[1].equalsIgnoreCase("pos1") || args[1].equalsIgnoreCase("pos2")) {
            sender.sendMessage(ColorUtils.toComponent("&7Corner-to-corner lobby borders were replaced by a square: stand in the"
                    + " middle and use &f/border lobby set <size> here&7."));
            return true;
        } else if (args[1].matches("\\d+(\\.\\d+)?")) {
            double radius = Double.parseDouble(args[1]);
            forwarded = "border lobby set " + (long) Math.max(4D, Math.round(radius * 2D));
        } else {
            forwarded = "border lobby " + String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        sender.sendMessage(ColorUtils.toComponent("&8(The lobby border is now set with &7/" + forwarded + "&8.)"));
        org.bukkit.Bukkit.dispatchCommand(sender, forwarded);
        return true;
    }

    /** /lobby delete [confirm] (also /dellobby): removes the lobby after a confirmation. */
    private boolean delete(CommandSender sender, String[] args, int confirmIndex) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.lobby.setlobby")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }
        if (!plugin.getLobbyManager().hasLobbyLocation()) {
            sender.sendMessage(ColorUtils.toComponent("&7There is no lobby to delete. Set one with &f/setlobby&7."));
            return true;
        }
        boolean confirmed = args.length > confirmIndex && args[confirmIndex].equalsIgnoreCase("confirm");
        if (!confirmed) {
            sender.sendMessage(ColorUtils.toComponent("&eDelete the lobby? &7Players will no longer be sent there when they join,"
                    + " anyone in it now goes back to the SMP, and its border is taken down."));
            com.bx.smpPlugin.core.Text.line(sender, "  ",
                    com.bx.smpPlugin.core.Text.button("&c&l[Yes, delete it]", "lobby delete confirm", "&7Delete the lobby"));
            return true;
        }
        if (plugin.getV3Module() != null) {
            plugin.getV3Module().turnOffBorder("lobby");
        }
        if (plugin.getLobbyManager().deleteLobby()) {
            sender.sendMessage(ColorUtils.toComponent("&aThe lobby was deleted. &7Set a new one any time with &f/setlobby&7."));
        } else {
            sender.sendMessage(ColorUtils.toComponent("&cThe lobby was removed for now but lobby.yml could not be saved; see the console."));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.lobby.reload")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to do that."));
            return true;
        }
        plugin.getConfigManager().reload();
        plugin.getLobbyManager().reload();
        sender.sendMessage(ColorUtils.toComponent("&aLobby configuration reloaded."));
        return true;
    }

    private boolean debug(CommandSender sender) {
        if (!PermissionUtils.has(sender, "smpplugin.admin.lobby.debug")) {
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
        sender.sendMessage(ColorUtils.toComponent("&e/lobby survival &7- Leave the lobby and enter the SMP."));
        boolean admin = PermissionUtils.has(sender, "smpplugin.admin.lobby.setlobby");
        if (admin) {
            sender.sendMessage(ColorUtils.toComponent("&e/setlobby &7- Set the lobby where you stand."));
            sender.sendMessage(ColorUtils.toComponent("&e/lobby delete &7- Delete the lobby (also &e/dellobby&7)."));
        }
        if (PermissionUtils.has(sender, "smpplugin.admin.border")) {
            sender.sendMessage(ColorUtils.toComponent("&e/border lobby set <size> [here] &7- The lobby area, shown as a real border."));
            sender.sendMessage(ColorUtils.toComponent("&e/border lobby on|off &7- Show or remove that border."));
        }
        if (PermissionUtils.has(sender, "smpplugin.admin.lobby.reload")) {
            sender.sendMessage(ColorUtils.toComponent("&e/lobby reload &7- Reload lobby.yml."));
        }
        if (PermissionUtils.has(sender, "smpplugin.admin.lobby.debug")) {
            sender.sendMessage(ColorUtils.toComponent("&e/lobby debug &7- Toggle debug output."));
        }
        sender.sendMessage(ColorUtils.toComponent("&8Players can't walk out of the lobby; they leave with /lobby survival or any teleport."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("dellobby".equalsIgnoreCase(alias)) {
            return args.length == 1 && PermissionUtils.has(sender, "smpplugin.admin.lobby.setlobby")
                    ? Stream.of("confirm").filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList())
                    : List.of();
        }
        if (args.length != 1) {
            if (args.length == 2 && "border".equalsIgnoreCase(args[0]) && !"smpplugin".equalsIgnoreCase(alias)
                    && PermissionUtils.has(sender, "smpplugin.admin.border")) {
                String partial = args[1].toLowerCase(Locale.ROOT);
                return Stream.of("set", "center", "on", "off", "mode", "warning", "info")
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }
            return List.of();
        }
        List<String> options = new java.util.ArrayList<>("smpplugin".equalsIgnoreCase(alias) ? SMPPLUGIN_SUBCOMMANDS : LOBBY_SUBCOMMANDS);
        if (!"smpplugin".equalsIgnoreCase(alias)) {
            for (String admin : ADMIN_SUBCOMMANDS) {
                String permission = switch (admin) {
                    case "border" -> "smpplugin.admin.border";
                    case "reload" -> "smpplugin.admin.lobby.reload";
                    case "debug" -> "smpplugin.admin.lobby.debug";
                    default -> "smpplugin.admin.lobby.setlobby";
                };
                if (PermissionUtils.has(sender, permission)) {
                    options.add(admin);
                }
            }
        }
        String partial = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
    }
}
