import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { WeeklyReport } from "@/types/api";

/** Live weekly summary + cached narrative for the week starting on `weekStart` (a Monday, ISO date). */
export function useWeeklyReport(weekStart: string) {
  return useQuery({
    queryKey: qk.weeklyReport(weekStart),
    queryFn: () => api.weeklyReport(weekStart),
    enabled: Boolean(getAccessToken()),
  });
}

/** Generate (or regenerate) the AI narrative; the result replaces the cached query for that week. */
export function useGenerateWeeklyReport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (weekStart: string) => api.generateWeeklyReport(weekStart),
    onSuccess: (report: WeeklyReport) => {
      qc.setQueryData(qk.weeklyReport(report.weekStart), report);
    },
  });
}
