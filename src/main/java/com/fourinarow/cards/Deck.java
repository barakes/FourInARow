package com.fourinarow.cards;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;

// The draw pile and the discard pile. When the draw pile runs out, the discards are shuffled back in.
public class Deck {

    public static final int COPIES_OF_EACH_PICTURE = 2;
    public static final int WILD_CARDS = 2;
    public static final int REMOVE_CARDS = 2;

    private final Deque<Card> drawPile = new ArrayDeque<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final Random random;

    public Deck(List<Picture> pictures, Random random) {
        this.random = random;

        List<Card> cards = new ArrayList<>();
        for (Picture picture : pictures) {
            for (int i = 0; i < COPIES_OF_EACH_PICTURE; i++) {
                cards.add(Card.of(picture));
            }
        }
        for (int i = 0; i < WILD_CARDS; i++) {
            cards.add(Card.wild());
        }
        for (int i = 0; i < REMOVE_CARDS; i++) {
            cards.add(Card.remove());
        }

        Collections.shuffle(cards, random);
        drawPile.addAll(cards);
    }

    // Empty only if every card is in someone's hand.
    public Optional<Card> draw() {
        if (drawPile.isEmpty()) {
            List<Card> reshuffled = new ArrayList<>(discardPile);
            discardPile.clear();
            Collections.shuffle(reshuffled, random);
            drawPile.addAll(reshuffled);
        }
        return Optional.ofNullable(drawPile.pollFirst());
    }

    public void discard(Card card) {
        discardPile.add(card);
    }

    public int remaining() {
        return drawPile.size();
    }
}