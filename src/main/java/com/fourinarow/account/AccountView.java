package com.fourinarow.account;

// What the browser may see about an account. Never the password hash.
public record AccountView(String username, int rating, int wins, int losses, int draws) {

    public static AccountView from(Account account) {
        return new AccountView(account.getUsername(), account.getRating(),
                account.getWins(), account.getLosses(), account.getDraws());
    }
}