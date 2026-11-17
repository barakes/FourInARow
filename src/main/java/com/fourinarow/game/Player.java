package com.fourinarow.game;

public enum Player {
    ONE,
    TWO;

    public Player opponent() {
        return this == ONE ? TWO : ONE;
    }
}