package com.fourinarow.room;

import com.fourinarow.game.Player;
import com.fourinarow.ws.ServerMessages.RatingChanged;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

// The extra part of a room in the random-opponent mode: which account sits in each seat,
// and recording the result once per game. Used by both kinds of room.
final class RatedGame {

    private final GameMode mode;
    private final ResultRecorder recorder;
    private final Map<Player, Long> accounts = new EnumMap<>(Player.class);
    private boolean recorded;

    RatedGame(GameMode mode, ResultRecorder recorder, long accountOne, long accountTwo) {
        this.mode = mode;
        this.recorder = recorder;
        accounts.put(Player.ONE, accountOne);
        accounts.put(Player.TWO, accountTwo);
    }

    // The classic rematch swaps seats, so the accounts swap too.
    void swapPlayers() {
        Long first = accounts.get(Player.ONE);
        accounts.put(Player.ONE, accounts.get(Player.TWO));
        accounts.put(Player.TWO, first);
    }

    void newGame() {
        recorded = false;
    }

    // Records the result (winner null = draw) and tells each player what changed.
    // Called when a game ends, or when a player leaves in the middle. Only the first call per game counts.
    void finish(Player winner, Function<Player, Connection> connectionOf) {
        if (recorded) {
            return;
        }
        recorded = true;

        RatingUpdate update = recorder.record(accounts.get(Player.ONE), accounts.get(Player.TWO), mode, winner);
        if (update == null) {
            return;
        }
        send(connectionOf.apply(Player.ONE), new RatingChanged(update.rated(), update.playerOneChange(), update.playerOneRating()));
        send(connectionOf.apply(Player.TWO), new RatingChanged(update.rated(), update.playerTwoChange(), update.playerTwoRating()));
    }

    private static void send(Connection connection, Object message) {
        if (connection != null) {
            connection.send(message);
        }
    }
}