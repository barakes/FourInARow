package com.fourinarow.rating;

import com.fourinarow.rating.EloRating.Change;
import com.fourinarow.rating.EloRating.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// No database here: plain math, so these tests are instant.
class EloRatingTest {

    @Test
    void equalPlayersMove16Points() {
        assertThat(EloRating.calculate(1000, 1000, Outcome.PLAYER_ONE_WINS)).isEqualTo(new Change(16, -16));
        assertThat(EloRating.calculate(1000, 1000, Outcome.PLAYER_TWO_WINS)).isEqualTo(new Change(-16, 16));
    }

    @Test
    void aDrawBetweenEqualPlayersChangesNothing() {
        assertThat(EloRating.calculate(1000, 1000, Outcome.DRAW)).isEqualTo(new Change(0, 0));
    }

    @Test
    void beatingAStrongerPlayerIsWorthMore() {
        // A surprise: the weaker player wins.
        assertThat(EloRating.calculate(1000, 1200, Outcome.PLAYER_ONE_WINS)).isEqualTo(new Change(24, -24));
        // Expected: the stronger player wins.
        assertThat(EloRating.calculate(1200, 1000, Outcome.PLAYER_ONE_WINS)).isEqualTo(new Change(8, -8));
    }

    @Test
    void inADrawTheWeakerPlayerGainsALittle() {
        assertThat(EloRating.calculate(1000, 1200, Outcome.DRAW)).isEqualTo(new Change(8, -8));
    }

    @Test
    void losingToAMuchStrongerPlayerCostsLittle() {
        assertThat(EloRating.calculate(1000, 1400, Outcome.PLAYER_TWO_WINS)).isEqualTo(new Change(-3, 3));
    }

    @Test
    void pointsAreNeverCreatedOrLost() {
        for (int one = 600; one <= 1800; one += 137) {
            for (int two = 600; two <= 1800; two += 151) {
                for (Outcome outcome : Outcome.values()) {
                    Change change = EloRating.calculate(one, two, outcome);
                    assertThat(change.playerOne() + change.playerTwo()).isZero();
                }
            }
        }
    }

    @Test
    void theExpectedScoreFollowsTheRatingGap() {
        assertThat(EloRating.expectedScore(1000, 1000)).isCloseTo(0.5, within(0.0001));
        assertThat(EloRating.expectedScore(1000, 1200)).isCloseTo(0.2403, within(0.0001));
        assertThat(EloRating.expectedScore(1200, 1000)).isCloseTo(0.7597, within(0.0001));
    }
}