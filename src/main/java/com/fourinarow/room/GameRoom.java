package com.fourinarow.room;

// What every kind of room can do. The room service works with this, without caring which game is played inside.
public interface GameRoom {

    String getCode();

    boolean isFull();

    void join(String nickname, Connection guest);

    // A move in the classic game.
    void play(Connection connection, int column);

    // A move in the card game.
    void playCard(Connection connection, int handIndex, int row, int column);

    void leave(Connection connection);
}