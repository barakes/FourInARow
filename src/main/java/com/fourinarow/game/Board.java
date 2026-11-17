package com.fourinarow.game;

import java.util.ArrayList;
import java.util.List;

// The grid of discs. Row 0 is the BOTTOM row, so a dropped disc lands at the column's current height.
public class Board {

    public static final int DEFAULT_ROWS = 6;
    public static final int DEFAULT_COLUMNS = 7;
    public static final int WIN_LENGTH = 4;

    // The four directions a line can go: horizontal, vertical, and the two diagonals.
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    public record Position(int row, int column) {
    }

    private final int rows;
    private final int columns;
    private final Player[][] cells;
    private final int[] heights;
    private int discCount;

    public Board() {
        this(DEFAULT_ROWS, DEFAULT_COLUMNS);
    }

    public Board(int rows, int columns) {
        if (rows < WIN_LENGTH || columns < WIN_LENGTH) {
            throw new IllegalArgumentException("The board must be at least " + WIN_LENGTH + " by " + WIN_LENGTH);
        }
        this.rows = rows;
        this.columns = columns;
        this.cells = new Player[rows][columns];
        this.heights = new int[columns];
    }

    // Drops a disc into a column and returns the row where it landed.
    public int drop(int column, Player player) {
        if (column < 0 || column >= columns) {
            throw new InvalidMoveException("Column " + column + " is outside the board");
        }
        if (!canDrop(column)) {
            throw new InvalidMoveException("Column " + column + " is full");
        }

        int row = heights[column];
        cells[row][column] = player;
        heights[column]++;
        discCount++;
        return row;
    }

    // Removes the top disc of a column. Used by the computer player to try a move and take it back.
    public void undo(int column) {
        if (column < 0 || column >= columns || heights[column] == 0) {
            throw new IllegalStateException("There is no disc to remove in column " + column);
        }
        heights[column]--;
        cells[heights[column]][column] = null;
        discCount--;
    }

    public boolean canDrop(int column) {
        return column >= 0 && column < columns && heights[column] < rows;
    }

    public boolean isFull() {
        return discCount == rows * columns;
    }

    // Returns the winning line that goes through this cell, or an empty list if there is none.
    // Works for any player: it only looks at the disc that is in this cell.
    public List<Position> findWinningLine(int row, int column) {
        Player player = cells[row][column];
        if (player == null) {
            return List.of();
        }

        for (int[] direction : DIRECTIONS) {
            List<Position> line = new ArrayList<>();
            line.add(new Position(row, column));
            collect(line, row, column, direction[0], direction[1], player);
            collect(line, row, column, -direction[0], -direction[1], player);

            if (line.size() >= WIN_LENGTH) {
                return List.copyOf(line);
            }
        }
        return List.of();
    }

    // Walks from a cell in one direction while the discs belong to the same player.
    private void collect(List<Position> line, int row, int column, int rowStep, int columnStep, Player player) {
        int r = row + rowStep;
        int c = column + columnStep;
        while (r >= 0 && r < rows && c >= 0 && c < columns && cells[r][c] == player) {
            line.add(new Position(r, c));
            r += rowStep;
            c += columnStep;
        }
    }

    public Player get(int row, int column) {
        return cells[row][column];
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }
}