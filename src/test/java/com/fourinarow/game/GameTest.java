package com.fourinarow.game;

import com.fourinarow.game.Board.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

    @Test
    void playerOneStartsAndTurnsAlternate() {
        Game game = new Game();

        assertThat(game.getCurrentPlayer()).isEqualTo(Player.ONE);
        game.play(Player.ONE, 3);
        assertThat(game.getCurrentPlayer()).isEqualTo(Player.TWO);
        game.play(Player.TWO, 3);
        assertThat(game.getCurrentPlayer()).isEqualTo(Player.ONE);
    }

    @Test
    void discsStackFromTheBottom() {
        Game game = new Game();

        assertThat(game.play(Player.ONE, 0).row()).isZero();
        assertThat(game.play(Player.TWO, 0).row()).isEqualTo(1);
        assertThat(game.getCell(0, 0)).isEqualTo(Player.ONE);
        assertThat(game.getCell(1, 0)).isEqualTo(Player.TWO);
    }

    @Test
    void detectsHorizontalWin() {
        Game game = playAll(0, 0, 1, 1, 2, 2, 3);

        assertWonByPlayerOne(game,
                new Position(0, 0), new Position(0, 1), new Position(0, 2), new Position(0, 3));
    }

    @Test
    void detectsVerticalWin() {
        Game game = playAll(0, 1, 0, 1, 0, 1, 0);

        assertWonByPlayerOne(game,
                new Position(0, 0), new Position(1, 0), new Position(2, 0), new Position(3, 0));
    }

    @Test
    void detectsDiagonalWinGoingUpRight() {
        Game game = playAll(0, 1, 1, 2, 2, 3, 2, 3, 3, 6, 3);

        assertWonByPlayerOne(game,
                new Position(0, 0), new Position(1, 1), new Position(2, 2), new Position(3, 3));
    }

    @Test
    void detectsDiagonalWinGoingUpLeft() {
        Game game = playAll(3, 2, 2, 1, 1, 0, 1, 0, 0, 6, 0);

        assertWonByPlayerOne(game,
                new Position(0, 3), new Position(1, 2), new Position(2, 1), new Position(3, 0));
    }

    @Test
    void detectsWinWhenTheLastDiscFillsAGapInTheMiddle() {
        // Player one has columns 0, 1 and 3, and wins by filling column 2.
        Game game = playAll(0, 0, 1, 1, 3, 3, 2);

        assertWonByPlayerOne(game,
                new Position(0, 0), new Position(0, 1), new Position(0, 2), new Position(0, 3));
    }

    @Test
    void endsInADrawWhenTheBoardIsFullWithoutAWinner() {
        Game game = playAll(
                0, 1, 0, 1, 0, 1,
                1, 0, 1, 0, 1, 0,
                2, 3, 2, 3, 2, 3,
                3, 2, 3, 2, 3, 2,
                4, 5, 4, 5, 4, 5,
                5, 4, 5, 4, 5, 4,
                6, 6, 6, 6, 6, 6);

        assertThat(game.getStatus()).isEqualTo(Game.Status.DRAW);
        assertThat(game.getWinner()).isNull();
        assertThat(game.getWinningLine()).isEmpty();
    }

    @Test
    void rejectsAMoveOutOfTurn() {
        Game game = new Game();

        assertThatThrownBy(() -> game.play(Player.TWO, 0))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("turn");
    }

    @Test
    void rejectsAFullColumn() {
        Game game = playAll(0, 0, 0, 0, 0, 0);

        assertThat(game.canPlay(0)).isFalse();
        assertThatThrownBy(() -> game.play(game.getCurrentPlayer(), 0))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("full");
    }

    @Test
    void rejectsAColumnOutsideTheBoard() {
        Game game = new Game();

        assertThatThrownBy(() -> game.play(Player.ONE, -1)).isInstanceOf(InvalidMoveException.class);
        assertThatThrownBy(() -> game.play(Player.ONE, 7)).isInstanceOf(InvalidMoveException.class);
    }

    @Test
    void rejectsMovesAfterTheGameIsOver() {
        Game game = playAll(0, 1, 0, 1, 0, 1, 0);

        assertThatThrownBy(() -> game.play(game.getCurrentPlayer(), 5))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("over");
    }

    @Test
    void supportsOtherBoardSizes() {
        Game game = new Game(5, 9);

        assertThat(game.getRows()).isEqualTo(5);
        assertThat(game.getColumns()).isEqualTo(9);
        assertThat(game.play(Player.ONE, 8).column()).isEqualTo(8);
    }

    @Test
    void rejectsABoardTooSmallToWinOn() {
        assertThatThrownBy(() -> new Game(3, 7)).isInstanceOf(IllegalArgumentException.class);
    }

    // Plays the given columns in order, alternating players.
    private static Game playAll(int... columns) {
        Game game = new Game();
        for (int column : columns) {
            game.play(game.getCurrentPlayer(), column);
        }
        return game;
    }

    private static void assertWonByPlayerOne(Game game, Position... expectedLine) {
        assertThat(game.getStatus()).isEqualTo(Game.Status.WON);
        assertThat(game.getWinner()).isEqualTo(Player.ONE);
        assertThat(game.getWinningLine()).containsExactlyInAnyOrder(expectedLine);
    }
}