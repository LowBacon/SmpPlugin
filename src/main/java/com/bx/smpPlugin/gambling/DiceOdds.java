package com.bx.smpPlugin.gambling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * The rules and the maths of /dice, with no server code so they can be tested.
 *
 * <p>Every bet uses fair six-sided dice. The multiplier is the total a win pays back, the bet
 * included (a 1.9x win on $100 pays $190: $90 profit). Chances and fair multipliers:
 * <pre>
 *   Bet        Wins when                       Win chance   Fair multiplier
 *   DUEL       your die beats the house die    15/36        2.0x  (a tie, 6/36, gives the bet back)
 *   HIGH       your die shows 4, 5 or 6        1/2          2.0x
 *   LOW        your die shows 1, 2 or 3        1/2          2.0x
 *   NUMBER n   your die shows exactly n        1/6          6.0x
 * </pre>
 * A configured multiplier is never allowed above the fair one, so on average the house never pays
 * out more than it takes in. "Return to player" is the average a bet pays back per coin staked.
 */
public final class DiceOdds {

    public enum Mode {
        DUEL, HIGH, LOW, NUMBER
    }

    public enum Result {
        WIN, PUSH, LOSE
    }

    /** What the player bets on; {@code number} is only used by {@link Mode#NUMBER}. */
    public record Bet(Mode mode, int number) {

        public static Bet duel() {
            return new Bet(Mode.DUEL, 0);
        }

        public String describe() {
            return switch (mode) {
                case DUEL -> "beat the house";
                case HIGH -> "high (4-6)";
                case LOW -> "low (1-3)";
                case NUMBER -> "exactly " + number;
            };
        }
    }

    /** The dice that were rolled. {@code house} is 0 unless the bet is a duel. */
    public record Roll(int player, int house) {
    }

    private DiceOdds() {
    }

    /** Reads what a player typed after the amount: nothing or duel, high, low, or a number 1-6. */
    public static Optional<Bet> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(Bet.duel());
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "duel", "vs", "house", "versus" -> Optional.of(Bet.duel());
            case "high", "hi", "h", "over" -> Optional.of(new Bet(Mode.HIGH, 0));
            case "low", "lo", "l", "under" -> Optional.of(new Bet(Mode.LOW, 0));
            default -> {
                if (text.matches("[1-6]")) {
                    yield Optional.of(new Bet(Mode.NUMBER, Integer.parseInt(text)));
                }
                yield Optional.empty();
            }
        };
    }

    public static double winChance(Bet bet) {
        return switch (bet.mode()) {
            case DUEL -> 15D / 36D;
            case HIGH, LOW -> 0.5D;
            case NUMBER -> 1D / 6D;
        };
    }

    public static double pushChance(Bet bet, boolean tieRefunds) {
        return bet.mode() == Mode.DUEL && tieRefunds ? 6D / 36D : 0D;
    }

    /** The multiplier at which the bet is exactly break-even on average. */
    public static double fairMultiplier(Bet bet, boolean tieRefunds) {
        return (1D - pushChance(bet, tieRefunds)) / winChance(bet);
    }

    /** A configured multiplier kept between 1.0x and the fair multiplier, rounded to two decimals. */
    public static double clampMultiplier(double configured, Bet bet, boolean tieRefunds) {
        double fair = fairMultiplier(bet, tieRefunds);
        double value = Double.isFinite(configured) ? configured : 1D;
        double clamped = Math.max(1D, Math.min(fair, value));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.DOWN).doubleValue();
    }

    /** Average paid back per coin staked, e.g. 0.95 = 95%. */
    public static double returnToPlayer(Bet bet, double multiplier, boolean tieRefunds) {
        return winChance(bet) * multiplier + pushChance(bet, tieRefunds);
    }

    /** Rolls the dice a bet needs with the given die (which must return 1 to 6). */
    public static Roll roll(Bet bet, IntSupplier die) {
        int player = die.getAsInt();
        int house = bet.mode() == Mode.DUEL ? die.getAsInt() : 0;
        return new Roll(player, house);
    }

    public static Result judge(Bet bet, Roll roll, boolean tieRefunds) {
        return switch (bet.mode()) {
            case DUEL -> roll.player() > roll.house() ? Result.WIN
                    : roll.player() == roll.house() && tieRefunds ? Result.PUSH : Result.LOSE;
            case HIGH -> roll.player() >= 4 ? Result.WIN : Result.LOSE;
            case LOW -> roll.player() <= 3 ? Result.WIN : Result.LOSE;
            case NUMBER -> roll.player() == bet.number() ? Result.WIN : Result.LOSE;
        };
    }

    /** What a bet pays back in cents: the stake times the multiplier for a win, the stake for a push, else 0. */
    public static long payoutCents(long stakeCents, Result result, double multiplier) {
        return switch (result) {
            case WIN -> BigDecimal.valueOf(stakeCents).multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.DOWN).longValueExact();
            case PUSH -> stakeCents;
            case LOSE -> 0L;
        };
    }

    /** "41.7%" */
    public static String percent(double chance) {
        return String.format(Locale.ROOT, "%.1f%%", chance * 100D);
    }

    /** "1.9x" or "5.75x" */
    public static String multiplier(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + "x";
    }

    /** The die face character for 1-6 (⚀ to ⚅). */
    public static String face(int value) {
        return value >= 1 && value <= 6 ? String.valueOf((char) ('⚀' + value - 1)) : "?";
    }
}
