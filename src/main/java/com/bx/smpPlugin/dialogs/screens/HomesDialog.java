// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import com.bx.smpPlugin.models.Home;
import com.bx.smpPlugin.models.Team;
import org.bukkit.Material;
import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.entity.Player;
import com.bx.smpPlugin.utils.SoundUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HomesDialog extends DialogScreen {

    private static final String LIST = "HOMES_DIALOG";
    private static final String CREATE = "CREATE_HOME_DIALOG";
    private static final String MANAGE = "HOME_MANAGE_DIALOG";
    private static final String ICON = "CHANGE_HOME_ICON_DIALOG";
    private static final String RENAME = "RENAME_HOME_DIALOG";
    private static final String DELETE = "DELETE_HOME_CONFIRM_DIALOG";
    private static final String TEAM_SLOT = "team";

    public HomesDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    private static final int[] DEFAULT_TIERS = {3, 9, 27, 90};

    public boolean open(Player player) {
        return open(player, 0);
    }

    private int[] tiers() {
        List<Integer> list = config.integerList(LIST + ".TIERS");
        if (list != null && !list.isEmpty()) {
            return list.stream().mapToInt(Integer::intValue).toArray();
        }
        return DEFAULT_TIERS;
    }

    private boolean open(Player player, int page) {
        List<Home> homes = sortedHomes(player);
        int maxHomes = plugin.getHomeManager().visibleHomeSlots(player);
        int[] tiers = tiers();
        int tierIndex = Math.max(0, Math.min(page, tiers.length - 1));
        int totalSlots = Math.max(config.integer(LIST + ".TOTAL-SLOTS", 90), homes.size());
        int shown = Math.min(totalSlots, tiers[tierIndex]);

        Map<String, String> tokens = DialogConfig.tokens("player", player.getName());
        int width = config.buttonWidth(LIST, 65);
        int columns = (tierIndex == 0)
                ? config.columns(LIST, 4)
                : Math.max(1, config.integer(LIST + ".EXPANDED-COLUMNS", 6));

        DialogFactory.Screen screen = screen(player)
                .title(config.string(LIST + ".TITLE", "Homes", tokens))
                .externalTitle(config.string(LIST + ".EXTERNAL-TITLE", null, tokens))
                .columns(columns)
                .item(config.string(LIST + ".ITEM", "WHITE_BED"))
                .text(config.string(LIST + ".TEXT", null, tokens));

        Team team = plugin.getFeatureManager().isEnabled(com.bx.smpPlugin.managers.FeatureManager.Feature.TEAMS)
                ? plugin.getTeamManager().getTeam(player)
                : null;
        if (team != null) {
            screen.button(
                    config.string(LIST + ".TEAM-HOME-LABEL", "Team Home"),
                    null,
                    width,
                    DialogActions.HOME_TEAM
            );
        }

        String entryFormat = config.string(LIST + ".ENTRY-FORMAT", "%icon% %name%");
        String plainFormat = config.string(LIST + ".ENTRY-FORMAT-NO-ICON", "%name%");
        String tooltip = config.string(LIST + ".HOME-TOOLTIP", "%name%\nClick to manage");
        for (int index = 0; index < shown; index++) {
            if (index < homes.size()) {
                Home home = homes.get(index);
                String icon = iconTagOf(home);
                Map<String, String> entryTokens = DialogConfig.tokens(
                        "name", home.getName(),
                        "slot", String.valueOf(index + 1),
                        "icon", icon
                );
                screen.button(
                        DialogConfig.apply(icon.isEmpty() ? plainFormat : entryFormat, entryTokens),
                        DialogConfig.apply(tooltip, entryTokens),
                        width,
                        DialogActions.HOME_MANAGE + index
                );
            } else if (index < maxHomes) {
                screen.button(
                        config.string(LIST + ".NEW-HOME-LABEL", "New Home"),
                        config.string(LIST + ".NEW-HOME-TOOLTIP", "Click to set new home"),
                        width,
                        DialogActions.HOME_CREATE_PROMPT
                );
            } else {
                screen.inertButton(
                        config.string(LIST + ".LOCKED-LABEL", "&cLocked"),
                        lockedTooltip(index, tiers, tokens),
                        width
                );
            }
        }

        if (shown < totalSlots && tierIndex < tiers.length - 1) {
            screen.button(
                    config.string(LIST + ".SHOW-MORE-LABEL", "Show More"),
                    null,
                    width,
                    DialogActions.HOMES_PAGE + (tierIndex + 1)
            );
        }
        screen.exit(config.buttonsOf(LIST, tokens).stream().findFirst().orElse(null));
        return show(player, screen.build());
    }

    String lockedTooltip(int index, int[] tiers, Map<String, String> tokens) {
        String fallback = config.string(LIST + ".LOCKED-TOOLTIP", "&7Get a higher rank for more home slots", tokens);
        for (int tier = 0; tiers != null && tier < tiers.length; tier++) {
            if (index < tiers[tier]) {
                return config.string(LIST + ".LOCKED-TOOLTIP-" + (tier + 1), fallback, tokens);
            }
        }
        return fallback;
    }

    private boolean openCreate(Player player) {
        Map<String, String> tokens = DialogConfig.tokens("player", player.getName());
        return show(player, screen(player)
                .title(config.string(CREATE + ".TITLE", "Create Home", tokens))
                .externalTitle(config.string(CREATE + ".EXTERNAL-TITLE", null, tokens))
                .columns(config.columns(CREATE, 2))
                .item(config.string(CREATE + ".ITEM", "WHITE_BED"))
                .inputs(config.inputs(CREATE, tokens))
                .buttons(config.buttonsOf(CREATE, tokens))
                .build());
    }

    private boolean openManage(Player player, int index) {
        Home home = homeAt(player, index);
        if (home == null) {
            return open(player);
        }
        Map<String, String> tokens = homeTokens(home, index);
        return show(player, screen(player)
                .title(config.string(MANAGE + ".TITLE", "%name%", tokens))
                .externalTitle(config.string(MANAGE + ".EXTERNAL-TITLE", null, tokens))
                .columns(config.columns(MANAGE, 2))
                .item(iconOf(home))
                .buttons(config.buttonsOf(MANAGE, tokens))
                .build());
    }

    private boolean openRename(Player player, int index) {
        Home home = homeAt(player, index);
        if (home == null) {
            return open(player);
        }
        Map<String, String> tokens = homeTokens(home, index);
        return show(player, screen(player)
                .title(config.string(RENAME + ".TITLE", "Rename Home", tokens))
                .externalTitle(config.string(RENAME + ".EXTERNAL-TITLE", null, tokens))
                .columns(config.columns(RENAME, 2))
                .item(config.string(RENAME + ".ITEM", "WHITE_BED"))
                .inputs(config.inputs(RENAME, tokens))
                .buttons(config.buttonsOf(RENAME, tokens))
                .build());
    }

    private boolean openDeleteConfirm(Player player, int index) {
        Home home = homeAt(player, index);
        if (home == null) {
            return open(player);
        }
        Map<String, String> tokens = homeTokens(home, index);
        return show(player, screen(player)
                .title(config.string(DELETE + ".TITLE", "Delete Home?", tokens))
                .externalTitle(config.string(DELETE + ".EXTERNAL-TITLE", null, tokens))
                .columns(config.columns(DELETE, 2))
                .item(config.string(DELETE + ".ITEM", "RED_BED"))
                .text(home.getName())
                .buttons(config.buttonsOf(DELETE, tokens))
                .build());
    }

    private boolean openIconPicker(Player player, int index, String query) {
        Home home = homeAt(player, index);
        if (home == null) {
            return open(player);
        }
        DialogSession session = session(player);
        session.setHomeSlot(String.valueOf(index));
        session.setIconQuery(query);

        Map<String, String> tokens = homeTokens(home, index);
        int width = config.buttonWidth(ICON, 96);
        DialogFactory.Screen screen = screen(player)
                .title(config.string(ICON + ".TITLE", "Choose Icon", tokens))
                .externalTitle(config.string(ICON + ".EXTERNAL-TITLE", null, tokens))
                .columns(config.columns(ICON, 4))
                .item(iconOf(home))
                .input(new DialogConfig.InputSpec(
                        "icon_query",
                        config.string(ICON + ".INPUT-LABEL", "Search"),
                        200,
                        32,
                        query,
                        List.of()
                ));

        // The three controls lead the grid rather than sitting under it: the results scroll, and an
        // exit action is pinned outside that scroll area where it would never be seen.
        screen.button(config.string(ICON + ".SEARCH-LABEL", "Search"), null, width,
                DialogActions.HOME_ICON_SEARCH + index);
        screen.button(config.string(ICON + ".DEFAULT-LABEL", "Default"), null, width,
                DialogActions.HOME_ICON_DEFAULT + index);
        screen.button(config.string(ICON + ".BACK-LABEL", "Back"), null, width,
                DialogActions.HOME_MANAGE + index);

        int resultWidth = config.integer(ICON + ".RESULT-WIDTH", 128);
        for (Material material : searchIcons(query, config.integer(ICON + ".MAX-RESULTS", 2000))) {
            String sprite = QuickBuyItemDialog.getSpriteTag(material);
            String label = "&f" + prettify(material);
            screen.button(
                    sprite.isEmpty() ? label : sprite + " " + label,
                    null,
                    resultWidth,
                    DialogActions.HOME_ICON_SET + index + "_" + material.name().toLowerCase(Locale.ROOT)
            );
        }
        return show(player, screen.build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.HOMES_MENU.equals(action) || DialogActions.HOMES.equals(action)) {
            open(player);
            return true;
        }
        if (DialogActions.HOME_CREATE_PROMPT.equals(action)) {
            openCreate(player);
            return true;
        }
        if (DialogActions.HOME_CREATE_EXECUTE.equals(action)) {
            createHome(player, input(response, "home_name"));
            return true;
        }
        if (DialogActions.HOME_TEAM.equals(action)) {
            click(player);
            runCommand(player, "team home");
            return true;
        }

        String page = DialogActions.argument(action, DialogActions.HOMES_PAGE);
        if (page != null) {
            SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.PAGE-TURN"));
            open(player, parseIndex(page, 0));
            return true;
        }

        String iconDefault = DialogActions.argument(action, DialogActions.HOME_ICON_DEFAULT);
        if (iconDefault != null) {
            setIcon(player, parseIndex(iconDefault, -1), "");
            return true;
        }

        String iconSet = DialogActions.argument(action, DialogActions.HOME_ICON_SET);
        if (iconSet != null) {
            int separator = iconSet.indexOf('_');
            if (separator > 0) {
                setIcon(player, parseIndex(iconSet.substring(0, separator), -1), iconSet.substring(separator + 1));
            } else {
                open(player);
            }
            return true;
        }

        String iconSearch = DialogActions.argument(action, DialogActions.HOME_ICON_SEARCH);
        if (iconSearch != null) {
            openIconPicker(player, parseIndex(iconSearch, -1), input(response, "icon_query"));
            return true;
        }

        String iconPrompt = DialogActions.argument(action, DialogActions.HOME_ICON_PROMPT);
        if (iconPrompt != null) {
            openIconPicker(player, parseIndex(iconPrompt, -1), null);
            return true;
        }

        String renameExecute = DialogActions.argument(action, DialogActions.HOME_RENAME_EXECUTE);
        if (renameExecute != null) {
            renameHome(player, parseIndex(renameExecute, -1), input(response, "new_name"));
            return true;
        }

        String renamePrompt = DialogActions.argument(action, DialogActions.HOME_RENAME_PROMPT);
        if (renamePrompt != null) {
            openRename(player, parseIndex(renamePrompt, -1));
            return true;
        }

        String deleteExecute = DialogActions.argument(action, DialogActions.HOME_DELETE_EXECUTE);
        if (deleteExecute != null) {
            deleteHome(player, parseIndex(deleteExecute, -1));
            return true;
        }

        String deletePrompt = DialogActions.argument(action, DialogActions.HOME_DELETE_PROMPT);
        if (deletePrompt != null) {
            openDeleteConfirm(player, parseIndex(deletePrompt, -1));
            return true;
        }

        String teleport = DialogActions.argument(action, DialogActions.HOME_TELEPORT);
        if (teleport != null) {
            teleportHome(player, parseIndex(teleport, -1));
            return true;
        }

        String manage = DialogActions.argument(action, DialogActions.HOME_MANAGE);
        if (manage != null) {
            openManage(player, parseIndex(manage, -1));
            return true;
        }
        return false;
    }

    private void createHome(Player player, String name) {
        if (name == null) {
            openCreate(player);
            return;
        }
        click(player);
        if (!com.bx.smpPlugin.managers.HomeManager.isValidHomeName(name)) {
            message(player, plugin.getConfigManager().getMessage("HOME.INVALID-NAME"));
            openCreate(player);
            return;
        }
        if (plugin.getHomeManager().isInArena(player)) {
            message(player, "&cYou can't set a home inside a duel or FFA arena.");
            open(player);
            return;
        }
        if (plugin.getHomeManager().getHome(player.getUniqueId(), name) != null) {
            message(player, plugin.getConfigManager().getMessage("HOME.ALREADY-EXISTS"));
            openCreate(player);
            return;
        }
        if (!plugin.getHomeManager().canSetHome(player) || !plugin.getHomeManager().setHome(player, name)) {
            message(player, "&cYou cannot create another home right now.");
            open(player);
            return;
        }
        message(player, plugin.getConfigManager().getMessage("HOME.SET"));
        open(player);
    }

    private void renameHome(Player player, int index, String newName) {
        Home home = homeAt(player, index);
        if (home == null || newName == null) {
            openRename(player, index);
            return;
        }
        click(player);
        if (!com.bx.smpPlugin.managers.HomeManager.isValidHomeName(newName)) {
            message(player, plugin.getConfigManager().getMessage("HOME.INVALID-NAME"));
            openRename(player, index);
            return;
        }
        if (!plugin.getHomeManager().renameHome(player.getUniqueId(), home.getName(), newName)) {
            message(player, plugin.getConfigManager().getMessage("HOME.ALREADY-EXISTS"));
            openRename(player, index);
            return;
        }
        message(player, plugin.getConfigManager().getMessage("HOME.RENAME-SUCCESS", "{name}", newName));
        open(player);
    }

    private void deleteHome(Player player, int index) {
        Home home = homeAt(player, index);
        if (home == null) {
            open(player);
            return;
        }
        click(player);
        if (plugin.getHomeManager().deleteHome(player.getUniqueId(), home.getName())) {
            message(player, plugin.getConfigManager().getMessage("HOME.DELETED"));
        }
        open(player);
    }

    private void teleportHome(Player player, int index) {
        Home home = homeAt(player, index);
        if (home == null) {
            open(player);
            return;
        }
        click(player);
        com.bx.smpPlugin.dialogs.DialogSupport.close(player);
        if (home.getLocation() == null || home.getLocation().getWorld() == null) {
            message(player, "&cThat home's world is not loaded right now.");
            return;
        }
        plugin.getTeleportManager().queue(player, home.getLocation(), "HOME", null);
    }

    private void setIcon(Player player, int index, String materialName) {
        Home home = homeAt(player, index);
        if (home == null) {
            open(player);
            return;
        }
        Material material = materialName == null || materialName.isBlank()
                ? null
                : Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        plugin.getHomeManager().setIcon(player.getUniqueId(), home.getName(), material == null ? "" : material.name());
        click(player);
        openManage(player, index);
    }

    private List<Material> searchIcons(String query, int limit) {
        List<Material> results = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (Material material : Material.values()) {
            if (results.size() >= Math.max(1, limit)) {
                break;
            }
            if (material.isLegacy() || !material.isItem() || material.isAir()) {
                continue;
            }
            if (needle.isEmpty() || material.name().contains(needle)) {
                results.add(material);
            }
        }
        return results;
    }

    private List<Home> sortedHomes(Player player) {
        List<Home> homes = new ArrayList<>(plugin.getHomeManager().getHomes(player.getUniqueId()));
        homes.sort(Comparator.comparingLong(Home::getCreatedAt));
        return homes;
    }

    private Home homeAt(Player player, int index) {
        if (index < 0) {
            return null;
        }
        List<Home> homes = sortedHomes(player);
        return index < homes.size() ? homes.get(index) : null;
    }

    private Map<String, String> homeTokens(Home home, int index) {
        return DialogConfig.tokens(
                "name", home.getName(),
                "slot", String.valueOf(index),
                "number", String.valueOf(index + 1)
        );
    }

    private String iconOf(Home home) {
        return home.hasIcon() ? home.getIcon() : "WHITE_BED";
    }

    private String iconTagOf(Home home) {
        Material material = Material.matchMaterial(iconOf(home).toUpperCase(Locale.ROOT));
        return material == null ? "" : QuickBuyItemDialog.getSpriteTag(material);
    }

    private static int parseIndex(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }

    private static String prettify(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return builder.toString();
    }
}
