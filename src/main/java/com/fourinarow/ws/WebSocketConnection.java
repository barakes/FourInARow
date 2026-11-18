package com.fourinarow.ws;

import com.fourinarow.room.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

// A Connection that sends messages as JSON over a WebSocket.
public class WebSocketConnection implements Connection {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnection.class);

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final WebSocketSession session;
    private final JsonMapper jsonMapper;

    public WebSocketConnection(WebSocketSession session, JsonMapper jsonMapper) {
        // A plain WebSocket session must not be sent to from two threads at once
        // (for example, both players' moves answered at the same moment).
        // The decorator queues the messages and sends them one by one.
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String id() {
        return session.getId();
    }

    @Override
    public void send(Object message) {
        if (!session.isOpen()) {
            return;
        }

        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(message)));
        } catch (IOException | RuntimeException e) {
            // The player probably just disconnected; the disconnect itself is handled elsewhere.
            log.debug("Could not send a message to connection {}: {}", id(), e.getMessage());
        }
    }
}