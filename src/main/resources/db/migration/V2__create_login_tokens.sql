-- "Login tickets": after a successful login the browser gets a random token and sends it with every request.
-- Only a SHA-256 hash of the token is stored, so a leaked database can't be used to log in.
create table login_tokens (
                              token_hash varchar(64) primary key,
                              account_id bigint      not null references accounts (id) on delete cascade,
                              created_at timestamptz not null default now(),
                              expires_at timestamptz not null
);

-- To delete all of an account's tokens, and to clean up expired ones.
create index login_tokens_account on login_tokens (account_id);
create index login_tokens_expires on login_tokens (expires_at);