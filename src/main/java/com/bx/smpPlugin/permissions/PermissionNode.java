// SmpPlugin by LowBacon
package com.bx.smpPlugin.permissions;

import org.bukkit.permissions.PermissionDefault;

import java.util.List;

public record PermissionNode(
        String node,
        String description,
        PermissionCategory category,
        PermissionDefault defaultValue,
        List<String> children,
        String history
) {
    public PermissionNode {
        children = List.copyOf(children);
        history = history == null ? "" : history;
    }
}
