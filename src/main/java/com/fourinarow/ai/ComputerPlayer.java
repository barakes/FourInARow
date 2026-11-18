package com.fourinarow.ai;

import com.fourinarow.game.Board;
import com.fourinarow.game.Game;
import com.fourinarow.game.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// Chooses a move using minimax (in its "negamax" form) with alpha-beta pruning.
public class ComputerPlayer {

    private static final int WIN_SCORE = 1_000_000;
    private static final int INFINITY = Integer.MAX_VALUE;

    private final Difficulty difficulty;
    private final Random random;

    public ComputerPlayer(Difficulty difficulty) {
        this(difficulty, new Random());
    }

    // Lets tests pass a Random with a fixed seed, so the "random" choices are repeatable.
    public ComputerPlayer(Difficulty difficulty, Random random) {
        this.difficulty = difficulty;
        this.random = random;
    }

    public int chooseColumn(Game game) {
        if (game.getStatus() != Game.Status.IN_PROGRESS) {
            throw new IllegalStateException("The game is already over");
        }

        Player me = game.getCurrentPlayer();
        Board board = game.copyBoard();
        List<Integer> moves = orderedMoves(board);

        // Lower levels sometimes play a random move, so children can beat them.
        if (random.nextDouble() < difficulty.randomMoveChance()) {
            return moves.get(random.nextInt(moves.size()));
        }

        int bestScore = -INFINITY;
        List<Integer> bestMoves = new ArrayList<>();

        for (int column : moves) {
            int score = scoreMove(board, column, me, difficulty.depth(), -INFINITY, INFINITY);

            if (score > bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(column);
            } else if (score == bestScore) {
                bestMoves.add(column);
            }
        }

        // Several equally good moves: pick one at random, so the computer doesn't always play the same game.
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    // Plays a move, scores it from the mover's point of view, and takes it back.
    private int scoreMove(Board board, int column, Player player, int depth, int alpha, int beta) {
        int row = board.drop(column, player);
        int score;

        if (!board.findWinningLine(row, column).isEmpty()) {
            // A win now is better than a win later, so remaining depth is added.
            score = WIN_SCORE + depth;
        } else {
            score = -negamax(board, player.opponent(), depth - 1, -beta, -alpha);
        }

        board.undo(column);
        return score;
    }

    // The best score `player` (who moves next) can force, looking `depth` moves ahead.
    // One player's gain is the other's loss, so the opponent's best score is simply negated.
    private int negamax(Board board, Player player, int depth, int alpha, int beta) {
        if (board.isFull()) {
            return 0;
        }
        if (depth == 0) {
            return evaluate(board, player);
        }

        int best = -INFINITY;

        for (int column : orderedMoves(board)) {
            int score = scoreMove(board, column, player, depth, alpha, beta);

            best = Math.max(best, score);
            alpha = Math.max(alpha, score);

            // Alpha-beta pruning: the opponent already has a better option elsewhere,
            // so they will never let the game reach this position. No need to look further.
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    // Center columns first: they are usually stronger, and good moves first make pruning cut more.
    private static List<Integer> orderedMoves(Board board) {
        double center = (board.getColumns() - 1) / 2.0;
        List<Integer> moves = new ArrayList<>();

        for (int column = 0; column < board.getColumns(); column++) {
            if (board.canDrop(column)) {
                moves.add(column);
            }
        }
        moves.sort(Comparator.comparingDouble(column -> Math.abs(column - center)));
        return moves;
    }

    // A rough score of a position that has no winner yet, from `player`'s point of view.
    // Every group of four cells that only one player uses is a chance for that player to win.
    private static int evaluate(Board board, Player player) {
        int score = 0;
        int rows = board.getRows();
        int columns = board.getColumns();
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                for (int[] direction : directions) {
                    int endRow = row + direction[0] * (Board.WIN_LENGTH - 1);
                    int endColumn = column + direction[1] * (Board.WIN_LENGTH - 1);
                    if (endRow < 0 || endRow >= rows || endColumn < 0 || endColumn >= columns) {
                        continue;
                    }

                    int mine = 0;
                    int theirs = 0;
                    for (int i = 0; i < Board.WIN_LENGTH; i++) {
                        Player cell = board.get(row + direction[0] * i, column + direction[1] * i);
                        if (cell == player) {
                            mine++;
                        } else if (cell != null) {
                            theirs++;
                        }
                    }

                    if (theirs == 0) {
                        score += windowScore(mine);
                    } else if (mine == 0) {
                        score -= windowScore(theirs);
                    }
                }
            }
        }
        return score;
    }

    private static int windowScore(int discs) {
        return switch (discs) {
            case 3 -> 50;
            case 2 -> 5;
            case 1 -> 1;
            default -> 0;
        };
    }
}