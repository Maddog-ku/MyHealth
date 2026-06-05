-- Unlocked achievement badges. Streaks themselves are computed live from meals/
-- workouts/measurements (never stored); only the unlock moment of each badge is
-- persisted here so we can show "unlocked on" and avoid re-notifying.

CREATE TABLE achievements (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code VARCHAR(64) NOT NULL,            -- AchievementCatalog enum name, e.g. STREAK_7
  unlocked_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, code)
);
CREATE INDEX idx_achievements_user ON achievements(user_id);
