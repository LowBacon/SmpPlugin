package com.bx.smpPlugin.moderation;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * /notes &lt;player&gt; [add &lt;text&gt; | remove &lt;id&gt;]: private notes staff keep about a
 * player, such as a warning given in chat or a suspicion worth remembering. Only staff with the
 * permission can read them, and other online staff are told when a note is added.
 */
public final class StaffNotes implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.staff.notes";
    private static final String USAGE = "&7/notes <player> &8| &7/notes <player> add <text> &8| &7/notes <player> remove <id>";

    private record Note(long id, String author, String text, long createdAt) {
    }

    private final SmpPlugin plugin;
    private final PlayerStore store;

    public StaffNotes(SmpPlugin plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            Text.send(sender, "NOTES.USAGE", USAGE);
            return true;
        }
        com.bx.smpPlugin.core.Lookup.account(plugin, sender, args[0], account -> {
            if (account == null) {
                Text.send(sender, "UTIL.SEEN-UNKNOWN", "&cNo player called &f{player} &chas joined this server.", "{player}", args[0]);
            } else {
                handle(sender, account, args);
            }
        });
        return true;
    }

    private void handle(CommandSender sender, EconomyManager.AccountReference account, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "add" -> {
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                if (text.isEmpty()) {
                    Text.send(sender, "NOTES.USAGE", USAGE);
                    return;
                }
                add(sender, account, text.length() > 300 ? text.substring(0, 300) : text);
            }
            case "remove", "delete" -> {
                try {
                    remove(sender, account, Long.parseLong(args.length > 2 ? args[2] : ""));
                } catch (NumberFormatException ignored) {
                    Text.send(sender, "NOTES.USAGE", USAGE);
                }
            }
            default -> list(sender, account);
        }
    }

    private void reply(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            plugin.getSpigotScheduler().runEntity(player, action);
        } else {
            plugin.getSpigotScheduler().runGlobal(action);
        }
    }

    private void add(CommandSender sender, EconomyManager.AccountReference account, String text) {
        String author = sender.getName();
        plugin.getDatabaseManager().executeAsync(() -> {
            try (PreparedStatement insert = store.connection().prepareStatement(
                    "INSERT INTO v3_staff_notes (player_uuid, author_name, note_text, created_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, account.uuid().toString());
                insert.setString(2, author);
                insert.setString(3, text);
                insert.setLong(4, System.currentTimeMillis());
                insert.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not save a staff note", exception);
            }
            reply(sender, () -> Text.send(sender, "NOTES.ADDED", "&aNote added to &f{player}&a.", "{player}", account.displayName()));
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.getName().equals(author) && PermissionUtils.has(staff, PERMISSION)) {
                    plugin.getSpigotScheduler().runEntity(staff, () -> Text.send(staff, "NOTES.ADDED-ALERT",
                            "&8[Notes] &f{author} &7noted on &f{player}&7: &f{text}",
                            "{author}", author, "{player}", account.displayName(), "{text}", text));
                }
            }
        });
    }

    private void remove(CommandSender sender, EconomyManager.AccountReference account, long id) {
        plugin.getDatabaseManager().executeAsync(() -> {
            int removed = 0;
            try (PreparedStatement delete = store.connection().prepareStatement(
                    "DELETE FROM v3_staff_notes WHERE note_id = ? AND player_uuid = ?")) {
                delete.setLong(1, id);
                delete.setString(2, account.uuid().toString());
                removed = delete.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not delete a staff note", exception);
            }
            boolean done = removed > 0;
            reply(sender, () -> Text.send(sender, done ? "NOTES.REMOVED" : "NOTES.NOT-FOUND",
                    done ? "&7Note &f#{id} &7removed." : "&cThere is no note #{id} for that player.", "{id}", Long.toString(id)));
        });
    }

    private void list(CommandSender sender, EconomyManager.AccountReference account) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Note> notes = new ArrayList<>();
            try (PreparedStatement select = store.connection().prepareStatement(
                    "SELECT note_id, author_name, note_text, created_at FROM v3_staff_notes WHERE player_uuid = ? ORDER BY created_at DESC")) {
                select.setString(1, account.uuid().toString());
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next() && notes.size() < 30) {
                        notes.add(new Note(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)));
                    }
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read staff notes", exception);
            }
            reply(sender, () -> {
                Text.send(sender, "NOTES.HEADER", "&6Staff notes for &f{player} &7({count})",
                        "{player}", account.displayName(), "{count}", Integer.toString(notes.size()));
                SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy", Locale.ROOT);
                for (Note note : notes) {
                    Text.line(sender, Text.button("&c[✖] ", "notes " + account.displayName() + " remove " + note.id(), "&7Remove this note"),
                            "&8#" + note.id() + " &7" + format.format(new Date(note.createdAt())) + " &f" + note.author() + "&8: &7",
                            note.text());
                }
                if (notes.isEmpty()) {
                    Text.raw(sender, "&8  No notes yet. Add one: /notes " + account.displayName() + " add <text>");
                }
            });
        });
    }

    /** How many notes a player has; answers on the main thread. */
    public void count(UUID uuid, Consumer<Integer> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            int count = 0;
            try (PreparedStatement select = store.connection().prepareStatement(
                    "SELECT COUNT(*) FROM v3_staff_notes WHERE player_uuid = ?")) {
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    count = rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not count staff notes", exception);
            }
            int finalCount = count;
            plugin.getSpigotScheduler().runGlobal(() -> callback.accept(finalCount));
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            return List.of("add", "remove").stream().filter(o -> o.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
