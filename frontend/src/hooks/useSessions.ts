import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useSessions() {
  return useQuery({ queryKey: qk.sessions, queryFn: () => api.sessions() });
}

export function useRevokeSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.revokeSession(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.sessions }),
  });
}

export function useRevokeOtherSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.revokeOtherSessions(),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.sessions }),
  });
}
