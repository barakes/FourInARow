package com.fourinarow.rating;

import com.fourinarow.IntegrationTest;
import com.fourinarow.account.Account;
import com.fourinarow.account.AccountRepository;
import com.fourinarow.account.LoginTokenRepository;
import com.fourinarow.game.Player;
import com.fourinarow.rating.EloRating.Outcome;
import com.fourinarow.room.GameMode;
import com.fourinarow.room.RatingUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingServiceTest extends IntegrationTest {

    @Autowired
    private RatingService service;

    @Autowired
    private RatingRecorder recorder;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @Autowired
    private MatchRepository matches;

    private Account dana;
    private Account tom;

    @BeforeEach
    void createPlayers() {
        matches.deleteAll();
        tokens.deleteAll();
        accounts.deleteAll();
        // The password hash doesn't matter here: these tests never log in.
        dana = accounts.save(new Account("Dana", "not-a-real-hash"));
        tom = accounts.save(new Account("Tom", "not-a-real-hash"));
    }

    @Test
    void aWinMovesPointsAndUpdatesTheStatistics() {
        RatingService.Result result = service.recordResult(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.PLAYER_ONE_WINS);

        assertThat(result.rated()).isTrue();
        assertThat(result.playerOneChange()).isEqualTo(16);
        assertThat(result.playerTwoChange()).isEqualTo(-16);

        // Read the accounts again from the database, to check that the changes were really saved.
        Account savedDana = accounts.findById(dana.getId()).orElseThrow();
        Account savedTom = accounts.findById(tom.getId()).orElseThrow();
        assertThat(savedDana.getRating()).isEqualTo(1016);
        assertThat(savedDana.getWins()).isEqualTo(1);
        assertThat(savedTom.getRating()).isEqualTo(984);
        assertThat(savedTom.getLosses()).isEqualTo(1);

        assertThat(matches.findAll()).singleElement().satisfies(match -> {
            assertThat(match.isRated()).isTrue();
            assertThat(match.getRatingChange()).isEqualTo(16);
            assertThat(match.getMode()).isEqualTo(GameMode.CLASSIC);
        });
    }

    @Test
    void aDrawCountsForBothPlayers() {
        service.recordResult(dana.getId(), tom.getId(), GameMode.CARDS, Outcome.DRAW);

        assertThat(accounts.findById(dana.getId()).orElseThrow().getDraws()).isEqualTo(1);
        assertThat(accounts.findById(tom.getId()).orElseThrow().getDraws()).isEqualTo(1);
        assertThat(accounts.findById(dana.getId()).orElseThrow().getRating()).isEqualTo(1000);
    }

    @Test
    void theFourthGameAgainstTheSameOpponentIsFriendly() {
        for (int game = 0; game < 3; game++) {
            assertThat(service.recordResult(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.PLAYER_ONE_WINS).rated())
                    .isTrue();
        }
        Account before = accounts.findById(dana.getId()).orElseThrow();

        RatingService.Result fourth = service.recordResult(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.PLAYER_ONE_WINS);

        assertThat(fourth.rated()).isFalse();
        assertThat(fourth.playerOneChange()).isZero();
        Account after = accounts.findById(dana.getId()).orElseThrow();
        assertThat(after.getRating()).isEqualTo(before.getRating());
        assertThat(after.getWins()).isEqualTo(3);
        // The friendly game is still kept in the history.
        assertThat(matches.count()).isEqualTo(4);
    }

    @Test
    void itDoesNotMatterWhoWasPlayerOne() {
        service.recordResult(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.DRAW);
        service.recordResult(tom.getId(), dana.getId(), GameMode.CLASSIC, Outcome.DRAW);
        service.recordResult(dana.getId(), tom.getId(), GameMode.CARDS, Outcome.DRAW);

        assertThat(service.recordResult(tom.getId(), dana.getId(), GameMode.CARDS, Outcome.DRAW).rated()).isFalse();
    }

    @Test
    void gamesOlderThan24HoursDoNotCount() {
        Instant longAgo = Instant.now().minus(Duration.ofHours(25));
        for (int game = 0; game < 3; game++) {
            matches.save(new Match(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.DRAW, true, 0, longAgo));
        }

        assertThat(service.recordResult(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.DRAW).rated()).isTrue();
    }

    @Test
    void theLimitIsPerOpponent() {
        Account noa = accounts.save(new Account("Noa", "not-a-real-hash"));
        for (int game = 0; game < 3; game++) {
            service.recordResult(dana.getId(), tom.getId(), GameMode.CLASSIC, Outcome.DRAW);
        }

        assertThat(service.recordResult(dana.getId(), noa.getId(), GameMode.CLASSIC, Outcome.DRAW).rated()).isTrue();
    }

    @Test
    void aPlayerCannotPlayAgainstThemselves() {
        assertThatThrownBy(() -> service.recordResult(dana.getId(), dana.getId(), GameMode.CLASSIC, Outcome.DRAW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- The recorder that the rooms use ----------

    @Test
    void theRecorderUsedByTheRoomsReallySavesTheResult() {
        RatingUpdate update = recorder.record(dana.getId(), tom.getId(), GameMode.CARDS, Player.TWO);

        assertThat(update.rated()).isTrue();
        assertThat(update.playerTwoChange()).isEqualTo(16);
        assertThat(update.playerTwoRating()).isEqualTo(1016);
        // If the transaction didn't work, the new rating would not be in the database.
        assertThat(accounts.findById(tom.getId()).orElseThrow().getRating()).isEqualTo(1016);
        assertThat(accounts.findById(tom.getId()).orElseThrow().getWins()).isEqualTo(1);
    }

    @Test
    void theRecorderTurnsAProblemIntoNullInsteadOfBreakingTheRoom() {
        RatingUpdate update = recorder.record(dana.getId(), dana.getId(), GameMode.CLASSIC, null);

        assertThat(update).isNull();
        assertThat(matches.count()).isZero();
    }
}