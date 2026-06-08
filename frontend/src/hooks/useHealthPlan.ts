import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { HealthPlanSettingsRequest } from "@/types/api";

export function useHealthPlan(date: string) {
  return useQuery({
    queryKey: qk.healthPlan(date),
    queryFn: () => api.healthPlan(date),
  });
}

export function useHealthPlanSettings() {
  return useQuery({
    queryKey: qk.healthPlanSettings,
    queryFn: () => api.healthPlanSettings(),
  });
}

export function useUpdateHealthPlanSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: HealthPlanSettingsRequest) => api.updateHealthPlanSettings(body),
    onSuccess: (settings) => {
      qc.setQueryData(qk.healthPlanSettings, settings);
      qc.invalidateQueries({ queryKey: ["health-plan"] });
      qc.invalidateQueries({ queryKey: ["stats"] });
      qc.invalidateQueries({ queryKey: qk.me });
      qc.invalidateQueries({ queryKey: qk.weightGoal });
      qc.invalidateQueries({ queryKey: qk.workoutGoal });
    },
  });
}
