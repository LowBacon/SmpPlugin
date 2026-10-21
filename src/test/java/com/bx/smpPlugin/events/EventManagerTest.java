package com.bx.smpPlugin.events;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.managers.ConfigManager;
import com.bx.smpPlugin.utils.SpigotScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Starting, stopping and switching events, with a test event that counts what happens to it. */
class EventManagerTest {

    @TempDir
    Path folder;

    private final YamlConfiguration events = new YamlConfiguration();
    private MockedStatic<Bukkit> bukkit;
    private PluginManager pluginManager;
    private SmpPlugin plugin;
    private BukkitTask eventTask;

    /** A minimal event: one listener and one repeating task while it runs. */
    static final class CountingEvent extends SeasonalEvent implements Listener {
        int starts;
        int stops;

        CountingEvent(SmpPlugin plugin, EventManager manager) {
            super(plugin, manager, "test");
        }

        @Override
        protected String defaultName() {
            return "Test";
        }

        @Override
        protected String defaultDescription() {
            return "A test event.";
        }

        @Override
        protected Material defaultIcon() {
            return Material.CAKE;
        }

        @Override
        protected void onStart() {
            starts++;
            listen(this);
            task(plugin.getSpigotScheduler().runGlobalTimer(() -> { }, 20L, 20L));
        }

        @Override
        protected void onStop() {
            stops++;
        }
    }

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        pluginManager = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(org.bukkit.command.ConsoleCommandSender.class));
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> java.util.List.of());
        plugin = mock(SmpPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        ConfigManager config = mock(ConfigManager.class);
        when(config.getEvents()).thenReturn(events);
        when(plugin.getConfigManager()).thenReturn(config);
        SpigotScheduler scheduler = mock(SpigotScheduler.class);
        eventTask = mock(BukkitTask.class);
        when(scheduler.runGlobalTimer(any(), eq(20L), eq(20L))).thenReturn(eventTask);
        when(scheduler.runGlobalTimer(any(), eq(1200L), eq(1200L))).thenReturn(mock(BukkitTask.class));
        when(scheduler.runAsync(any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        when(plugin.getSpigotScheduler()).thenReturn(scheduler);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void anEventRunsOnlyInsideItsDates() {
        events.set("EVENTS.TEST.START", "2020-01-01");
        events.set("EVENTS.TEST.END", "2020-01-02");
        EventManager manager = new EventManager(plugin);
        CountingEvent event = new CountingEvent(plugin, manager);
        manager.register(event);
        manager.start();
        assertFalse(event.isRunning(), "the window is over");
        assertEquals("&7over", manager.statusLine(event));

        events.set("EVENTS.TEST.START", "01-01");
        events.set("EVENTS.TEST.END", "12-31 23:59");
        manager.tick();
        assertTrue(event.isRunning());
        assertEquals(1, event.starts);
        verify(pluginManager).registerEvents(event, plugin);
    }

    @Test
    void staffSwitchesStopEverythingAndAreRemembered() throws Exception {
        EventManager manager = new EventManager(plugin);
        CountingEvent event = new CountingEvent(plugin, manager);
        manager.register(event);
        manager.start();
        assertFalse(event.isRunning(), "no dates: off until switched on");

        manager.setMode(event, EventManager.Mode.ON);
        assertTrue(event.isRunning());
        manager.setMode(event, EventManager.Mode.OFF);
        assertFalse(event.isRunning());
        assertEquals(1, event.stops);
        verify(eventTask, times(1)).cancel();

        File state = folder.resolve("data/events-state.yml").toFile();
        assertTrue(Files.readString(state.toPath()).contains("test: 'OFF'")
                || Files.readString(state.toPath()).contains("test: OFF"));

        // After a restart the staff choice still holds.
        EventManager restarted = new EventManager(plugin);
        CountingEvent again = new CountingEvent(plugin, restarted);
        restarted.register(again);
        events.set("EVENTS.TEST.START", "01-01");
        events.set("EVENTS.TEST.END", "12-31 23:59");
        restarted.start();
        assertEquals(EventManager.Mode.OFF, restarted.mode(again));
        assertFalse(again.isRunning(), "switched off by staff beats the dates");

        restarted.setMode(again, EventManager.Mode.AUTO);
        assertTrue(again.isRunning(), "back on its dates");
    }

    @Test
    void anEventDisabledInTheFileNeverRuns() {
        events.set("EVENTS.TEST.ENABLED", false);
        EventManager manager = new EventManager(plugin);
        CountingEvent event = new CountingEvent(plugin, manager);
        manager.register(event);
        manager.start();
        manager.setMode(event, EventManager.Mode.ON);
        assertFalse(event.isRunning());
        assertEquals(0, event.starts);
        verify(pluginManager, never()).registerEvents(any(), any());
    }

    @Test
    void stoppingTheManagerStopsEveryEvent() {
        EventManager manager = new EventManager(plugin);
        CountingEvent event = new CountingEvent(plugin, manager);
        manager.register(event);
        manager.start();
        manager.setMode(event, EventManager.Mode.ON);
        manager.stop();
        assertFalse(event.isRunning());
        assertEquals(1, event.stops);
        verify(plugin.getSpigotScheduler(), times(1)).runGlobalTimer(any(), eq(20L), anyLong());
    }
}
