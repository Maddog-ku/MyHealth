import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: (count, err) => {
        const status = (err as { status?: number })?.status;
        if (status && status >= 400 && status < 500) return false;
        return count < 2;
      },
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false },
  },
});

export const qk = {
  me: ["me"] as const,
  sessions: ["me", "sessions"] as const,
  dailyStats: (date: string) => ["stats", "daily", date] as const,
  workouts: (date: string) => ["workouts", date] as const,
  workoutSchedules: ["workout-schedules"] as const,
  workoutVolume: (weeks: number) => ["workouts", "volume", weeks] as const,
  workoutGoal: ["workout-goal"] as const,
  meals: (date: string) => ["meals", date] as const,
  recentMeals: (date: string) => ["meals", "recent", date] as const,
  favoriteMeals: ["meals", "favorites"] as const,
  foods: (q: string) => ["foods", q] as const,
  foodSuggestions: (date: string) => ["foods", "suggestions", date] as const,
  aiStatus: ["ai", "status"] as const,
  chat: ["ai", "chat"] as const,
  weeklyReport: (weekStart: string) => ["reports", "weekly", weekStart] as const,
  streak: ["streak"] as const,
  notifications: ["notifications"] as const,
  weightGoal: ["weight-goal"] as const,
  calorieBudget: (date: string) => ["stats", "budget", date] as const,
  healthPlan: (date: string) => ["health-plan", date] as const,
  healthPlanSettings: ["health-plan", "settings"] as const,
  dailyHabits: (date: string) => ["habits", "daily", date] as const,
  search: (q: string) => ["search", q] as const,
  systemStatus: ["system", "status"] as const,
};
