import { useQuery } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";

/**
 * Live streaks + badge wall. The GET reconciles achievements server-side, so the
 * returned `newlyUnlocked` reports anything earned since the last view.
 */
export function useStreak() {
  return useQuery({
    queryKey: qk.streak,
    queryFn: () => api.streak(),
    enabled: Boolean(getAccessToken()),
    // Streaks change at most once a day and the GET reconciles achievements server-side,
    // so keep it fresh for a while instead of refetching on every dashboard visit.
    staleTime: 5 * 60_000,
  });
}
