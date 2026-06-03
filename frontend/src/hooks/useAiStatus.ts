import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useAiStatus() {
  return useQuery({
    queryKey: qk.aiStatus,
    queryFn: api.aiStatus,
    refetchInterval: () => (document.visibilityState === "visible" ? 120_000 : false),
    refetchIntervalInBackground: false,
    staleTime: 60_000,
  });
}
