CREATE TABLE habit_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  type VARCHAR(30) NOT NULL,
  completed_at TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date, type)
);

CREATE INDEX idx_habit_logs_user_date ON habit_logs(user_id, date);
