package com.bx.smpPlugin.models;

public enum EconomyReason {
    ADMIN_ADD,
    ADMIN_REMOVE,
    ADMIN_SET,
    PLAYER_PAY,
    SHOP_PURCHASE,
    SHOP_REFUND,
    SELL_PAYOUT,
    AUCTION_LISTING_FEE,
    AUCTION_PURCHASE,
    AUCTION_REFUND,
    AUCTION_CLAIM,
    ORDER_CREATE_ESCROW,
    ORDER_CREATE_FEE,
    ORDER_DELIVERY_PAYOUT,
    ORDER_REFUND,
    BOUNTY_PLACE,
    BOUNTY_REWARD,
    BILLFORD_REWARD,
    AMETHYST_SELL,
    COINFLIP_STAKE,
    COINFLIP_PAYOUT,
    COINFLIP_REFUND,
    /** Daily rewards, the Halloween calendar, challenges, chat games and other 3.0 prizes. */
    REWARD,
    /** A /dice or /roulette bet taken from the player. */
    GAMBLE_STAKE,
    /** /dice or /roulette winnings. */
    GAMBLE_PAYOUT,
    /** A /dice or /roulette bet given back (tie, cancelled round, recovery after a crash). */
    GAMBLE_REFUND,
    /** Money a crate gave. */
    CRATE_REWARD,
    /** Another plugin paid the player through Vault. */
    VAULT_DEPOSIT,
    /** Another plugin charged the player through Vault. */
    VAULT_WITHDRAW,
    /** /eco reset: the balance was put back to the starting amount. */
    ADMIN_RESET,
    /** A seasonal event (calendar, Trick or Treat, event milestones). */
    EVENT_REWARD
}
