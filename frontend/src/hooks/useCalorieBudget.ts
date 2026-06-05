import { useQuery } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";
import { todayLocalISO } from "@/lib/date";

/** The given day's calorie budget ring + macro targets (defaults to today). */
export function useCalorieBudget(date: string = todayLocalISO()) {
  return useQuery({
    queryKey: qk.calorieBudget(date),
    queryFn: () => api.calorieBudget(date),
    enabled: Boolean(getAccessToken()),
  });
}
