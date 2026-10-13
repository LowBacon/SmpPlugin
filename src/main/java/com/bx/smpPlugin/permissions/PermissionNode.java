package com.bx.smpPlugin.permissions;

import org.bukkit.permissions.PermissionDefault;

import java.util.List;

/**
 * One declared permission.
 *
 * @param node        the permission string, lower case
 * @param description what holding it allows
 * @param category    intended use; decides admin-bundle membership
 * @param defaultValue who has it without an explicit grant
 * @param children    nodes granted together with this one
 * @param history     how this node relates to version 1.5 (new, renamed, default changed); empty when unchanged
 */
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
