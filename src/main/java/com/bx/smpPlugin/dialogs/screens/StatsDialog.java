package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.dialogs.DialogText;
import com.bx.smpPlugin.managers.CurrencyManager;
import com.bx.smpPlugin.managers.HideManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.Team;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /stats as a pop-up: your own stats straight away, in a few short lines, with a button to look
 * up someone else and one for the leaderboards. That is the whole screen.
 *
 * <p>A player who is not online is read on the database thread and the sheet opens when the data
 * arrives. Disguised players appear under their alias and without their skin, unless the viewer
 * may see through disguises.
 */
public final class StatsDialog extends DialogScreen {

    private static final String SEARCH = "STATS_SEARCH_DIALOG";
    private static final String SHEET = "PLAYER_STATS_DIALOG";
    private static final String NOT_FOUND = "&cNo player by that name has joined this server.";

    public StatsDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    /** /stats: your own sheet. */
    public boolean open(Player player) {
        openSheet(player, player.getUniqueId(), plugin.getHideManager().visibleName(player, player));
        return true;
    }

    /** /stats &lt;name&gt;: the command has already resolved the name (aliases included). */
    public boolean openFor(Player player, UUID target, String name) {
        if (target == null) {
            return open(player);
        }
        openSheet(player, target, name);
        return true;
    }

    private boolean openSearch(Player player) {
        Map<String, String> tokens = DialogConfig.tokens("player", player.getName());
        return show(player, screen(player)
                .title(config.string(SEARCH + ".TITLE", "Search Player", tokens))
                .externalTitle(config.string(SEARCH + ".EXTERNAL-TITLE", null, tokens))
                .afterAction(config.string(SEARCH + ".AFTER-ACTION", "NONE"))
                .columns(config.columns(SEARCH, 2))
                .text(config.string(SEARCH + ".DESCRIPTION", "Type a player's name", tokens))
                .inputs(config.inputs(SEARCH, tokens))
                .buttons(config.buttonsOf(SEARCH, tokens))
                .build());
    }

    // ------------------------------------------------------------------ stat sheet

    /** Remembers who was asked for, so a slow load that loses the race is thrown away. */
    private void openSheet(Player viewer, UUID target, String fallbackName) {
        session(viewer).setStatsTarget(target.toString());
        PlayerData live = plugin.getPlayerDataManager().get(target);
        if (live != null) {
            showSheet(viewer, target, nameOf(viewer, target, live, fallbackName), live);
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            PlayerData data = plugin.getDatabaseManager().loadPlayer(target);
            plugin.getSpigotScheduler().runEntity(viewer, () -> {
                if (!viewer.isOnline() || !target.toString().equals(session(viewer).getStatsTarget())) {
                    return;
                }
                if (data == null) {
                    message(viewer, plugin.getConfigManager()
                            .getMessageOrDefault("STATS.PLAYER-NOT-FOUND", "&cThat player has no stats yet."));
                    return;
                }
                showSheet(viewer, target, nameOf(viewer, target, data, fallbackName), data);
            });
        });
    }

    private void showSheet(Player viewer, UUID target, String name, PlayerData data) {
        Map<String, String> tokens = statTokens(viewer, target, name, data);
        DialogFactory.Screen screen = screen(viewer)
                .title(config.string(SHEET + ".TITLE", "%player%", tokens))
                .externalTitle(config.string(SHEET + ".EXTERNAL-TITLE", null, tokens))
                // CLOSE, so the Close button closes; the other buttons open their own screen anyway.
                .afterAction(config.string(SHEET + ".AFTER-ACTION", "CLOSE"))
                .columns(config.columns(SHEET, 2));

        boolean skin = showSkin(viewer, target);
        Component head = skin ? DialogPlayerHeads.component(plugin, target) : Component.empty();
        Component header = DialogText.of(config.string(SHEET + ".HEADER", "&f&l%player% &r%status%", tokens), viewer);
        if (!Component.empty().equals(head)) {
            screen.text(Component.empty().append(head).append(Component.space()).append(header));
        } else {
            if (skin) {
                screen.item(ItemUtils.createPlayerHead(Bukkit.getOfflinePlayer(target), name, List.of()));
            }
            screen.text(header);
        }

        int textWidth = config.integer(SHEET + ".TEXT-WIDTH", 220);
        for (String line : config.stringList(SHEET + ".STATS-FORMAT")) {
            String filled = DialogConfig.apply(line, tokens);
            if (!filled.isBlank()) {
                screen.text(filled, textWidth);
            }
        }
        screen.buttons(config.buttonsOf(SHEET, tokens));
        String closeLabel = config.string(SHEET + ".CLOSE-LABEL", "Close");
        if (closeLabel != null && !closeLabel.isBlank()) {
            screen.exit(new DialogConfig.ButtonSpec(closeLabel, null, DialogConfig.clampWidth(config.integer(SHEET + ".CLOSE-WIDTH", 200)),
                    null, null));
        }
        show(viewer, screen.build());
    }

    private Map<String, String> statTokens(Player viewer, UUID target, String name, PlayerData data) {
        CurrencyManager currency = plugin.getCurrencyManager();
        boolean online = isVisiblyOnline(viewer, target);
        Team team = plugin.getTeamManager() == null ? null : plugin.getTeamManager().getTeam(target);
        return DialogConfig.tokens(
                "player", name,
                "player_uuid", target.toString(),
                "status", online
                        ? config.string(SHEET + ".ONLINE-STATUS", "&a● Online")
                        : config.string(SHEET + ".OFFLINE-STATUS", "&7● Offline"),
                "team", team == null ? config.string(SHEET + ".NO-TEAM", "&7none") : team.getName().toUpperCase(Locale.ROOT),
                "money", currency.formatCompactAmount(CurrencyManager.CurrencyType.MONEY, data.getMoney()),
                "shards", currency.formatCompactAmount(CurrencyManager.CurrencyType.SHARDS, data.getShards()),
                "kills", NumberUtils.format(data.getKills()),
                "deaths", NumberUtils.format(data.getDeaths()),
                "kd", kd(data.getKills(), data.getDeaths()),
                "playtime", NumberUtils.formatTime(data.getTotalPlaytimeSeconds()),
                "blocks_broken", NumberUtils.formatNice(data.getBlocksBroken()),
                "blocks_placed", NumberUtils.formatNice(data.getBlocksPlaced()),
                "mobs_killed", NumberUtils.formatNice(data.getMobsKilled()),
                "kill_streak", NumberUtils.format(data.getKillStreak()),
                "highest_kill_streak", NumberUtils.format(data.getHighestKillStreak())
        );
    }

    // ------------------------------------------------------------------ actions

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.STATS_MENU.equals(action)) {
            open(player);
            return true;
        }
        if (DialogActions.STATS_ADD_PROMPT.equals(action)) {
            openSearch(player);
            return true;
        }
        if (DialogActions.STATS_ADD_EXECUTE.equals(action)) {
            search(player, response);
            return true;
        }
        String view = DialogActions.argument(action, DialogActions.STATS_VIEW);
        if (view != null && DialogActions.argument(action, DialogActions.STATS_VIEW_FULL) == null) {
            UUID target = parseUuid(view);
            if (target == null) {
                open(player);
            } else {
                openSheet(player, target, null);
            }
            return true;
        }
        return false;
    }

    /**
     * Looks up a typed name. Online players (by alias for disguised ones) are found at once; anyone
     * else is looked up on the database thread.
     */
    private void search(Player player, DialogResponseView response) {
        String name = input(response, "player_name");
        if (name == null) {
            openSearch(player);
            return;
        }
        HideManager hide = plugin.getHideManager();
        Player online = hide.findOnlinePlayer(player, name);
        if (online != null) {
            openSheet(player, online.getUniqueId(), hide.visibleName(player, online));
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            UUID uuid = hide.findKnownPlayerUuid(player, name);
            String known = uuid == null ? null : plugin.getDatabaseManager().getLastKnownUsername(uuid);
            plugin.getSpigotScheduler().runEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (uuid == null) {
                    message(player, plugin.getConfigManager().getMessageOrDefault("STATS.PLAYER-NOT-FOUND", NOT_FOUND));
                    openSearch(player);
                    return;
                }
                openSheet(player, uuid, hide.publicName(uuid, known == null || known.isBlank() ? name : known));
            });
        });
    }

    // ------------------------------------------------------------------ names and disguises

    private String nameOf(Player viewer, UUID target, PlayerData data, String fallback) {
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            return plugin.getHideManager().visibleName(viewer, online);
        }
        String stored = fallback != null && !fallback.isBlank() ? fallback
                : data != null && data.getUsername() != null ? data.getUsername() : target.toString().substring(0, 8);
        return plugin.getHideManager().publicName(target, stored);
    }

    /** A disguised player's skin would give them away, so only staff who can see through it get it. */
    private boolean showSkin(Player viewer, UUID target) {
        HideManager hide = plugin.getHideManager();
        return hide == null || !hide.isHidden(target) || hide.canSeeRealIdentity(viewer);
    }

    private boolean isVisiblyOnline(Player viewer, UUID target) {
        Player online = Bukkit.getPlayer(target);
        return online != null && viewer.canSee(online);
    }

    static String kd(int kills, int deaths) {
        double ratio = deaths <= 0 ? kills : kills / (double) deaths;
        return String.format(Locale.ROOT, "%.2f", ratio);
    }
}
