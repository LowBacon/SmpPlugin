// SmpPlugin by LowBacon
package com.bx.smpPlugin.gambling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntSupplier;

public final class DiceOdds {

    public enum Mode {
        DUEL, HIGH, LOW, NUMBER
    }

    public enum Result {
        WIN, PUSH, LOSE
    }

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

    public record Roll(int player, int house) {
    }

    private DiceOdds() {
    }

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

    public static double fairMultiplier(Bet bet, boolean tieRefunds) {
        return (1D - pushChance(bet, tieRefunds)) / winChance(bet);
    }

    public static double clampMultiplier(double configured, Bet bet, boolean tieRefunds) {
        double fair = fairMultiplier(bet, tieRefunds);
        double value = Double.isFinite(configured) ? configured : 1D;
        double clamped = Math.max(1D, Math.min(fair, value));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.DOWN).doubleValue();
    }

    public static double returnToPlayer(Bet bet, double multiplier, boolean tieRefunds) {
        return winChance(bet) * multiplier + pushChance(bet, tieRefunds);
    }

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

    public static long payoutCents(long stakeCents, Result result, double multiplier) {
        return switch (result) {
            case WIN -> BigDecimal.valueOf(stakeCents).multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.DOWN).longValueExact();
            case PUSH -> stakeCents;
            case LOSE -> 0L;
        };
    }

    public static String percent(double chance) {
        return String.format(Locale.ROOT, "%.1f%%", chance * 100D);
    }

    public static String multiplier(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + "x";
    }

    public static String face(int value) {
        return value >= 1 && value <= 6 ? String.valueOf((char) ('⚀' + value - 1)) : "?";
    }
}
