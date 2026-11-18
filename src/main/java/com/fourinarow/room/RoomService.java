package com.fourinarow.room;

import com.fourinarow.ai.Difficulty;
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
    private final Map<String, Room> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, Room> roomsByConnection = new ConcurrentHashMap<>();

    public synchronized void createRoom(Connection connection, String nickname) {
        leave(connection);
        Room room = Room.forFriends(newCode(), cleanNickname(nickname), connection);
        register(room, connection);
    }

    public synchronized void joinRoom(Connection connection, String code, String nickname) {
        Room room = code == null ? null : roomsByCode.get(code.strip().toUpperCase(Locale.ROOT));
        if (room == null) {
            throw new RoomException("There is no room with this code");
        }

        leave(connection);
        room.join(cleanNickname(nickname), connection);
        roomsByConnection.put(connection.id(), room);
    }

    public synchronized void playAgainstComputer(Connection connection, String nickname, Difficulty difficulty) {
        if (difficulty == null) {
            throw new RoomException("Please choose a difficulty");
        }

        leave(connection);
        Room room = Room.againstComputer(newCode(), cleanNickname(nickname), connection, difficulty, new Random());
        register(room, connection);
    }

    // Not synchronized on the service: the room has its own lock, so games in different rooms don't wait for each other.
    public void move(Connection connection, Integer column) {
        if (column == null) {
            throw new RoomException("Please choose a column");
        }

        Room room = roomsByConnection.get(connection.id());
        if (room == null) {
            throw new RoomException("You are not in a game");
        }
        room.play(connection, column);
    }

    // Called when a player disconnects or starts another game. The whole room is closed.
    public synchronized void leave(Connection connection) {
        Room room = roomsByConnection.remove(connection.id());
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

    private void register(Room room, Connection connection) {
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

    // Keeps letters (in any language, including Hebrew), digits, spaces, '-' and '_'. Everything else is removed.
    static String cleanNickname(String nickname) {
        String cleaned = nickname == null ? "" : nickname.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        if (cleaned.isEmpty()) {
            return "Player";
        }
        return cleaned.length() > MAX_NICKNAME_LENGTH ? cleaned.substring(0, MAX_NICKNAME_LENGTH) : cleaned;
    }
}