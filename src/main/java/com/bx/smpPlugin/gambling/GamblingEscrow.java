package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.models.EconomyReason;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Money that has left a player's balance but not been settled yet: a dice roll that is still
 * spinning, or a coin flip waiting for someone to join it. Every hold is written to
 * {@code data/gambling-escrow.yml} (in the background, in order) before the game goes on, and
 * removed once the money has been paid out or given back.
 *
 * <p>A clean shutdown settles everything first, so the file ends up empty. If the server crashes
 * instead, {@link #recover()} gives every hold still in the file back to its owner at the next
 * start, so a bet can never simply disappear.
 */
final class GamblingEscrow {

    record Hold(String id, UUID player, String name, long cents, String game) {
    }

    private final SmpPlugin plugin;
    private final Wallet wallet;
    private final File file;
    private final Map<String, Hold> holds = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SmpPlugin-gambling-escrow");
        thread.setDaemon(true);
        return thread;
    });

    GamblingEscrow(SmpPlugin plugin, Wallet wallet) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.file = new File(new File(plugin.getDataFolder(), "data"), "gambling-escrow.yml");
    }

    synchronized void open(Hold hold) {
        holds.put(hold.id(), hold);
        save();
    }

    /** Changes what a hold is worth, e.g. a win the economy refused to pay is kept as the payout. */
    synchronized void update(String id, long cents) {
        Hold hold = holds.get(id);
        if (hold != null) {
            holds.put(id, new Hold(id, hold.player(), hold.name(), cents, hold.game()));
            save();
        }
    }

    synchronized void close(String id) {
        if (holds.remove(id) != null) {
            save();
        }
    }

    synchronized int size() {
        return holds.size();
    }

    /** Gives back whatever a crash left in the file. Runs once at start-up, before any game opens. */
    void recover() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not read " + file.getPath()
                    + "; it is left untouched so nobody's money is lost. Fix or remove it by hand.", exception);
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("HOLDS");
        if (section == null || section.getKeys(false).isEmpty()) {
            return;
        }
        int refunded = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                UUID player = UUID.fromString(entry.getString("PLAYER", ""));
                String name = entry.getString("NAME", player.toString());
                long cents = entry.getLong("CENTS", 0L);
                String game = entry.getString("GAME", "a game");
                Hold hold = new Hold(key, player, name, cents, game);
                if (cents > 0L && !wallet.pay(player, name, cents, EconomyReason.GAMBLE_REFUND,
                        "Refund of an unfinished " + game + " after a restart:")) {
                    synchronized (this) {
                        holds.put(key, hold);
                    }
                    continue;
                }
                refunded++;
                plugin.getLogger().info("[Gambling] Gave " + name + " back " + wallet.money(cents)
                        + " from an unfinished " + game + ".");
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("[Gambling] Skipped an unreadable escrow entry '" + key + "'.");
            }
        }
        plugin.getLogger().info("[Gambling] Recovered " + refunded + " unfinished bet(s) after the last shutdown.");
        synchronized (this) {
            save();
        }
    }

    /** Writes the last state and stops the writer. Called after every game has settled. */
    void shutdown() {
        synchronized (this) {
            save();
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[Gambling] The escrow file took too long to write.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void save() {
        List<Hold> snapshot = new ArrayList<>(holds.values());
        if (writer.isShutdown()) {
            write(snapshot);
            return;
        }
        writer.execute(() -> write(snapshot));
    }

    private void write(List<Hold> snapshot) {
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("Cannot create " + folder);
            }
            if (snapshot.isEmpty()) {
                Files.deleteIfExists(file.toPath());
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            for (Hold hold : snapshot) {
                String path = "HOLDS." + hold.id();
                yaml.set(path + ".PLAYER", hold.player().toString());
                yaml.set(path + ".NAME", hold.name());
                yaml.set(path + ".CENTS", hold.cents());
                yaml.set(path + ".GAME", hold.game());
            }
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(temp.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[Gambling] Could not save the escrow file", exception);
        }
    }
}
