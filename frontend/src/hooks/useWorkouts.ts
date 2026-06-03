import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useWorkouts(date: string) {
  return useQuery({ queryKey: qk.workouts(date), queryFn: () => api.workouts(date) });
}

export function useGenerateWorkout(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { date: string; category: string; durationMin: number; intensity: string }) =>
      api.generateWorkout(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.workouts(date) }),
  });
}

export function useCompleteWorkout(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, actualKcal }: { id: number; actualKcal?: number }) =>
      api.completeWorkout(id, actualKcal),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.workouts(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}

/**
 * Cancel exercises (one or many at once) from plans, and/or delete whole plans.
 * `itemsByPlan` maps a plan id to the exercise indices to drop; `deletePlanIds`
 * lists plans to remove entirely. Everything runs in one batch, then the day's
 * workouts and stats are refetched once.
 */
export function useCancelWorkoutSelection(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      itemsByPlan,
      deletePlanIds,
    }: {
      itemsByPlan?: Record<number, number[]>;
      deletePlanIds?: number[];
    }) => {
      await Promise.all([
        ...Object.entries(itemsByPlan ?? {})
          .filter(([, indices]) => indices.length > 0)
          .map(([id, indices]) => api.removeWorkoutItems(Number(id), indices)),
        ...(deletePlanIds ?? []).map((id) => api.deleteWorkout(id)),
      ]);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.workouts(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}
