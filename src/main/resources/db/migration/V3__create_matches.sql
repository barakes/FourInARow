-- Every finished game between two registered players in the random-opponent mode.
create table matches (
                         id             bigint generated always as identity primary key,
                         player_one_id  bigint      not null references accounts (id) on delete cascade,
                         player_two_id  bigint      not null references accounts (id) on delete cascade,
    -- CLASSIC or CARDS
                         mode           varchar(10) not null,
    -- PLAYER_ONE_WINS, PLAYER_TWO_WINS or DRAW
                         outcome        varchar(20) not null,
    -- false for a friendly game: the same two players already played 3 rated games in the last 24 hours.
                         rated          boolean     not null,
    -- Points player one gained (or lost, if negative). Player two got the opposite. 0 when not rated.
                         rating_change  integer     not null,
                         played_at      timestamptz not null default now()
);

-- To count recent games of a player (and of a pair of players).
create index matches_player_one on matches (player_one_id, played_at);
create index matches_player_two on matches (player_two_id, played_at);