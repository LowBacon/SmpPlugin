package com.bx.smpPlugin.ranked;

/**
 * Elo rating maths, free of any server call so it can be tested.
 *
 * <p>The expected score of A against B is {@code 1 / (1 + 10^((B - A) / 400))}; after the match A
 * moves by {@code K * (actual - expected)}, where actual is 1 for a win, 0.5 for a draw and 0 for a
 * loss. Changes are rounded to whole points and both players' changes are worked out from the
 * ratings they had before the match.
 */
public final class Elo {

    /** The K-factor rules and the lowest rating allowed. */
    public record Settings(int kFactor, int kProvisional, int provisionalGames, int kHigh, int highRating, int floor) {

        public static Settings defaults() {
            return new Settings(24, 40, 10, 16, 2000, 100);
        }

        /** The K-factor for a player with this rating and this many games played. */
        public int kFor(int rating, int gamesPlayed) {
            if (gamesPlayed < provisionalGames) {
                return kProvisional;
            }
            return rating >= highRating ? kHigh : kFactor;
        }
    }

    public enum Outcome {
        PLAYER_ONE_WINS(1D), PLAYER_TWO_WINS(0D), DRAW(0.5D);

        private final double playerOneScore;

        Outcome(double playerOneScore) {
            this.playerOneScore = playerOneScore;
        }

        public double playerOneScore() {
            return playerOneScore;
        }
    }

    /** New ratings for both players. */
    public record Result(int playerOneAfter, int playerTwoAfter) {
    }

    private Elo() {
    }

    /** The chance (0 to 1) that a player rated {@code rating} beats one rated {@code opponent}. */
    public static double expected(int rating, int opponent) {
        return 1D / (1D + Math.pow(10D, (opponent - rating) / 400D));
    }

    public static Result rate(int playerOne, int playerOneGames, int playerTwo, int playerTwoGames,
                              Outcome outcome, Settings settings) {
        double scoreOne = outcome.playerOneScore();
        double expectedOne = expected(playerOne, playerTwo);
        int changeOne = (int) Math.round(settings.kFor(playerOne, playerOneGames) * (scoreOne - expectedOne));
        int changeTwo = (int) Math.round(settings.kFor(playerTwo, playerTwoGames) * ((1D - scoreOne) - (1D - expectedOne)));
        return new Result(Math.max(settings.floor(), playerOne + changeOne), Math.max(settings.floor(), playerTwo + changeTwo));
    }
}
