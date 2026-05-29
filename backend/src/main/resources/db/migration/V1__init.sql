CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  name VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  email_verified TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE profiles (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  gender VARCHAR(10) NOT NULL,
  height_cm NUMERIC(5,2) NOT NULL,
  weight_kg NUMERIC(5,2) NOT NULL,
  age INT,
  body_fat_pct NUMERIC(4,1),
  muscle_mass_kg NUMERIC(5,2),
  bmr_kcal INT,
  waist_cm NUMERIC(5,2),
  body_water_pct NUMERIC(4,1),
  goal VARCHAR(30),
  equipment TEXT[],
  experience VARCHAR(20),
  theme VARCHAR(10) NOT NULL DEFAULT 'system',
  language VARCHAR(10) NOT NULL DEFAULT 'zh-TW',
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE body_measurements (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  measured_at TIMESTAMP NOT NULL DEFAULT NOW(),
  weight_kg NUMERIC(5,2),
  body_fat_pct NUMERIC(4,1),
  muscle_mass_kg NUMERIC(5,2),
  bmr_kcal INT,
  waist_cm NUMERIC(5,2),
  body_water_pct NUMERIC(4,1),
  note TEXT
);
CREATE INDEX idx_body_measurements_user_time ON body_measurements(user_id, measured_at);

CREATE TABLE refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL,
  device_info VARCHAR(255),
  expires_at TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);

CREATE TABLE workout_plans (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  category VARCHAR(30) NOT NULL,
  items JSONB NOT NULL,
  total_kcal INT NOT NULL,
  done BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_workout_user_date ON workout_plans(user_id, date);

CREATE TABLE meals (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  slot VARCHAR(20) NOT NULL,
  description TEXT,
  image_url TEXT,
  items JSONB NOT NULL,
  total_kcal INT NOT NULL,
  total_protein NUMERIC(6,2),
  total_fat NUMERIC(6,2),
  total_carb NUMERIC(6,2),
  ai_suggestion TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_meals_user_date ON meals(user_id, date);

CREATE TABLE daily_stats (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  intake_kcal INT NOT NULL DEFAULT 0,
  burn_kcal INT NOT NULL DEFAULT 0,
  weight_kg NUMERIC(5,2),
  PRIMARY KEY (user_id, date)
);
