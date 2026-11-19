package com.fourinarow.rating;

import com.fourinarow.account.Account;
import com.fourinarow.account.AccountRepository;
import com.fourinarow.room.GameMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Records the result of a game between two registered players, and updates their ratings.
@Service
public class RatingService {

    // The same two players get at most 3 rated games in 24 hours; after that their games are friendly.
    // This stops two friends from "passing" points to each other again and again.
    public static final int RATED_GAMES_PER_PAIR = 3;
    public static final Duration PAIR_LIMIT_WINDOW = Duration.ofHours(24);

    // What changed, so the players can be told. The ratings are the new ones.
    public record Result(boolean rated, int playerOneChange, int playerTwoChange,
                         int playerOneRating, int playerTwoRating) {
    }

    private final AccountRepository accounts;
    private final MatchRepository matches;
    private final Clock clock;

    public RatingService(AccountRepository accounts, MatchRepository matches, Clock clock) {
        this.accounts = accounts;
        this.matches = matches;
        this.clock = clock;
    }

    // One transaction: both accounts and the match are saved together, or nothing is saved.
    @Transactional
    public Result recordResult(Long playerOneId, Long playerTwoId, GameMode mode, EloRating.Outcome outcome) {
        if (playerOneId.equals(playerTwoId)) {
            throw new IllegalArgumentException("A player cannot play against themselves");
        }

        Instant now = clock.instant();
        Account one = accounts.findById(playerOneId).orElseThrow();
        Account two = accounts.findById(playerTwoId).orElseThrow();

        long recentRatedGames = matches.countRatedGamesBetween(one.getId(), two.getId(), now.minus(PAIR_LIMIT_WINDOW));
        boolean rated = recentRatedGames < RATED_GAMES_PER_PAIR;

        EloRating.Change change = rated
                ? EloRating.calculate(one.getRating(), two.getRating(), outcome)
                : new EloRating.Change(0, 0);

        if (rated) {
            int newRatingOne = one.getRating() + change.playerOne();
            int newRatingTwo = two.getRating() + change.playerTwo();
            switch (outcome) {
                case PLAYER_ONE_WINS -> {
                    one.recordWin(newRatingOne);
                    two.recordLoss(newRatingTwo);
                }
                case PLAYER_TWO_WINS -> {
                    one.recordLoss(newRatingOne);
                    two.recordWin(newRatingTwo);
                }
                case DRAW -> {
                    one.recordDraw(newRatingOne);
                    two.recordDraw(newRatingTwo);
                }
            }
            // No save() needed for the accounts: JPA notices the changes and writes them when the transaction ends.
        }

        matches.save(new Match(one.getId(), two.getId(), mode, outcome, rated, change.playerOne(), now));
        return new Result(rated, change.playerOne(), change.playerTwo(), one.getRating(), two.getRating());
    }
}