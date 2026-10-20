package com.bx.smpPlugin.menus;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.models.Team;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.NumberUtils;
import com.bx.smpPlugin.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /menu: one chest screen that reaches the main features. Every button simply runs its command as
 * the player, so permissions and feature toggles keep working, and buttons for commands the player
 * cannot use are left out. The whole layout lives in menus.yml under MAIN-MENU.
 */
public class MainMenu extends BaseMenu {

    private static final String PATH = "MAIN-MENU";

    private final Map<Integer, String> slotCommands = new HashMap<>();

    public MainMenu(SmpPlugin plugin, Player viewer) {
        super(plugin, title(plugin, viewer), size(plugin));
    }

    @Override
    public void build(Player player) {
        clear();
        slotCommands.clear();
        ConfigurationSection menu = plugin.getConfigManager().getMenus().getConfigurationSection(PATH);
        String filler = menu == null ? "GRAY_STAINED_GLASS_PANE" : menu.getString("FILLER", "GRAY_STAINED_GLASS_PANE");
        if (filler != null && !filler.isBlank() && !filler.equalsIgnoreCase("NONE")) {
            fill(ItemUtils.parseMaterial(filler));
        }

        ConfigurationSection buttons = menu == null ? null : menu.getConfigurationSection("BUTTONS");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                ConfigurationSection button = buttons.getConfigurationSection(key);
                if (button == null) {
                    continue;
                }
                int slot = button.getInt("SLOT", -1);
                String command = stripSlash(button.getString("COMMAND", ""));
                if (slot < 0 || slot >= inventory.getSize() || command.isBlank() || !canUse(player, command)) {
                    continue;
                }
                List<String> lore = new ArrayList<>();
                for (String line : button.getStringList("LORE")) {
                    lore.add(line.replace("{player}", player.getName()));
                }
                lore.add("");
                lore.add("&eClick to open");
                ItemStack item = ItemUtils.createItem(
                        ItemUtils.parseMaterial(button.getString("MATERIAL", "STONE")),
                        button.getString("NAME", "&f" + key).replace("{player}", player.getName()),
                        lore);
                set(slot, ItemUtils.hideMenuNoise(item, true));
                slotCommands.put(slot, command);
            }
        }

        int profileSlot = menu == null ? 4 : menu.getInt("PROFILE-SLOT", 4);
        if (profileSlot >= 0 && profileSlot < inventory.getSize()) {
            set(profileSlot, profileItem(player));
            if (canUse(player, "stats")) {
                slotCommands.put(profileSlot, "stats");
            }
        }
        int closeSlot = menu == null ? inventory.getSize() - 5 : menu.getInt("CLOSE-SLOT", inventory.getSize() - 5);
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            set(closeSlot, ItemUtils.createItem(Material.BARRIER, "&cClose", List.of()));
            slotCommands.put(closeSlot, "");
        }
    }

    @Override
    public void handleClick(int slot, Player player, ClickType clickType) {
        String command = slotCommands.get(slot);
        if (command == null) {
            return;
        }
        SoundUtils.play(player, plugin.getConfigManager().getSound("MENUS.BUTTON-CLICK"));
        player.closeInventory();
        if (command.isEmpty()) {
            return;
        }
        // One tick later, so the command's own menu opens after this one has closed.
        plugin.getSpigotScheduler().runEntityLater(player, () -> {
            if (player.isOnline()) {
                player.performCommand(com.bx.smpPlugin.utils.SpigotScheduler.renamedCommand(command));
            }
        }, 1L);
    }

    private ItemStack profileItem(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        List<String> lore = new ArrayList<>();
        if (data != null) {
            lore.add("&7Money: &f" + plugin.getCurrencyManager().formatMoneyCompact(data.getMoney()));
            lore.add("&7Shards: &f" + plugin.getCurrencyManager().formatShardsCompact(data.getShards()));
            lore.add("&7Kills: &f" + data.getKills() + " &8| &7Deaths: &f" + data.getDeaths());
            lore.add("&7Playtime: &f" + NumberUtils.formatTime(data.getTotalPlaytimeSeconds()));
        }
        Team team = plugin.getTeamManager().getTeam(player);
        lore.add("&7Team: &f" + (team == null ? "none" : team.getName().toUpperCase(Locale.ROOT)));
        lore.add("");
        lore.add("&eClick for your full stats");
        return ItemUtils.createPlayerHead(player, "&f&l" + plugin.getHideManager().publicName(player), lore);
    }

    /** A command is shown when it exists and the player passes its permission. */
    private boolean canUse(Player player, String command) {
        String name = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        PluginCommand pluginCommand = Bukkit.getPluginCommand(name);
        if (pluginCommand == null) {
            // Another plugin's or a vanilla command: leave the check to it.
            return true;
        }
        return pluginCommand.testPermissionSilent(player);
    }

    private static String stripSlash(String command) {
        String trimmed = command == null ? "" : command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private static String title(SmpPlugin plugin, Player viewer) {
        return plugin.getConfigManager().getMenus().getString(PATH + ".TITLE", "&8Menu")
                .replace("{player}", viewer == null ? "" : viewer.getName());
    }

    private static int size(SmpPlugin plugin) {
        int size = plugin.getConfigManager().getMenus().getInt(PATH + ".SIZE", 45);
        return size >= 9 && size <= 54 && size % 9 == 0 ? size : 45;
    }
}
