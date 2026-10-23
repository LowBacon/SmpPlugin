// SmpPlugin by LowBacon
package com.bx.smpPlugin.display;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SidebarCommand implements CommandExecutor, TabCompleter {

    public static final String TOGGLE_PERMISSION = "smpplugin.sidebar";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.sidebar";
    private static final int MAX_LINES = 15;
    private static final int MAX_LENGTH = 200;
    private static final List<String> ADMIN_ACTIONS = List.of("lines", "add", "set", "insert", "remove", "title", "reload");

    private final SmpPlugin plugin;

    public SidebarCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("toggle")) {
            toggle(sender);
            return true;
        }
        if (!ADMIN_ACTIONS.contains(action)) {
            Text.send(sender, "SIDEBAR.USAGE", "&7/sidebar &8(show or hide)"
                    + (PermissionUtils.has(sender, ADMIN_PERMISSION) ? " &8| &7/sidebar lines|add|set|insert|remove|title|reload" : ""));
            return true;
        }
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        FileConfiguration config = plugin.getConfigManager().getScoreboard();
        if (config == null) {
            Text.send(sender, "SIDEBAR.NO-CONFIG", "&cscoreboard.yml could not be loaded; fix it and run /sidebar reload.");
            return true;
        }
        List<String> lines = new ArrayList<>(config.getStringList("SCOREBOARD.LINES"));
        switch (action) {
            case "lines" -> list(sender, config, lines);
            case "reload" -> {
                plugin.getConfigManager().reloadScoreboard();
                plugin.getScoreboardManager().markDirty();
                Text.send(sender, "SIDEBAR.RELOADED", "&aReloaded scoreboard.yml.");
            }
            case "add" -> {
                if (lines.size() >= MAX_LINES) {
                    Text.send(sender, "SIDEBAR.FULL", "&cThe sidebar already has 15 lines, the most Minecraft shows.");
                    return true;
                }
                lines.add(text(args, 1));
                save(sender, config, lines, "&aAdded line " + lines.size() + ".");
            }
            case "set", "insert", "remove" -> {
                int index = number(args, lines.size() + (action.equals("insert") ? 1 : 0));
                if (index < 0) {
                    Text.send(sender, "SIDEBAR.BAD-LINE", "&cGive a line number from 1 to {max}. See them with &f/sidebar lines&c.",
                            "{max}", Integer.toString(Math.max(1, lines.size())));
                    return true;
                }
                if (action.equals("set")) {
                    lines.set(index, text(args, 2));
                    save(sender, config, lines, "&aLine " + (index + 1) + " changed.");
                } else if (action.equals("insert")) {
                    if (lines.size() >= MAX_LINES) {
                        Text.send(sender, "SIDEBAR.FULL", "&cThe sidebar already has 15 lines, the most Minecraft shows.");
                        return true;
                    }
                    lines.add(index, text(args, 2));
                    save(sender, config, lines, "&aInserted line " + (index + 1) + ".");
                } else {
                    lines.remove(index);
                    save(sender, config, lines, "&aRemoved line " + (index + 1) + ".");
                }
            }
            case "title" -> {
                String title = text(args, 1);
                if (title.isBlank()) {
                    Text.send(sender, "SIDEBAR.TITLE-USAGE", "&7/sidebar title <text>");
                    return true;
                }
                config.set("SCOREBOARD.TITLE", List.of(title));
                if (plugin.getConfigManager().saveScoreboard()) {
                    plugin.getScoreboardManager().markDirty();
                    Text.send(sender, "SIDEBAR.TITLE-SET", "&aTitle set to &r{title}&a.", "{title}", title);
                } else {
                    Text.send(sender, "SIDEBAR.SAVE-FAILED", "&cCould not save scoreboard.yml; see the console.");
                }
            }
            default -> {
                // handled above
            }
        }
        return true;
    }

    private void toggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return;
        }
        if (!PermissionUtils.has(player, TOGGLE_PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            Text.send(player, "SIDEBAR.NOT-LOADED", "&7Your settings are still loading; try again in a moment.");
            return;
        }
        data.setScoreboardVisible(!data.isScoreboardVisible());
        plugin.getScoreboardManager().applyVisibility(player);
        Text.send(player, data.isScoreboardVisible() ? "SIDEBAR.SHOWN" : "SIDEBAR.HIDDEN",
                data.isScoreboardVisible() ? "&7Your sidebar is now &ashown&7." : "&7Your sidebar is now &chidden&7. &8(/sidebar to show it)");
    }

    private void list(CommandSender sender, FileConfiguration config, List<String> lines) {
        List<String> titles = config.getStringList("SCOREBOARD.TITLE");
        Text.raw(sender, "");
        Text.raw(sender, Text.get("SIDEBAR.LIST-TITLE", "&6&lSidebar &8» &7title: &r{title}{frames}",
                "{title}", titles.isEmpty() ? "&8(none)" : titles.getFirst(),
                "{frames}", titles.size() > 1 ? " &8(+" + (titles.size() - 1) + " animation frames)" : ""));
        Text.raw(sender, Text.get("SIDEBAR.LIST-HINT", "&8  Lines show as players see them; [edit] puts the raw text in your chat box."));
        for (int i = 0; i < lines.size(); i++) {
            int number = i + 1;
            String line = lines.get(i);
            Text.line(sender, "&8" + (number < 10 ? " " : "") + number + ". ",
                    Text.suggest("&e[edit] ", "/sidebar set " + number + " " + line, "&7Put this line in the chat box to edit it"),
                    Text.button("&c[x] ", "/sidebar remove " + number, "&7Remove this line"),
                    line.isEmpty() ? "&8(empty line)" : line.replace("§", "&") + " ");
        }
        Text.line(sender, "  ", Text.suggest("&a[+ add a line]", "/sidebar add ", "&7Type the text after /sidebar add"),
                lines.size() >= MAX_LINES ? " &8(full: 15 lines)" : " &8(" + lines.size() + "/15)");
        if (plugin.getLobbyManager() != null && plugin.getLobbyManager().hasLobbyLocation()) {
            Text.raw(sender, Text.get("SIDEBAR.LOBBY-NOTE", "&8  In the lobby, the lines from lobby.yml are shown instead."));
        }
    }

    private void save(CommandSender sender, FileConfiguration config, List<String> lines, String done) {
        config.set("SCOREBOARD.LINES", lines);
        if (!plugin.getConfigManager().saveScoreboard()) {
            Text.send(sender, "SIDEBAR.SAVE-FAILED", "&cCould not save scoreboard.yml; see the console.");
            return;
        }
        plugin.getScoreboardManager().markDirty();
        Text.raw(sender, Text.prefix() + done);
    }

    private static int number(String[] args, int limit) {
        if (args.length < 2) {
            return -1;
        }
        try {
            int index = Integer.parseInt(args[1]) - 1;
            return index >= 0 && index < limit ? index : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String text(String[] args, int from) {
        String text = args.length > from ? String.join(" ", Arrays.copyOfRange(args, from, args.length)) : "";
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = PermissionUtils.has(sender, ADMIN_PERMISSION);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("toggle"));
            if (admin) {
                options.addAll(ADMIN_ACTIONS);
            }
            String typed = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(typed)).toList();
        }
        if (admin && args.length == 2 && List.of("set", "insert", "remove").contains(args[0].toLowerCase(Locale.ROOT))) {
            FileConfiguration config = plugin.getConfigManager().getScoreboard();
            int count = config == null ? 0 : config.getStringList("SCOREBOARD.LINES").size();
            List<String> numbers = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                numbers.add(Integer.toString(i));
            }
            return numbers;
        }
        if (admin && args.length == 3 && args[0].equalsIgnoreCase("set")) {
            FileConfiguration config = plugin.getConfigManager().getScoreboard();
            List<String> lines = config == null ? List.of() : config.getStringList("SCOREBOARD.LINES");
            try {
                int index = Integer.parseInt(args[1]) - 1;
                if (index >= 0 && index < lines.size() && !lines.get(index).isEmpty()) {
                    return List.of(lines.get(index));
                }
            } catch (NumberFormatException ignored) {
                // no suggestion
            }
        }
        return List.of();
    }
}
