package com.fourinarow.cards;

// The two board sizes a player can choose.
public enum BoardSize {
    // 6x6 = 36 cells: 18 pictures, each on two cells.
    SMALL(6, false),
    // 7x7 = 49 cells: 24 pictures, each on two cells, plus a free cell in the middle.
    LARGE(7, true);

    private final int size;
    private final boolean freeCenter;

    BoardSize(int size, boolean freeCenter) {
        this.size = size;
        this.freeCenter = freeCenter;
    }

    public int size() {
        return size;
    }

    public boolean hasFreeCenter() {
        return freeCenter;
    }

    public int pictureCount() {
        int cells = size * size - (freeCenter ? 1 : 0);
        return cells / 2;
    }
}