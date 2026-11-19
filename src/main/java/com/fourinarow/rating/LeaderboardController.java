package com.fourinarow.rating;

import com.fourinarow.account.AccountRepository;
import com.fourinarow.account.AccountView;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The top players. Open to everyone, also without logging in.
@RestController
public class LeaderboardController {

    public static final int SIZE = 10;

    private final AccountRepository accounts;

    public LeaderboardController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/api/leaderboard")
    public List<AccountView> leaderboard() {
        return accounts.findLeaderboard(PageRequest.of(0, SIZE)).stream()
                .map(AccountView::from)
                .toList();
    }
}