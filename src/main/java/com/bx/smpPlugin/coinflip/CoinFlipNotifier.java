package com.bx.smpPlugin.coinflip;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the service tells players and staff. The plugin implements it with chat, titles, sounds and menus. */
public interface CoinFlipNotifier {

    /** Sends the message {@code key} from coinflip-messages.yml to a player if they are online. */
    void send(UUID player, String key, Map<String, String> placeholders);

    /** Tells the invitee about a private challenge, with clickable accept and decline options. */
    void challengeReceived(CoinFlipMatch match);

    /**
     * Starts the reveal for both players. Returns how many ticks to wait before paying out; the result is already
     * stored, so the delay is presentation only and is the same whatever the viewers do.
     */
    long beginReveal(CoinFlipMatch match);

    /** Shows the result. {@code winnerPaidNow} is false when the winner was offline and receives a claim later. */
    void matchCompleted(CoinFlipMatch match, boolean winnerPaidNow);

    void achievementsUnlocked(UUID player, List<Achievement> achievements);

    void staffAlert(String message);

    /** Open browsers and match menus should redraw. */
    void matchesChanged();
}
