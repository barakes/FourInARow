package com.fourinarow.cards;

import com.fourinarow.ai.CardComputerPlayer;
import com.fourinarow.ai.Difficulty;
import com.fourinarow.game.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

// Lives in the "cards" package so it can use the card game's test helpers.
class CardComputerPlayerTest {

    @Test
    void takesAWinWhenItHasOne() {
        for (int seed = 0; seed < 10; seed++) {
            CardGame game = gameForPlayerOne();
            game.putDiscForTesting(0, 0, Player.ONE);
            game.putDiscForTesting(0, 1, Player.ONE);
            game.putDiscForTesting(0, 2, Player.ONE);
            game.setHandForTesting(Player.ONE, List.of(Card.of(Picture.CAT), Card.wild(), Card.of(Picture.DOG)));

            CardGame.Move move = new CardComputerPlayer(Difficulty.HARD, new Random(seed)).chooseMove(game);
            game.play(Player.ONE, move.handIndex(), move.row(), move.column());

            assertThat(game.getStatus()).isEqualTo(CardGame.Status.WON);
        }
    }

    @Test
    void blocksAnOpponentWhoIsAboutToWin() {
        for (int seed = 0; seed < 10; seed++) {
            CardGame game = gameForPlayerOne();
            game.putDiscForTesting(5, 0, Player.TWO);
            game.putDiscForTesting(5, 1, Player.TWO);
            game.putDiscForTesting(5, 2, Player.TWO);
            game.setHandForTesting(Player.ONE, List.of(Card.wild(), Card.wild(), Card.wild()));

            CardGame.Move move = new CardComputerPlayer(Difficulty.HARD, new Random(seed)).chooseMove(game);

            assertThat(move.row()).isEqualTo(5);
            assertThat(move.column()).isEqualTo(3);
        }
    }

    @Test
    void removesADiscWhenItCannotBlockAnyOtherWay() {
        for (int seed = 0; seed < 10; seed++) {
            CardGame game = gameForPlayerOne();
            game.putDiscForTesting(5, 0, Player.TWO);
            game.putDiscForTesting(5, 1, Player.TWO);
            game.putDiscForTesting(5, 2, Player.TWO);

            // A remove card, plus two picture cards that can't reach the blocking cell (5, 3).
            Picture blockingPicture = game.pictureAt(5, 3);
            List<Card> hand = new ArrayList<>();
            hand.add(Card.remove());
            for (Picture picture : BoardLayout.usedPictures(BoardSize.SMALL)) {
                if (hand.size() < 3 && picture != blockingPicture) {
                    hand.add(Card.of(picture));
                }
            }
            game.setHandForTesting(Player.ONE, hand);

            CardGame.Move move = new CardComputerPlayer(Difficulty.HARD, new Random(seed)).chooseMove(game);

            assertThat(move.handIndex()).isZero();
            assertThat(move.row()).isEqualTo(5);
            assertThat(move.column()).isBetween(0, 2);
        }
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void alwaysChoosesALegalMoveUntilTheGameEnds(Difficulty difficulty) {
        for (int seed = 0; seed < 20; seed++) {
            CardGame game = new CardGame(BoardSize.LARGE, new Random(seed));
            CardComputerPlayer computer = new CardComputerPlayer(difficulty, new Random(seed));

            // CardGame.play throws if a move is illegal, so finishing the game proves every move was legal.
            while (game.getStatus() == CardGame.Status.IN_PROGRESS) {
                CardGame.Move move = computer.chooseMove(game);
                game.play(game.getCurrentPlayer(), move.handIndex(), move.row(), move.column());
            }

            assertThat(game.getStatus()).isIn(CardGame.Status.WON, CardGame.Status.DRAW);
        }
    }

    @Test
    void hardUsuallyBeatsRandomMoves() {
        int hardWins = 0;
        int games = 100;

        for (int seed = 0; seed < games; seed++) {
            Random random = new Random(seed);
            CardGame game = new CardGame(BoardSize.SMALL, random);
            CardComputerPlayer hard = new CardComputerPlayer(Difficulty.HARD, new Random(seed));

            while (game.getStatus() == CardGame.Status.IN_PROGRESS) {
                CardGame.Move move;
                if (game.getCurrentPlayer() == Player.ONE) {
                    move = hard.chooseMove(game);
                } else {
                    List<CardGame.Move> moves = game.legalMoves();
                    move = moves.get(random.nextInt(moves.size()));
                }
                game.play(game.getCurrentPlayer(), move.handIndex(), move.row(), move.column());
            }

            if (game.getWinner() == Player.ONE) {
                hardWins++;
            }
        }

        // Cards add luck, so it can't win every game, but it should win most of them.
        assertThat(hardWins).isGreaterThan(70);
    }

    @Test
    void doesNotChangeTheRealGameWhileThinking() {
        CardGame game = gameForPlayerOne();
        game.putDiscForTesting(2, 2, Player.TWO);
        game.setHandForTesting(Player.ONE, List.of(Card.wild(), Card.remove(), Card.wild()));

        new CardComputerPlayer(Difficulty.HARD, new Random(1)).chooseMove(game);

        assertThat(game.discAt(2, 2)).isEqualTo(Player.TWO);
        assertThat(game.hand(Player.ONE)).hasSize(3);
        assertThat(game.getCurrentPlayer()).isEqualTo(Player.ONE);
    }

    private static CardGame gameForPlayerOne() {
        CardGame game = new CardGame(BoardSize.SMALL, new Random(1));
        game.setCurrentPlayerForTesting(Player.ONE);
        return game;
    }
}