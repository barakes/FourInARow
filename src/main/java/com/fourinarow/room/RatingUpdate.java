package com.fourinarow.room;

// What a rated game changed. Ratings are the new ones; changes are positive (gained) or negative (lost).
public record RatingUpdate(boolean rated, int playerOneChange, int playerTwoChange,
                           int playerOneRating, int playerTwoRating) {
}