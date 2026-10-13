package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.Settlement;
import com.bx.smpPlugin.coinflip.storage.CoinFlipDao;
import com.bx.smpPlugin.coinflip.storage.CoinFlipRepository;

import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Cached rankings. All categories are refreshed together in one storage task every few minutes; menus only read
 * the cache, so opening a leaderboard never runs a query. Ties are broken in SQL by matches played, then UUID.
 */
public final class CoinFlipLeaderboards {

    private final CoinFlipRepository repository;
    private final Supplier<CoinFlipConfig> config;
    private final ZoneId zone;
    private final Logger logger;
    private volatile Map<CoinFlipDao.Metric, List<CoinFlipDao.RankRow>> cache = new EnumMap<>(CoinFlipDao.Metric.class);
    private volatile long refreshedAtMillis;
    private volatile boolean refreshing;

    public CoinFlipLeaderboards(CoinFlipRepository repository, Supplier<CoinFlipConfig> config, ZoneId zone, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.zone = zone;
        this.logger = logger;
    }

    public List<CoinFlipDao.RankRow> top(CoinFlipDao.Metric metric) {
        return cache.getOrDefault(metric, List.of());
    }

    public long refreshedAtMillis() {
        return refreshedAtMillis;
    }

    public void refreshIfDue() {
        if (System.currentTimeMillis() - refreshedAtMillis >= config.get().leaderboardRefreshMillis()) {
            refresh();
        }
    }

    /** Starts a refresh unless one is running. Safe to call from any thread. */
    public CompletableFuture<Void> refresh() {
        if (refreshing || !repository.isReady()) {
            return CompletableFuture.completedFuture(null);
        }
        refreshing = true;
        CoinFlipConfig current = config.get();
        long now = System.currentTimeMillis();
        String week = Settlement.weekKey(now, zone);
        String month = Settlement.monthKey(now, zone);
        return repository.transaction(dao -> {
            Map<CoinFlipDao.Metric, List<CoinFlipDao.RankRow>> fresh = new EnumMap<>(CoinFlipDao.Metric.class);
            for (CoinFlipDao.Metric metric : CoinFlipDao.Metric.values()) {
                String period = metric.periodic() ? (metric.weekly() ? week : month) : null;
                fresh.put(metric, List.copyOf(dao.leaderboard(metric, period, current.leaderboardSize(), current.winRateMinMatches())));
            }
            return fresh;
        }).handle((fresh, error) -> {
            refreshing = false;
            if (error != null) {
                logger.warning("[CoinFlip] Leaderboard refresh failed: " + error.getMessage());
                return null;
            }
            cache = fresh;
            refreshedAtMillis = System.currentTimeMillis();
            return null;
        });
    }
}
