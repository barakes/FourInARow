package com.fourinarow.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fourinarow.ai.Difficulty;
import com.fourinarow.cards.BoardSize;
import com.fourinarow.room.GameMode;

// A message from the browser. Only the fields that belong to its type are filled in:
//   CREATE_ROOM   -> nickname, mode, boardSize (cards only)
//   JOIN_ROOM     -> nickname, code
//   PLAY_COMPUTER -> nickname, difficulty, mode, boardSize (cards only)
//   MOVE          -> column                    (classic game)
//   CARD_MOVE     -> handIndex, row, column    (card game)
//   REMATCH       -> nothing                   (both games, after a game is over)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientMessage(Type type, String nickname, String code, Difficulty difficulty,
                            GameMode mode, BoardSize boardSize,
                            Integer column, Integer row, Integer handIndex) {

    public enum Type {
        CREATE_ROOM,
        JOIN_ROOM,
        PLAY_COMPUTER,
        MOVE,
        CARD_MOVE,
        REMATCH
    }
}