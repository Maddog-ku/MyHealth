import axios, { AxiosError, AxiosHeaders, AxiosInstance, AxiosRequestConfig } from "axios";
import type { AuthResponse, DailyStats, FoodItem, Meal, PageEnvelope, Profile, User, WorkoutPlan } from "@/types/api";

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
  completeWorkout: (id: number) => http.post<WorkoutPlan>(`/workouts/${id}/complete`, {}).then((r) => r.data),

  meals: (date: string) => http.get<PageEnvelope<Meal>>(`/meals`, { params: { date } }).then((r) => r.data),
  createMeal: (form: FormData) => http.post<Meal>("/meals", form).then((r) => r.data),
  updateMeal: (id: number, body: { items: FoodItem[]; aiSuggestion: string | null }) =>
    http.put<Meal>(`/meals/${id}`, body).then((r) => r.data),
  deleteMeal: (id: number) => http.delete<void>(`/meals/${id}`).then(() => undefined),

  aiStatus: () =>
    http
      .get<{ provider: string; textModel: string; visionModel: string; loaded: boolean; idleTimeoutSec: number; lastUsedAt?: string }>(
        "/ai/status",
      )
      .then((r) => r.data),
  aiUnload: () => http.post<{ unloaded: boolean }>("/ai/unload", {}).then((r) => r.data),
};
