-- One active weight goal per user. The start weight/date are snapshotted when the
-- goal is (re)set so progress is measured from that anchor; current weight, rate and
-- projected attainment date are all computed live from body_measurements.

CREATE TABLE weight_goals (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  target_weight_kg NUMERIC(5,2) NOT NULL,
  start_weight_kg NUMERIC(5,2) NOT NULL,
  start_date DATE NOT NULL,
  target_date DATE,                              -- optional deadline
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
