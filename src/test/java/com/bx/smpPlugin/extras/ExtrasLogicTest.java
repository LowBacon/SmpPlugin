package com.bx.smpPlugin.extras;

import com.bx.smpPlugin.utils.SpigotScheduler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtrasLogicTest {

    @Test
    void calculatorFollowsTheUsualOrderOfOperations() {
        assertEquals(14D, Calculator.evaluate("2 + 3 * 4"));
        assertEquals(20D, Calculator.evaluate("(2 + 3) * 4"));
        assertEquals(512D, Calculator.evaluate("2 ^ 3 ^ 2"), "powers group from the right");
        assertEquals(-5D, Calculator.evaluate("-(2 + 3)"));
        assertEquals(1728D, Calculator.evaluate("64x27"));
        assertEquals(1_000_000D, Calculator.evaluate("1m"));
        assertEquals(2_500D, Calculator.evaluate("2.5k"));
        assertEquals(3_000D, Calculator.evaluate("1,000 * 3"));
        assertEquals(4D, Calculator.evaluate("sqrt(16)"));
        assertEquals(7D, Calculator.evaluate("max(3;7)"));
        assertEquals(1D, Calculator.evaluate("10 % 3"));
    }

    @Test
    void calculatorRejectsNonsenseInsteadOfRunningIt() {
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate("1 / 0"));
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate("java.lang.System.exit(0)"));
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate("(1 + 2"));
        assertThrows(IllegalArgumentException.class, () -> Calculator.evaluate(""));
    }

    @Test
    void calculatorResultsReadNaturally() {
        assertEquals("1,728", Calculator.format(1728D));
        assertEquals("0.5", Calculator.format(0.5D));
        assertEquals("3.333333", Calculator.format(10D / 3D));
    }

    @Test
    void facingNamesFollowMinecraftYaw() {
        assertEquals("south", UtilityCommands.facing(0F));
        assertEquals("west", UtilityCommands.facing(90F));
        assertEquals("north", UtilityCommands.facing(180F));
        assertEquals("east", UtilityCommands.facing(-90F));
    }

    @Test
    void oldAuctionHouseCommandsInConfigsStillWork() {
        assertEquals("ah", SpigotScheduler.renamedCommand("auctionhouse"));
        assertEquals("ah sell 100", SpigotScheduler.renamedCommand("/auctionhouse sell 100"));
        assertEquals("ah", SpigotScheduler.renamedCommand("ah"));
        assertEquals("auctionhouses", SpigotScheduler.renamedCommand("auctionhouses"));
        assertEquals("menu", SpigotScheduler.renamedCommand("menu"));
    }
}
