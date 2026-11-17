package com.fourinarow.game;

// Thrown when a move breaks the rules: a full column, a column outside the board,
// a move out of turn, or a move after the game has ended.
public class InvalidMoveException extends RuntimeException {

    public InvalidMoveException(String message) {
        super(message);
    }
}