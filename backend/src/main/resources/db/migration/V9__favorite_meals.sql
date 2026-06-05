CREATE TABLE favorite_meals (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source_meal_id BIGINT REFERENCES meals(id) ON DELETE SET NULL,
  name VARCHAR(80) NOT NULL,
  slot VARCHAR(20) NOT NULL,
  description TEXT,
  items JSONB NOT NULL,
  total_kcal INT NOT NULL,
  total_protein NUMERIC(6,2),
  total_fat NUMERIC(6,2),
  total_carb NUMERIC(6,2),
  ai_suggestion TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_favorite_meals_user_created ON favorite_meals(user_id, created_at DESC);
