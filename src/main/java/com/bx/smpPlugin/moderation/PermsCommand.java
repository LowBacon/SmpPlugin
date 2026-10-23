// SmpPlugin by LowBacon
package com.bx.smpPlugin.moderation;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.core.Text;
import com.bx.smpPlugin.permissions.PermissionNode;
import com.bx.smpPlugin.permissions.PermissionRegistry;
import com.bx.smpPlugin.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class PermsCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "smpplugin.admin.perms";
    private static final int PAGE_SIZE = 15;

    private final SmpPlugin plugin;

    public PermsCommand(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            Text.send(sender, "GENERAL.NO-PERMISSION", "&cYou don't have permission to do that.");
            return true;
        }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "roles" -> roles(sender);
            case "role" -> role(sender, args);
            case "explain" -> explain(sender, args);
            case "check" -> check(sender, args);
            case "player" -> player(sender, args);
            default -> {
                Text.send(sender, "PERMS.HELP-HEADER", "&6&lPermission tools");
                Text.raw(sender, "&e/perms roles &7- the ready-made staff roles");
                Text.raw(sender, "&e/perms role <name> [page] &7- every node in a role");
                Text.raw(sender, "&e/perms explain <node> &7- what a node does");
                Text.raw(sender, "&e/perms check <player> <node> &7- does a player have it, and why");
                Text.raw(sender, "&e/perms player <player> &7- which roles a player has");
            }
        }
        return true;
    }

    static Set<String> expand(String node) {
        Set<String> out = new LinkedHashSet<>();
        expand(node, out);
        return out;
    }

    private static void expand(String node, Set<String> out) {
        Optional<PermissionNode> found = PermissionRegistry.find(node);
        if (found.isEmpty()) {
            return;
        }
        for (String child : found.get().children()) {
            if (out.add(child)) {
                expand(child, out);
            }
        }
    }

    private static String roleNode(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("smpplugin.role.") ? lower : "smpplugin.role." + lower;
    }

    private void roles(CommandSender sender) {
        Text.send(sender, "PERMS.ROLES-HEADER", "&6&lStaff roles &7(give one node to a rank)");
        for (String role : PermissionRegistry.ROLES) {
            PermissionNode node = PermissionRegistry.find(role).orElseThrow();
            Text.line(sender, Text.button("&e" + role, "perms role " + role.substring(role.lastIndexOf('.') + 1),
                            "&7Click to list every node"),
                    " &8(" + expand(role).size() + " nodes)");
            Text.raw(sender, "  &7" + node.description());
        }
    }

    private void role(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "PERMS.ROLE-USAGE", "&7/perms role <helper|moderator|admin|owner> [page]");
            return;
        }
        String role = roleNode(args[1]);
        if (!PermissionRegistry.ROLES.contains(role)) {
            Text.send(sender, "PERMS.ROLE-UNKNOWN", "&cThere is no role called &f{role}&c.", "{role}", args[1]);
            return;
        }
        List<String> nodes = new ArrayList<>(expand(role));
        nodes.removeIf(node -> PermissionRegistry.find(node).map(found -> !found.children().isEmpty()
                && found.category() == com.bx.smpPlugin.permissions.PermissionCategory.BUNDLE).orElse(false));
        int pages = Math.max(1, (nodes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = 1;
        if (args.length > 2) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        Text.send(sender, "PERMS.ROLE-HEADER", "&6{role} &7- page {page}/{pages}",
                "{role}", role, "{page}", Integer.toString(page), "{pages}", Integer.toString(pages));
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(nodes.size(), page * PAGE_SIZE); i++) {
            String node = nodes.get(i);
            Text.line(sender, Text.button("&f" + node, "perms explain " + node, "&7Click to explain"));
        }
        if (page < pages) {
            Text.line(sender, Text.button("&e[Next page]", "perms role " + args[1] + " " + (page + 1), "&7Show more"));
        }
    }

    private void explain(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "PERMS.EXPLAIN-USAGE", "&7/perms explain <node>");
            return;
        }
        Optional<PermissionNode> found = PermissionRegistry.find(args[1]);
        if (found.isEmpty()) {
            Text.send(sender, "PERMS.UNKNOWN", "&c{node} is not a CustomSmp permission.", "{node}", args[1]);
            return;
        }
        PermissionNode node = found.get();
        Text.send(sender, "PERMS.EXPLAIN-HEADER", "&6{node}", "{node}", node.node());
        Text.raw(sender, "&7" + node.description());
        Text.raw(sender, "&7Type: &f" + node.category().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " &8| &7Default: &f" + node.defaultValue().name().toLowerCase(Locale.ROOT));
        if (!node.children().isEmpty()) {
            Text.raw(sender, "&7Includes &f" + expand(node.node()).size() + " &7node(s).");
        }
        List<String> inRoles = new ArrayList<>();
        for (String role : PermissionRegistry.ROLES) {
            if (expand(role).contains(node.node())) {
                inRoles.add(role.substring(role.lastIndexOf('.') + 1));
            }
        }
        Text.raw(sender, "&7In roles: &f" + (inRoles.isEmpty() ? "none" : String.join(", ", inRoles)));
        if (!node.history().isBlank()) {
            Text.raw(sender, "&8" + node.history());
        }
    }

    private void check(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.send(sender, "PERMS.CHECK-USAGE", "&7/perms check <player> <node>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, "GENERAL.PLAYER-NOT-FOUND", "&cThat player is not online.");
            return;
        }
        String node = args[2].toLowerCase(Locale.ROOT);
        boolean has = target.hasPermission(node);
        String source = "default";
        for (PermissionAttachmentInfo info : target.getEffectivePermissions()) {
            if (info.getPermission().equalsIgnoreCase(node)) {
                source = info.getAttachment() == null ? "default or a parent node"
                        : "set by " + info.getAttachment().getPlugin().getName();
                break;
            }
        }
        Text.send(sender, "PERMS.CHECK", "&f{player} {has} &f{node} &8({source})",
                "{player}", target.getName(), "{has}", has ? "&ahas" : "&cdoes not have", "{node}", node, "{source}", source);
    }

    private void player(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "PERMS.PLAYER-USAGE", "&7/perms player <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.send(sender, "GENERAL.PLAYER-NOT-FOUND", "&cThat player is not online.");
            return;
        }
        List<String> roles = new ArrayList<>();
        for (String role : PermissionRegistry.ROLES) {
            if (target.hasPermission(role)) {
                roles.add(role.substring(role.lastIndexOf('.') + 1));
            }
        }
        int staffNodes = 0;
        int adminNodes = 0;
        for (PermissionNode node : PermissionRegistry.all()) {
            if (!target.hasPermission(node.node())) {
                continue;
            }
            if (node.category() == com.bx.smpPlugin.permissions.PermissionCategory.STAFF) {
                staffNodes++;
            } else if (node.category() == com.bx.smpPlugin.permissions.PermissionCategory.ADMIN) {
                adminNodes++;
            }
        }
        Text.send(sender, "PERMS.PLAYER", "&f{player}&7: roles &f{roles}&7, &f{staff} &7staff and &f{admin} &7admin nodes{op}.",
                "{player}", target.getName(), "{roles}", roles.isEmpty() ? "none" : String.join(", ", roles),
                "{staff}", Integer.toString(staffNodes), "{admin}", Integer.toString(adminNodes),
                "{op}", target.isOp() ? " &c(operator)" : "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionUtils.has(sender, PERMISSION)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("roles", "role", "explain", "check", "player"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("role")) {
            options.addAll(List.of("helper", "moderator", "admin", "owner"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("explain")) {
            PermissionRegistry.all().forEach(node -> options.add(node.node()));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            PermissionRegistry.all().forEach(node -> options.add(node.node()));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).limit(100).toList();
    }
}
