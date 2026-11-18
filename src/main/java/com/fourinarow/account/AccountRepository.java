package com.fourinarow.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // "Dana" and "dana" are the same username.
    Optional<Account> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    // The leaderboard: highest rating first; on equal ratings, whoever registered first.
    List<Account> findTop10ByOrderByRatingDescIdAsc();
}