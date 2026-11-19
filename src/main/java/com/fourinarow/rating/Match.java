package com.fourinarow.rating;

import com.fourinarow.room.GameMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// One finished game between two registered players. One row in the "matches" table.
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_one_id", nullable = false)
    private Long playerOneId;

    @Column(name = "player_two_id", nullable = false)
    private Long playerTwoId;

    // Stored as text ("CLASSIC"), not as a number, so adding a new mode later can't mix up old rows.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EloRating.Outcome outcome;

    @Column(nullable = false)
    private boolean rated;

    @Column(name = "rating_change", nullable = false)
    private int ratingChange;

    @Column(name = "played_at", nullable = false, updatable = false)
    private Instant playedAt;

    // For JPA only.
    protected Match() {
    }

    public Match(Long playerOneId, Long playerTwoId, GameMode mode, EloRating.Outcome outcome,
                 boolean rated, int ratingChange, Instant playedAt) {
        this.playerOneId = playerOneId;
        this.playerTwoId = playerTwoId;
        this.mode = mode;
        this.outcome = outcome;
        this.rated = rated;
        this.ratingChange = ratingChange;
        this.playedAt = playedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPlayerOneId() {
        return playerOneId;
    }

    public Long getPlayerTwoId() {
        return playerTwoId;
    }

    public GameMode getMode() {
        return mode;
    }

    public EloRating.Outcome getOutcome() {
        return outcome;
    }

    public boolean isRated() {
        return rated;
    }

    public int getRatingChange() {
        return ratingChange;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }
}