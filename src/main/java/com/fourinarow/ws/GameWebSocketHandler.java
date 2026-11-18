package com.fourinarow.ws;

import com.fourinarow.game.InvalidMoveException;
import com.fourinarow.room.Connection;
import com.fourinarow.room.RoomException;
import com.fourinarow.room.RoomService;
import com.fourinarow.ws.ServerMessages.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Receives every message from every browser and passes it on to the room service.
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    // Our messages are tiny; anything bigger is a mistake or an attack.
    private static final int MAX_MESSAGE_LENGTH = 1_000;

    private final RoomService roomService;
    private final JsonMapper jsonMapper;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public GameWebSocketHandler(RoomService roomService, JsonMapper jsonMapper) {
        this.roomService = roomService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        connections.put(session.getId(), new WebSocketConnection(session, jsonMapper));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Connection connection = connections.get(session.getId());
        if (connection == null) {
            return;
        }

        if (message.getPayloadLength() > MAX_MESSAGE_LENGTH) {
            connection.send(new ErrorMessage("The message is too long"));
            return;
        }

        try {
            ClientMessage request = jsonMapper.readValue(message.getPayload(), ClientMessage.class);
            handle(connection, request);
        } catch (RoomException | InvalidMoveException e) {
            // Expected problems, like a full column: their messages are written for the player.
            connection.send(new ErrorMessage(e.getMessage()));
        } catch (JacksonException e) {
            connection.send(new ErrorMessage("The message could not be read"));
        } catch (RuntimeException e) {
            // A bug: log the details for us, and send the player only a general message.
            log.error("Unexpected error while handling a message", e);
            connection.send(new ErrorMessage("Something went wrong, please try again"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.remove(session.getId());
        if (connection != null) {
            roomService.leave(connection);
        }
    }

    private void handle(Connection connection, ClientMessage request) {
        if (request.type() == null) {
            throw new RoomException("The message has no type");
        }

        switch (request.type()) {
            case CREATE_ROOM -> roomService.createRoom(
                    connection, request.nickname(), request.mode(), request.boardSize());
            case JOIN_ROOM -> roomService.joinRoom(
                    connection, request.code(), request.nickname());
            case PLAY_COMPUTER -> roomService.playAgainstComputer(
                    connection, request.nickname(), request.difficulty(), request.mode(), request.boardSize());
            case MOVE -> roomService.move(
                    connection, request.column());
            case CARD_MOVE -> roomService.cardMove(
                    connection, request.handIndex(), request.row(), request.column());
        }
    }
}