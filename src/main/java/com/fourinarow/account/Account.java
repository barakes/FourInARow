package com.fourinarow.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// A registered player. One row in the "accounts" table.
@Entity
@Table(name = "accounts")
public class Account {

    public static final int STARTING_RATING = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private int rating = STARTING_RATING;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int draws;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // For JPA only.
    protected Account() {
    }

    public Account(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    // Called after a rated game, with the new rating that the Elo calculation gave.
    public void recordWin(int newRating) {
        wins++;
        rating = newRating;
    }

    public void recordLoss(int newRating) {
        losses++;
        rating = newRating;
    }

    public void recordDraw(int newRating) {
        draws++;
        rating = newRating;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getRating() {
        return rating;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}