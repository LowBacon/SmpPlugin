package com.bx.smpPlugin.testsupport;

/**
 * Test conditions that depend on which server API the tests are compiled against.
 *
 * <p>On the Paper API, {@code ItemStack} and {@code Material} item lookups go through the server's
 * registries ({@code io.papermc.paper.registry.RegistryAccess}), which only exist on a running server.
 * Unit tests that build real ItemStacks therefore run on the Spigot profile only:</p>
 * <pre>mvn test -Pspigot</pre>
 * <p>The plugin's own bytecode is identical in both builds apart from constant ordering, so the
 * Spigot-profile run covers the same plugin logic.</p>
 */
public final class ServerApiCondition {

    /** Use with {@code @DisabledIf(ServerApiCondition.ITEM_STACKS_NEED_SERVER)}. */
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
