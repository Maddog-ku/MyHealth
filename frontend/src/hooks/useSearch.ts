import { useQuery } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";

/** Unified meal + workout search. Pass an already-debounced term; runs only when non-empty. */
export function useSearch(term: string) {
  const q = term.trim();
  return useQuery({
    queryKey: qk.search(q),
    queryFn: () => api.search(q),
    enabled: Boolean(getAccessToken()) && q.length >= 1,
    staleTime: 30_000,
  });
}
