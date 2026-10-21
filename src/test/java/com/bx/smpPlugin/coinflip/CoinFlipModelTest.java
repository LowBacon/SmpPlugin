package com.bx.smpPlugin.coinflip;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure CoinFlip logic: amounts, payouts, states, statistics, preferences, presets and configuration. */
class CoinFlipModelTest {

    @Test
    void amountsParseSafely() {
        assertEquals(150_000L, StakeMath.parse("1,500").cents());
        assertEquals(150_000L, StakeMath.parse(" 1 500 ").cents());
        assertEquals(250_000L, StakeMath.parse("2.5k").cents());
        assertEquals(300_000_000L, StakeMath.parse("3M").cents());
        assertEquals(1_050L, StakeMath.parse("10.50").cents());
        assertEquals(StakeMath.ParseError.NEGATIVE, StakeMath.parse("-5").error());
        assertEquals(StakeMath.ParseError.MALFORMED, StakeMath.parse("1e5").error());
        assertEquals(StakeMath.ParseError.MALFORMED, StakeMath.parse("NaN").error());
        assertEquals(StakeMath.ParseError.MALFORMED, StakeMath.parse("12abc").error());
        assertEquals(StakeMath.ParseError.TOO_MANY_DECIMALS, StakeMath.parse("1.005").error());
        assertEquals(StakeMath.ParseError.TOO_LARGE, StakeMath.parse("99999999999999999999999").error());
        assertEquals(StakeMath.ParseError.TOO_LARGE, StakeMath.parse("2000b").error());
        assertEquals(StakeMath.ParseError.ZERO, StakeMath.parse("0.00").error());
        assertEquals(StakeMath.ParseError.EMPTY, StakeMath.parse("   ").error());
    }

    @Test
    void payoutMathConservesCurrencyAndRoundsTheFeeDown() {
        long stake = 3_333L;
        int bps = 250;
        long pot = StakeMath.pot(stake);
        long fee = StakeMath.fee(pot, bps);
        assertEquals(6_666L, pot);
        assertEquals(166L, fee);
        assertEquals(pot - fee, StakeMath.payout(stake, bps));
        assertEquals(StakeMath.payout(stake, bps) - stake, StakeMath.winnerNet(stake, bps));
        assertEquals(0L, StakeMath.fee(pot, 0));
        assertEquals(2L * StakeMath.HARD_MAX_STAKE_CENTS, StakeMath.payout(StakeMath.HARD_MAX_STAKE_CENTS, 0));
        assertTrue(StakeMath.pot(StakeMath.HARD_MAX_STAKE_CENTS) < (1L << 53), "every pot converts exactly to a double");
        assertEquals("1,234.50", StakeMath.format(123_450L));
        assertEquals("1,234", StakeMath.format(123_400L));
        assertEquals("2.5%", StakeMath.percent(250));
        assertEquals(1_234_56L, StakeMath.fromEconomy(1_234.567D));
        // Balances have no limit now; a double beyond the largest one reads as the most cents there are.
        assertEquals(9_200_000_000_000_000_000L, StakeMath.fromEconomy(9.2e16D));
        assertEquals(Long.MAX_VALUE, StakeMath.fromEconomy(9.3e16D));
        assertEquals(Long.MAX_VALUE, StakeMath.fromEconomy(1e300D));
        assertEquals(1_234.56D, StakeMath.toEconomy(123_456L));
    }

    @Test
    void onlyLegalStateTransitionsHappenAndOnlyOnce() {
        CoinFlipMatch match = new CoinFlipMatch("ABCDEFGH", UUID.randomUUID(), "A", CoinSide.HEADS, false, null, null,
                100L, 0, 0L, 1_000L, MatchState.WAITING);
        assertFalse(match.transition(MatchState.WAITING, MatchState.COMPLETED), "cannot skip to completed");
        assertFalse(match.transition(MatchState.LOCKED, MatchState.SETTLING), "wrong expected state");
        assertTrue(match.transition(MatchState.WAITING, MatchState.JOINING));
        assertFalse(match.transition(MatchState.WAITING, MatchState.CANCELLING), "a joining match cannot be cancelled");
        assertTrue(MatchState.COMPLETED.isFinal());
        assertEquals(MatchState.LOCKED, MatchState.RESOLVING.durable());
        UUID opponent = UUID.randomUUID();
        assertTrue(match.lock(opponent, "B", CoinSide.TAILS, "id1", 5L));
        assertFalse(match.lock(UUID.randomUUID(), "C", CoinSide.HEADS, "id2", 6L), "the outcome is set once");
        assertEquals(opponent, match.winnerId());
    }

    @Test
    void statisticsCountCompletedMatchesOnlyAndSeparateGrossFromNet() {
        UUID id = UUID.randomUUID();
        CoinFlipStats stats = CoinFlipStats.empty(id, "A")
                .afterMatch("A", true, 1_000, 2_000, CoinSide.HEADS, false, false, 5_000, 10L, "2026-10-12")
                .afterMatch("A", true, 1_000, 2_000, CoinSide.HEADS, true, true, 5_000, 20L, "2026-10-12")
                .afterMatch("A", false, 3_000, 6_000, CoinSide.TAILS, false, true, 5_000, 30L, "2026-10-13");
        assertEquals(3, stats.played());
        assertEquals(2, stats.wins());
        assertEquals(4_000L, stats.grossPayoutCents());
        assertEquals(1_000L + 1_000L - 3_000L, stats.netCents());
        assertEquals(0, stats.currentStreak());
        assertEquals(2, stats.bestStreak());
        assertEquals(2, stats.activeDays());
        assertEquals(CoinSide.HEADS, stats.favouriteSide());
        assertEquals(2, stats.joinedMatches());
        assertEquals(10L, stats.firstMatchMillis());
        assertEquals(66.666D, stats.winRate(), 0.01D);
        assertTrue(Achievement.reachedBy(stats).contains(Achievement.FIRST_WIN));
        assertFalse(Achievement.reachedBy(stats).contains(Achievement.WINS_10));
        assertEquals(-1D, CoinFlipStats.empty(id, "B").winRate());
    }

    @Test
    void noAchievementRewardsLosingOrStakingMore() {
        for (Achievement achievement : Achievement.values()) {
            String text = (achievement.displayName() + " " + achievement.description()).toLowerCase();
            assertFalse(text.contains("lose") || text.contains("loss") || text.contains("stake") || text.contains("bet"),
                    achievement.name());
        }
    }

    @Test
    void preferencesRoundTripAndTolerateGarbage() {
        CoinFlipPreferences prefs = CoinFlipPreferences.defaults(AnimationPreset.CLASSIC, false)
                .withAnimation(AnimationPreset.REDUCED).withSounds(false).withSaved(CoinSide.TAILS, 5_000L)
                .withRecentStake(100L).withRecentStake(200L).withRecentStake(100L).withSelfExclusionUntil(99L);
        CoinFlipPreferences decoded = CoinFlipPreferences.decode(prefs.encode(), CoinFlipPreferences.defaults(AnimationPreset.FAST, true));
        assertEquals(prefs, decoded);
        assertEquals(List.of(100L, 200L), decoded.recentStakes());
        CoinFlipPreferences fallback = CoinFlipPreferences.defaults(AnimationPreset.FAST, true);
        assertEquals(fallback, CoinFlipPreferences.decode("anim=WHAT;snd=7;;=;stake=abc", fallback));
        assertTrue(decoded.selfExcluded(50L));
        assertFalse(decoded.selfExcluded(100L));
        assertEquals(99L, decoded.withSelfExclusionUntil(10L).selfExcludedUntilMillis(), "self-exclusion cannot be shortened");
    }

    @Test
    void presetsSpeedUpThenSlowDownToTheReveal() {
        for (AnimationPreset preset : AnimationPreset.values()) {
            List<Integer> delays = preset.frameDelays();
            if (preset == AnimationPreset.OFF) {
                assertTrue(delays.isEmpty());
                continue;
            }
            assertTrue(delays.getLast() >= delays.get(delays.size() / 2), preset + " slows down at the end");
            assertTrue(preset.totalTicks() <= 200, preset + " stays under ten seconds");
            delays.forEach(delay -> assertTrue(delay >= 1, preset + " has a zero delay"));
        }
    }

    @Test
    void secureRandomIsRoughlyFair() {
        RandomSource random = RandomSource.secure();
        int heads = 0;
        int flips = 200_000;
        for (int i = 0; i < flips; i++) {
            if (random.flip() == CoinSide.HEADS) {
                heads++;
            }
        }
        // A sanity check only: about 6.7 standard deviations of slack. It cannot prove fairness.
        assertTrue(Math.abs(heads - flips / 2) < 1_500, "heads " + heads + " of " + flips);
        assertEquals(24, random.newOutcomeId().length());
    }

    @Test
    void bundledConfigurationIsValidAndSafe() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File("src/main/resources/coinflip.yml"));
        CoinFlipConfig config = CoinFlipConfig.from(yaml);
        assertEquals(List.of(), config.problems());
        assertFalse(config.wageringBlocked());
        assertEquals(0, config.feeBasisPoints(), "the default fee is zero");
        assertFalse(config.allowFullBalance(), "all-in is off by default");
        assertFalse(config.webhookEnabled());
        assertFalse(config.achievementRewardsEnabled());
        YamlConfiguration messages = new YamlConfiguration();
        messages.load(new File("src/main/resources/coinflip-messages.yml"));
        for (String key : CoinFlipMessages.DEFAULTS.keySet()) {
            assertTrue(messages.isString(key), "coinflip-messages.yml is missing " + key);
        }
    }

    @Test
    void invalidMoneySettingsBlockWageringInsteadOfBeingRelaxed() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ECONOMY.FEE-PERCENT", 75);
        CoinFlipConfig badFee = CoinFlipConfig.from(yaml);
        assertTrue(badFee.wageringBlocked());
        assertTrue(badFee.problems().getFirst().startsWith("ECONOMY.FEE-PERCENT"));

        yaml = new YamlConfiguration();
        yaml.set("ECONOMY.MIN-STAKE", 500);
        yaml.set("ECONOMY.MAX-STAKE", 100);
        assertTrue(CoinFlipConfig.from(yaml).wageringBlocked());

        yaml = new YamlConfiguration();
        yaml.set("ECONOMY.MIN-STAKE", "lots");
        assertTrue(CoinFlipConfig.from(yaml).wageringBlocked());

        yaml = new YamlConfiguration();
        yaml.set("GUI.MAIN-SLOTS.CREATE", 22);
        CoinFlipConfig duplicate = CoinFlipConfig.from(yaml);
        assertEquals(20, duplicate.mainSlot("CREATE"), "duplicate slots fall back to the default layout");
        assertFalse(duplicate.wageringBlocked());
        assertFalse(duplicate.problems().isEmpty());
    }

    @Test
    void sidesParseFromCommandInput() {
        assertEquals(CoinSide.HEADS, CoinSide.parse("H").orElseThrow());
        assertEquals(CoinSide.TAILS, CoinSide.parse("tails").orElseThrow());
        assertTrue(CoinSide.parse("edge").isEmpty());
        assertNull(CoinFlipStats.empty(UUID.randomUUID(), "x").favouriteSide());
        assertEquals("ABC123", CoinFlipService.normalizeId(" #abc-123 "));
    }
}
