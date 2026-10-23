// SmpPlugin by LowBacon
package com.bx.smpPlugin.commands;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpVisibilityTest {

    private static CommandSender sender(boolean op, Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOp" -> op;
                    case "hasPermission", "isPermissionSet" -> args[0] instanceof String node && permissions.contains(node);
                    case "getName" -> "tester";
                    case "hashCode" -> 0;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static CommandCatalog.Entry entry(String name, CommandCatalog.Category category, String permission) {
        return new CommandCatalog.Entry(name, category, "test", "/" + name, List.of(), permission);
    }

    @Test
    void playerCommandsAreListedForEveryone() {
        assertTrue(CommandCatalog.visibleTo(sender(false, Set.of()), entry("home", CommandCatalog.Category.TRAVEL, ""), null));
    }

    @Test
    void staffCommandsWithoutANodeAreForOperatorsOnly() {
        CommandCatalog.Entry hidden = entry("secret", CommandCatalog.Category.ADMIN, "");
        assertFalse(CommandCatalog.visibleTo(sender(false, Set.of()), hidden, null));
        assertTrue(CommandCatalog.visibleTo(sender(true, Set.of()), hidden, null));
    }

    @Test
    void commandsCheckedInCodeNeedTheirRealPermission() {
        CommandCatalog.Entry eco = entry("eco", CommandCatalog.Category.ADMIN, "");
        assertFalse(CommandCatalog.visibleTo(sender(false, Set.of()), eco, null));
        assertTrue(CommandCatalog.visibleTo(sender(false, Set.of("smpplugin.admin.economy.history")), eco, null));
        CommandCatalog.Entry wipe = entry("playerwipe", CommandCatalog.Category.ADMIN, "");
        assertFalse(CommandCatalog.visibleTo(sender(true, Set.of()), wipe, null),
                "operator status alone does not show a command whose own check needs a permission");
    }
}
