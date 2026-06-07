import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useWorkoutSchedules() {
  return useQuery({
    queryKey: qk.workoutSchedules,
    queryFn: () => api.workoutSchedules(),
  });
}

export function useGenerateWorkoutSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { startDate: string; daysPerWeek: number; weeks: number; intensity: string }) =>
      api.generateWorkoutSchedule(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.workoutSchedules }),
  });
}

export function useDeleteWorkoutSchedule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteWorkoutSchedule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.workoutSchedules }),
  });
}

/**
 * Materialize one weekday of a schedule into a real workout plan. The created plan lands
 * on the chosen date, so the workouts/stats queries for THAT date are invalidated.
 */
export function useApplyScheduleDay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, date, weekday }: { id: number; date: string; weekday: number }) =>
      api.applyScheduleDay(id, { date, weekday }),
    onSuccess: (_plan, { date }) => {
      qc.invalidateQueries({ queryKey: qk.workouts(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}
