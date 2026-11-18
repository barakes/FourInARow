package com.fourinarow.cards;

import com.fourinarow.game.InvalidMoveException;
import com.fourinarow.game.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// The card version: a card decides where a disc may go. Four in a row wins.
// All public methods are synchronized, because moves arrive from different server threads.
public class CardGame {

    public static final int HAND_SIZE = 3;
    public static final int WIN_LENGTH = 4;

    // If players are stuck this many turns in a row, nobody can make progress, so the game ends in a draw.
    private static final int MAX_SKIPPED_TURNS_IN_A_ROW = 10;

    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    public enum Status {
        IN_PROGRESS,
        WON,
        DRAW
    }

    public record Position(int row, int column) {
    }

    // One legal way to play: which card from the hand, on which cell.
    public record Move(int handIndex, int row, int column) {
    }

    // What happened in a turn. It contains no hands, so it is safe to send to both players.
    public record TurnResult(Player player, Card card, int row, int column, boolean removed,
                             Status status, Player winner, List<Position> winningLine,
                             Player currentPlayer, List<Player> skippedPlayers) {
    }

    private final BoardLayout layout;
    private final Player[][] discs;
    private final Deck deck;
    private final Map<Player, List<Card>> hands = new EnumMap<>(Player.class);
    private Player currentPlayer;
    private Status status = Status.IN_PROGRESS;
    private Player winner;
    private List<Position> winningLine = List.of();

    public CardGame(BoardSize boardSize, Random random) {
        this.layout = BoardLayout.shuffled(boardSize, random);
        this.discs = new Player[boardSize.size()][boardSize.size()];
        this.deck = new Deck(BoardLayout.usedPictures(boardSize), random);

        for (Player player : Player.values()) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < HAND_SIZE; i++) {
                deck.draw().ifPresent(hand::add);
            }
            hands.put(player, hand);
        }

        this.currentPlayer = random.nextBoolean() ? Player.ONE : Player.TWO;
        skipStuckPlayers();
    }

    public synchronized TurnResult play(Player player, int handIndex, int row, int column) {
        if (status != Status.IN_PROGRESS) {
            throw new InvalidMoveException("The game is already over");
        }
        if (player != currentPlayer) {
            throw new InvalidMoveException("It is not your turn");
        }

        List<Card> hand = hands.get(player);
        if (handIndex < 0 || handIndex >= hand.size()) {
            throw new InvalidMoveException("There is no such card in your hand");
        }
        if (!isInside(row, column)) {
            throw new InvalidMoveException("This cell is outside the board");
        }

        Card card = hand.get(handIndex);
        boolean removed = false;

        switch (card.kind()) {
            case PICTURE -> {
                if (layout.pictureAt(row, column) != card.picture()) {
                    throw new InvalidMoveException("This card does not match that cell");
                }
                requireEmpty(row, column);
                discs[row][column] = player;
            }
            case WILD -> {
                if (layout.isFreeCell(row, column)) {
                    throw new InvalidMoveException("The free cell can't be taken");
                }
                requireEmpty(row, column);
                discs[row][column] = player;
            }
            case REMOVE -> {
                if (discs[row][column] != player.opponent()) {
                    throw new InvalidMoveException("You can only remove an opponent's disc");
                }
                discs[row][column] = null;
                removed = true;
            }
        }

        // The card is used: throw it away and take a new one, so the hand is full again.
        hand.remove(handIndex);
        deck.discard(card);
        deck.draw().ifPresent(hand::add);

        if (!removed) {
            List<Position> line = findWinningLine(row, column, player);
            if (!line.isEmpty()) {
                status = Status.WON;
                winner = player;
                winningLine = line;
            }
        }

        List<Player> skipped = List.of();
        if (status == Status.IN_PROGRESS) {
            if (isBoardFull()) {
                status = Status.DRAW;
            } else {
                currentPlayer = player.opponent();
                skipped = skipStuckPlayers();
            }
        }

        return new TurnResult(player, card, row, column, removed, status, winner, winningLine, currentPlayer, skipped);
    }

    // Every legal move for the player whose turn it is.
    public synchronized List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>();
        if (status != Status.IN_PROGRESS) {
            return moves;
        }

        List<Card> hand = hands.get(currentPlayer);
        for (int index = 0; index < hand.size(); index++) {
            Card card = hand.get(index);
            for (int row = 0; row < size(); row++) {
                for (int column = 0; column < size(); column++) {
                    if (canPlay(card, currentPlayer, row, column)) {
                        moves.add(new Move(index, row, column));
                    }
                }
            }
        }
        return moves;
    }

    // A copy: the caller can't change the real hand.
    public synchronized List<Card> hand(Player player) {
        return List.copyOf(hands.get(player));
    }

    public synchronized Player discAt(int row, int column) {
        return discs[row][column];
    }

    public Picture pictureAt(int row, int column) {
        return layout.pictureAt(row, column);
    }

    public boolean isFreeCell(int row, int column) {
        return layout.isFreeCell(row, column);
    }

    public int size() {
        return layout.size();
    }

    public synchronized Player getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized Player getWinner() {
        return winner;
    }

    public synchronized List<Position> getWinningLine() {
        return winningLine;
    }

    public synchronized int cardsLeftInDeck() {
        return deck.remaining();
    }

    // ---- Test helpers: package-private, so only tests in this package can set up exact positions. ----

    synchronized void putDiscForTesting(int row, int column, Player player) {
        discs[row][column] = player;
    }

    synchronized void setHandForTesting(Player player, List<Card> cards) {
        hands.put(player, new ArrayList<>(cards));
    }

    synchronized void setCurrentPlayerForTesting(Player player) {
        currentPlayer = player;
    }

    // The rare case: the player has no card that can be played anywhere.
    // They lose the turn and their dead cards are replaced with new ones.
    private List<Player> skipStuckPlayers() {
        List<Player> skipped = new ArrayList<>();

        while (!hasPlayableCard(currentPlayer)) {
            if (skipped.size() >= MAX_SKIPPED_TURNS_IN_A_ROW) {
                status = Status.DRAW;
                break;
            }
            replaceDeadCards(currentPlayer);
            skipped.add(currentPlayer);
            currentPlayer = currentPlayer.opponent();
        }
        return List.copyOf(skipped);
    }

    private void replaceDeadCards(Player player) {
        List<Card> hand = hands.get(player);
        List<Card> dead = hand.stream().filter(card -> !isPlayable(card, player)).toList();

        for (Card card : dead) {
            hand.remove(card);
            deck.discard(card);
        }
        while (hand.size() < HAND_SIZE) {
            var next = deck.draw();
            if (next.isEmpty()) {
                break;
            }
            hand.add(next.get());
        }
    }

    private boolean hasPlayableCard(Player player) {
        return hands.get(player).stream().anyMatch(card -> isPlayable(card, player));
    }

    private boolean isPlayable(Card card, Player player) {
        for (int row = 0; row < size(); row++) {
            for (int column = 0; column < size(); column++) {
                if (canPlay(card, player, row, column)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canPlay(Card card, Player player, int row, int column) {
        return switch (card.kind()) {
            case PICTURE -> layout.pictureAt(row, column) == card.picture() && discs[row][column] == null;
            case WILD -> !layout.isFreeCell(row, column) && discs[row][column] == null;
            case REMOVE -> discs[row][column] == player.opponent();
        };
    }

    private void requireEmpty(int row, int column) {
        if (discs[row][column] != null) {
            throw new InvalidMoveException("This cell is already taken");
        }
    }

    private boolean isBoardFull() {
        for (int row = 0; row < size(); row++) {
            for (int column = 0; column < size(); column++) {
                if (!layout.isFreeCell(row, column) && discs[row][column] == null) {
                    return false;
                }
            }
        }
        return true;
    }

    // The free cell counts for both players.
    private boolean countsFor(Player player, int row, int column) {
        return isInside(row, column) && (discs[row][column] == player || layout.isFreeCell(row, column));
    }

    private List<Position> findWinningLine(int row, int column, Player player) {
        for (int[] direction : DIRECTIONS) {
            List<Position> line = new ArrayList<>();
            line.add(new Position(row, column));
            collect(line, row, column, direction[0], direction[1], player);
            collect(line, row, column, -direction[0], -direction[1], player);

            if (line.size() >= WIN_LENGTH) {
                return List.copyOf(line);
            }
        }
        return List.of();
    }

    private void collect(List<Position> line, int row, int column, int rowStep, int columnStep, Player player) {
        int r = row + rowStep;
        int c = column + columnStep;
        while (countsFor(player, r, c)) {
            line.add(new Position(r, c));
            r += rowStep;
            c += columnStep;
        }
    }

    private boolean isInside(int row, int column) {
        return row >= 0 && row < size() && column >= 0 && column < size();
    }
}