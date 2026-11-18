package com.fourinarow.room;

import com.fourinarow.ai.ComputerPlayer;
import com.fourinarow.ai.Difficulty;
import com.fourinarow.game.Game;
import com.fourinarow.game.Player;
import com.fourinarow.ws.ServerMessages.GameStarted;
import com.fourinarow.ws.ServerMessages.MoveMade;
import com.fourinarow.ws.ServerMessages.OpponentLeft;
import com.fourinarow.ws.ServerMessages.RematchRequested;
import com.fourinarow.ws.ServerMessages.RoomCreated;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

// One classic game between two players: two people, or a person and the computer.
// All methods are synchronized, so moves from both players are handled one at a time.
public class Room implements GameRoom {

    // A seat at the table. The computer's seat has no connection.
    private record Seat(String nickname, Connection connection) {
    }

    private final String code;
    private Game game = new Game();
    private final Map<Player, Seat> seats = new EnumMap<>(Player.class);
    private final ComputerPlayer computer;
    private final Set<Player> rematchRequests = EnumSet.noneOf(Player.class);

    private Room(String code, ComputerPlayer computer) {
        this.code = code;
        this.computer = computer;
    }

    // A room for two people. The host gets a code to share and waits for a friend.
    public static Room forFriends(String code, String nickname, Connection host) {
        Room room = new Room(code, null);
        room.seats.put(Player.ONE, new Seat(nickname, host));
        host.send(new RoomCreated(code, Player.ONE));
        return room;
    }

    // A game against the computer. It starts right away; the person always moves first.
    public static Room againstComputer(String code, String nickname, Connection human,
                                       Difficulty difficulty, Random random) {
        Room room = new Room(code, new ComputerPlayer(difficulty, random));
        room.seats.put(Player.ONE, new Seat(nickname, human));
        room.seats.put(Player.TWO, new Seat("Computer (" + difficulty.name().toLowerCase(Locale.ROOT) + ")", null));
        room.sendGameStarted();
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
    public synchronized void play(Connection connection, int column) {
        if (!isFull()) {
            throw new RoomException("Waiting for another player to join");
        }

        Player player = playerOf(connection);
        broadcast(moveMade(game.play(player, column)));

        // Against the computer, it answers immediately.
        if (computer != null && game.getStatus() == Game.Status.IN_PROGRESS) {
            int computerColumn = computer.chooseColumn(game);
            broadcast(moveMade(game.play(game.getCurrentPlayer(), computerColumn)));
        }
    }

    @Override
    public void playCard(Connection connection, int handIndex, int row, int column) {
        throw new RoomException("This room is playing the classic game");
    }

    @Override
    public synchronized void requestRematch(Connection connection) {
        if (!isFull()) {
            throw new RoomException("Waiting for another player to join");
        }
        if (game.getStatus() == Game.Status.IN_PROGRESS) {
            throw new RoomException("The game is not over yet");
        }

        Player player = playerOf(connection);

        // Against the computer there is nobody to ask. The person keeps moving first.
        if (computer != null) {
            startNewGame();
            return;
        }

        if (!rematchRequests.add(player)) {
            return;
        }
        if (rematchRequests.size() < 2) {
            seats.get(player.opponent()).connection().send(new RematchRequested());
            return;
        }

        // Both agreed. Player one always starts, so the players swap seats: the one who went second now goes first.
        Seat first = seats.get(Player.ONE);
        seats.put(Player.ONE, seats.get(Player.TWO));
        seats.put(Player.TWO, first);
        startNewGame();
    }

    // Called when a player disconnects: the other person, if any, is told.
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

    // Package-private: lets tests look at the game without being able to change it from outside the package.
    Game getGame() {
        return game;
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

    private void startNewGame() {
        rematchRequests.clear();
        game = new Game();
        sendGameStarted();
    }

    private MoveMade moveMade(Game.MoveResult result) {
        return new MoveMade(result, game.getWinner(), game.getCurrentPlayer());
    }

    private void sendGameStarted() {
        String playerOneName = seats.get(Player.ONE).nickname();
        String playerTwoName = seats.get(Player.TWO).nickname();

        for (Map.Entry<Player, Seat> entry : seats.entrySet()) {
            Connection connection = entry.getValue().connection();
            if (connection != null) {
                connection.send(new GameStarted(entry.getKey(), playerOneName, playerTwoName,
                        game.getRows(), game.getColumns(), game.getCurrentPlayer()));
            }
        }
    }

    private void broadcast(Object message) {
        seats.values().stream()
                .map(Seat::connection)
                .filter(Objects::nonNull)
                .forEach(connection -> connection.send(message));
    }
}