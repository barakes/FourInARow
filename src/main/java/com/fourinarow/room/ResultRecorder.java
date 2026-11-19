package com.fourinarow.room;

import com.fourinarow.game.Player;

// Where a room sends the result of a rated game. The rating service implements it;
// tests can use a fake one, so room tests need no database.
public interface ResultRecorder {

    // "winner" is null for a draw. Returns null if the result could not be recorded.
    RatingUpdate record(long accountOne, long accountTwo, GameMode mode, Player winner);
}