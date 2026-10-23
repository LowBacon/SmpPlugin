// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.LeaderboardManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BaltopCommand implements CommandExecutor, TabCompleter {

    static final String PERMISSION = "smpplugin.baltop";

    private final SmpPlugin plugin;

    public BaltopCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        LeaderboardManager leaderboards = plugin.getLeaderboardManager();
        int pageSize = Math.max(5, Math.min(20, plugin.getConfigManager().getConfig().getInt("ECONOMY.BALTOP.PAGE-SIZE", 10)));
        int total = leaderboards.getTotalEntries(LeaderboardManager.LeaderboardType.MONEY);
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        int page = args.length >= 1 ? Math.min(pages, TransactionsCommand.parsePage(args[0])) : 1;

        Text.raw(sender, Text.get("BALTOP.HEADER", "&6&l💰 Richest players &8(&7page {page}/{pages}&8)",
                "{page}", Integer.toString(page), "{pages}", Integer.toString(pages)));
        List<LeaderboardManager.LeaderboardEntry> entries =
                leaderboards.getEntries(LeaderboardManager.LeaderboardType.MONEY, (page - 1) * pageSize, pageSize);
        if (entries.isEmpty()) {
            Text.raw(sender, Text.get("BALTOP.EMPTY", "&7Nobody is on the list yet."));
        }
        for (LeaderboardManager.LeaderboardEntry entry : entries) {
            Text.raw(sender, Text.get("BALTOP.LINE", "{color}#{rank} &f{player} &8- &a{money}",
                    "{color}", entry.position() == 1 ? "&6&l" : entry.position() <= 3 ? "&e" : "&7",
                    "{rank}", Integer.toString(entry.position()),
                    "{player}", entry.playerData().getUsername(),
                    "{money}", plugin.getCurrencyManager().formatMoney(entry.playerData().getMoney())));
        }
        if (sender instanceof Player player) {
            LeaderboardManager.LeaderboardEntry own =
                    leaderboards.getPlayerEntry(player.getUniqueId(), LeaderboardManager.LeaderboardType.MONEY);
            if (own != null) {
                Text.raw(sender, Text.get("BALTOP.YOU", "&7You are &e#{rank} &7with &a{money}&7.",
                        "{rank}", Integer.toString(own.position()),
                        "{money}", plugin.getCurrencyManager().formatMoney(own.playerData().getMoney())));
            }
        }
        List<Object> navigation = new ArrayList<>();
        if (page > 1) {
            navigation.add(Text.button(Text.get("BALTOP.PREVIOUS", "&e[« Previous]"), label + " " + (page - 1),
                    Text.get("BALTOP.PREVIOUS-HOVER", "&7Page {page}", "{page}", Integer.toString(page - 1))));
            navigation.add(" ");
        }
        if (page < pages) {
            navigation.add(Text.button(Text.get("BALTOP.NEXT", "&e[Next »]"), label + " " + (page + 1),
                    Text.get("BALTOP.NEXT-HOVER", "&7Page {page}", "{page}", Integer.toString(page + 1))));
        }
        if (!navigation.isEmpty()) {
            Text.line(sender, navigation.toArray());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("1", "2", "3") : List.of();
    }
}
