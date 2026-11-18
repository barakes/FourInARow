package com.fourinarow.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fourinarow.ai.Difficulty;

// A message from the browser. Only the fields that belong to its type are filled in:
//   CREATE_ROOM   -> nickname
//   JOIN_ROOM     -> nickname, code
//   PLAY_COMPUTER -> nickname, difficulty
//   MOVE          -> column
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientMessage(Type type, String nickname, String code, Integer column, Difficulty difficulty) {

    public enum Type {
        CREATE_ROOM,
        JOIN_ROOM,
        PLAY_COMPUTER,
        MOVE
    }
}