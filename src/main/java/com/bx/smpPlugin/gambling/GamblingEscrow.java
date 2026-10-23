// SmpPlugin by LowBacon
package com.bx.smpPlugin.gambling;

import com.bx.smpPlugin.SmpPlugin;
import com.bx.smpPlugin.economy.EconomyContext;
import com.bx.smpPlugin.economy.EconomyStore;
import com.bx.smpPlugin.models.EconomyReason;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

final class GamblingEscrow {

    record Hold(String id, UUID player, String name, long cents, String game) {
    }

    private final SmpPlugin plugin;
    private final File legacyFile;
    private final Map<String, Hold> open = new ConcurrentHashMap<>();

    GamblingEscrow(SmpPlugin plugin) {
        this.plugin = plugin;
        this.legacyFile = new File(new File(plugin.getDataFolder(), "data"), "gambling-escrow.yml");
    }

    EconomyContext.SqlStep openStep(Hold hold) {
        long now = System.currentTimeMillis();
        return connection -> EconomyStore.insertHold(connection,
                new EconomyStore.Hold(hold.id(), hold.player(), hold.name(), hold.cents(), hold.game(), now));
    }

    EconomyContext.SqlStep closeStep(Collection<String> ids) {
        List<String> copy = List.copyOf(ids);
        return connection -> {
            for (String id : copy) {
                EconomyStore.deleteHold(connection, id);
            }
        };
    }

    void opened(Hold hold) {
        open.put(hold.id(), hold);
    }

    void closed(Collection<String> ids) {
        ids.forEach(open::remove);
    }

    void closeWithoutPayment(Collection<String> ids) {
        closed(ids);
        runInBackground(closeStep(ids), "close " + ids);
    }

    void keepAsPayout(String winnerId, long payoutCents, Collection<String> otherIds) {
        Hold winner = open.get(winnerId);
        if (winner != null) {
            open.put(winnerId, new Hold(winnerId, winner.player(), winner.name(), payoutCents, winner.game()));
        }
        closed(otherIds);
        EconomyContext.SqlStep closeOthers = closeStep(otherIds);
        runInBackground(connection -> {
            EconomyStore.updateHold(connection, winnerId, payoutCents);
            closeOthers.run(connection);
        }, "keep payout " + winnerId);
    }

    int size() {
        return open.size();
    }

    void recover() {
        recoverLegacyFile();
        if (plugin.getEconomyManager() == null || !plugin.getEconomyManager().isStorageReady()) {
            plugin.getLogger().warning("[Gambling] The database is not ready; unfinished bets are refunded at the next start.");
            return;
        }
        List<EconomyStore.Hold> holds;
        try {
            holds = EconomyStore.loadHolds(plugin.getDatabaseManager().getConnection());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not read unfinished bets; they stay for the next start.",
                    exception);
            return;
        }
        int refunded = 0;
        for (EconomyStore.Hold hold : holds) {
            if (hold.cents() <= 0L) {
                runInBackground(closeStep(List.of(hold.id())), "drop empty hold " + hold.id());
                continue;
            }
            EconomyContext context = EconomyContext.note("Refund of an unfinished " + hold.game() + " after a restart")
                    .withStep(closeStep(List.of(hold.id())));
            String name = hold.name() == null ? hold.player().toString() : hold.name();
            var result = plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(hold.player()),
                    Wallet.toEconomy(hold.cents()), EconomyReason.GAMBLE_REFUND, context);
            if (result.success()) {
                refunded++;
                plugin.getLogger().info("[Gambling] Gave " + name + " back " + plugin.getCurrencyManager()
                        .formatMoney(Wallet.toEconomy(hold.cents())) + " from an unfinished " + hold.game() + ".");
            } else {
                plugin.getLogger().warning("[Gambling] Could not refund " + name + " (" + result.failureReason()
                        + "); the bet stays held for the next start.");
            }
        }
        if (refunded > 0) {
            plugin.getLogger().info("[Gambling] Recovered " + refunded + " unfinished bet(s) after the last shutdown.");
        }
    }

    private void recoverLegacyFile() {
        if (!legacyFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(legacyFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not read " + legacyFile.getPath()
                    + "; it is left untouched so nobody's money is lost. Fix or remove it by hand.", exception);
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("HOLDS");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                try {
                    UUID player = UUID.fromString(entry.getString("PLAYER", ""));
                    long cents = entry.getLong("CENTS", 0L);
                    String game = entry.getString("GAME", "a game");
                    boolean paid = cents <= 0L || plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(player),
                            Wallet.toEconomy(cents), EconomyReason.GAMBLE_REFUND,
                            EconomyContext.note("Refund of an unfinished " + game + " (3.1 escrow file)")).success();
                    if (paid) {
                        yaml.set("HOLDS." + key, null);
                        saveLegacy(yaml);
                    }
                } catch (IllegalArgumentException invalid) {
                    plugin.getLogger().warning("[Gambling] Skipped an unreadable 3.1 escrow entry '" + key + "'.");
                }
            }
        }
        ConfigurationSection left = yaml.getConfigurationSection("HOLDS");
        if (left != null && !left.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("[Gambling] " + left.getKeys(false).size() + " bet(s) in " + legacyFile.getName()
                    + " could not be refunded; they are kept for the next start.");
            return;
        }
        try {
            Files.move(legacyFile.toPath(), new File(legacyFile.getParentFile(), legacyFile.getName() + ".recovered")
                    .toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[Gambling] Refunded the 3.1 escrow file but could not rename it.",
                    exception);
        }
    }

    private void saveLegacy(YamlConfiguration yaml) {
        try {
            yaml.save(legacyFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not update " + legacyFile.getPath()
                    + " after a refund; remove the refunded entry by hand before the next start.", exception);
        }
    }

    private void runInBackground(EconomyContext.SqlStep step, String what) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Connection connection = plugin.getDatabaseManager().getConnection();
            try {
                EconomyStore.inTransaction(connection, c -> {
                    step.run(c);
                    return null;
                });
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "[Gambling] Could not " + what + " in the database.", exception);
            }
        });
    }
}
