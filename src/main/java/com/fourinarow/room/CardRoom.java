package com.fourinarow.room;

import com.fourinarow.ai.CardComputerPlayer;
import com.fourinarow.ai.Difficulty;
import com.fourinarow.cards.BoardSize;
import com.fourinarow.cards.CardGame;
import com.fourinarow.cards.Picture;
import com.fourinarow.game.Player;
import com.fourinarow.ws.ServerMessages.CardGameStarted;
import com.fourinarow.ws.ServerMessages.CardTurnPlayed;
import com.fourinarow.ws.ServerMessages.OpponentLeft;
import com.fourinarow.ws.ServerMessages.RoomCreated;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

// One card game between two players: two people, or a person and the computer.
// Every message that contains a hand is built separately for each player, so nobody ever receives the opponent's cards.
public class CardRoom implements GameRoom {

    // A seat at the table. The computer's seat has no connection.
    private record Seat(String nickname, Connection connection) {
    }

    private final String code;
    private final CardGame game;
    private final Map<Player, Seat> seats = new EnumMap<>(Player.class);
    private final CardComputerPlayer computer;

    private CardRoom(String code, BoardSize boardSize, Random random, CardComputerPlayer computer) {
        this.code = code;
        this.game = new CardGame(boardSize, random);
        this.computer = computer;
    }

    public static CardRoom forFriends(String code, String nickname, Connection host, BoardSize boardSize, Random random) {
        CardRoom room = new CardRoom(code, boardSize, random, null);
        room.seats.put(Player.ONE, new Seat(nickname, host));
        host.send(new RoomCreated(code, Player.ONE));
        return room;
    }

    // Starts right away. The first player is chosen at random, so the computer may move first.
    public static CardRoom againstComputer(String code, String nickname, Connection human,
                                           Difficulty difficulty, BoardSize boardSize, Random random) {
        CardRoom room = new CardRoom(code, boardSize, random, new CardComputerPlayer(difficulty, random));
        room.seats.put(Player.ONE, new Seat(nickname, human));
        room.seats.put(Player.TWO, new Seat("Computer (" + difficulty.name().toLowerCase(Locale.ROOT) + ")", null));
        synchronized (room) {
            room.sendGameStarted();
            room.playComputerTurns();
        }
        return room;
    }

    @Override
    public synchronized void join(String nickname, Connection guest) {
        if (computer != null || seats.containsKey(Player.TWO)) {
            throw new RoomException("This room is already full");
        }
        seats.put(Player.TWO, new Seat(nickname, guest));
        sendGameStarted();
    }

    @Override
    public void play(Connection connection, int column) {
        throw new RoomException("This room is playing the card game");
    }

    @Override
    public synchronized void playCard(Connection connection, int handIndex, int row, int column) {
        if (!isFull()) {
            throw new RoomException("Waiting for another player to join");
        }

        Player player = playerOf(connection);
        sendTurn(game.play(player, handIndex, row, column));
        playComputerTurns();
    }

    @Override
    public synchronized void leave(Connection connection) {
        for (Seat seat : seats.values()) {
            if (seat.connection() != null && !seat.connection().id().equals(connection.id())) {
                seat.connection().send(new OpponentLeft());
            }
        }
    }

    @Override
    public synchronized boolean isFull() {
        return seats.size() == 2;
    }

    @Override
    public String getCode() {
        return code;
    }

    // Package-private, for tests.
    CardGame getGame() {
        return game;
    }

    // The computer keeps playing while it is its turn (it can get several turns if the person is stuck).
    private void playComputerTurns() {
        while (computer != null
                && game.getStatus() == CardGame.Status.IN_PROGRESS
                && game.getCurrentPlayer() == Player.TWO) {
            CardGame.Move move = computer.chooseMove(game);
            sendTurn(game.play(Player.TWO, move.handIndex(), move.row(), move.column()));
        }
    }

    // Each person gets the same turn, but with their own hand only.
    private void sendTurn(CardGame.TurnResult result) {
        for (Map.Entry<Player, Seat> entry : seats.entrySet()) {
            Connection connection = entry.getValue().connection();
            if (connection != null) {
                connection.send(new CardTurnPlayed(result, game.hand(entry.getKey()), game.cardsLeftInDeck()));
            }
        }
    }

    private void sendGameStarted() {
        List<List<Picture>> board = new ArrayList<>();
        for (int row = 0; row < game.size(); row++) {
            List<Picture> pictures = new ArrayList<>();
            for (int column = 0; column < game.size(); column++) {
                // ArrayList (not List.of) because the free cell is null.
                pictures.add(game.pictureAt(row, column));
            }
            board.add(pictures);
        }

        String playerOneName = seats.get(Player.ONE).nickname();
        String playerTwoName = seats.get(Player.TWO).nickname();

        for (Map.Entry<Player, Seat> entry : seats.entrySet()) {
            Connection connection = entry.getValue().connection();
            if (connection != null) {
                connection.send(new CardGameStarted(entry.getKey(), playerOneName, playerTwoName, game.size(),
                        board, game.getCurrentPlayer(), game.hand(entry.getKey()), game.cardsLeftInDeck()));
            }
        }
    }

    private Player playerOf(Connection connection) {
        for (Map.Entry<Player, Seat> entry : seats.entrySet()) {
            Connection seated = entry.getValue().connection();
            if (seated != null && seated.id().equals(connection.id())) {
                return entry.getKey();
            }
        }
        throw new RoomException("You are not a player in this room");
    }
}