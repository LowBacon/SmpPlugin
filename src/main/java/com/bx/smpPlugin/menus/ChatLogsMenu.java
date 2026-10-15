package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.PlayerLogsManager;
import com.bx.smpPlugin.models.PlayerLogEntry;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Staff browser for logged chat, newest first. Pages are read on the database thread so a long
 * history never stalls the server; the menu shows a loading pane until the page arrives.
 */
public class ChatLogsMenu extends BaseMenu {

    private static final int PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int TYPE_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int LORE_LINE_LENGTH = 40;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final UUID targetUuid;
    private final String targetName;
    private boolean teamChat;
    private int page;
    private int total;
    private int loadToken;

    /** Pass a null uuid to browse everyone's chat instead of one player's. */
    public ChatLogsMenu(SmpPlugin plugin, UUID targetUuid, String targetName) {
        super(plugin, targetUuid == null ? "&8Chat Log" : "&8Chat Log: " + targetName, 54);
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    @Override
    public void build(Player player) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        set(22, ItemUtils.createItem(Material.CLOCK, "&7Loading...", List.of("&8Reading the chat log.")));
        renderControls();

        int token = ++loadToken;
        int requestedPage = page;
        String type = currentType();
        plugin.getDatabaseManager().executeAsync(() -> {
            int count = plugin.getDatabaseManager().getLogsByTypeCount(targetUuid, type);
            int pages = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
            int safePage = Math.min(requestedPage, pages - 1);
            List<PlayerLogEntry> logs = plugin.getDatabaseManager()
                    .getLogsByType(targetUuid, type, PER_PAGE, safePage * PER_PAGE);
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (token != loadToken || player.getOpenInventory().getTopInventory().getHolder() != this) {
                    return;
                }
                total = count;
                page = safePage;
                render(logs);
            });
        });
    }

    private void render(List<PlayerLogEntry> logs) {
        clear();
        fill(Material.GRAY_STAINED_GLASS_PANE);
        if (logs.isEmpty()) {
            set(22, ItemUtils.createItem(Material.BARRIER, "&cNo messages",
                    List.of(targetUuid == null
                            ? "&7Nothing has been said here yet."
                            : "&7" + targetName + " has not said anything here yet.")));
        }
        for (int i = 0; i < logs.size() && i < PER_PAGE; i++) {
            set(i, createChatItem(logs.get(i)));
        }
        renderControls();
    }

    private void renderControls() {
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        set(PREVIOUS_SLOT, page > 0
                ? ItemUtils.createItem(Material.ARROW, "&aPrevious page", List.of("&7Go to page " + page + "."))
                : ItemUtils.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE));
        set(NEXT_SLOT, page + 1 < pages
                ? ItemUtils.createItem(Material.ARROW, "&aNext page", List.of("&7Go to page " + (page + 2) + "."))
                : ItemUtils.createPlaceholder(Material.GRAY_STAINED_GLASS_PANE));
        set(TYPE_SLOT, ItemUtils.createItem(teamChat ? Material.CYAN_DYE : Material.LIME_DYE,
                "&eShowing: " + (teamChat ? "&bTeam chat" : "&aPublic chat"),
                List.of("&7Click to switch to " + (teamChat ? "public" : "team") + " chat.")));
        set(INFO_SLOT, ItemUtils.createItem(Material.BOOK, "&fPage " + (page + 1) + "&7/&f" + pages,
                List.of("&7" + total + " message" + (total == 1 ? "" : "s"), "", "&cClick to close")));
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
        } else if (slot == NEXT_SLOT && page + 1 < pages) {
            page++;
        } else if (slot == TYPE_SLOT) {
            teamChat = !teamChat;
            page = 0;
            total = 0;
        } else if (slot == INFO_SLOT) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
            player.closeInventory();
            return;
        } else {
            return;
        }
        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.PAGE-TURN"));
        build(player);
    }

    private String currentType() {
        return teamChat ? PlayerLogsManager.TEAM_CHAT_TYPE : PlayerLogsManager.PUBLIC_CHAT_TYPE;
    }

    private ItemStack createChatItem(PlayerLogEntry entry) {
        String name = entry.playerName() == null || entry.playerName().isBlank() ? "Unknown" : entry.playerName();
        List<String> lore = new ArrayList<>();
        lore.add("&7" + TIME.format(Instant.ofEpochMilli(entry.timestamp())));
        lore.add("");
        for (String line : wrap(entry.details())) {
            lore.add("&f" + line);
        }
        return ItemUtils.createPlayerHead(Bukkit.getOfflinePlayer(entry.playerUuid()), "&e" + name, lore);
    }

    /** Breaks a message over several lore lines so long ones stay readable. */
    static List<String> wrap(String message) {
        List<String> lines = new ArrayList<>();
        if (message == null || message.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : message.trim().split("\\s+")) {
            while (word.length() > LORE_LINE_LENGTH) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, LORE_LINE_LENGTH));
                word = word.substring(LORE_LINE_LENGTH);
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > LORE_LINE_LENGTH) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
