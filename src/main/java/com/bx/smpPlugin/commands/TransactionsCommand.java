package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.economy.LedgerEntry;
import com.bx.smpPlugin.economy.LedgerText;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /transactions [page]: a player's own balance history from the economy ledger, newest first. */
public class TransactionsCommand implements CommandExecutor, TabCompleter {

    static final String PERMISSION = "smpplugin.transactions";
    static final int PAGE_SIZE = 8;

    private final SmpPlugin plugin;

    public TransactionsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "ECONOMY.PLAYERS-ONLY", "&cOnly players have a balance history. Use /eco history <player>.");
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        int page = args.length >= 1 ? parsePage(args[0]) : 1;
        plugin.getEconomyManager().history(player.getUniqueId(), page, PAGE_SIZE, result -> {
            if (player.isOnline()) {
                render(plugin, player, result, Text.get("ECONOMY.HISTORY-HEADER", "&6&lYour transactions"), label);
            }
        });
        return true;
    }

    static int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    /** Prints a history page with Previous / Next buttons that run {@code /<command> <page>}. */
    static void render(SmpPlugin plugin, CommandSender to, EconomyManager.HistoryPage page, String header, String command) {
        int pages = page.pages();
        int shown = Math.min(page.page(), pages);
        Text.raw(to, header + Text.get("ECONOMY.HISTORY-PAGE", " &8(&7page {page}/{pages}&8)",
                "{page}", Integer.toString(shown), "{pages}", Integer.toString(pages)));
        if (page.entries().isEmpty()) {
            Text.raw(to, Text.get("ECONOMY.HISTORY-EMPTY", "&7No balance changes recorded yet."));
            return;
        }
        for (LedgerEntry entry : page.entries()) {
            Text.raw(to, LedgerText.line(plugin, entry, page));
        }
        List<Object> navigation = new ArrayList<>();
        if (shown > 1) {
            navigation.add(Text.button(Text.get("ECONOMY.HISTORY-PREVIOUS", "&e[« Previous]"),
                    command + " " + (shown - 1), Text.get("ECONOMY.HISTORY-PREVIOUS-HOVER", "&7Newer changes")));
            navigation.add(" ");
        }
        if (shown < pages) {
            navigation.add(Text.button(Text.get("ECONOMY.HISTORY-NEXT", "&e[Next »]"),
                    command + " " + (shown + 1), Text.get("ECONOMY.HISTORY-NEXT-HOVER", "&7Older changes")));
        }
        if (!navigation.isEmpty()) {
            Text.line(to, navigation.toArray());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("1", "2", "3") : List.of();
    }
}
