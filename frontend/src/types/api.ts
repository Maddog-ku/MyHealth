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

export interface SessionInfo {
  id: number;
  /** Friendly "Browser · OS" label derived from the User-Agent. */
  device: string;
  createdAt: string;
  lastActiveAt: string;
  expiresAt: string;
  current: boolean;
}

export interface SessionList {
  sessions: SessionInfo[];
}

export interface ScheduleDay {
  /** ISO weekday: 1 = Monday … 7 = Sunday. */
  weekday: number;
  rest: boolean;
  /** WorkoutCategory code (e.g. "legs"); null/empty on a rest day. */
  category: string | null;
  durationMin: number;
  focus: string;
}

export interface WorkoutSchedule {
  id: number;
  goal: string;
  /** First day the pattern applies from (always a Monday). */
  startDate: string;
  weeks: number;
  daysPerWeek: number;
  intensity: string;
  days: ScheduleDay[];
  createdAt: string;
}

export interface CategoryVolume {
  category: string;
  sessions: number;
  sets: number;
  kcal: number;
}

export interface WeekVolume {
  /** Monday of the bucketed week. */
  weekStart: string;
  sessions: number;
  sets: number;
  kcal: number;
}

export interface WorkoutVolume {
  from: string;
  to: string;
  weeks: number;
  totalSessions: number;
  totalSets: number;
  totalKcal: number;
  activeDays: number;
  avgSessionsPerWeek: number;
  byCategory: CategoryVolume[];
  series: WeekVolume[];
}

export interface WorkoutGoalProgress {
  targetSessionsPerWeek: number;
  completedThisWeek: number;
  remaining: number;
  progressPct: number;
  weekStart: string;
  achieved: boolean;
  createdAt: string;
}

/** `progress` is null when the user has no weekly workout goal set. */
export interface WorkoutGoalResponse {
  progress: WorkoutGoalProgress | null;
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

export interface FoodCatalogItem {
  id: string;
  name: string;
  category: string;
  servingGrams: number;
  kcal: number;
  protein: number;
  fat: number;
  carb: number;
  aliases: string[];
}

/** One recommended food (a common serving) to fill today's nutrition gap. */
export interface FoodSuggestion {
  id: string;
  name: string;
  category: string;
  grams: number;
  kcal: number;
  protein: number;
  reason: string;
}

export interface FoodSuggestions {
  remainingKcal: number;
  proteinGapG: number;
  over: boolean;
  headline: string;
  items: FoodSuggestion[];
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

export interface MealPreview {
  date: string;
  slot: string;
  description?: string;
  items: FoodItem[];
  totalKcal: number;
  totalProtein: number;
  totalFat: number;
  totalCarb: number;
  aiSuggestion?: string;
}

export interface FavoriteMeal {
  id: number;
  name: string;
  slot: string;
  description?: string;
  items: FoodItem[];
  totalKcal: number;
  totalProtein: number;
  totalFat: number;
  totalCarb: number;
  aiSuggestion?: string;
  createdAt: string;
}

export interface RecentMeal {
  id: number;
  date: string;
  displayName: string;
  slot: string;
  description?: string;
  items: FoodItem[];
  totalKcal: number;
  totalProtein: number;
  totalFat: number;
  totalCarb: number;
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

/** Goal-attainment rates for the week, each clamped 0–100. */
export interface WeeklyAdherence {
  caloriePct: number;
  proteinPct: number;
  workoutPct: number;
  /** Weekly session goal, or null when the user has no workout goal set. */
  workoutTarget: number | null;
  loggingPct: number;
  daysLogged: number;
  daysCovered: number;
}

/** A detected pattern worth acting on this week. */
export interface WeeklyTrend {
  type: "PROTEIN_LOW" | "LOGGING_GAP" | "WORKOUT_DECLINE" | "WEIGHT_PLATEAU" | string;
  severity: "info" | "warn";
  title: string;
  detail: string;
}

export interface WeeklyReport {
  weekStart: string;
  weekEnd: string;
  summary: WeeklySummary;
  adherence: WeeklyAdherence;
  trends: WeeklyTrend[];
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

export interface SearchResult {
  type: "MEAL" | "WORKOUT";
  id: number;
  title: string;
  subtitle: string;
  date: string;
  kcal: number;
}

export interface SearchResponse {
  query: string;
  results: SearchResult[];
}

export interface MacroBudget {
  name: "protein" | "carb" | "fat";
  targetG: number;
  consumedG: number;
  pct: number;
}

export interface CalorieBudget {
  date: string;
  goalKcal: number;
  intakeKcal: number;
  burnKcal: number;
  budgetKcal: number;
  remainingKcal: number;
  consumedPct: number;
  over: boolean;
  macros: MacroBudget[];
}

export interface HealthPlanNutrition {
  goalKcal: number;
  budgetKcal: number;
  intakeKcal: number;
  burnKcal: number;
  remainingKcal: number;
  consumedPct: number;
  over: boolean;
  macros: MacroBudget[];
}

export interface HealthPlanWeight {
  configured: boolean;
  currentWeightKg: number | null;
  targetWeightKg: number | null;
  remainingKg: number | null;
  progressPct: number;
  targetDate: string | null;
  projectedDate: string | null;
  onTrack: boolean | null;
  achieved: boolean;
}

export interface HealthPlanWorkout {
  configured: boolean;
  workoutsDoneToday: number;
  workoutsPlannedToday: number;
  targetSessionsPerWeek: number | null;
  completedThisWeek: number | null;
  remainingThisWeek: number | null;
  progressPct: number | null;
  achievedThisWeek: boolean;
}

export interface HealthPlanStreak {
  current: number;
  longest: number;
  lastActiveDate: string | null;
}

export interface HealthPlanAction {
  type: string;
  title: string;
  detail: string;
  priority: number;
  href: string;
}

export interface HealthPlan {
  date: string;
  primaryGoal: string;
  readinessScore: number;
  nutrition: HealthPlanNutrition;
  weight: HealthPlanWeight;
  workout: HealthPlanWorkout;
  streak: HealthPlanStreak;
  nextActions: HealthPlanAction[];
}

export interface HealthPlanSettings {
  primaryGoal: Goal;
  currentWeightKg: number | null;
  weightGoal: WeightGoalProgress | null;
  workoutGoal: WorkoutGoalProgress | null;
}

export interface HealthPlanSettingsRequest {
  primaryGoal: Goal;
  weightGoal: {
    enabled: boolean;
    targetWeightKg: number | null;
    targetDate?: string | null;
  };
  workoutGoal: {
    enabled: boolean;
    targetSessionsPerWeek: number | null;
  };
}

export type HabitType = "WATER" | "STRETCH" | "PROTEIN" | "SLEEP";

export interface HabitItem {
  type: HabitType;
  title: string;
  description: string;
  completed: boolean;
  completedAt: string | null;
  /** Consecutive days this habit has been completed, ending today (grace day if not yet done). */
  streak: number;
}

export interface DailyHabits {
  date: string;
  completed: number;
  total: number;
  items: HabitItem[];
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

export type SystemComponentStatusValue = "UP" | "DEGRADED" | "DOWN";

export interface SystemComponentStatus {
  key: string;
  label: string;
  status: SystemComponentStatusValue;
  detail: string;
}

export interface SystemStatus {
  status: SystemComponentStatusValue;
  checkedAt: string;
  components: SystemComponentStatus[];
}

export interface PageEnvelope<T> {
  data: T[];
  page: number;
  size: number;
  total: number;
}
