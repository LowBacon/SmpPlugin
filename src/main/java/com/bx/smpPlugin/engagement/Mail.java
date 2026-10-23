// SmpPlugin by LowBacon
package com.bx.smpPlugin.engagement;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.PlayerStore;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.managers.EconomyManager;
import com.bx.smpPlugin.managers.FeatureManager;
import com.bx.smpPlugin.models.PunishmentType;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class Mail implements CommandExecutor, TabCompleter, Listener {

    public static final String PERMISSION = "smpplugin.mail";

    private record Letter(long id, String sender, String text, long sentAt, boolean read) {
    }

    private final SmpPlugin plugin;
    private final PlayerStore store;
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    public Mail(SmpPlugin plugin, PlayerStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    private FileConfiguration config() {
        FileConfiguration extras = plugin.getConfigManager().getExtras();
        return extras == null ? new YamlConfiguration() : extras;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "GENERAL.PLAYERS-ONLY", "&cOnly players can do that.");
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            Text.send(player, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        String action = args.length == 0 ? "read" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "send" -> send(player, args);
            case "clear" -> clear(player);
            case "delete" -> delete(player, args);
            case "read", "inbox" -> read(player);
            default -> Text.send(player, "MAIL.USAGE", "&7/mail send <player> <message> &8| &7/mail read &8| &7/mail clear");
        }
        return true;
    }

    private void send(Player player, String[] args) {
        if (args.length < 3) {
            Text.send(player, "MAIL.USAGE", "&7/mail send <player> <message> &8| &7/mail read &8| &7/mail clear");
            return;
        }
        if (plugin.getPunishmentManager() != null
                && plugin.getPunishmentManager().getActiveRecord(player.getUniqueId(), PunishmentType.MUTE).isPresent()) {
            Text.send(player, "MAIL.MUTED", "&cYou can't send mail while you are muted.");
            return;
        }
        long cooldown = Math.max(0L, config().getLong("MAIL.SEND-COOLDOWN-SECONDS", 15L)) * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastSent.get(player.getUniqueId());
        if (last != null && now - last < cooldown && !PermissionUtils.has(player, "smpplugin.staff.chat.use")) {
            Text.send(player, "MAIL.COOLDOWN", "&7Wait &f{time} &7before sending more mail.",
                    "{time}", Text.duration((cooldown - (now - last)) / 1000L));
            return;
        }
        com.bx.smpPlugin.core.Lookup.account(plugin, player, args[1], account -> {
            if (account == null) {
                Text.send(player, "MAIL.UNKNOWN", "&cNo player called &f{player} &chas joined this server.", "{player}", args[1]);
            } else {
                send(player, account, args, now);
            }
        });
    }

    private void send(Player player, EconomyManager.AccountReference account, String[] args, long now) {
        UUID target = account.uuid();
        if (plugin.getIgnoreManager() != null && plugin.getIgnoreManager().isIgnoring(target, player.getUniqueId())) {
            Text.send(player, "MAIL.BLOCKED", "&cYou can't send mail to that player.");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        int maxLength = Math.max(10, config().getInt("MAIL.MAX-LENGTH", 200));
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        if (!PermissionUtils.has(player, "smpplugin.chat.color")) {
            // A full-width ampersand looks the same in chat but cannot start a colour code.
            text = text.replace('&', '＆').replace('§', '＆');
        }
        String message = text;
        int maxInbox = Math.max(1, config().getInt("MAIL.MAX-INBOX", 50));
        lastSent.put(player.getUniqueId(), now);
        String senderName = player.getName();
        plugin.getDatabaseManager().executeAsync(() -> {
            boolean full = countBlocking(target, false) >= maxInbox;
            if (!full) {
                try (PreparedStatement insert = store.connection().prepareStatement(
                        "INSERT INTO v3_mail (player_uuid, sender_name, mail_text, sent_at, read_flag) VALUES (?, ?, ?, ?, 0)")) {
                    insert.setString(1, target.toString());
                    insert.setString(2, senderName);
                    insert.setString(3, message);
                    insert.setLong(4, now);
                    insert.executeUpdate();
                } catch (SQLException exception) {
                    plugin.getLogger().log(Level.WARNING, "Could not save mail", exception);
                    full = true;
                }
            }
            boolean saved = !full;
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (!saved) {
                    Text.send(player, "MAIL.FULL", "&c{player}'s mailbox is full.", "{player}", account.displayName());
                    return;
                }
                Text.send(player, "MAIL.SENT", "&aMail sent to &f{player}&a.", "{player}", account.displayName());
                Player online = Bukkit.getPlayer(target);
                if (online != null) {
                    plugin.getSpigotScheduler().runEntity(online, () -> notifyNew(online, senderName));
                }
            });
        });
    }

    private void notifyNew(Player player, String from) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 0.8F);
        Text.line(player, Text.prefix(), Text.get("MAIL.NEW", "&7New mail from &f{player}&7! ", "{player}", from),
                Text.button(Text.get("MAIL.READ-BUTTON", "&e&l[Read]"), "mail read", Text.get("MAIL.READ-HOVER", "&7Open your mailbox")));
    }

    private void read(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Letter> letters = new ArrayList<>();
            try (PreparedStatement select = store.connection().prepareStatement(
                    "SELECT mail_id, sender_name, mail_text, sent_at, read_flag FROM v3_mail WHERE player_uuid = ? ORDER BY sent_at DESC")) {
                select.setString(1, uuid.toString());
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next() && letters.size() < 20) {
                        letters.add(new Letter(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getInt(5) != 0));
                    }
                }
                try (PreparedStatement mark = store.connection().prepareStatement(
                        "UPDATE v3_mail SET read_flag = 1 WHERE player_uuid = ?")) {
                    mark.setString(1, uuid.toString());
                    mark.executeUpdate();
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read mail", exception);
            }
            plugin.getSpigotScheduler().runEntity(player, () -> show(player, letters));
        });
    }

    private void show(Player player, List<Letter> letters) {
        if (!player.isOnline()) {
            return;
        }
        if (letters.isEmpty()) {
            Text.send(player, "MAIL.EMPTY", "&7Your mailbox is empty.");
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("d MMM HH:mm", Locale.ROOT);
        Text.raw(player, Text.get("MAIL.HEADER", "&6&l✉ Mailbox &7({count})", "{count}", Integer.toString(letters.size())));
        for (Letter letter : letters) {
            Text.line(player,
                    Text.button("&c[✖] ", "mail delete " + letter.id(), Text.get("MAIL.DELETE-HOVER", "&7Delete this mail")),
                    (letter.read() ? "&8" : "&e● ") + "&7" + format.format(new Date(letter.sentAt())) + " &f" + letter.sender() + "&8: &7",
                    letter.text());
        }
        Text.line(player, Text.button(Text.get("MAIL.CLEAR-BUTTON", "&c[Clear all]"), "mail clear",
                Text.get("MAIL.CLEAR-HOVER", "&7Delete every mail")));
    }

    private void delete(Player player, String[] args) {
        long id;
        try {
            id = Long.parseLong(args.length > 1 ? args[1] : "");
        } catch (NumberFormatException ignored) {
            return;
        }
        UUID uuid = player.getUniqueId();
        plugin.getDatabaseManager().executeAsync(() -> {
            try (PreparedStatement delete = store.connection().prepareStatement(
                    "DELETE FROM v3_mail WHERE mail_id = ? AND player_uuid = ?")) {
                delete.setLong(1, id);
                delete.setString(2, uuid.toString());
                delete.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not delete mail", exception);
            }
            plugin.getSpigotScheduler().runEntity(player, () -> Text.send(player, "MAIL.DELETED", "&7Mail deleted."));
        });
    }

    private void clear(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getDatabaseManager().executeAsync(() -> {
            try (PreparedStatement delete = store.connection().prepareStatement("DELETE FROM v3_mail WHERE player_uuid = ?")) {
                delete.setString(1, uuid.toString());
                delete.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not clear mail", exception);
            }
            plugin.getSpigotScheduler().runEntity(player, () -> Text.send(player, "MAIL.CLEARED", "&7Your mailbox is empty now."));
        });
    }

    private int countBlocking(UUID uuid, boolean unreadOnly) {
        try (PreparedStatement count = store.connection().prepareStatement(
                "SELECT COUNT(*) FROM v3_mail WHERE player_uuid = ?" + (unreadOnly ? " AND read_flag = 0" : ""))) {
            count.setString(1, uuid.toString());
            try (ResultSet rs = count.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not count mail", exception);
            return 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!store.isReady() || !plugin.getFeatureManager().isEnabled(FeatureManager.Feature.MAIL)
                || !PermissionUtils.has(player, PERMISSION)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        plugin.getDatabaseManager().executeAsync(() -> {
            int unread = countBlocking(uuid, true);
            if (unread <= 0) {
                return;
            }
            plugin.getSpigotScheduler().runEntityLater(player, () -> com.bx.smpPlugin.worlds.SmpArrival.run(plugin, player,
                    "mail", () -> {
                        if (player.isOnline()) {
                            Text.line(player, Text.prefix(),
                                    Text.get("MAIL.UNREAD", "&7You have &e{count} &7unread mail. ", "{count}", Integer.toString(unread)),
                                    Text.button(Text.get("MAIL.READ-BUTTON", "&e&l[Read]"), "mail read",
                                            Text.get("MAIL.READ-HOVER", "&7Open your mailbox")));
                        }
                    }), 100L);
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("read", "send", "clear").stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if ((!(sender instanceof Player viewer) || viewer.canSee(online))
                        && online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
