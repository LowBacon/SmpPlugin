// SmpPlugin by LowBacon
package com.bx.smpPlugin.testsupport;

public final class ServerApiCondition {

    public static final String ITEM_STACKS_NEED_SERVER =
            "com.bx.smpPlugin.testsupport.ServerApiCondition#itemStacksNeedServer";

    private ServerApiCondition() {
    }

    public static boolean itemStacksNeedServer() {
        try {
            Class.forName("io.papermc.paper.registry.RegistryAccess", false, ServerApiCondition.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
