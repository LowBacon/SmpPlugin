// SmpPlugin by LowBacon
package com.bx.smpPlugin.perks;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.MenuFx;
import com.bx.smpPlugin.core.MenuStyle;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.menus.BaseMenu;
import com.bx.smpPlugin.utils.ItemUtils;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PerksCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.perks";
    public static final String ADMIN_PERMISSION = "smpplugin.admin.perks";

    private final SmpPlugin plugin;
    private final Perks perks;

    public PerksCommand(SmpPlugin plugin, Perks perks) {
        this.plugin = plugin;
        this.perks = perks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION) && !PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("check")) {
            check(sender, args);
            return true;
        }
        if (args.length >= 1) {
            Optional<Rank> rank = perks.rank(args[0]);
            if (rank.isEmpty()) {
                Text.send(sender, "PERKS.UNKNOWN-RANK", "&7No rank called &f{rank}&7. Ranks: &f{ranks}",
                        "{rank}", args[0], "{ranks}", rankIds());
                return true;
            }
            Text.send(sender, "PERKS.RANK-HEADER", "{rank} &7perks:", "{rank}", rank.get().display());
            for (String line : lines(rank.get())) {
                Text.raw(sender, "&8 » " + line);
            }
            return true;
        }
        if (sender instanceof Player player) {
            new PerksMenu(player).open(player);
        } else {
            for (Rank rank : perks.ranks()) {
                Text.raw(sender, rank.display() + " &8(" + rank.permission() + ", priority " + rank.priority() + ")");
                for (String line : lines(rank)) {
                    Text.raw(sender, "&8 » " + line);
                }
            }
        }
        return true;
    }

    private void check(CommandSender sender, String[] args) {
        if (!PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        if (target == null) {
            Text.send(sender, "PERKS.CHECK-USAGE", "&7/perks check <online player>");
            return;
        }
        Optional<Rank> rank = perks.rankOf(node -> PermissionUtils.has(target, node));
        if (rank.isEmpty()) {
            Text.send(sender, "PERKS.CHECK-NONE", "&f{player} &7has no rank{off}.", "{player}", target.getName(),
                    "{off}", perks.isEnabled() ? "" : " &8(perks are switched off)");
            return;
        }
        Text.send(sender, "PERKS.CHECK-RANK", "&f{player} &7gets {rank} &8(from {permission})",
                "{player}", target.getName(), "{rank}", rank.get().display(), "{permission}", rank.get().permission());
        for (String line : lines(rank.get())) {
            Text.raw(sender, "&8 » " + line);
        }
    }

    static List<String> lines(Rank rank) {
        List<String> lines = new ArrayList<>();
        for (Perk perk : Perk.values()) {
            int value = rank.value(perk);
            if (value > 0) {
                lines.add(MenuStyle.GOOD + "+" + value + perk.unit() + " " + MenuStyle.TEXT + perk.description());
            }
        }
        if (lines.isEmpty()) {
            lines.add(MenuStyle.MUTED + "No perks set.");
        }
        return lines;
    }

    private String rankIds() {
        List<String> ids = new ArrayList<>();
        perks.ranks().forEach(rank -> ids.add(rank.id()));
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            perks.ranks().forEach(rank -> options.add(rank.id()));
            if (PermissionUtils.has(sender, ADMIN_PERMISSION)) {
                options.add("check");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check") && PermissionUtils.has(sender, ADMIN_PERMISSION)) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String lower = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private final class PerksMenu extends BaseMenu {

        private static final int INFO_SLOT = 4;
        private static final int CLOSE_SLOT = 31;
        private static final int[] RANK_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        PerksMenu(Player viewer) {
            super(PerksCommand.this.plugin, Text.get("PERKS.MENU-TITLE", "&8» " + MenuStyle.ACCENT + "&lRank Perks"), 36);
        }

        @Override
        public void open(Player player) {
            super.open(player);
            MenuFx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
        }

        @Override
        public void build(Player player) {
            clear();
            MenuStyle.frame(inventory, Material.PURPLE_STAINED_GLASS_PANE);
            Optional<Rank> own = perks.rankOf(node -> PermissionUtils.has(player, node));
            List<String> info = new ArrayList<>();
            info.add(MenuStyle.MUTED + "Ranks add to event rewards and");
            info.add(MenuStyle.MUTED + "shorten event waits. They never");
            info.add(MenuStyle.MUTED + "change odds, prices or the rules.");
            info.add("");
            info.add(MenuStyle.TEXT + "Your rank: " + own.map(Rank::display).orElse(MenuStyle.MUTED + "none"));
            if (!perks.isEnabled()) {
                info.add(MenuStyle.BAD + "Rank perks are switched off.");
            }
            set(INFO_SLOT, ItemUtils.createItem(Material.NETHER_STAR, MenuStyle.ACCENT + "&lRank Perks", info));
            List<Rank> ranks = perks.ranks();
            for (int i = 0; i < ranks.size() && i < RANK_SLOTS.length; i++) {
                Rank rank = ranks.get(i);
                boolean mine = own.isPresent() && own.get().id().equals(rank.id());
                List<String> lore = new ArrayList<>(lines(rank));
                if (mine) {
                    lore.add("");
                    lore.add(MenuStyle.GOOD + "✔ Your rank");
                }
                ItemStack item = ItemUtils.createItem(mine ? Material.NETHER_STAR : Material.AMETHYST_SHARD, rank.display(), lore);
                set(RANK_SLOTS[i], ItemUtils.setGlint(item, mine));
            }
            if (ranks.isEmpty()) {
                set(13, ItemUtils.createItem(Material.GRAY_DYE, MenuStyle.MUTED + "No ranks yet",
                        List.of(MenuStyle.MUTED + "Staff add them in perks.yml.")));
            }
            set(CLOSE_SLOT, MenuStyle.close());
        }

        @Override
        public void handleClick(int slot, Player player, ClickType clickType) {
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
            }
        }
    }
}
