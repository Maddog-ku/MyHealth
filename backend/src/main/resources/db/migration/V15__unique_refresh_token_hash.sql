DROP INDEX IF EXISTS idx_refresh_tokens_hash;

CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
