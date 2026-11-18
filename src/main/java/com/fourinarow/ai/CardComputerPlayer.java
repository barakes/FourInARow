package com.fourinarow.ai;

import com.fourinarow.cards.Card;
import com.fourinarow.cards.CardGame;
import com.fourinarow.game.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// The computer player for the card game.
// It can't look ahead like in the classic game: it doesn't know the opponent's cards or the next card it will draw.
// Instead it tries every legal move, scores the board after it, and picks the best one.
public class CardComputerPlayer {

    private static final int WIN_SCORE = 1_000_000;
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    // Special cards are rare, so using one on an ordinary move costs a little.
    private static final int SPECIAL_CARD_COST = 15;

    private final Difficulty difficulty;
    private final Random random;

    public CardComputerPlayer(Difficulty difficulty) {
        this(difficulty, new Random());
    }

    public CardComputerPlayer(Difficulty difficulty, Random random) {
        this.difficulty = difficulty;
        this.random = random;
    }

    public CardGame.Move chooseMove(CardGame game) {
        List<CardGame.Move> moves = game.legalMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException("There is no legal move");
        }

        // Lower levels sometimes play a random move, so children can beat them.
        if (random.nextDouble() < difficulty.randomMoveChance()) {
            return moves.get(random.nextInt(moves.size()));
        }

        Player me = game.getCurrentPlayer();
        List<Card> hand = game.hand(me);
        Player[][] board = snapshot(game);

        int bestScore = Integer.MIN_VALUE;
        List<CardGame.Move> bestMoves = new ArrayList<>();

        for (CardGame.Move move : moves) {
            int score = scoreMove(game, board, hand.get(move.handIndex()), move, me);
            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }

        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    // Plays the move on a copy of the board, scores the result, and takes it back.
    private int scoreMove(CardGame game, Player[][] board, Card card, CardGame.Move move, Player me) {
        int row = move.row();
        int column = move.column();
        Player before = board[row][column];

        board[row][column] = card.kind() == Card.Kind.REMOVE ? null : me;

        int score;
        if (card.kind() != Card.Kind.REMOVE && isWin(game, board, row, column, me)) {
            score = WIN_SCORE;
        } else {
            score = evaluate(game, board, me);
            if (card.kind() != Card.Kind.PICTURE) {
                score -= SPECIAL_CARD_COST;
            }
        }

        board[row][column] = before;
        return score;
    }

    // Every group of four cells in a line that only one player uses is a chance for that player.
    // The opponent's chances count a bit more, so the computer prefers blocking a threat over building its own.
    private static int evaluate(CardGame game, Player[][] board, Player me) {
        int size = game.size();
        int score = 0;

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                for (int[] direction : DIRECTIONS) {
                    int endRow = row + direction[0] * (CardGame.WIN_LENGTH - 1);
                    int endColumn = column + direction[1] * (CardGame.WIN_LENGTH - 1);
                    if (endRow < 0 || endRow >= size || endColumn < 0 || endColumn >= size) {
                        continue;
                    }

                    int mine = 0;
                    int theirs = 0;
                    for (int i = 0; i < CardGame.WIN_LENGTH; i++) {
                        int r = row + direction[0] * i;
                        int c = column + direction[1] * i;
                        if (game.isFreeCell(r, c)) {
                            // The free cell helps both players equally.
                            mine++;
                            theirs++;
                        } else if (board[r][c] == me) {
                            mine++;
                        } else if (board[r][c] != null) {
                            theirs++;
                        }
                    }

                    boolean hasMyDisc = containsDisc(board, row, column, direction, me);
                    boolean hasTheirDisc = containsDisc(board, row, column, direction, me.opponent());

                    if (!hasTheirDisc) {
                        score += windowScore(mine, 1);
                    }
                    if (!hasMyDisc) {
                        score -= windowScore(theirs, 2);
                    }
                }
            }
        }
        return score;
    }

    private static boolean containsDisc(Player[][] board, int row, int column, int[] direction, Player player) {
        for (int i = 0; i < CardGame.WIN_LENGTH; i++) {
            if (board[row + direction[0] * i][column + direction[1] * i] == player) {
                return true;
            }
        }
        return false;
    }

    private static int windowScore(int discs, int weight) {
        return weight * switch (discs) {
            case 3 -> 40;
            case 2 -> 5;
            case 1 -> 1;
            default -> 0;
        };
    }

    private static boolean isWin(CardGame game, Player[][] board, int row, int column, Player player) {
        for (int[] direction : DIRECTIONS) {
            int count = 1
                    + countInDirection(game, board, row, column, direction[0], direction[1], player)
                    + countInDirection(game, board, row, column, -direction[0], -direction[1], player);
            if (count >= CardGame.WIN_LENGTH) {
                return true;
            }
        }
        return false;
    }

    private static int countInDirection(CardGame game, Player[][] board, int row, int column,
                                        int rowStep, int columnStep, Player player) {
        int count = 0;
        int r = row + rowStep;
        int c = column + columnStep;
        while (r >= 0 && r < game.size() && c >= 0 && c < game.size()
                && (board[r][c] == player || game.isFreeCell(r, c))) {
            count++;
            r += rowStep;
            c += columnStep;
        }
        return count;
    }

    private static Player[][] snapshot(CardGame game) {
        Player[][] board = new Player[game.size()][game.size()];
        for (int row = 0; row < game.size(); row++) {
            for (int column = 0; column < game.size(); column++) {
                board[row][column] = game.discAt(row, column);
            }
        }
        return board;
    }
}