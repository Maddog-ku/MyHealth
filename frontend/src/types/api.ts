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
  /** Which coach persona to show. Backend resolves null → gender, so this is always set on read. */
  assistantAvatar?: "male" | "female";
  theme: "light" | "dark" | "system";
  language: "zh-TW" | "en";
}

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
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
  /** Per-set work seconds the user actually times against. May be 0 on legacy plans (derive from reps). */
  durationSec: number;
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
  /** Energy actually burned once completed; null until the timed session is finished. */
  burnedKcal: number | null;
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

export interface WeeklySummary {
  totalIntakeKcal: number;
  avgIntakeKcal: number;
  totalBurnKcal: number;
  netKcal: number;
  goalKcal: number;
  weightStart: number | null;
  weightEnd: number | null;
  weightDelta: number | null;
  workoutsDone: number;
  mealsLogged: number;
  daysCovered: number;
}

export interface WeeklyReport {
  weekStart: string;
  weekEnd: string;
  summary: WeeklySummary;
  /** Cached AI narrative, or null if none has been generated yet for this week. */
  narrative: string | null;
  generatedAt: string | null;
}

export interface StreakInfo {
  current: number;
  longest: number;
  lastActiveDate: string | null;
}

export interface Achievement {
  code: string;
  title: string;
  emoji: string;
  description: string;
  threshold: number;
  progress: number;
  unlocked: boolean;
  unlockedAt: string | null;
}

export interface StreakSummary {
  mealStreak: StreakInfo;
  workoutStreak: StreakInfo;
  overallStreak: StreakInfo;
  achievements: Achievement[];
  /** Badge codes unlocked by the request that returned this payload (for celebration). */
  newlyUnlocked: string[];
}

export interface WeightGoalProgress {
  targetWeightKg: number;
  startWeightKg: number;
  currentWeightKg: number;
  startDate: string;
  targetDate: string | null;
  remainingKg: number;
  changeSoFarKg: number;
  progressPct: number;
  ratePerWeekKg: number | null;
  projectedDate: string | null;
  onTrack: boolean | null;
  achieved: boolean;
  createdAt: string;
}

/** `progress` is null when the user has no weight goal set. */
export interface WeightGoalResponse {
  progress: WeightGoalProgress | null;
}

export type NotificationType = "ACHIEVEMENT" | "MEAL_REMINDER" | "STREAK_RISK" | "WEIGHT_REMINDER";
export type NotificationSeverity = "success" | "info" | "warning";

export interface NotificationItem {
  key: string;
  type: NotificationType;
  title: string;
  body: string;
  emoji: string;
  severity: NotificationSeverity;
  createdAt: string;
  actionHref: string | null;
  read: boolean;
}

export interface NotificationFeed {
  items: NotificationItem[];
  unreadCount: number;
}

export interface PageEnvelope<T> {
  data: T[];
  page: number;
  size: number;
  total: number;
}
