package com.bx.smpPlugin.coinflip.bukkit;

import com.bx.smpPlugin.coinflip.CoinFlipConfig;
import com.bx.smpPlugin.coinflip.CoinFlipMatch;
import com.bx.smpPlugin.coinflip.StakeMath;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Optional Discord post for finished matches with a large pot. Disabled by default. Posts go out on the HTTP
 * client's own threads with a timeout, at most one per MIN-INTERVAL, and contain only player names (reduced to
 * safe characters), the pot and the match ID: never balances, UUIDs or transaction details.
 */
public final class CoinFlipWebhook {

    private final Logger logger;
    private final Supplier<CoinFlipConfig> config;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile long lastPostMillis;

    public CoinFlipWebhook(Logger logger, Supplier<CoinFlipConfig> config) {
        this.logger = logger;
        this.config = config;
    }

    public void matchCompleted(CoinFlipMatch match) {
        CoinFlipConfig current = config.get();
        if (!current.webhookEnabled() || match.potCents() < current.webhookMinPotCents()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPostMillis < current.webhookMinIntervalMillis()) {
            return;
        }
        lastPostMillis = now;
        String loser = match.winnerId().equals(match.creatorId()) ? match.opponentName() : match.creatorName();
        String content = "**" + safe(match.winnerName()) + "** won a CoinFlip pot of " + StakeMath.format(match.potCents())
                + " against **" + safe(loser) + "** (#" + safe(match.id()) + "). In-game currency only.";
        String body = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"allowed_mentions\":{\"parse\":[]}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(current.webhookUrl()))
                .timeout(Duration.ofSeconds(current.webhookTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                logger.warning("[CoinFlip] Webhook post failed: " + error.getClass().getSimpleName());
            } else if (response.statusCode() >= 300) {
                logger.warning("[CoinFlip] Webhook answered HTTP " + response.statusCode());
            }
        });
    }

    /** Player names and IDs only ever contain letters, digits and underscores. */
    static String safe(String value) {
        return value == null ? "?" : value.replaceAll("[^A-Za-z0-9_]", "");
    }
}
