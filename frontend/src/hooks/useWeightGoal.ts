import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { WeightGoalResponse } from "@/types/api";

/** Current weight goal + live progress (`progress` is null when none is set). */
export function useWeightGoal() {
  return useQuery({
    queryKey: qk.weightGoal,
    queryFn: () => api.weightGoal(),
    enabled: Boolean(getAccessToken()),
  });
}

export function useSetWeightGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { targetWeightKg: number; targetDate?: string | null }) => api.setWeightGoal(body),
    onSuccess: (res: WeightGoalResponse) => qc.setQueryData(qk.weightGoal, res),
  });
}

export function useDeleteWeightGoal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.deleteWeightGoal(),
    onSuccess: () => qc.setQueryData<WeightGoalResponse>(qk.weightGoal, { progress: null }),
  });
}
