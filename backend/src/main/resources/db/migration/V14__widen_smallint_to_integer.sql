-- The workout_schedules (V11) and workout_goals (V13) tables declared a few counters as
-- SMALLINT, but their JPA entities map them to Java int, which Hibernate schema-validation
-- expects to be INTEGER — causing startup to fail. Widen the columns to match the entities.
-- (We add a new migration rather than editing V11/V13 so already-applied checksums stay valid.)

ALTER TABLE workout_schedules
  ALTER COLUMN weeks TYPE integer,
  ALTER COLUMN days_per_week TYPE integer;

ALTER TABLE workout_goals
  ALTER COLUMN target_sessions_per_week TYPE integer;
