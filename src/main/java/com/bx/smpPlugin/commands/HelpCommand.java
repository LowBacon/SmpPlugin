// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.commands.CommandCatalog.Category;
import com.bx.smpPlugin.commands.CommandCatalog.Entry;
import com.bx.smpPlugin.menus.ServerInfoMenu;
import com.bx.smpPlugin.utils.ColorUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// /help: the help menu, or in chat the categories, a category's commands page by page, or one
// command. Only commands that are registered, switched on and allowed for the reader are listed.
public class HelpCommand implements CommandExecutor, TabCompleter {

    static final int PAGE_SIZE = 8;

    private final SmpPlugin plugin;

    public HelpCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfigManager().isCommandEnabled("HELP")) {
            send(sender, message("DISABLED", "&cHelp is turned off on this server."));
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                ServerInfoMenu menu = new ServerInfoMenu(plugin);
                if (menu.hasValidButtons()) {
                    menu.open(player);
                    return true;
                }
            }
            overview(sender);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("list") || first.equals("categories")) {
            overview(sender);
            return true;
        }
        Optional<Category> category = Category.parse(first);
        if (category.isPresent()) {
            category(sender, category.get(), args.length > 1 ? parsePage(args[1]) : 1);
            return true;
        }
        Optional<Entry> entry = CommandCatalog.find(entries(), first);
        if (entry.isPresent() && (visible(sender, entry.get()) || !enabled(entry.get()))) {
            detail(sender, entry.get());
            return true;
        }
        send(sender, message("UNKNOWN", "&cThere is no help for &f{input}&c. Try &f/help &cto see the categories.",
                "{input}", args[0]));
        return true;
    }

    // ------------------------------------------------------------------ pages

    private void overview(CommandSender sender) {
        Map<Category, List<Entry>> grouped = CommandCatalog.byCategory(visibleEntries(sender));
        send(sender, message("HEADER", "&8&m------&r &b&lHelp &8&m------"));
        if (grouped.isEmpty()) {
            send(sender, message("EMPTY", "&7There are no commands you can use right now."));
            return;
        }
        for (Category category : Category.values()) {
            List<Entry> commands = grouped.get(category);
            if (commands == null || commands.isEmpty()) {
                continue;
            }
            String line = message("CATEGORY-LINE", "&b{title} &8({count}) &7- {description}",
                    "{title}", category.title(), "{count}", Integer.toString(commands.size()),
                    "{description}", category.description());
            clickable(sender, line, "/help " + category.key(),
                    message("CATEGORY-HOVER", "&7Click to see the {title} commands", "{title}", category.title()), true);
        }
        send(sender, message("OVERVIEW-FOOTER", "&7Use &f/help <category> &7or &f/help <command>&7."));
    }

    private void category(CommandSender sender, Category category, int requestedPage) {
        List<Entry> commands = new ArrayList<>();
        for (Entry entry : visibleEntries(sender)) {
            if (entry.category() == category) {
                commands.add(entry);
            }
        }
        if (commands.isEmpty()) {
            send(sender, message("CATEGORY-EMPTY", "&7You have no &f{title} &7commands.", "{title}", category.title()));
            return;
        }
        int pages = pageCount(commands.size());
        int page = clampPage(requestedPage, commands.size());
        send(sender, message("CATEGORY-HEADER", "&8&m------&r &b&l{title} &7({page}/{pages}) &8&m------",
                "{title}", category.title(), "{page}", Integer.toString(page), "{pages}", Integer.toString(pages)));
        for (Entry entry : page(commands, page)) {
            String line = message("COMMAND-LINE", "&f/{command} &8- &7{description}",
                    "{command}", entry.name(), "{description}", entry.description());
            String hover = message("COMMAND-HOVER", "&f{usage}\n&7Click to type it", "{usage}", entry.usage());
            clickable(sender, line, "/" + entry.name() + " ", hover, false);
        }
        if (pages > 1) {
            navigation(sender, category, page, pages);
        }
    }

    private void detail(CommandSender sender, Entry entry) {
        send(sender, message("COMMAND-HEADER", "&8&m------&r &b&l/{command} &8&m------", "{command}", entry.name()));
        if (!entry.description().isBlank()) {
            send(sender, "&7" + entry.description());
        }
        send(sender, message("COMMAND-USAGE", "&7Usage: &f{usage}", "{usage}", entry.usage()));
        if (!entry.aliases().isEmpty()) {
            send(sender, message("COMMAND-ALIASES", "&7Also: &f{aliases}",
                    "{aliases}", "/" + String.join(" /", entry.aliases())));
        }
        if (!enabled(entry)) {
            send(sender, message("COMMAND-DISABLED", "&cThis command is turned off on this server."));
        }
        // Permission nodes are for whoever sets up ranks.
        if (!entry.permission().isBlank() && (sender.isOp() || sender.hasPermission("smpplugin.admin.perms"))) {
            send(sender, message("COMMAND-PERMISSION", "&7Permission: &f{permission}",
                    "{permission}", entry.permission().replace(";", ", ")));
        }
    }

    private void navigation(CommandSender sender, Category category, int page, int pages) {
        if (!(sender instanceof Player player)) {
            send(sender, message("PAGE-HINT", "&7More: &f/help {category} {next}",
                    "{category}", category.key(), "{next}", Integer.toString(Math.min(pages, page + 1))));
            return;
        }
        TextComponent bar = new TextComponent();
        if (page > 1) {
            TextComponent previous = ColorUtils.toBaseComponent(message("PREVIOUS", "&b« Previous"));
            previous.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/help " + category.key() + " " + (page - 1)));
            bar.addExtra(previous);
            bar.addExtra(ColorUtils.toBaseComponent("&8   "));
        }
        if (page < pages) {
            TextComponent next = ColorUtils.toBaseComponent(message("NEXT", "&bNext »"));
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/help " + category.key() + " " + (page + 1)));
            bar.addExtra(next);
        }
        player.spigot().sendMessage(bar);
    }

    // ------------------------------------------------------------------ data

    private List<Entry> entries() {
        return CommandCatalog.entries(plugin.getDescription().getCommands());
    }

    private List<Entry> visibleEntries(CommandSender sender) {
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : entries()) {
            if (enabled(entry) && visible(sender, entry)) {
                visible.add(entry);
            }
        }
        return visible;
    }

    private boolean visible(CommandSender sender, Entry entry) {
        PluginCommand command = plugin.getCommand(entry.name());
        return command != null && CommandCatalog.visibleTo(sender, entry, command);
    }

    private boolean enabled(Entry entry) {
        return plugin.getFeatureManager() == null || plugin.getFeatureManager().isCommandFeatureEnabled(entry.name());
    }

    static int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clampPage(int requested, int size) {
        return Math.max(1, Math.min(requested, pageCount(size)));
    }

    static <T> List<T> page(List<T> items, int page) {
        int from = Math.min(items.size(), (page - 1) * PAGE_SIZE);
        return items.subList(from, Math.min(items.size(), from + PAGE_SIZE));
    }

    static int parsePage(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    // ------------------------------------------------------------------ output

    private String message(String key, String fallback, String... placeholders) {
        return plugin.getConfigManager().getMessageOrDefault("COMMAND-HELP." + key, fallback, placeholders);
    }

    private void send(CommandSender sender, String text) {
        sender.sendMessage(ColorUtils.toComponent(text));
    }

    private void clickable(CommandSender sender, String text, String command, String hover, boolean run) {
        if (!(sender instanceof Player player)) {
            send(sender, text);
            return;
        }
        TextComponent line = ColorUtils.toBaseComponent(text);
        line.setClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, command));
        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ColorUtils.toBaseComponents(hover)));
        player.spigot().sendMessage(line);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        List<Entry> visible = visibleEntries(sender);
        for (Category category : CommandCatalog.byCategory(visible).keySet()) {
            if (category.key().startsWith(typed)) {
                out.add(category.key());
            }
        }
        for (Entry entry : visible) {
            if (out.size() >= 40) {
                break;
            }
            if (entry.name().startsWith(typed)) {
                out.add(entry.name());
            }
        }
        return out;
    }
}
