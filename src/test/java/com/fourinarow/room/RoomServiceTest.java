package com.fourinarow.room;

import com.fourinarow.ai.Difficulty;
import com.fourinarow.cards.BoardSize;
import com.fourinarow.cards.Card;
import com.fourinarow.cards.Picture;
import com.fourinarow.game.Game;
import com.fourinarow.game.InvalidMoveException;
import com.fourinarow.game.Player;
import com.fourinarow.ws.ServerMessages.CardGameStarted;
import com.fourinarow.ws.ServerMessages.CardTurnPlayed;
import com.fourinarow.ws.ServerMessages.GameStarted;
import com.fourinarow.ws.ServerMessages.MoveMade;
import com.fourinarow.ws.ServerMessages.OpponentLeft;
import com.fourinarow.ws.ServerMessages.RematchRequested;
import com.fourinarow.ws.ServerMessages.RoomCreated;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomServiceTest {

    // A connection that keeps every message instead of sending it.
    private static class FakeConnection implements Connection {
        private final String id;
        final List<Object> messages = new ArrayList<>();

        FakeConnection(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void send(Object message) {
            messages.add(message);
        }

        <T> List<T> messagesOf(Class<T> type) {
            return messages.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private final RoomService service = new RoomService();
    private final FakeConnection host = new FakeConnection("host");
    private final FakeConnection guest = new FakeConnection("guest");

    // ---------- Rooms ----------

    @Test
    void aFriendCanJoinWithTheCodeInAnyLetterCase() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        String code = host.messagesOf(RoomCreated.class).getFirst().code();

        service.joinRoom(guest, code.toLowerCase(), "Tom");

        assertThat(host.messagesOf(GameStarted.class).getFirst().you()).isEqualTo(Player.ONE);
        assertThat(guest.messagesOf(GameStarted.class).getFirst().you()).isEqualTo(Player.TWO);
        assertThat(guest.messagesOf(GameStarted.class).getFirst().playerOneName()).isEqualTo("Dana");
    }

    @Test
    void joiningAWrongCodeFails() {
        assertThatThrownBy(() -> service.joinRoom(guest, "ZZZZZ", "Tom"))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("no room");
    }

    @Test
    void aThirdPlayerCannotJoinAFullRoom() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        String code = host.messagesOf(RoomCreated.class).getFirst().code();
        service.joinRoom(guest, code, "Tom");

        assertThatThrownBy(() -> service.joinRoom(new FakeConnection("third"), code, "Noa"))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("full");
    }

    @Test
    void leavingTellsTheOpponentAndClosesTheRoom() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        service.leave(host);

        assertThat(guest.messagesOf(OpponentLeft.class)).hasSize(1);
        assertThat(service.openRooms()).isZero();
    }

    @Test
    void nicknamesAreCleanedAndShortened() {
        assertThat(RoomService.cleanNickname("<b>Tom</b>")).isEqualTo("bTomb");
        assertThat(RoomService.cleanNickname("ברק")).isEqualTo("ברק");
        assertThat(RoomService.cleanNickname("   ")).isEqualTo("Player");
        assertThat(RoomService.cleanNickname("a".repeat(50))).hasSize(20);
    }

    // ---------- The classic game ----------

    @Test
    void bothPlayersSeeEveryClassicMove() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        service.move(host, 3);

        assertThat(host.messagesOf(MoveMade.class)).hasSize(1);
        assertThat(guest.messagesOf(MoveMade.class)).hasSize(1);
        assertThat(guest.messagesOf(MoveMade.class).getFirst().column()).isEqualTo(3);
    }

    @Test
    void theComputerAnswersAClassicMoveImmediately() {
        service.playAgainstComputer(host, "Dana", Difficulty.EASY, GameMode.CLASSIC, null);

        service.move(host, 3);

        List<MoveMade> moves = host.messagesOf(MoveMade.class);
        assertThat(moves).hasSize(2);
        assertThat(moves.get(1).player()).isEqualTo(Player.TWO);
    }

    @Test
    void aPageThatSendsNoModeGetsTheClassicGame() {
        service.playAgainstComputer(host, "Dana", Difficulty.EASY, null, null);

        assertThat(host.messagesOf(GameStarted.class)).hasSize(1);
    }

    // ---------- The card game ----------

    @Test
    void eachPlayerGetsTheSameBoardButOnlyTheirOwnHand() {
        service.createRoom(host, "Dana", GameMode.CARDS, BoardSize.SMALL);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        CardGameStarted hostStart = host.messagesOf(CardGameStarted.class).getFirst();
        CardGameStarted guestStart = guest.messagesOf(CardGameStarted.class).getFirst();

        assertThat(hostStart.board()).isEqualTo(guestStart.board());
        assertThat(hostStart.size()).isEqualTo(6);
        assertThat(hostStart.hand()).hasSize(3);
        assertThat(guestStart.hand()).hasSize(3);
    }

    @Test
    void afterACardTurnEachPlayerGetsTheTurnWithTheirOwnHand() {
        service.createRoom(host, "Dana", GameMode.CARDS, BoardSize.SMALL);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        CardGameStarted hostStart = host.messagesOf(CardGameStarted.class).getFirst();
        CardGameStarted guestStart = guest.messagesOf(CardGameStarted.class).getFirst();

        // Whoever starts plays a legal card.
        boolean hostStarts = hostStart.currentPlayer() == Player.ONE;
        FakeConnection mover = hostStarts ? host : guest;
        FakeConnection waiter = hostStarts ? guest : host;
        CardGameStarted moverStart = hostStarts ? hostStart : guestStart;
        CardGameStarted waiterStart = hostStarts ? guestStart : hostStart;

        int[] move = firstLegalMove(moverStart);
        service.cardMove(mover, move[0], move[1], move[2]);

        CardTurnPlayed seenByMover = mover.messagesOf(CardTurnPlayed.class).getFirst();
        CardTurnPlayed seenByWaiter = waiter.messagesOf(CardTurnPlayed.class).getFirst();

        assertThat(seenByWaiter.row()).isEqualTo(seenByMover.row());
        assertThat(seenByWaiter.column()).isEqualTo(seenByMover.column());
        // The waiting player didn't play, so their hand is unchanged: they got their own hand, not the mover's.
        assertThat(seenByWaiter.hand()).isEqualTo(waiterStart.hand());
        assertThat(seenByMover.hand()).hasSize(3);
    }

    @Test
    void aCardGameAgainstTheComputerStartsRightAway() {
        service.playAgainstComputer(host, "Dana", Difficulty.EASY, GameMode.CARDS, BoardSize.LARGE);

        CardGameStarted start = host.messagesOf(CardGameStarted.class).getFirst();
        assertThat(start.size()).isEqualTo(7);
        assertThat(start.playerTwoName()).startsWith("Computer");

        // If the computer was chosen to start, it has already played.
        if (start.currentPlayer() == Player.TWO) {
            assertThat(host.messagesOf(CardTurnPlayed.class)).isNotEmpty();
            assertThat(host.messagesOf(CardTurnPlayed.class).getLast().currentPlayer()).isEqualTo(Player.ONE);
        }
    }

    @Test
    void aClassicMoveIsRejectedInACardRoomAndTheOtherWayAround() {
        service.playAgainstComputer(host, "Dana", Difficulty.EASY, GameMode.CARDS, BoardSize.SMALL);
        service.playAgainstComputer(guest, "Tom", Difficulty.EASY, GameMode.CLASSIC, null);

        assertThatThrownBy(() -> service.move(host, 3)).isInstanceOf(RoomException.class);
        assertThatThrownBy(() -> service.cardMove(guest, 0, 0, 0)).isInstanceOf(RoomException.class);
    }

    // ---------- Rematch ----------

    @Test
    void aRematchCannotBeRequestedBeforeTheGameIsOver() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        assertThatThrownBy(() -> service.rematch(host))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("not over");
    }

    @Test
    void aCardRematchCannotBeRequestedBeforeTheGameIsOver() {
        service.playAgainstComputer(host, "Dana", Difficulty.EASY, GameMode.CARDS, BoardSize.SMALL);

        assertThatThrownBy(() -> service.rematch(host))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("not over");
    }

    @Test
    void aClassicRematchStartsWhenBothAgreeAndThePlayersSwapWhoStarts() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        // The host stacks four in column 0 while the guest plays column 1: the host wins.
        service.move(host, 0);
        service.move(guest, 1);
        service.move(host, 0);
        service.move(guest, 1);
        service.move(host, 0);
        service.move(guest, 1);
        service.move(host, 0);
        assertThat(host.messagesOf(MoveMade.class).getLast().status()).isEqualTo(Game.Status.WON);

        // The host asks: the guest is told once, even if the host clicks twice.
        service.rematch(host);
        service.rematch(host);
        assertThat(guest.messagesOf(RematchRequested.class)).hasSize(1);
        assertThat(host.messagesOf(GameStarted.class)).hasSize(1);

        // The guest agrees: a new game starts, and this time the guest goes first.
        service.rematch(guest);
        assertThat(host.messagesOf(GameStarted.class).getLast().you()).isEqualTo(Player.TWO);
        assertThat(guest.messagesOf(GameStarted.class).getLast().you()).isEqualTo(Player.ONE);

        service.move(guest, 3);
        assertThat(host.messagesOf(MoveMade.class).getLast().column()).isEqualTo(3);
    }

    @Test
    void aRematchAgainstTheComputerStartsRightAway() {
        service.playAgainstComputer(host, "Dana", Difficulty.EASY, GameMode.CLASSIC, null);

        // Play columns in turn until the game is over. A full column is simply skipped.
        int column = 0;
        while (host.messagesOf(MoveMade.class).isEmpty()
                || host.messagesOf(MoveMade.class).getLast().status() == Game.Status.IN_PROGRESS) {
            try {
                service.move(host, column);
            } catch (InvalidMoveException e) {
                // The column is full; try the next one.
            }
            column = (column + 1) % 7;
        }

        service.rematch(host);

        assertThat(host.messagesOf(GameStarted.class)).hasSize(2);
        assertThat(host.messagesOf(GameStarted.class).getLast().you()).isEqualTo(Player.ONE);
    }

    // At the start the board is empty: a picture card fits its picture's cell, and a wild card fits any cell.
    private static int[] firstLegalMove(CardGameStarted start) {
        List<Card> hand = start.hand();
        List<List<Picture>> board = start.board();

        for (int index = 0; index < hand.size(); index++) {
            Card card = hand.get(index);
            for (int row = 0; row < board.size(); row++) {
                for (int column = 0; column < board.size(); column++) {
                    Picture picture = board.get(row).get(column);
                    boolean fits = switch (card.kind()) {
                        case PICTURE -> picture == card.picture();
                        case WILD -> picture != null;
                        case REMOVE -> false;
                    };
                    if (fits) {
                        return new int[]{index, row, column};
                    }
                }
            }
        }
        throw new IllegalStateException("No legal move in the starting hand");
    }
}