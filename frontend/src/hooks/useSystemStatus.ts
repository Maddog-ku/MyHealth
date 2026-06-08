import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useSystemStatus() {
  return useQuery({
    queryKey: qk.systemStatus,
    queryFn: api.systemStatus,
    refetchInterval: () => (document.visibilityState === "visible" ? 120_000 : false),
    refetchIntervalInBackground: false,
    staleTime: 30_000,
  });
}
