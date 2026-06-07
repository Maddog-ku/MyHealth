-- Session-management metadata on refresh tokens. Each non-revoked, unexpired token row is
-- one active device session. `created_at` is the original login time (carried forward across
-- token rotation), `last_used_at` is when the current token in the chain was issued.

ALTER TABLE refresh_tokens
  ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  ADD COLUMN last_used_at TIMESTAMP NOT NULL DEFAULT NOW();

CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens(user_id, revoked);
