-- Timed workouts can be completed partially: only the exercises whose timer was
-- actually finished count toward energy burned. burned_kcal stores that actual
-- amount (NULL for legacy plans, which fall back to total_kcal when done).
ALTER TABLE workout_plans ADD COLUMN burned_kcal INT;
