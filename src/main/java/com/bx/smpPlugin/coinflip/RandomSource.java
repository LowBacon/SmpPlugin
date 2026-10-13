package com.bx.smpPlugin.coinflip;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Where coin results come from. Production uses {@link SecureRandom}; tests inject a deterministic source.
 * The source never sees names, stakes, balances, history or timing: every flip is an independent 50/50.
 */
public interface RandomSource {

    CoinSide flip();

    /** A random identifier recorded with each result, for audit references. */
    String newOutcomeId();

    /** The production source: one {@link SecureRandom} drawing one bit per flip. */
    static RandomSource secure() {
        SecureRandom random = new SecureRandom();
        return new RandomSource() {
            @Override
            public synchronized CoinSide flip() {
                return random.nextBoolean() ? CoinSide.HEADS : CoinSide.TAILS;
            }

            @Override
            public synchronized String newOutcomeId() {
                byte[] bytes = new byte[12];
                random.nextBytes(bytes);
                return HexFormat.of().formatHex(bytes);
            }
        };
    }
}
