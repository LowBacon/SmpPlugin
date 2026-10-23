// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CoinFlipNotifier {

    void send(UUID player, String key, Map<String, String> placeholders);

    void challengeReceived(CoinFlipMatch match);

    long beginReveal(CoinFlipMatch match);

    void matchCompleted(CoinFlipMatch match, boolean winnerPaidNow);

    void achievementsUnlocked(UUID player, List<Achievement> achievements);

    void staffAlert(String message);

    void matchesChanged();
}
