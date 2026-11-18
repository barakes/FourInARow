package com.fourinarow.cards;

import com.fourinarow.game.InvalidMoveException;
import com.fourinarow.game.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardGameTest {

    // ---------- The board ----------

    @ParameterizedTest
    @EnumSource(BoardSize.class)
    void everyPictureAppearsExactlyTwice(BoardSize boardSize) {
        CardGame game = new CardGame(boardSize, new Random(1));
        Map<Picture, Integer> counts = new HashMap<>();

        for (int row = 0; row < game.size(); row++) {
            for (int column = 0; column < game.size(); column++) {
                Picture picture = game.pictureAt(row, column);
                if (picture != null) {
                    counts.merge(picture, 1, Integer::sum);
                }
            }
        }

        assertThat(counts).hasSize(boardSize.pictureCount());
        assertThat(counts.values()).allMatch(count -> count == 2);
    }

    @ParameterizedTest
    @EnumSource(BoardSize.class)
    void equalPicturesNeverTouchEvenDiagonally(BoardSize boardSize) {
        for (int seed = 0; seed < 50; seed++) {
            CardGame game = new CardGame(boardSize, new Random(seed));

            for (int row = 0; row < game.size(); row++) {
                for (int column = 0; column < game.size(); column++) {
                    Picture picture = game.pictureAt(row, column);
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int r = row + dr;
                            int c = column + dc;
                            boolean neighbour = (dr != 0 || dc != 0) && r >= 0 && c >= 0 && r < game.size() && c < game.size();
                            if (picture != null && neighbour) {
                                assertThat(game.pictureAt(r, c)).isNotEqualTo(picture);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void onlyTheLargeBoardHasAFreeCellInTheMiddle() {
        CardGame large = new CardGame(BoardSize.LARGE, new Random(1));
        CardGame small = new CardGame(BoardSize.SMALL, new Random(1));

        assertThat(large.isFreeCell(3, 3)).isTrue();
        assertThat(large.pictureAt(3, 3)).isNull();
        assertThat(small.isFreeCell(3, 3)).isFalse();
    }

    @Test
    void deckHasTwoOfEachPictureAndTwoOfEachSpecialCard() {
        assertThat(countCards(BoardSize.SMALL)).isEqualTo(18 * 2 + 4);
        assertThat(countCards(BoardSize.LARGE)).isEqualTo(24 * 2 + 4);
    }

    // ---------- Playing cards ----------

    @Test
    void eachPlayerStartsWithThreeCards() {
        CardGame game = new CardGame(BoardSize.SMALL, new Random(1));

        assertThat(game.hand(Player.ONE)).hasSize(3);
        assertThat(game.hand(Player.TWO)).hasSize(3);
    }

    @Test
    void aPictureCardPlacesADiscOnAMatchingCellAndTheHandIsRefilled() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.of(Picture.DOG), Card.wild(), Card.remove());
        CardGame.Position dog = find(game, Picture.DOG, 0);

        CardGame.TurnResult result = game.play(Player.ONE, 0, dog.row(), dog.column());

        assertThat(game.discAt(dog.row(), dog.column())).isEqualTo(Player.ONE);
        assertThat(game.hand(Player.ONE)).hasSize(3);
        assertThat(result.currentPlayer()).isEqualTo(Player.TWO);
    }

    @Test
    void aPictureCardCanUseEitherOfItsTwoCells() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.of(Picture.DOG), Card.wild(), Card.remove());
        CardGame.Position secondDog = find(game, Picture.DOG, 1);

        game.play(Player.ONE, 0, secondDog.row(), secondDog.column());

        assertThat(game.discAt(secondDog.row(), secondDog.column())).isEqualTo(Player.ONE);
    }

    @Test
    void aPictureCardCannotBePlayedOnAnotherPicture() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.of(Picture.CAT), Card.wild(), Card.remove());
        CardGame.Position dog = find(game, Picture.DOG, 0);

        assertThatThrownBy(() -> game.play(Player.ONE, 0, dog.row(), dog.column()))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void aWildCardCanGoOnAnyEmptyCellButNotATakenOne() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.wild(), Card.wild(), Card.wild());
        game.putDiscForTesting(0, 0, Player.TWO);

        assertThatThrownBy(() -> game.play(Player.ONE, 0, 0, 0))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("taken");

        game.play(Player.ONE, 0, 5, 5);
        assertThat(game.discAt(5, 5)).isEqualTo(Player.ONE);
    }

    @Test
    void aWildCardCannotTakeTheFreeCell() {
        CardGame game = gameWithHand(BoardSize.LARGE, Card.wild(), Card.wild(), Card.wild());

        assertThatThrownBy(() -> game.play(Player.ONE, 0, 3, 3))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("free cell");
    }

    @Test
    void aRemoveCardRemovesAnOpponentsDiscButNotYourOwn() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.remove(), Card.wild(), Card.wild());
        game.putDiscForTesting(0, 0, Player.TWO);
        game.putDiscForTesting(1, 1, Player.ONE);

        assertThatThrownBy(() -> game.play(Player.ONE, 0, 1, 1))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("opponent");

        CardGame.TurnResult result = game.play(Player.ONE, 0, 0, 0);

        assertThat(result.removed()).isTrue();
        assertThat(game.discAt(0, 0)).isNull();
    }

    @Test
    void rejectsAMoveOutOfTurn() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.wild(), Card.wild(), Card.wild());

        assertThatThrownBy(() -> game.play(Player.TWO, 0, 0, 0))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("turn");
    }

    @Test
    void handsAreCopiesThatCannotBeChanged() {
        CardGame game = new CardGame(BoardSize.SMALL, new Random(1));

        assertThatThrownBy(() -> game.hand(Player.ONE).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---------- Winning ----------

    @Test
    void fourInARowWins() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.wild(), Card.wild(), Card.wild());
        game.putDiscForTesting(0, 0, Player.ONE);
        game.putDiscForTesting(0, 1, Player.ONE);
        game.putDiscForTesting(0, 2, Player.ONE);

        CardGame.TurnResult result = game.play(Player.ONE, 0, 0, 3);

        assertThat(result.status()).isEqualTo(CardGame.Status.WON);
        assertThat(result.winner()).isEqualTo(Player.ONE);
        assertThat(result.winningLine()).hasSize(4);
    }

    @Test
    void theFreeCellCountsForBothPlayers() {
        // Row 3 on the large board: discs at columns 1 and 2, the free cell at column 3, and the new disc at column 4.
        for (Player player : Player.values()) {
            CardGame game = new CardGame(BoardSize.LARGE, new Random(7));
            game.setCurrentPlayerForTesting(player);
            game.setHandForTesting(player, List.of(Card.wild(), Card.wild(), Card.wild()));
            game.putDiscForTesting(3, 1, player);
            game.putDiscForTesting(3, 2, player);

            CardGame.TurnResult result = game.play(player, 0, 3, 4);

            assertThat(result.status()).isEqualTo(CardGame.Status.WON);
            assertThat(result.winningLine()).contains(new CardGame.Position(3, 3));
        }
    }

    @Test
    void noMovesAfterTheGameIsOver() {
        CardGame game = gameWithHand(BoardSize.SMALL, Card.wild(), Card.wild(), Card.wild());
        game.putDiscForTesting(0, 0, Player.ONE);
        game.putDiscForTesting(0, 1, Player.ONE);
        game.putDiscForTesting(0, 2, Player.ONE);
        game.play(Player.ONE, 0, 0, 3);

        assertThat(game.legalMoves()).isEmpty();
        assertThatThrownBy(() -> game.play(game.getCurrentPlayer(), 0, 5, 5))
                .isInstanceOf(InvalidMoveException.class)
                .hasMessageContaining("over");
    }

    // ---------- The rare case ----------

    @Test
    void aPlayerWithNoPlayableCardLosesTheTurnAndGetsNewCards() {
        CardGame game = new CardGame(BoardSize.SMALL, new Random(7));
        game.setCurrentPlayerForTesting(Player.ONE);

        // Fill both cells of three pictures, then give player two exactly those three pictures.
        for (Picture picture : List.of(Picture.CAT, Picture.DOG, Picture.RABBIT)) {
            for (int i = 0; i < 2; i++) {
                CardGame.Position position = find(game, picture, i);
                game.putDiscForTesting(position.row(), position.column(), Player.ONE);
            }
        }
        game.setHandForTesting(Player.TWO, List.of(Card.of(Picture.CAT), Card.of(Picture.DOG), Card.of(Picture.RABBIT)));
        game.setHandForTesting(Player.ONE, List.of(Card.wild(), Card.wild(), Card.wild()));

        CardGame.Position empty = firstEmptyCell(game);
        CardGame.TurnResult result = game.play(Player.ONE, 0, empty.row(), empty.column());

        assertThat(result.skippedPlayers()).containsExactly(Player.TWO);
        assertThat(result.currentPlayer()).isEqualTo(Player.ONE);
        assertThat(game.hand(Player.TWO)).hasSize(3)
                .doesNotContain(Card.of(Picture.CAT), Card.of(Picture.DOG), Card.of(Picture.RABBIT));
    }

    // ---------- Whole games ----------

    @ParameterizedTest
    @EnumSource(BoardSize.class)
    void randomGamesAlwaysFinishAndHandsStayFull(BoardSize boardSize) {
        for (int seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            CardGame game = new CardGame(boardSize, random);

            while (game.getStatus() == CardGame.Status.IN_PROGRESS) {
                assertThat(game.hand(Player.ONE)).hasSize(3);
                assertThat(game.hand(Player.TWO)).hasSize(3);

                List<CardGame.Move> moves = game.legalMoves();
                assertThat(moves).isNotEmpty();

                CardGame.Move move = moves.get(random.nextInt(moves.size()));
                game.play(game.getCurrentPlayer(), move.handIndex(), move.row(), move.column());
            }

            assertThat(game.getStatus()).isIn(CardGame.Status.WON, CardGame.Status.DRAW);
        }
    }

    // ---------- Helpers ----------

    // A game where it is player one's turn, holding exactly these cards.
    private static CardGame gameWithHand(BoardSize boardSize, Card... cards) {
        CardGame game = new CardGame(boardSize, new Random(1));
        game.setCurrentPlayerForTesting(Player.ONE);
        game.setHandForTesting(Player.ONE, List.of(cards));
        return game;
    }

    // The first (occurrence 0) or second (occurrence 1) cell with this picture.
    private static CardGame.Position find(CardGame game, Picture picture, int occurrence) {
        List<CardGame.Position> found = new ArrayList<>();
        for (int row = 0; row < game.size(); row++) {
            for (int column = 0; column < game.size(); column++) {
                if (game.pictureAt(row, column) == picture) {
                    found.add(new CardGame.Position(row, column));
                }
            }
        }
        return found.get(occurrence);
    }

    private static CardGame.Position firstEmptyCell(CardGame game) {
        for (int row = 0; row < game.size(); row++) {
            for (int column = 0; column < game.size(); column++) {
                if (game.discAt(row, column) == null && !game.isFreeCell(row, column)) {
                    return new CardGame.Position(row, column);
                }
            }
        }
        throw new IllegalStateException("The board is full");
    }

    private static int countCards(BoardSize boardSize) {
        Deck deck = new Deck(BoardLayout.usedPictures(boardSize), new Random(1));
        int count = 0;
        while (deck.draw().isPresent()) {
            count++;
        }
        return count;
    }
}