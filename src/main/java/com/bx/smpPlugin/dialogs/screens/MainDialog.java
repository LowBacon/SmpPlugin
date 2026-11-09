// SmpPlugin by LowBacon
package com.bx.smpPlugin.dialogs.screens;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.dialogs.DialogActions;
import com.bx.smpPlugin.dialogs.DialogConfig;
import com.bx.smpPlugin.dialogs.DialogFactory;
import com.bx.smpPlugin.dialogs.DialogScreen;
import com.bx.smpPlugin.dialogs.DialogSession;
import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dialog form of the configurable /menu, used by the Custom SMP ESC entry. */
public final class MainDialog extends DialogScreen {

    private static final String PATH = "MAIN-MENU";

    public MainDialog(SmpPlugin plugin, DialogConfig config, DialogSession.Store sessions) {
        super(plugin, config, sessions);
    }

    public boolean open(Player player) {
        ConfigurationSection menu = plugin.getConfigManager().getMenus().getConfigurationSection(PATH);
        List<Map<String, Object>> buttons = new ArrayList<>();
        ConfigurationSection configured = menu == null ? null : menu.getConfigurationSection("BUTTONS");
        if (configured != null) {
            for (String key : configured.getKeys(false)) {
                ConfigurationSection button = configured.getConfigurationSection(key);
                if (button == null) continue;
                String command = stripSlash(button.getString("COMMAND", ""));
                if (command.isBlank() || !canUse(player, command)) continue;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("LABEL", button.getString("NAME", "&f" + key));
                entry.put("TOOLTIP", String.join("\n", button.getStringList("LORE")));
                entry.put("WIDTH", 150);
                entry.put("COMMAND", command);
                buttons.add(entry);
            }
        }
        DialogFactory.Screen screen = screen(player)
                .title("Custom SMP")
                .externalTitle("Custom SMP")
                .columns(2)
                .text("Choose a feature to open")
                .buttons(config.buttonsFrom(buttons, DialogConfig.tokens("player", player.getName()), 150));
        return show(player, screen.build());
    }

    @Override
    public boolean handle(Player player, String action, DialogResponseView response) {
        if (DialogActions.MAIN_MENU.equals(action)) {
            open(player);
            return true;
        }
        String rawIndex = DialogActions.argument(action, DialogActions.MAIN_COMMAND);
        if (rawIndex != null) {
            int index;
            try {
                index = Integer.parseInt(rawIndex);
            } catch (NumberFormatException ignored) {
                return true;
            }
            ConfigurationSection buttons = plugin.getConfigManager().getMenus().getConfigurationSection(PATH + ".BUTTONS");
            if (buttons == null || index < 0 || index >= buttons.getKeys(false).size()) return true;
            String key = new ArrayList<>(buttons.getKeys(false)).get(index);
            ConfigurationSection button = buttons.getConfigurationSection(key);
            String command = button == null ? "" : stripSlash(button.getString("COMMAND", ""));
            if (!command.isBlank() && canUse(player, command)) runCommand(player, command);
            return true;
        }
        return false;
    }

    private boolean canUse(Player player, String command) {
        String name = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        PluginCommand pluginCommand = Bukkit.getPluginCommand(name);
        return pluginCommand == null || pluginCommand.testPermissionSilent(player);
    }

    private static String stripSlash(String command) {
        String value = command == null ? "" : command.trim();
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
