-- AI weekly/monthly workout schedule: a one-week training split (7 days, each a rest
-- day or a category with a target duration) that repeats across N weeks. The split is
-- generated once by the local model and stored as a small JSON pattern; individual days
-- are later "applied" into real workout_plans via the existing generation flow.

CREATE TABLE workout_schedules (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  goal VARCHAR(40) NOT NULL,            -- goal label the split was built for (減脂/增肌/維持…)
  start_date DATE NOT NULL,             -- first day (a Monday) the schedule applies from
  weeks SMALLINT NOT NULL,              -- how many weeks the one-week pattern repeats (1–4)
  days_per_week SMALLINT NOT NULL,      -- number of training (non-rest) days in the pattern
  intensity VARCHAR(10) NOT NULL,       -- low/medium/high, applied when a day is materialized
  days JSONB NOT NULL,                  -- the 7-day pattern: [{weekday,rest,category,durationMin,focus}]
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_workout_schedules_user_created ON workout_schedules(user_id, created_at DESC);
