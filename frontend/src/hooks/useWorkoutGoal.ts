import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useWorkoutGoal() {
  return useQuery({ queryKey: qk.workoutGoal, queryFn: () => api.workoutGoal() });
}

export function useSetWorkoutGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (targetSessionsPerWeek: number) => api.setWorkoutGoal(targetSessionsPerWeek),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.workoutGoal }),
  });
}

export function useDeleteWorkoutGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.deleteWorkoutGoal(),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.workoutGoal }),
  });
}
