export type Gender = "male" | "female" | "other";
export type Goal = "muscle_gain" | "fat_loss" | "maintain";
export type Experience = "beginner" | "intermediate" | "advanced";

export interface Profile {
  gender: Gender;
  heightCm: number;
  weightKg: number;
  age?: number;
  bodyFatPct?: number;
  muscleMassKg?: number;
  bmrKcal?: number;
  waistCm?: number;
  bodyWaterPct?: number;
  goal?: Goal;
  equipment: string[];
  experience?: Experience;
  theme: "light" | "dark" | "system";
  language: "zh-TW" | "en";
}

export interface User {
  id: number;
  email: string;
  name: string;
  role: "USER" | "ADMIN";
  profile: Profile;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: Pick<User, "id" | "email" | "name" | "role">;
}

export interface ExerciseItem {
  name: string;
  sets: number;
  reps: string;
  restSec: number;
  kcal: number;
  note: string;
  alt: string[];
}

export interface WorkoutPlan {
  id: number;
  date: string;
  category: string;
  items: ExerciseItem[];
  totalKcal: number;
  done: boolean;
  createdAt: string;
}

export interface FoodItem {
  name: string;
  grams: number;
  kcal: number;
  protein: number;
  fat: number;
  carb: number;
  confidence: number;
}

export interface Meal {
  id: number;
  date: string;
  slot: string;
  description?: string;
  imageUrl?: string;
  items: FoodItem[];
  totalKcal: number;
  totalProtein: number;
  totalFat: number;
  totalCarb: number;
  aiSuggestion?: string;
  createdAt: string;
}

export interface DailyStats {
  date: string;
  intakeKcal: number;
  burnKcal: number;
  netKcal: number;
  protein: number;
  fat: number;
  carb: number;
  weightKg: number;
  goalKcal: number;
  workoutsDone: number;
  workoutsPlanned: number;
}

export interface PageEnvelope<T> {
  data: T[];
  page: number;
  size: number;
  total: number;
}
