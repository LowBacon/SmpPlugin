// SmpPlugin by LowBacon
package com.bx.smpPlugin.coinflip;

import java.security.SecureRandom;
import java.util.HexFormat;

public interface RandomSource {

    CoinSide flip();

    String newOutcomeId();

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
