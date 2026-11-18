package com.fourinarow.ws;

import com.fourinarow.game.Board;
import com.fourinarow.game.Game;
import com.fourinarow.game.Player;

import java.util.List;

// The messages the server sends to the browser. Each one carries a "type" field,
// so the browser knows how to read it.
public final class ServerMessages {

    private ServerMessages() {
    }

    // Sent to the player who opened a room: share this code with a friend.
    public record RoomCreated(String type, String code, Player you) {
        public RoomCreated(String code, Player you) {
            this("ROOM_CREATED", code, you);
        }
    }

    // Sent to each player when both seats are taken.
    public record GameStarted(String type, Player you, String playerOneName, String playerTwoName,
                              int rows, int columns, Player currentPlayer) {
        public GameStarted(Player you, String playerOneName, String playerTwoName,
                           int rows, int columns, Player currentPlayer) {
            this("GAME_STARTED", you, playerOneName, playerTwoName, rows, columns, currentPlayer);
        }
    }

    // Sent to both players after every move, including the computer's.
    public record MoveMade(String type, int row, int column, Player player, Game.Status status,
                           Player winner, List<Board.Position> winningLine, Player currentPlayer) {
        public MoveMade(Game.MoveResult result, Player winner, Player currentPlayer) {
            this("MOVE_MADE", result.row(), result.column(), result.player(), result.status(),
                    winner, result.winningLine(), currentPlayer);
        }
    }

    // Sent to the remaining player when the other one disconnects.
    public record OpponentLeft(String type) {
        public OpponentLeft() {
            this("OPPONENT_LEFT");
        }
    }

    // Sent only to the player whose request was rejected.
    public record ErrorMessage(String type, String message) {
        public ErrorMessage(String message) {
            this("ERROR", message);
        }
    }
}