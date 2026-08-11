package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AnvilModerationCommand implements CommandExecutor, TabCompleter {

    private final FinnishSmp plugin;

    public AnvilModerationCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!PermissionUtils.has(sender, "anvilmod.admin")) {
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getLanguageManager().text("MESSAGES.ANVILMOD.NO-PERMISSION", "&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ.")
            ));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            if (args.length < 2) {
                sendUsage(sender);
                return true;
            }

            String word = args[1];
            boolean success = plugin.getAnvilModerationManager().addBannedWord(word);
            if (success) {
                sender.sendMessage(ColorUtils.toComponent(
                        plugin.getLanguageManager().text("MESSAGES.ANVILMOD.ADDED", "&aѕᴜᴄᴄᴇѕѕꜰᴜʟʟʏ ᴀᴅᴅᴇᴅ '&e{word}&a' ᴛᴏ ʙᴀɴɴᴇᴅ ᴡᴏʀᴅѕ.", "{word}", word)
                ));
            } else {
                sender.sendMessage(ColorUtils.toComponent(
                        plugin.getLanguageManager().text("MESSAGES.ANVILMOD.ALREADY-EXISTS", "&cᴛʜᴀᴛ ᴡᴏʀᴅ ɪѕ ᴀʟʀᴇᴀᴅʏ ɪɴ ᴛʜᴇ ʙᴀɴɴᴇᴅ ʟɪѕᴛ.")
                ));
            }
            return true;
        } else if (sub.equals("reload")) {
            plugin.getAnvilModerationManager().load();
            sender.sendMessage(ColorUtils.toComponent(
                    plugin.getLanguageManager().text("MESSAGES.ANVILMOD.RELOADED", "&aᴀɴᴠɪʟ ᴍᴏᴅᴇʀᴀᴛɪᴏɴ ᴄᴏɴꜰɪɢ ʀᴇʟᴏᴀᴅᴇᴅ.")
            ));
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorUtils.toComponent(
                plugin.getLanguageManager().text("MESSAGES.ANVILMOD.USAGE", "&cᴜѕᴀɢᴇ: /amod <add|reload> [ᴡᴏʀᴅ]")
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!PermissionUtils.has(sender, "anvilmod.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase(Locale.ROOT);
            if ("add".startsWith(input)) suggestions.add("add");
            if ("reload".startsWith(input)) suggestions.add("reload");
            return suggestions;
        }

        return Collections.emptyList();
    }
}
