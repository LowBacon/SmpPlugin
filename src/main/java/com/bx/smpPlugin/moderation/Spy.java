// SmpPlugin by LowBacon
package com.bx.smpPlugin.moderation;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class Spy implements CommandExecutor, Listener {

    public static final String SOCIAL_PERMISSION = "smpplugin.staff.socialspy";
    public static final String COMMAND_PERMISSION = "smpplugin.staff.commandspy";
    private static final String SECTION = "spy";

    static final Set<String> MESSAGE_COMMANDS = Set.of("msg", "message", "tell", "whisper", "w", "m", "t", "r", "reply");
    static final Set<String> REPLY_COMMANDS = Set.of("r", "reply");
    static final Set<String> NEVER_SHOWN = Set.of("login", "l", "log", "register", "reg", "changepassword",
            "changepass", "cp", "2fa", "authme", "premium", "unregister");

    private final SmpPlugin plugin;
    private final PlayerStore store;

    public Spy(SmpPlugin plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    private boolean isOn(UUID uuid, String kind) {
        String value = store.get(uuid, SECTION);
        return value != null && value.contains(kind);
    }

    private void toggle(Player player, String kind) {
        String value = store.get(player.getUniqueId(), SECTION);
        boolean social = value != null && value.contains("social");
        boolean command = value != null && value.contains("command");
        if (kind.equals("social")) {
            social = !social;
        } else {
            command = !command;
        }
        String updated = (social ? "social," : "") + (command ? "command" : "");
        store.set(player.getUniqueId(), SECTION, updated.isEmpty() ? null : updated);
        boolean nowOn = kind.equals("social") ? social : command;
        Text.send(player, "SPY.TOGGLED", "&7{kind} spy is now {state}&7.",
                "{kind}", kind.equals("social") ? "Social" : "Command", "{state}", nowOn ? "&aon" : "&coff");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        boolean social = command.getName().equalsIgnoreCase("socialspy");
        if (!PermissionUtils.has(player, social ? SOCIAL_PERMISSION : COMMAND_PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        toggle(player, social ? "social" : "command");
        return true;
    }

    static String label(String message) {
        String body = message.startsWith("/") ? message.substring(1) : message;
        int space = body.indexOf(' ');
        String label = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);
        return label.substring(label.indexOf(':') + 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2 || !plugin.getFeatureManager().isEnabled(
                com.bx.smpPlugin.managers.FeatureManager.Feature.STAFF_EXTRAS)) {
            return;
        }
        String label = label(message);
        if (NEVER_SHOWN.contains(label)) {
            return;
        }
        Player sender = event.getPlayer();
        boolean isMessage = MESSAGE_COMMANDS.contains(label);
        // Players' text is shown as typed; a full-width ampersand stops it faking colours for staff.
        message = message.replace('&', '＆').replace('§', '＆');
        String[] parts = message.split(" ", 3);
        String line;
        if (isMessage && REPLY_COMMANDS.contains(label)) {
            if (parts.length < 2) {
                return;
            }
            line = Text.get("SPY.SOCIAL-REPLY", "&8[&dSpy&8] &f{player} &7replied: &f{text}",
                    "{player}", sender.getName(), "{text}", message.substring(message.indexOf(' ') + 1));
        } else if (isMessage) {
            if (parts.length < 3) {
                return;
            }
            line = Text.get("SPY.SOCIAL", "&8[&dSpy&8] &f{player} &7→ &f{target}&8: &f{text}",
                    "{player}", sender.getName(), "{target}", parts[1], "{text}", parts[2]);
        } else {
            line = Text.get("SPY.COMMAND", "&8[&bCmd&8] &f{player}&8: &7{command}", "{player}", sender.getName(), "{command}", message);
        }
        String kind = isMessage ? "social" : "command";
        String permission = isMessage ? SOCIAL_PERMISSION : COMMAND_PERMISSION;
        String colored = Text.color(line);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.equals(sender) && isOn(staff.getUniqueId(), kind) && PermissionUtils.has(staff, permission)) {
                staff.sendMessage(colored);
            }
        }
    }
}
