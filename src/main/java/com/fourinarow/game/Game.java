package com.fourinarow.game;

import java.util.List;

// The rules of a single game: whose turn it is, and when the game ends.
public class Game {

    public enum Status {
        IN_PROGRESS,
        WON,
        DRAW
    }

    // Everything a client needs to know after a move.
    public record MoveResult(int row, int column, Player player, Status status, List<Board.Position> winningLine) {
    }

    private final Board board;
    private Player currentPlayer = Player.ONE;
    private Status status = Status.IN_PROGRESS;
    private Player winner;
    private List<Board.Position> winningLine = List.of();

    public Game() {
        this(Board.DEFAULT_ROWS, Board.DEFAULT_COLUMNS);
    }

    public Game(int rows, int columns) {
        this.board = new Board(rows, columns);
    }

    // synchronized: on the server, two players' moves may arrive at the same moment from different threads.
    public synchronized MoveResult play(Player player, int column) {
        if (status != Status.IN_PROGRESS) {
            throw new InvalidMoveException("The game is already over");
        }
        if (player != currentPlayer) {
            throw new InvalidMoveException("It is not player " + player + "'s turn");
        }

        int row = board.drop(column, player);
        List<Board.Position> line = board.findWinningLine(row, column);

        if (!line.isEmpty()) {
            status = Status.WON;
            winner = player;
            winningLine = line;
        } else if (board.isFull()) {
            status = Status.DRAW;
        } else {
            currentPlayer = player.opponent();
        }

        return new MoveResult(row, column, player, status, winningLine);
    }

    public synchronized boolean canPlay(int column) {
        return status == Status.IN_PROGRESS && board.canDrop(column);
    }

    public synchronized Player getCell(int row, int column) {
        return board.get(row, column);
    }

    public int getRows() {
        return board.getRows();
    }

    public int getColumns() {
        return board.getColumns();
    }

    public synchronized Player getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized Player getWinner() {
        return winner;
    }

    public synchronized List<Board.Position> getWinningLine() {
        return winningLine;
    }
}