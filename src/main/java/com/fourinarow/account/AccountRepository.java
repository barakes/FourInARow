package com.fourinarow.account;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // "Dana" and "dana" are the same username.
    Optional<Account> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    // The leaderboard: only players who played at least one rated game.
    // Highest rating first; on equal ratings, whoever registered first.
    // "pageable" says how many rows to return (for example the top 10).
    @Query("""
            select a from Account a
            where a.wins + a.losses + a.draws > 0
            order by a.rating desc, a.id asc
            """)
    List<Account> findLeaderboard(Pageable pageable);
}