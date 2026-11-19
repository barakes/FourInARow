package com.fourinarow.rating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MatchRepository extends JpaRepository<Match, Long> {

    // How many rated games these two players played against each other since a moment in time.
    // Either of them may have been player one, so both orders are checked.
    @Query("""
            select count(m) from Match m
            where m.rated = true
              and m.playedAt > :since
              and ((m.playerOneId = :first and m.playerTwoId = :second)
                or (m.playerOneId = :second and m.playerTwoId = :first))
            """)
    long countRatedGamesBetween(@Param("first") Long first, @Param("second") Long second,
                                @Param("since") Instant since);
}