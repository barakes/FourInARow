package com.fourinarow.cards;

// A card: either a picture card (matching two cells on the board), or a special card.
public record Card(Kind kind, Picture picture) {

    public enum Kind {
        // Place a disc on an empty cell with the same picture.
        PICTURE,
        // Place a disc on any empty cell.
        WILD,
        // Remove one of the opponent's discs.
        REMOVE
    }

    // Runs on every new card, so an invalid card can never exist.
    public Card {
        if (kind == null) {
            throw new IllegalArgumentException("A card must have a kind");
        }
        if ((kind == Kind.PICTURE) != (picture != null)) {
            throw new IllegalArgumentException("Only picture cards have a picture, and they always do");
        }
    }

    public static Card of(Picture picture) {
        return new Card(Kind.PICTURE, picture);
    }

    public static Card wild() {
        return new Card(Kind.WILD, null);
    }

    public static Card remove() {
        return new Card(Kind.REMOVE, null);
    }
}