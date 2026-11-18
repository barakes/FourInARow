package com.fourinarow.room;

import com.fourinarow.ai.Difficulty;
import com.fourinarow.cards.BoardSize;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

// Keeps track of all open rooms, and which room each connected player is in.
@Service
public class RoomService {

    // No 0/O or 1/I/L, so children can read a code to a friend without mix-ups.
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 5;
    private static final int MAX_NICKNAME_LENGTH = 20;

    private final Random random = new SecureRandom();
    private final Map<String, GameRoom> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> roomsByConnection = new ConcurrentHashMap<>();

    public synchronized void createRoom(Connection connection, String nickname, GameMode mode, BoardSize boardSize) {
        leave(connection);
        String code = newCode();
        String name = cleanNickname(nickname);

        GameRoom room = switch (orDefault(mode)) {
            case CLASSIC -> Room.forFriends(code, name, connection);
            case CARDS -> CardRoom.forFriends(code, name, connection, orDefault(boardSize), new SecureRandom());
        };
        register(room, connection);
    }

    public synchronized void joinRoom(Connection connection, String code, String nickname) {
        GameRoom room = code == null ? null : roomsByCode.get(code.strip().toUpperCase(Locale.ROOT));
        if (room == null) {
            throw new RoomException("There is no room with this code");
        }

        leave(connection);
        room.join(cleanNickname(nickname), connection);
        roomsByConnection.put(connection.id(), room);
    }

    public synchronized void playAgainstComputer(Connection connection, String nickname, Difficulty difficulty,
                                                 GameMode mode, BoardSize boardSize) {
        if (difficulty == null) {
            throw new RoomException("Please choose a difficulty");
        }

        leave(connection);
        String code = newCode();
        String name = cleanNickname(nickname);

        GameRoom room = switch (orDefault(mode)) {
            case CLASSIC -> Room.againstComputer(code, name, connection, difficulty, new Random());
            case CARDS -> CardRoom.againstComputer(code, name, connection, difficulty, orDefault(boardSize), new SecureRandom());
        };
        register(room, connection);
    }

    // Not synchronized on the service: the room has its own lock, so games in different rooms don't wait for each other.
    public void move(Connection connection, Integer column) {
        if (column == null) {
            throw new RoomException("Please choose a column");
        }
        roomOf(connection).play(connection, column);
    }

    public void cardMove(Connection connection, Integer handIndex, Integer row, Integer column) {
        if (handIndex == null || row == null || column == null) {
            throw new RoomException("Please choose a card and a cell");
        }
        roomOf(connection).playCard(connection, handIndex, row, column);
    }

    // Called when a player disconnects or starts another game. The whole room is closed.
    public synchronized void leave(Connection connection) {
        GameRoom room = roomsByConnection.remove(connection.id());
        if (room == null) {
            return;
        }

        room.leave(connection);
        roomsByCode.remove(room.getCode());
        roomsByConnection.values().removeIf(other -> other == room);
    }

    public int openRooms() {
        return roomsByCode.size();
    }

    private GameRoom roomOf(Connection connection) {
        GameRoom room = roomsByConnection.get(connection.id());
        if (room == null) {
            throw new RoomException("You are not in a game");
        }
        return room;
    }

    private void register(GameRoom room, Connection connection) {
        roomsByCode.put(room.getCode(), room);
        roomsByConnection.put(connection.id(), room);
    }

    private String newCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = builder.toString();
        } while (roomsByCode.containsKey(code));
        return code;
    }

    // Older pages that don't send a mode get the classic game.
    private static GameMode orDefault(GameMode mode) {
        return mode == null ? GameMode.CLASSIC : mode;
    }

    private static BoardSize orDefault(BoardSize boardSize) {
        return boardSize == null ? BoardSize.LARGE : boardSize;
    }

    // Keeps letters (in any language, including Hebrew), digits, spaces, '-' and '_'. Everything else is removed.
    static String cleanNickname(String nickname) {
        String cleaned = nickname == null ? "" : nickname.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        if (cleaned.isEmpty()) {
            return "Player";
        }
        return cleaned.length() > MAX_NICKNAME_LENGTH ? cleaned.substring(0, MAX_NICKNAME_LENGTH) : cleaned;
    }
}