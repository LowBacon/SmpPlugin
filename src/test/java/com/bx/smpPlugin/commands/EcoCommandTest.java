package com.bx.smpPlugin.commands;

import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.economy.LedgerEntry;
import com.bx.smpPlugin.managers.ConfigManager;
import com.bx.smpPlugin.models.PlayerData;
import com.bx.smpPlugin.testsupport.EconomyHarness;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoCommandTest {

    private static final UUID ERIN = UUID.fromString("40000000-0000-0000-0000-00000000000e");

    private EconomyHarness harness;
    private EcoCommand eco;

    @BeforeEach
    void setUp() throws Exception {
        harness = new EconomyHarness();
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages.yml"));
        EconomyHarness.set(ConfigManager.class, harness.plugin.getConfigManager(), "messages", messages);
        harness.config.set("SETTINGS.MONEY-PER-DEFAULT", 1000D);
        // /eco finds players by name through the players table, which addOnline fills.
        eco = new EcoCommand(harness.plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        harness.close();
    }

    @Test
    void giveRecordsTheStaffMemberAndTheNote() throws Exception {
        PlayerData erin = harness.addOnline(ERIN, "Erin", 10D);
        Sender staff = sender("Mod", "smpplugin.admin.addmoney");

        eco.onCommand(staff.proxy, null, "eco", new String[]{"give", "Erin", "2.5k", "event", "prize"});
        assertEquals(2510D, erin.getMoney(), 1e-9);
        harness.drainWrites();
        LedgerEntry row = EconomyStore.history(harness.connection(), ERIN, 1, 0).get(0);
        assertEquals("ADMIN_ADD", row.reason());
        assertEquals("Mod", row.actor());
        assertEquals("event prize", row.note());
        assertTrue(staff.joined().contains("Erin"), staff.joined());
    }

    @Test
    void takingMoreThanTheBalanceExplainsWhyAndChangesNothing() throws Exception {
        PlayerData erin = harness.addOnline(ERIN, "Erin", 10D);
        Sender staff = sender("Mod", "smpplugin.admin.removemoney");

        eco.onCommand(staff.proxy, null, "eco", new String[]{"take", "Erin", "11"});
        assertEquals(10D, erin.getMoney(), 1e-9);
        assertTrue(staff.joined().contains("does not have enough"), staff.joined());
    }

    @Test
    void setAboveTheLimitIsRefusedWithTheLimitShown() throws Exception {
        harness.config.set("ECONOMY.MAX-BALANCE", 5000D);
        PlayerData erin = harness.addOnline(ERIN, "Erin", 10D);
        Sender staff = sender("Admin", "smpplugin.admin.setmoney");

        eco.onCommand(staff.proxy, null, "eco", new String[]{"set", "Erin", "6000"});
        assertEquals(10D, erin.getMoney(), 1e-9);
        assertTrue(staff.joined().contains("balance limit"), staff.joined());
    }

    @Test
    void resetPutsTheStartingBalanceBack() throws Exception {
        PlayerData erin = harness.addOnline(ERIN, "Erin", 77D);
        Sender staff = sender("Admin", "smpplugin.admin.setmoney");

        eco.onCommand(staff.proxy, null, "eco", new String[]{"reset", "Erin", "season", "end"});
        assertEquals(1000D, erin.getMoney(), 1e-9);
        harness.drainWrites();
        assertEquals("ADMIN_RESET", EconomyStore.history(harness.connection(), ERIN, 1, 0).get(0).reason());
    }

    @Test
    void everySubcommandChecksItsOwnPermission() throws Exception {
        PlayerData erin = harness.addOnline(ERIN, "Erin", 10D);
        Sender giver = sender("Helper", "smpplugin.admin.addmoney");

        eco.onCommand(giver.proxy, null, "eco", new String[]{"set", "Erin", "999999"});
        eco.onCommand(giver.proxy, null, "eco", new String[]{"take", "Erin", "10"});
        eco.onCommand(giver.proxy, null, "eco", new String[]{"reset", "Erin"});
        assertEquals(10D, erin.getMoney(), 1e-9, "giving money does not allow setting or taking it");
        assertEquals(List.of("give"), eco.onTabComplete(giver.proxy, null, "eco", new String[]{""}));
    }

    private Sender sender(String name, String... permissions) {
        return new Sender(name, Set.of(permissions));
    }

    private static final class Sender {
        final List<String> messages = new ArrayList<>();
        final CommandSender proxy;

        Sender(String name, Set<String> permissions) {
            proxy = (CommandSender) Proxy.newProxyInstance(EcoCommandTest.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class}, (p, method, args) -> switch (method.getName()) {
                        case "getName" -> name;
                        case "hasPermission" -> args[0] instanceof String node && permissions.contains(node);
                        case "isPermissionSet" -> args[0] instanceof String node && permissions.contains(node);
                        case "isOp" -> false;
                        case "getEffectivePermissions" -> Set.of();
                        case "sendMessage" -> {
                            if (args[0] instanceof String text) {
                                messages.add(text);
                            } else if (args[0] instanceof String[] texts) {
                                messages.addAll(List.of(texts));
                            }
                            yield null;
                        }
                        case "hashCode" -> name.hashCode();
                        case "equals" -> p == args[0];
                        case "toString" -> "Sender[" + name + "]";
                        default -> method.getReturnType() == boolean.class ? false : null;
                    });
        }

        String joined() {
            return String.join("\n", messages);
        }
    }
}
