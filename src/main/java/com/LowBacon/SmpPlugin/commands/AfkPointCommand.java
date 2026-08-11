package com.bx.finnishSmp.commands;

import com.bx.finnishSmp.FinnishSmp;
import com.bx.finnishSmp.managers.AfkPointManager;
import com.bx.finnishSmp.managers.SpawnManager;
import com.bx.finnishSmp.utils.ColorUtils;
import com.bx.finnishSmp.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Backs {@code /setafk}, {@code /delafk} and {@code /afklist}. */
public class AfkPointCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "finnishsmp.admin.setafk";

    private final FinnishSmp plugin;

    public AfkPointCommand(FinnishSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            sender.sendMessage(ColorUtils.toComponent("&cʏᴏᴜ ᴅᴏ ɴᴏᴛ ʜᴀᴠᴇ ᴘᴇʀᴍɪѕѕɪᴏɴ ᴛᴏ ᴍᴀɴᴀɢᴇ ᴀꜰᴋ ᴘᴏɪɴᴛѕ."));
            return true;
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "delafk" -> handleDelete(sender, args);
            case "afklist" -> handleList(sender);
            default -> handleSet(sender, args);
        };
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cᴏɴʟʏ ᴀ ᴘʟᴀʏᴇʀ ᴄᴀɴ ѕᴇᴛ ᴀɴ ᴀꜰᴋ ᴘᴏɪɴᴛ."));
            return true;
        }

        AfkPointManager manager = plugin.getAfkPointManager();
        String name = args.length > 0 ? args[0] : manager.nextAutoId();
        if (name == null) {
            sender.sendMessage(ColorUtils.toComponent("&cᴛᴏᴏ ᴍᴀɴʏ ᴀᴜᴛᴏ-ɴᴀᴍᴇᴅ ᴀꜰᴋ ᴘᴏɪɴᴛѕ. ᴜѕᴇ &f/setafk <name>&c."));
            return true;
        }

        Location location = player.getLocation();
        AfkPointManager.PointResult result = manager.setPoint(name, location);
        if (!result.success()) {
            sender.sendMessage(ColorUtils.toComponent("&cᴄᴏᴜʟᴅ ɴᴏᴛ ѕᴀᴠᴇ ᴛʜᴇ ᴀꜰᴋ ᴘᴏɪɴᴛ: &f" + result.message()));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent((result.overwrote() ? "&aᴜᴘᴅᴀᴛᴇᴅ" : "&aѕᴀᴠᴇᴅ")
                + " ᴀꜰᴋ ᴘᴏɪɴᴛ &f" + result.id() + " &7ᴀᴛ &f" + describe(location) + "&7."));
        sender.sendMessage(ColorUtils.toComponent(manager.count() > 1
                ? "&7ᴘʟᴀʏᴇʀѕ ᴜѕɪɴɢ &f/afk&7 ᴡɪʟʟ ɴᴏᴡ ᴘɪᴄᴋ ꜰʀᴏᴍ &f" + manager.count() + "&7 ᴀꜰᴋ ᴢᴏɴᴇѕ."
                : "&7ᴘʟᴀʏᴇʀѕ ᴄᴀɴ ɴᴏᴡ ᴜѕᴇ &f/afk&7 ᴛᴏ ᴛᴇʟᴇᴘᴏʀᴛ ʜᴇʀᴇ."));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtils.toComponent("&cᴜѕᴀɢᴇ: &f/delafk <name>"));
            sendKnownPoints(sender);
            return true;
        }

        AfkPointManager.PointResult result = plugin.getAfkPointManager().deletePoint(args[0]);
        if (!result.success()) {
            sender.sendMessage(ColorUtils.toComponent("&c" + result.message()));
            sendKnownPoints(sender);
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent("&aʀᴇᴍᴏᴠᴇᴅ ᴀꜰᴋ ᴘᴏɪɴᴛ &f" + result.id() + "&a."));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        AfkPointManager manager = plugin.getAfkPointManager();
        List<AfkPointManager.AfkPoint> points = manager.getPoints();
        List<SpawnManager.TeleportArea> legacyAreas =
                plugin.getSpawnManager().getValidAreas(SpawnManager.AreaType.AFK);

        if (points.isEmpty() && legacyAreas.isEmpty()) {
            sender.sendMessage(ColorUtils.toComponent("&cɴᴏ ᴀꜰᴋ ᴅᴇѕᴛɪɴᴀᴛɪᴏɴѕ ᴀʀᴇ ᴄᴏɴꜰɪɢᴜʀᴇᴅ."));
            sender.sendMessage(ColorUtils.toComponent("&7ѕᴛᴀɴᴅ ᴡʜᴇʀᴇ ʏᴏᴜ ᴡᴀɴᴛ ᴏɴᴇ ᴀɴᴅ ʀᴜɴ &f/setafk&7."));
            return true;
        }

        sender.sendMessage(ColorUtils.toComponent("&8&m                                        "));
        sender.sendMessage(ColorUtils.toComponent("&#A303F9ᴀꜰᴋ ᴅᴇѕᴛɪɴᴀᴛɪᴏɴѕ"));

        for (AfkPointManager.AfkPoint point : points) {
            Location location = point.location();
            if (location == null) {
                sender.sendMessage(ColorUtils.toComponent("&8 • &f" + point.id()
                        + " &c(world not loaded) &8" + point.rawLocation()));
                continue;
            }
            sender.sendMessage(ColorUtils.toComponent("&8 • &f" + point.id() + " &7" + describe(location)
                    + " &8(" + manager.countPlayersNear(location) + "/" + point.capacity() + ")"));
        }

        for (SpawnManager.TeleportArea area : legacyAreas) {
            Location location = plugin.getSpawnManager().resolveDestination(area);
            sender.sendMessage(ColorUtils.toComponent("&8 • &7" + area.id() + " &8(cuboid area"
                    + (area.cuboidName() == null || area.cuboidName().isBlank() ? "" : ": " + area.cuboidName())
                    + ") &7" + (location == null ? "unresolved" : describe(location))));
        }

        sender.sendMessage(ColorUtils.toComponent("&7ᴜѕᴇ &f/setafk [name]&7 ᴛᴏ ᴀᴅᴅ, &f/delafk <name>&7 ᴛᴏ ʀᴇᴍᴏᴠᴇ."));
        sender.sendMessage(ColorUtils.toComponent("&8&m                                        "));
        return true;
    }

    private void sendKnownPoints(CommandSender sender) {
        List<String> ids = plugin.getAfkPointManager().getSortedIds();
        if (ids.isEmpty()) {
            sender.sendMessage(ColorUtils.toComponent("&7ᴛʜᴇʀᴇ ᴀʀᴇ ɴᴏ ᴀꜰᴋ ᴘᴏɪɴᴛѕ ʏᴇᴛ."));
            return;
        }
        sender.sendMessage(ColorUtils.toComponent("&7ᴋɴᴏᴡɴ ᴀꜰᴋ ᴘᴏɪɴᴛѕ: &f" + String.join("&7, &f", ids)));
    }

    private String describe(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION) || args.length != 1) {
            return List.of();
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("afklist")) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String id : plugin.getAfkPointManager().getSortedIds()) {
            if (id.startsWith(prefix)) {
                suggestions.add(id);
            }
        }

        if (commandName.equals("setafk") && suggestions.isEmpty() && prefix.isEmpty()) {
            String next = plugin.getAfkPointManager().nextAutoId();
            if (next != null) {
                suggestions.add(next);
            }
        }
        return suggestions;
    }
}
