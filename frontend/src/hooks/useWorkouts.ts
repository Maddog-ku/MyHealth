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
    mutationFn: (id: number) => api.completeWorkout(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.workouts(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}
