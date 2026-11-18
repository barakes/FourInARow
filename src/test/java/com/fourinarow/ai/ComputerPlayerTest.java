package com.fourinarow.ai;

import com.fourinarow.game.Game;
import com.fourinarow.game.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComputerPlayerTest {

    @Test
    void takesAnImmediateWin() {
        // Player one has three in a row at the bottom (columns 0-2) and can win in column 3.
        Game game = playAll(0, 0, 1, 1, 2, 2);

        assertAlwaysChooses(game, 3);
    }

    @Test
    void blocksTheOpponentsImmediateWin() {
        // Player one threatens column 3; player two must block it.
        Game game = playAll(0, 6, 1, 6, 2);

        assertAlwaysChooses(game, 3);
    }

    @Test
    void prefersWinningOverBlocking() {
        // Player one threatens column 3, but player two can win right now in column 5.
        Game game = playAll(0, 5, 1, 5, 2, 5, 6);

        assertAlwaysChooses(game, 5);
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void alwaysChoosesALegalMoveUntilTheGameEnds(Difficulty difficulty) {
        for (int seed = 0; seed < 5; seed++) {
            Game game = new Game();
            ComputerPlayer computer = new ComputerPlayer(difficulty, new Random(seed));

            // Game.play throws if a move is illegal, so finishing the game proves every move was legal.
            while (game.getStatus() == Game.Status.IN_PROGRESS) {
                game.play(game.getCurrentPlayer(), computer.chooseColumn(game));
            }

            assertThat(game.getStatus()).isIn(Game.Status.WON, Game.Status.DRAW);
        }
    }

    @Test
    void hardBeatsEasy() {
        for (int seed = 0; seed < 5; seed++) {
            for (Player hardPlays : Player.values()) {
                ComputerPlayer hard = new ComputerPlayer(Difficulty.HARD, new Random(seed));
                ComputerPlayer easy = new ComputerPlayer(Difficulty.EASY, new Random(100 + seed));
                Game game = new Game();

                while (game.getStatus() == Game.Status.IN_PROGRESS) {
                    ComputerPlayer current = game.getCurrentPlayer() == hardPlays ? hard : easy;
                    game.play(game.getCurrentPlayer(), current.chooseColumn(game));
                }

                assertThat(game.getWinner()).isEqualTo(hardPlays);
            }
        }
    }

    @Test
    void doesNotChangeTheRealGameWhileThinking() {
        Game game = playAll(3, 3, 2);

        new ComputerPlayer(Difficulty.HARD, new Random(1)).chooseColumn(game);

        assertThat(game.getCurrentPlayer()).isEqualTo(Player.TWO);
        assertThat(game.getCell(0, 4)).isNull();
        assertThat(game.getCell(2, 3)).isNull();
    }

    @Test
    void refusesToMoveWhenTheGameIsOver() {
        Game game = playAll(0, 1, 0, 1, 0, 1, 0);
        ComputerPlayer computer = new ComputerPlayer(Difficulty.HARD);

        assertThatThrownBy(() -> computer.chooseColumn(game)).isInstanceOf(IllegalStateException.class);
    }

    // The hard level never plays random moves, so with any seed it must find the forced move.
    private static void assertAlwaysChooses(Game game, int expectedColumn) {
        for (int seed = 0; seed < 10; seed++) {
            ComputerPlayer computer = new ComputerPlayer(Difficulty.HARD, new Random(seed));
            assertThat(computer.chooseColumn(game)).isEqualTo(expectedColumn);
        }
    }

    private static Game playAll(int... columns) {
        Game game = new Game();
        for (int column : columns) {
            game.play(game.getCurrentPlayer(), column);
        }
        return game;
    }
}