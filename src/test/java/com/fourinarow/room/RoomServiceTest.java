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
import com.fourinarow.ws.ServerMessages.RatingChanged;
import com.fourinarow.ws.ServerMessages.RematchRequested;
import com.fourinarow.ws.ServerMessages.RoomCreated;
import com.fourinarow.ws.ServerMessages.WaitingForOpponent;
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

    // A result recorder that remembers what it was asked to record, instead of using a database.
    // The winner gets 16 points and the loser loses 16; a draw changes nothing.
    private static class FakeRecorder implements ResultRecorder {
        record Call(long accountOne, long accountTwo, GameMode mode, Player winner) {
        }

        final List<Call> calls = new ArrayList<>();

        @Override
        public RatingUpdate record(long accountOne, long accountTwo, GameMode mode, Player winner) {
            calls.add(new Call(accountOne, accountTwo, mode, winner));
            int change = winner == null ? 0 : winner == Player.ONE ? 16 : -16;
            return new RatingUpdate(true, change, -change, 1000 + change, 1000 - change);
        }
    }

    private final FakeRecorder recorder = new FakeRecorder();
    private final RoomService service = new RoomService(recorder);
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

        playerOneWins(host, guest);
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

    // ---------- Random opponent (rated games) ----------

    @Test
    void twoPlayersInTheSameQueueAreMatched() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);
        assertThat(host.messagesOf(WaitingForOpponent.class)).hasSize(1);
        assertThat(service.waitingPlayers()).isEqualTo(1);

        service.findOpponent(guest, 2, "Tom", GameMode.CLASSIC);

        assertThat(service.waitingPlayers()).isZero();
        // Whoever waited longer is player one.
        assertThat(host.messagesOf(GameStarted.class).getFirst().you()).isEqualTo(Player.ONE);
        assertThat(guest.messagesOf(GameStarted.class).getFirst().you()).isEqualTo(Player.TWO);
        assertThat(guest.messagesOf(GameStarted.class).getFirst().playerOneName()).isEqualTo("Dana");
    }

    @Test
    void aPlayerIsNeverMatchedWithThemselves() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);

        // The same account, in another browser window.
        assertThatThrownBy(() -> service.findOpponent(new FakeConnection("second-window"), 1, "Dana", GameMode.CLASSIC))
                .isInstanceOf(RoomException.class)
                .hasMessageContaining("another window");
        assertThat(service.waitingPlayers()).isEqualTo(1);
    }

    @Test
    void theClassicAndCardQueuesAreSeparate() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);
        service.findOpponent(guest, 2, "Tom", GameMode.CARDS);

        assertThat(service.waitingPlayers()).isEqualTo(2);
        assertThat(host.messagesOf(GameStarted.class)).isEmpty();

        // A second card player arrives: the card game always uses the large board.
        FakeConnection third = new FakeConnection("third");
        service.findOpponent(third, 3, "Noa", GameMode.CARDS);
        assertThat(third.messagesOf(CardGameStarted.class).getFirst().size()).isEqualTo(7);
    }

    @Test
    void theResultOfARatedGameIsRecordedAndBothPlayersAreTold() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);
        service.findOpponent(guest, 2, "Tom", GameMode.CLASSIC);

        playerOneWins(host, guest);

        assertThat(recorder.calls).containsExactly(new FakeRecorder.Call(1, 2, GameMode.CLASSIC, Player.ONE));
        assertThat(host.messagesOf(RatingChanged.class).getLast().change()).isEqualTo(16);
        assertThat(guest.messagesOf(RatingChanged.class).getLast().change()).isEqualTo(-16);
        assertThat(guest.messagesOf(RatingChanged.class).getLast().rating()).isEqualTo(984);
    }

    @Test
    void leavingARatedGameInTheMiddleCountsAsALoss() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);
        service.findOpponent(guest, 2, "Tom", GameMode.CLASSIC);
        service.move(host, 3);

        service.leave(guest);

        assertThat(recorder.calls).containsExactly(new FakeRecorder.Call(1, 2, GameMode.CLASSIC, Player.ONE));
        assertThat(host.messagesOf(RatingChanged.class)).hasSize(1);
        assertThat(host.messagesOf(OpponentLeft.class)).hasSize(1);

        // The room is closed: the other player leaving later records nothing more.
        service.leave(host);
        assertThat(recorder.calls).hasSize(1);
    }

    @Test
    void leavingAfterARatedGameIsOverRecordsNothingMore() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);
        service.findOpponent(guest, 2, "Tom", GameMode.CLASSIC);
        playerOneWins(host, guest);

        service.leave(guest);

        assertThat(recorder.calls).hasSize(1);
    }

    @Test
    void aRatedRematchSwapsTheAccountsWithTheSeats() {
        service.findOpponent(host, 1, "Dana", GameMode.CLASSIC);
        service.findOpponent(guest, 2, "Tom", GameMode.CLASSIC);
        playerOneWins(host, guest);

        service.rematch(host);
        service.rematch(guest);
        // Now the guest (account 2) is player one, and wins.
        playerOneWins(guest, host);

        assertThat(recorder.calls.getLast()).isEqualTo(new FakeRecorder.Call(2, 1, GameMode.CLASSIC, Player.ONE));
        assertThat(guest.messagesOf(RatingChanged.class).getLast().change()).isEqualTo(16);
    }

    @Test
    void cancellingOrDisconnectingTakesThePlayerOutOfTheQueue() {
        service.findOpponent(host, 1, "Dana", GameMode.CARDS);
        service.cancelSearch(host);
        assertThat(service.waitingPlayers()).isZero();

        service.findOpponent(host, 1, "Dana", GameMode.CARDS);
        service.leave(host);
        assertThat(service.waitingPlayers()).isZero();
    }

    @Test
    void gamesWithAFriendAreNotRated() {
        service.createRoom(host, "Dana", GameMode.CLASSIC, null);
        service.joinRoom(guest, host.messagesOf(RoomCreated.class).getFirst().code(), "Tom");

        playerOneWins(host, guest);

        assertThat(recorder.calls).isEmpty();
        assertThat(host.messagesOf(RatingChanged.class)).isEmpty();
    }

    // Player one stacks four discs in column 0 while player two plays column 1: player one wins.
    private void playerOneWins(FakeConnection playerOne, FakeConnection playerTwo) {
        for (int turn = 0; turn < 3; turn++) {
            service.move(playerOne, 0);
            service.move(playerTwo, 1);
        }
        service.move(playerOne, 0);
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