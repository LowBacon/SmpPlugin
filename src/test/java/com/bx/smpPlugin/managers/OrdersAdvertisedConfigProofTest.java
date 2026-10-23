// SmpPlugin by LowBacon
package com.bx.smpPlugin.managers;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdersAdvertisedConfigProofTest {

    @Test
    void deliveryModeDirectMustBeALiveEnumValue() {
        boolean hasDirect = Arrays.stream(OrdersManager.DeliveryMode.values())
                .anyMatch(mode -> mode.name().equals("DIRECT"));
        assertTrue(
                hasDirect,
                "orders.yml says DELIVERY.MODE is DEPOSIT_GUI or DIRECT"
        );
        assertEquals(OrdersManager.DeliveryMode.DIRECT, OrdersManager.DeliveryMode.fromConfig("DIRECT"));
        assertEquals(OrdersManager.DeliveryMode.DIRECT, OrdersManager.DeliveryMode.fromConfig("direct"));
    }

    @Test
    void donutStyleMustNotBeAdvertisedOrPresentInBundledYaml() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.load(java.nio.file.Path.of("src/main/resources/orders.yml").toFile());
        org.junit.jupiter.api.Assertions.assertFalse(config.contains("DONUT-STYLE"), "orders.yml must not contain unread DONUT-STYLE");

    }
}
