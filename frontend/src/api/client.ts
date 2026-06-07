import axios, { AxiosError, AxiosHeaders, AxiosInstance, AxiosRequestConfig } from "axios";
import type { AuthResponse, CalorieBudget, ChatMessage, DailyHabits, DailyStats, FavoriteMeal, FoodItem, HabitType, Meal, NotificationFeed, PageEnvelope, Profile, RecentMeal, SearchResponse, SessionList, StreakSummary, User, WeeklyReport, WeightGoalResponse, WorkoutPlan, WorkoutSchedule, WorkoutVolume } from "@/types/api";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

const ACCESS_KEY = "accessToken";
const REFRESH_KEY = "refreshToken";

export function getAccessToken() {
  return localStorage.getItem(ACCESS_KEY);
}
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY);
}
export function saveAuth(auth: Pick<AuthResponse, "accessToken" | "refreshToken">) {
  localStorage.setItem(ACCESS_KEY, auth.accessToken);
  localStorage.setItem(REFRESH_KEY, auth.refreshToken);
}
export function clearAuth() {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

export type ApiErrorBody = {
  status: number;
  error: string;
  message: string;
  path?: string;
  details?: { field: string; code: string; message: string }[];
};

export class ApiError extends Error {
  status: number;
  code: string;
  details?: ApiErrorBody["details"];
  constructor(body: ApiErrorBody | { status: number; error?: string; message?: string }) {
    super(body.message || body.error || `HTTP ${body.status}`);
    this.status = body.status;
    this.code = (body as ApiErrorBody).error ?? "UNKNOWN";
    this.details = (body as ApiErrorBody).details;
  }
}

export const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    const headers = AxiosHeaders.from(config.headers);
    headers.set("Authorization", `Bearer ${token}`);
    config.headers = headers;
  }
  if (config.data instanceof FormData) {
    const headers = AxiosHeaders.from(config.headers);
    headers.delete("Content-Type");
    config.headers = headers;
  }
  return config;
});

let refreshing: Promise<string> | null = null;

async function performRefresh(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new ApiError({ status: 401, error: "UNAUTHORIZED", message: "no refresh token" });
  const { data } = await axios.post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken });
  saveAuth(data);
  return data.accessToken;
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status ?? 0;
    const url = original?.url ?? "";
    const isAuthEndpoint = url.includes("/auth/");

    if (status === 401 && original && !original._retried && !isAuthEndpoint && getRefreshToken()) {
      original._retried = true;
      try {
        refreshing ??= performRefresh().finally(() => {
          refreshing = null;
        });
        await refreshing;
        return http.request(original);
      } catch {
        clearAuth();
      }
    }

    const body = error.response?.data;
    if (body && typeof body === "object" && "status" in body) {
      throw new ApiError(body as ApiErrorBody);
    }
    // status 0 = no HTTP response at all (backend down / network unreachable).
    // axios reports a bare "Network Error"; give users something actionable.
    const message = status === 0 ? "無法連線到伺服器，請確認後端是否啟動後重試" : error.message;
    throw new ApiError({ status, message });
  },
);

export const api = {
  register: (body: Record<string, unknown>) =>
    http.post<User>("/auth/register", body).then((r) => r.data),
  login: (email: string, password: string) =>
    http.post<AuthResponse>("/auth/login", { email, password }).then((r) => r.data),
  logout: (refreshToken: string) => http.post<void>("/auth/logout", { refreshToken }).then(() => undefined),
  me: () => http.get<User>("/me").then((r) => r.data),
  updateProfile: (profile: Profile) => http.put<Profile>("/me/profile", profile).then((r) => r.data),
  deleteAccount: () => http.delete<void>("/me").then(() => undefined),

  // The current refresh token identifies "this device" so the backend can flag it and
  // exclude it from "log out other devices" — sent as a header here, body for revoke-others.
  changePassword: (currentPassword: string, newPassword: string) =>
    http
      .post<void>("/me/password", { currentPassword, newPassword }, { headers: { "X-Refresh-Token": getRefreshToken() ?? "" } })
      .then(() => undefined),
  sessions: () =>
    http.get<SessionList>("/me/sessions", { headers: { "X-Refresh-Token": getRefreshToken() ?? "" } }).then((r) => r.data),
  revokeSession: (id: number) => http.delete<void>(`/me/sessions/${id}`).then(() => undefined),
  revokeOtherSessions: () =>
    http.post<SessionList>("/me/sessions/revoke-others", { refreshToken: getRefreshToken() ?? "" }).then((r) => r.data),

  dailyStats: (date: string) => http.get<DailyStats>(`/stats/daily`, { params: { date } }).then((r) => r.data),
  rangeStats: (from: string, to: string) =>
    http
      .get<{
        from: string;
        to: string;
        series: Array<{
          date: string;
          intakeKcal: number;
          burnKcal: number;
          weightKg: number | null;
          bodyFatPct: number | null;
          muscleMassKg: number | null;
          waistCm: number | null;
          bodyWaterPct: number | null;
        }>;
      }>(`/stats/range`, { params: { from, to } })
      .then((r) => r.data),

  workouts: (date: string) => http.get<PageEnvelope<WorkoutPlan>>(`/workouts`, { params: { date } }).then((r) => r.data),
  generateWorkout: (body: Record<string, unknown>) =>
    http.post<WorkoutPlan>("/workouts/generate", body).then((r) => r.data),
  completeWorkout: (id: number, actualKcal?: number) =>
    http.post<WorkoutPlan>(`/workouts/${id}/complete`, actualKcal === undefined ? {} : { actualKcal }).then((r) => r.data),
  removeWorkoutItems: (id: number, indices: number[]) =>
    http.post<WorkoutPlan>(`/workouts/${id}/items/remove`, { indices }).then((r) => r.data),
  deleteWorkout: (id: number) => http.delete(`/workouts/${id}`).then(() => undefined),

  workoutSchedules: () =>
    http.get<PageEnvelope<WorkoutSchedule>>("/workout-schedules").then((r) => r.data),
  generateWorkoutSchedule: (body: { startDate: string; daysPerWeek: number; weeks: number; intensity: string }) =>
    http.post<WorkoutSchedule>("/workout-schedules/generate", body).then((r) => r.data),
  applyScheduleDay: (id: number, body: { date: string; weekday: number }) =>
    http.post<WorkoutPlan>(`/workout-schedules/${id}/apply`, body).then((r) => r.data),
  deleteWorkoutSchedule: (id: number) => http.delete<void>(`/workout-schedules/${id}`).then(() => undefined),

  workoutVolume: (weeks?: number) =>
    http.get<WorkoutVolume>("/workouts/volume", { params: weeks ? { weeks } : undefined }).then((r) => r.data),

  meals: (date: string) => http.get<PageEnvelope<Meal>>(`/meals`, { params: { date } }).then((r) => r.data),
  recentMeals: (beforeDate: string, limit = 5) =>
    http.get<PageEnvelope<RecentMeal>>("/meals/recent", { params: { beforeDate, limit } }).then((r) => r.data),
  favoriteMeals: () => http.get<FavoriteMeal[]>("/meals/favorites").then((r) => r.data),
  createMeal: (form: FormData) => http.post<Meal>("/meals", form).then((r) => r.data),
  updateMeal: (id: number, body: { items: FoodItem[]; aiSuggestion: string | null }) =>
    http.put<Meal>(`/meals/${id}`, body).then((r) => r.data),
  favoriteMeal: (id: number, name?: string) =>
    http.post<FavoriteMeal>(`/meals/${id}/favorite`, name ? { name } : {}).then((r) => r.data),
  copyMeal: (id: number, body: { date: string; slot?: string }) =>
    http.post<Meal>(`/meals/${id}/copy`, body).then((r) => r.data),
  copyFavoriteMeal: (id: number, body: { date: string; slot?: string }) =>
    http.post<Meal>(`/meals/favorites/${id}/copy`, body).then((r) => r.data),
  deleteFavoriteMeal: (id: number) => http.delete<void>(`/meals/favorites/${id}`).then(() => undefined),
  deleteMeal: (id: number) => http.delete<void>(`/meals/${id}`).then(() => undefined),

  aiStatus: () =>
    http
      .get<{ provider: string; textModel: string; visionModel: string; loaded: boolean; idleTimeoutSec: number; lastUsedAt?: string }>(
        "/ai/status",
      )
      .then((r) => r.data),
  aiUnload: () => http.post<{ unloaded: boolean }>("/ai/unload", {}).then((r) => r.data),

  chatHistory: () =>
    http.get<{ messages: ChatMessage[] }>("/ai/chat/history").then((r) => r.data.messages),
  sendChat: (message: string) =>
    http
      .post<{
        userMessage: ChatMessage;
        reply: ChatMessage;
        mealLogged: boolean;
        workoutLogged: boolean;
        weightLogged: boolean;
        loggedDate: string | null;
      }>("/ai/chat", { message })
      .then((r) => r.data),
  clearChat: () => http.delete<void>("/ai/chat/history").then(() => undefined),

  weeklyReport: (weekStart?: string) =>
    http
      .get<WeeklyReport>("/reports/weekly", { params: weekStart ? { weekStart } : undefined })
      .then((r) => r.data),
  generateWeeklyReport: (weekStart?: string) =>
    http.post<WeeklyReport>("/reports/weekly/generate", weekStart ? { weekStart } : {}).then((r) => r.data),

  streak: () => http.get<StreakSummary>("/streak").then((r) => r.data),

  notifications: () => http.get<NotificationFeed>("/notifications").then((r) => r.data),
  markNotificationsRead: () => http.post<NotificationFeed>("/notifications/read").then((r) => r.data),

  calorieBudget: (date: string) =>
    http.get<CalorieBudget>("/stats/budget", { params: { date } }).then((r) => r.data),

  dailyHabits: (date: string) =>
    http.get<DailyHabits>("/habits/daily", { params: { date } }).then((r) => r.data),
  toggleHabit: (type: HabitType, body: { date: string; completed: boolean }) =>
    http.post<DailyHabits>(`/habits/${type}/toggle`, body).then((r) => r.data),

  search: (q: string, limit?: number) =>
    http.get<SearchResponse>("/search", { params: { q, ...(limit ? { limit } : {}) } }).then((r) => r.data),

  weightGoal: () => http.get<WeightGoalResponse>("/weight-goal").then((r) => r.data),
  setWeightGoal: (body: { targetWeightKg: number; targetDate?: string | null }) =>
    http.put<WeightGoalResponse>("/weight-goal", body).then((r) => r.data),
  deleteWeightGoal: () => http.delete<void>("/weight-goal").then(() => undefined),
};
