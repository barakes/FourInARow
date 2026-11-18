package com.fourinarow.ws;

import com.fourinarow.cards.Card;
import com.fourinarow.cards.CardGame;
import com.fourinarow.cards.Picture;
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

    // ---------- The classic game ----------

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

    // ---------- The card game ----------

    // Sent to each player separately: "hand" is only that player's own cards.
    // The board is a list of rows; the free cell is null.
    public record CardGameStarted(String type, Player you, String playerOneName, String playerTwoName,
                                  int size, List<List<Picture>> board, Player currentPlayer,
                                  List<Card> hand, int cardsLeft) {
        public CardGameStarted(Player you, String playerOneName, String playerTwoName, int size,
                               List<List<Picture>> board, Player currentPlayer, List<Card> hand, int cardsLeft) {
            this("CARD_GAME_STARTED", you, playerOneName, playerTwoName, size, board, currentPlayer, hand, cardsLeft);
        }
    }

    // Sent to each player separately after every turn. Everything except "hand" is the same for both.
    public record CardTurnPlayed(String type, Player player, Card card, int row, int column, boolean removed,
                                 CardGame.Status status, Player winner, List<CardGame.Position> winningLine,
                                 Player currentPlayer, List<Player> skippedPlayers,
                                 List<Card> hand, int cardsLeft) {
        public CardTurnPlayed(CardGame.TurnResult result, List<Card> hand, int cardsLeft) {
            this("CARD_TURN_PLAYED", result.player(), result.card(), result.row(), result.column(), result.removed(),
                    result.status(), result.winner(), result.winningLine(), result.currentPlayer(),
                    result.skippedPlayers(), hand, cardsLeft);
        }
    }

    // ---------- Both games ----------

    // Sent to the other player when one player asks for a rematch.
    public record RematchRequested(String type) {
        public RematchRequested() {
            this("REMATCH_REQUESTED");
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