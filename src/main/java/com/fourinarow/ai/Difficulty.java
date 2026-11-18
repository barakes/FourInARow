package com.fourinarow.ai;

// How strong the computer player is: how many moves ahead it looks,
// and how often it plays a random move instead of the best one.
public enum Difficulty {
    EASY(2, 0.5),
    MEDIUM(4, 0.1),
    HARD(7, 0.0);

    private final int depth;
    private final double randomMoveChance;

    Difficulty(int depth, double randomMoveChance) {
        this.depth = depth;
        this.randomMoveChance = randomMoveChance;
    }

    public int depth() {
        return depth;
    }

    public double randomMoveChance() {
        return randomMoveChance;
    }
}