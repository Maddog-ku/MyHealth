import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { HabitType } from "@/types/api";

export function useDailyHabits(date: string) {
  return useQuery({ queryKey: qk.dailyHabits(date), queryFn: () => api.dailyHabits(date) });
}

export function useToggleHabit(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ type, completed }: { type: HabitType; completed: boolean }) =>
      api.toggleHabit(type, { date, completed }),
    onSuccess: (data) => {
      qc.setQueryData(qk.dailyHabits(date), data);
      qc.invalidateQueries({ queryKey: qk.streak });
      qc.invalidateQueries({ queryKey: qk.notifications });
    },
  });
}
