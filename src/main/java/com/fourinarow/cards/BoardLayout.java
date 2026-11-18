package com.fourinarow.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Which picture is printed on each cell. Shuffled for every game,
// so that two cells with the same picture never touch (not even diagonally).
public final class BoardLayout {

    private static final int MAX_ATTEMPTS = 100_000;

    private final BoardSize boardSize;
    private final Picture[][] pictures;

    private BoardLayout(BoardSize boardSize, Picture[][] pictures) {
        this.boardSize = boardSize;
        this.pictures = pictures;
    }

    public static BoardLayout shuffled(BoardSize boardSize, Random random) {
        int size = boardSize.size();
        List<Picture> bag = new ArrayList<>();
        for (Picture picture : usedPictures(boardSize)) {
            bag.add(picture);
            bag.add(picture);
        }

        // Shuffle and check; try again until no two equal pictures touch.
        // A valid layout shows up within a few dozen tries on average, so this is fast.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Collections.shuffle(bag, random);
            Picture[][] grid = new Picture[size][size];
            int next = 0;

            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    if (!isFreeCell(boardSize, row, column)) {
                        grid[row][column] = bag.get(next++);
                    }
                }
            }

            if (noEqualNeighbours(grid)) {
                return new BoardLayout(boardSize, grid);
            }
        }
        throw new IllegalStateException("Could not build a board layout");
    }

    public static List<Picture> usedPictures(BoardSize boardSize) {
        return List.of(Picture.values()).subList(0, boardSize.pictureCount());
    }

    public static boolean isFreeCell(BoardSize boardSize, int row, int column) {
        int middle = boardSize.size() / 2;
        return boardSize.hasFreeCenter() && row == middle && column == middle;
    }

    // null for the free cell.
    public Picture pictureAt(int row, int column) {
        return pictures[row][column];
    }

    public boolean isFreeCell(int row, int column) {
        return isFreeCell(boardSize, row, column);
    }

    public int size() {
        return boardSize.size();
    }

    private static boolean noEqualNeighbours(Picture[][] grid) {
        int size = grid.length;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                Picture picture = grid[row][column];
                if (picture == null) {
                    continue;
                }
                // Checking right, down, and both lower diagonals covers every touching pair exactly once.
                if (same(grid, row, column + 1, picture) || same(grid, row + 1, column, picture)
                        || same(grid, row + 1, column + 1, picture) || same(grid, row + 1, column - 1, picture)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean same(Picture[][] grid, int row, int column, Picture picture) {
        return row >= 0 && row < grid.length && column >= 0 && column < grid.length && grid[row][column] == picture;
    }
}