package com.fourinarow.rating;

import com.fourinarow.game.Player;
import com.fourinarow.room.GameMode;
import com.fourinarow.room.RatingUpdate;
import com.fourinarow.room.ResultRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Connects the rooms to the rating service: the rooms only know "player one won",
// and this class turns that into a rating update in the database.
@Component
public class RatingRecorder implements ResultRecorder {

    private static final Logger log = LoggerFactory.getLogger(RatingRecorder.class);

    private final RatingService ratingService;

    public RatingRecorder(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @Override
    public RatingUpdate record(long accountOne, long accountTwo, GameMode mode, Player winner) {
        EloRating.Outcome outcome = winner == null ? EloRating.Outcome.DRAW
                : winner == Player.ONE ? EloRating.Outcome.PLAYER_ONE_WINS
                : EloRating.Outcome.PLAYER_TWO_WINS;

        try {
            // A call through Spring, so the @Transactional on recordResult works.
            RatingService.Result result = ratingService.recordResult(accountOne, accountTwo, mode, outcome);
            return new RatingUpdate(result.rated(), result.playerOneChange(), result.playerTwoChange(),
                    result.playerOneRating(), result.playerTwoRating());
        } catch (RuntimeException e) {
            // The game itself is over either way; a database problem must not break the room.
            log.error("Could not record the result of a rated game", e);
            return null;
        }
    }
}