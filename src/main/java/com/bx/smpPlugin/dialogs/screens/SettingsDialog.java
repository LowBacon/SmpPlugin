package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Settings, split into the categories declared in {@code dialog.yml}.
 *
 * <p>The category screen is rebuilt after every toggle so the labels — which are just
 * {@code %economy_setting_*%} placeholders — show the value that was just chosen.
 */
public final class SettingsDialog extends DialogScreen {

    private static final String PATH = "SETTINGS_DIALOG";
    private static final Pattern ITEM_TAG = Pattern.compile("(?i)<item:[^>]+>\\s*");

    public SettingsDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    /** Opens the category picker. */
    public boolean open(Player player) {
        Map<String, String> tokens = DialogConfig.tokens("player", player.getName());
        int defaultWidth = DialogConfig.clampWidth(config.integer(PATH + ".CATEGORIES-BUTTON-WIDTH", 150));
        DialogFactory.Screen screen = screen(player)
                .title(config.string(PATH + ".TITLE", "Settings", tokens))
                .externalTitle(config.string(PATH + ".EXTERNAL-TITLE", null, tokens))
                .afterAction(config.string(PATH + ".AFTER-ACTION", "NONE"))
                .columns(config.integer(PATH + ".CATEGORIES-COLUMNS", 2))
                .text(config.string(PATH + ".TEXT", null, tokens))
                .item(config.string(PATH + ".ITEM", null));

        for (Category category : categories()) {
            int width = category.width() > 0 ? category.width() : defaultWidth;
            screen.button(category.label(), null, width, DialogActions.SETTINGS_CATEGORY + category.id());
        }
        String backLabel = config.string(PATH + ".BACK-LABEL", null);
        if (backLabel != null && !backLabel.isBlank()) {
            screen.button(
                    backLabel,
                    null,
                    defaultWidth,
                    config.string(PATH + ".BACK-ACTION", DialogActions.NAMESPACE + ':' + DialogActions.MAIN_MENU)
            );
        }
        return show(player, screen.build());
    }

    private boolean openCategory(Player player, String id) {
        Category category = categories().stream()
                .filter(entry -> entry.id().equals(DialogActions.sanitise(id)))
                .findFirst()
                .orElse(null);
        if (category == null) {
            return open(player);
        }

        if ("general".equals(category.id()) && plugin.getPlayerSettingToggles() != null
                && !plugin.getPlayerSettingToggles().auctionCacheReady(player)) {
            plugin.getPlayerSettingToggles().prefetchAuction(player, () -> openCategory(player, category.id()));
            return true;
        }

        Map<String, String> tokens = DialogConfig.tokens(
                "player", player.getName(),
                "category", category.name()
        );
        // Resolve %economy_setting_<id>% here so the labels work without PlaceholderAPI installed.
        if (plugin.getPlayerSettingToggles() != null) {
            for (String toggleId : plugin.getPlayerSettingToggles().ids()) {
                String status = plugin.getPlayerSettingToggles().status(player, toggleId);
                if (status != null) {
                    tokens.put("economy_setting_" + toggleId, status);
                }
            }
        }
        int width = config.buttonWidth(PATH, 250);
        String title = config.string(PATH + ".CATEGORY-TITLE", "Settings - %category%", tokens);
        DialogFactory.Screen screen = screen(player)
                .title(title)
                .externalTitle(title)
                .afterAction(config.string(PATH + ".AFTER-ACTION", "NONE"))
                .columns(config.columns(PATH, 1))
                .buttons(config.buttonsFrom(category.buttons(), tokens, width));

        String backLabel = config.string(PATH + ".CATEGORY-BACK-LABEL", "Back");
        if (backLabel != null && !backLabel.isBlank()) {
            screen.button(
                    backLabel,
                    null,
                    width,
                    config.string(PATH + ".CATEGORY-BACK-ACTION",
                            DialogActions.NAMESPACE + ':' + DialogActions.SETTINGS)
            );
        }
        return show(player, screen.build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.SETTINGS.equals(action)) {
            open(player);
            return true;
        }

        String category = DialogActions.argument(action, DialogActions.SETTINGS_CATEGORY);
        if (category != null) {
            openCategory(player, category);
            return true;
        }

        String toggle = DialogActions.argument(action, DialogActions.TOGGLE);
        if (toggle == null || !plugin.getPlayerSettingToggles().has(toggle)) {
            return false;
        }

        click(player);
        String status = plugin.getPlayerSettingToggles().cycle(player, toggle);
        if (!player.isOnline()) {
            return true;
        }
        if (status != null) {
            message(player, "&7" + plugin.getPlayerSettingToggles().label(toggle) + " is now " + status + "&7.");
        }
        // Reopening the owning category keeps the player where they were instead of bouncing
        // them back to the category list after every click.
        String owner = categoryOf(toggle);
        if (owner == null) {
            open(player);
        } else {
            openCategory(player, owner);
        }
        return true;
    }

    /** A category as written in the config, holding its button list verbatim. */
    private record Category(String id, String name, String label, List<?> buttons, int width) {
    }

    private List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfigManager().getDialog().getMapList(PATH + ".CATEGORIES")) {
            Object id = entry.get("ID");
            Object label = entry.get("LABEL");
            if (id == null || label == null) {
                continue;
            }
            String labelText = String.valueOf(label);
            categories.add(new Category(
                    DialogActions.sanitise(String.valueOf(id)),
                    displayName(entry.get("NAME"), labelText),
                    labelText,
                    entry.get("BUTTONS") instanceof List<?> buttons ? buttons : List.of(),
                    intOr(entry.get("WIDTH"), 0)
            ));
        }
        return categories;
    }

    private static String displayName(Object name, String label) {
        if (name != null) {
            String value = String.valueOf(name).trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return ITEM_TAG.matcher(label).replaceAll("").trim();
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    /** Finds which category holds a toggle so the screen can be reopened after the click. */
    private String categoryOf(String toggleId) {
        String needle = DialogActions.TOGGLE + DialogActions.sanitise(toggleId);
        for (Category category : categories()) {
            for (DialogConfig.ButtonSpec button : config.buttonsFrom(category.buttons(), null, 220)) {
                if (!button.hasAction()) {
                    continue;
                }
                String value = button.action();
                int separator = value.indexOf(':');
                String id = separator < 0 ? value : value.substring(separator + 1);
                if (needle.equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return category.id();
                }
            }
        }
        return null;
    }
}
