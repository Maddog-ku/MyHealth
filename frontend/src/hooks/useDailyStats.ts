import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useDailyStats(date: string) {
  return useQuery({ queryKey: qk.dailyStats(date), queryFn: () => api.dailyStats(date) });
}
