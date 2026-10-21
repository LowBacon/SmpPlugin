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
    /** A /dice or /cf bet taken from the player. */
    GAMBLE_STAKE,
    /** /dice or /cf winnings. */
    GAMBLE_PAYOUT,
    /** A /dice or /cf bet given back (tie, cancelled game, recovery after a crash). */
    GAMBLE_REFUND,
    /** Money or shards from a crate reward. */
    CRATE_REWARD,
    /** The fee taken from a /pay when ECONOMY.PAY.FEE-PERCENT is set. */
    PAY_FEE,
    /** Shards earned for being online ("everywhere" shards). Not written to the ledger by default. */
    SHARD_PASSIVE,
    /** Shards for a player kill. */
    SHARD_KILL,
    /** Shards from an AFK zone. */
    SHARD_AFK,
    /** Shards from a shard area (cuboid). */
    SHARD_CUBOID,
    /** Shards spent in the shard shop. */
    SHARD_SHOP_PURCHASE,
    /** Shards given back when a shard shop purchase could not be delivered. */
    SHARD_SHOP_REFUND,
    /** /shardpay between players. */
    SHARD_PAY,
    /** Rewards from seasonal events (4.0). */
    EVENT_REWARD,
    /** Rewards for ranked PvP wins (4.0). */
    RANKED_REWARD
}
