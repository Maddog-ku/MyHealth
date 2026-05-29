import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useAiStatus() {
  return useQuery({
    queryKey: qk.aiStatus,
    queryFn: api.aiStatus,
    refetchInterval: 30_000,
    staleTime: 15_000,
  });
}
