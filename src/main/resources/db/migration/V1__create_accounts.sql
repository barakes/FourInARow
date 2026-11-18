-- Registered players (accounts). Guests who play without an account are not stored.
create table accounts (
                          id            bigint generated always as identity primary key,
                          username      varchar(20)  not null,
    -- Never the password itself: only its BCrypt hash.
                          password_hash varchar(100) not null,
    -- One rating for both games (Elo). Everyone starts at 1000.
                          rating        integer      not null default 1000,
                          wins          integer      not null default 0,
                          losses        integer      not null default 0,
                          draws         integer      not null default 0,
                          created_at    timestamptz  not null default now()
);

-- "Dana" and "dana" are the same username.
create unique index accounts_username_unique on accounts (lower(username));

-- For the leaderboard (top players by rating).
create index accounts_rating on accounts (rating desc);