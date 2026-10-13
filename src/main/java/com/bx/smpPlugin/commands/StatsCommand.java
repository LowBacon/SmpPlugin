package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.utils.ColorUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private final SmpPlugin plugin;

    public StatsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("ᴘʟᴀʏᴇʀ ᴏɴʟʏ."); return true; }

        OfflinePlayer target = args.length > 0 ? Bukkit.getOfflinePlayer(args[0]) : player;
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (data == null) data = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (data == null) { player.sendMessage(ColorUtils.toComponent("&cᴘʟᴀʏᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ.")); return true; }

        String name = target.getName() != null ? target.getName() : args[0];
        player.sendMessage(ColorUtils.toComponent("&7&m------------------"));
        player.sendMessage(ColorUtils.toComponent("&b" + name + "&7'ѕ ѕᴛᴀᴛѕ:"));
        player.sendMessage(ColorUtils.toComponent(plugin.getCurrencyManager().color(com.bx.smpPlugin.managers.CurrencyManager.CurrencyType.MONEY)
                + plugin.getCurrencyManager().symbol(com.bx.smpPlugin.managers.CurrencyManager.CurrencyType.MONEY)
                + " &f" + plugin.getCurrencyManager().plural(com.bx.smpPlugin.managers.CurrencyManager.CurrencyType.MONEY)
                + ": " + plugin.getCurrencyManager().formatMoneyCompact(data.getMoney())));
        player.sendMessage(ColorUtils.toComponent(plugin.getCurrencyManager().color(com.bx.smpPlugin.managers.CurrencyManager.CurrencyType.SHARDS)
                + plugin.getCurrencyManager().symbol(com.bx.smpPlugin.managers.CurrencyManager.CurrencyType.SHARDS)
                + " &f" + plugin.getCurrencyManager().plural(com.bx.smpPlugin.managers.CurrencyManager.CurrencyType.SHARDS)
                + ": " + plugin.getCurrencyManager().formatShards(data.getShards())));
        player.sendMessage(ColorUtils.toComponent("&#FC0000⚔ &fᴋɪʟʟѕ: &#FC0000" + data.getKills()));
        player.sendMessage(ColorUtils.toComponent("&#F97603☠ &fᴅᴇᴀᴛʜѕ: &#F97603" + data.getDeaths()));
        player.sendMessage(ColorUtils.toComponent("&#FCE300⌚ &fᴘʟᴀʏᴛɪᴍᴇ: &#FCE300" + NumberUtils.formatTimeLong(data.getTotalPlaytimeSeconds())));

        String teamName = plugin.getTeamManager().getTeam(target.getUniqueId()) != null
                ? plugin.getTeamManager().getTeam(target.getUniqueId()).getName()
                : null;
        player.sendMessage(ColorUtils.toComponent("&#00A4FC⚑ &fᴛᴇᴀᴍ: &#00A4FC" + (teamName != null ? teamName.toUpperCase() : "ɴᴏɴᴇ")));
        player.sendMessage(ColorUtils.toComponent("&7&m------------------"));
        return true;
    }
}
