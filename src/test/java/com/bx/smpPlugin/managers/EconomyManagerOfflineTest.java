package com.bx.smpPlugin.managers;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.EconomyFailureReason;
import com.bx.smpPlugin.models.EconomyReason;
import com.bx.smpPlugin.models.EconomyTransactionResult;
import com.bx.smpPlugin.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Money paid to an offline player. Before 4.0 every change read the stored row again; a second
 * payment made before the first one's background save had run read the old balance, and one of
 * the two payments was lost.
 */
class EconomyManagerOfflineTest {

    private final UUID offline = UUID.randomUUID();
    private final List<Runnable> queuedWrites = new ArrayList<>();
    private final YamlConfiguration config = new YamlConfiguration();
    private MockedStatic<Bukkit> bukkit;
    private DatabaseManager database;
    private EconomyManager economy;
    private PlayerDataManager players;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
        org.bukkit.OfflinePlayer stored = mock(org.bukkit.OfflinePlayer.class);
        when(stored.getName()).thenReturn("Sleeper");
        bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenReturn(stored);

        SmpPlugin plugin = mock(SmpPlugin.class);
        database = mock(DatabaseManager.class);
        // The stored row: 100.00. It never changes, because the queued writes have not run yet.
        when(database.loadPlayer(offline)).thenAnswer(invocation -> {
            PlayerData row = new PlayerData(offline, "Sleeper");
            row.setMoney(100D);
            return row;
        });
        doAnswer(invocation -> queuedWrites.add(invocation.getArgument(0))).when(database).executeAsync(any());

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfig()).thenReturn(config);
        CurrencyManager currency = mock(CurrencyManager.class);
        when(currency.formatMoney(anyDouble())).thenReturn("$");

        players = new PlayerDataManager(plugin);
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getCurrencyManager()).thenReturn(currency);
        when(plugin.getPlayerLogsManager()).thenReturn(mock(PlayerLogsManager.class));
        when(plugin.getPlayerDataManager()).thenReturn(players);
        economy = new EconomyManager(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private EconomyTransactionResult pay(double amount) {
        return economy.deposit(new EconomyManager.AccountReference(offline, "Sleeper", null), amount, EconomyReason.REWARD);
    }

    @Test
    void twoQuickPaymentsToAnOfflinePlayerBothCount() {
        assertTrue(pay(50D).success());
        assertTrue(pay(25.5D).success());
        assertEquals(175.5D, economy.getBalance(offline), 1e-9);
        verify(database, times(1)).loadPlayer(offline);
        assertEquals(2, queuedWrites.size(), "each change is saved");
    }

    @Test
    void aJoiningPlayerGetsTheChangedCopyNotTheOldRow() {
        pay(10D);
        assertTrue(players.isSaving(offline), "the login preload is skipped while a change is on its way");
        PlayerData copy = players.getOrLoadOffline(offline);
        assertEquals(110D, copy.getMoney(), 1e-9);
        assertSame(copy, players.getOrLoadOffline(offline));
    }

    @Test
    void withdrawalsCannotOverdrawAndLimitsHold() {
        EconomyTransactionResult tooMuch = economy.withdraw(new EconomyManager.AccountReference(offline, "Sleeper", null),
                100.01D, EconomyReason.SHOP_PURCHASE);
        assertFalse(tooMuch.success());
        assertEquals(EconomyFailureReason.INSUFFICIENT_FUNDS, tooMuch.failureReason());

        config.set("ECONOMY.MAX-BALANCE", 150D);
        EconomyTransactionResult pastLimit = pay(60D);
        assertEquals(EconomyFailureReason.BALANCE_LIMIT, pastLimit.failureReason());
        assertTrue(pay(50D).success(), "exactly the limit is allowed");
        assertEquals(150D, economy.getBalance(offline), 1e-9);

        assertEquals(EconomyFailureReason.INVALID_AMOUNT, pay(Double.NaN).failureReason());
        assertEquals(EconomyFailureReason.INVALID_AMOUNT, pay(-5D).failureReason());
        assertEquals(EconomyFailureReason.INVALID_AMOUNT, pay(0.001D).failureReason(), "less than a cent");
    }

    @Test
    void shardsUseTheSameCopy() {
        pay(5D);
        assertTrue(economy.depositShards(offline, "Sleeper", 7L, EconomyReason.REWARD).success());
        EconomyManager.ShardResult spend = economy.withdrawShards(offline, "Sleeper", 10L, EconomyReason.SHARD_SHOP_PURCHASE);
        assertEquals(EconomyFailureReason.INSUFFICIENT_FUNDS, spend.failure());
        PlayerData copy = players.getOrLoadOffline(offline);
        assertEquals(7L, copy.getShards());
        assertEquals(105D, copy.getMoney(), 1e-9, "the shard change did not undo the payment");
    }
}
