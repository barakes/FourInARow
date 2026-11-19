package com.fourinarow.rating;

// The Elo rating system, as used in chess.
// Pure math, no database: easy to test.
public final class EloRating {

    // How fast ratings move. 32 is a common choice for casual games.
    public static final int K = 32;

    public enum Outcome {
        PLAYER_ONE_WINS,
        PLAYER_TWO_WINS,
        DRAW
    }

    // How many points each player gains (positive) or loses (negative).
    // The two numbers always add up to 0: points move from one player to the other.
    public record Change(int playerOne, int playerTwo) {
    }

    private EloRating() {
    }

    public static Change calculate(int ratingOne, int ratingTwo, Outcome outcome) {
        double scoreOne = switch (outcome) {
            case PLAYER_ONE_WINS -> 1.0;
            case PLAYER_TWO_WINS -> 0.0;
            case DRAW -> 0.5;
        };

        int change = (int) Math.round(K * (scoreOne - expectedScore(ratingOne, ratingTwo)));
        return new Change(change, -change);
    }

    // The chance that a player with "rating" beats a player with "opponentRating" (between 0 and 1).
    // Equal ratings give 0.5; 200 points less gives about 0.24.
    static double expectedScore(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
    }
}