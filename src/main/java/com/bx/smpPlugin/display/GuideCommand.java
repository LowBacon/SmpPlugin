// SmpPlugin by LowBacon
package com.bx.smpPlugin.display;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuideCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.guide";

    record Line(String text, String command, boolean suggest) {
    }

    record Page(String key, String title, List<Line> lines) {
    }

    private final SmpPlugin plugin;

    public GuideCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        List<Page> pages = pagesFor(sender);
        if (pages.isEmpty()) {
            Text.send(sender, "GUIDE.EMPTY", "&7The guide has no pages yet. Staff can add them in guide.yml.");
            return true;
        }
        String wanted = args.length == 0 ? "1" : args[0].toLowerCase(Locale.ROOT);
        if (wanted.equals("topics") || wanted.equals("pages") || wanted.equals("list")) {
            topics(sender, pages);
            return true;
        }
        int index = -1;
        try {
            index = Integer.parseInt(wanted) - 1;
        } catch (NumberFormatException notANumber) {
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).key().equalsIgnoreCase(wanted)) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0 || index >= pages.size()) {
            Text.send(sender, "GUIDE.NO-PAGE", "&cThere is no page '{page}'. The guide has pages 1 to {count}.",
                    "{page}", wanted.replace('&', '＆'), "{count}", Integer.toString(pages.size()));
            topics(sender, pages);
            return true;
        }
        show(sender, pages, index);
        return true;
    }

    private void show(CommandSender sender, List<Page> pages, int index) {
        Page page = pages.get(index);
        String title = config() == null ? "&6&lServer Guide" : config().getString("TITLE", "&6&lServer Guide");
        Text.raw(sender, "");
        Text.raw(sender, fill(sender, title + " &8» &f" + page.title() + " &8(" + (index + 1) + "/" + pages.size() + ")"));
        for (Line line : page.lines()) {
            if (line.command() == null) {
                Text.raw(sender, fill(sender, "  " + line.text()));
                continue;
            }
            String shown = "&e" + line.command();
            String hover = line.suggest() ? "&7Click to type " + line.command() : "&7Click to run " + line.command();
            Text.line(sender, "  ",
                    line.suggest() ? Text.suggest(shown, line.command() + " ", hover) : Text.button(shown, line.command(), hover),
                    line.text().isEmpty() ? "" : " &8- &7" + fill(sender, line.text()));
        }
        List<Object> nav = new ArrayList<>();
        nav.add("  ");
        nav.add(index > 0 ? Text.button("&b« Previous", "guide " + index, "&7" + pages.get(index - 1).title()) : "&8« Previous");
        nav.add("  ");
        nav.add(Text.button("&e[All pages]", "guide topics", "&7Every page of the guide"));
        nav.add("  ");
        nav.add(index < pages.size() - 1 ? Text.button("&bNext »", "guide " + (index + 2), "&7" + pages.get(index + 1).title()) : "&8Next »");
        Text.line(sender, nav.toArray());
    }

    private void topics(CommandSender sender, List<Page> pages) {
        Text.raw(sender, Text.get("GUIDE.TOPICS", "&6&lGuide &8» &7pick a page"));
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            Text.line(sender, "  &8" + (i + 1) + ". ", Text.button("&f" + page.title(), "guide " + (i + 1),
                    "&7/guide " + page.key()));
        }
    }

    private FileConfiguration config() {
        return plugin.getConfigManager().getGuide();
    }

    List<Page> pagesFor(CommandSender reader) {
        FileConfiguration config = config();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("PAGES");
        List<Page> pages = new ArrayList<>();
        if (section == null) {
            return pages;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection page = section.getConfigurationSection(key);
            if (page == null) {
                continue;
            }
            List<Line> lines = new ArrayList<>();
            boolean hasCommand = false;
            for (Object raw : page.getList("LINES", List.of())) {
                Line line = parse(raw);
                if (line == null) {
                    continue;
                }
                if (line.command() != null) {
                    if (!usable(reader, line.command())) {
                        continue;
                    }
                    hasCommand = true;
                }
                lines.add(line);
            }
            boolean hasText = lines.stream().anyMatch(line -> line.command() == null && !line.text().isBlank());
            if (hasCommand || hasText) {
                pages.add(new Page(key.toLowerCase(Locale.ROOT), page.getString("TITLE", key), lines));
            }
        }
        return pages;
    }

    static Line parse(Object raw) {
        if (raw instanceof String text) {
            return new Line(text, null, false);
        }
        if (raw instanceof Map<?, ?> map) {
            Object command = map.get("COMMAND");
            Object text = map.get("TEXT");
            String cleaned = command == null ? null : command.toString().trim();
            if (cleaned != null && !cleaned.isEmpty() && !cleaned.startsWith("/")) {
                cleaned = "/" + cleaned;
            }
            return new Line(text == null ? "" : text.toString(), cleaned == null || cleaned.isEmpty() ? null : cleaned,
                    Boolean.parseBoolean(String.valueOf(map.get("SUGGEST"))));
        }
        return null;
    }

    private boolean usable(CommandSender reader, String commandLine) {
        String root = commandLine.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (root.isEmpty()) {
            return false;
        }
        PluginCommand command = Bukkit.getPluginCommand(root);
        if (command == null) {
            return false;
        }
        if (command.getPlugin() == plugin) {
            for (FeatureManager.Feature feature : FeatureManager.featuresForCommand(command.getName())) {
                if (!plugin.getFeatureManager().isEnabled(feature)) {
                    return false;
                }
            }
        }
        return command.testPermissionSilent(reader);
    }

    private static String fill(CommandSender sender, String text) {
        String name = sender.getName();
        String out = text.replace("%player%", name).replace("{player}", name);
        return sender instanceof Player player ? ColorUtils.colorize(out, player).replace('§', '&') : out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("topics"));
        List<Page> pages = pagesFor(sender);
        for (int i = 0; i < pages.size(); i++) {
            options.add(Integer.toString(i + 1));
            options.add(pages.get(i).key());
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
