-- Weekly workout commitment: how many training sessions the user aims to complete each
-- calendar week. One per user; live progress is computed against this week's done workouts.

CREATE TABLE workout_goals (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  target_sessions_per_week SMALLINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
