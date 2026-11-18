package com.fourinarow.room;

// A request that can't be done in the room's current state, like joining a full room.
// Its message is safe to show to the player.
public class RoomException extends RuntimeException {

    public RoomException(String message) {
        super(message);
    }
}