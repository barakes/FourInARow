package com.fourinarow.rating;

import com.fourinarow.IntegrationTest;
import com.fourinarow.account.Account;
import com.fourinarow.account.AccountRepository;
import com.fourinarow.account.LoginTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LeaderboardControllerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginTokenRepository tokens;

    @Autowired
    private MatchRepository matches;

    @BeforeEach
    void cleanDatabase() {
        matches.deleteAll();
        tokens.deleteAll();
        accounts.deleteAll();
    }

    @Test
    void theLeaderboardShowsTheTop10PlayersByRating() throws Exception {
        // 12 players who played one game each; player 11 has the highest rating.
        for (int number = 0; number < 12; number++) {
            Account player = new Account("Player" + number, "not-a-real-hash");
            player.recordWin(1000 + number * 10);
            accounts.save(player);
        }

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].username").value("Player11"))
                .andExpect(jsonPath("$[0].rating").value(1110))
                .andExpect(jsonPath("$[9].username").value("Player2"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void playersWhoNeverPlayedAreNotOnTheLeaderboard() throws Exception {
        accounts.save(new Account("JustRegistered", "not-a-real-hash"));
        Account player = new Account("Dana", "not-a-real-hash");
        player.recordLoss(984);
        accounts.save(player);

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("Dana"));
    }
}