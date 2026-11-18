package com.fourinarow.room;

// A connected player. Rooms only need to send messages; they don't know how the messages travel.
// On the server this wraps a WebSocket; in tests it can simply collect the messages.
public interface Connection {

    String id();

    void send(Object message);
}