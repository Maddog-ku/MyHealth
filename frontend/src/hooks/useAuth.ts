import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, clearAuth, getAccessToken, getRefreshToken, saveAuth } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { Profile } from "@/types/api";

export function useMe() {
  return useQuery({
    queryKey: qk.me,
    queryFn: api.me,
    enabled: Boolean(getAccessToken()),
    staleTime: 60_000,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => api.login(email, password),
    onSuccess: (data) => {
      saveAuth(data);
      qc.invalidateQueries({ queryKey: qk.me });
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => api.register(body),
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const token = getRefreshToken();
      if (token) {
        try {
          await api.logout(token);
        } catch {
          // swallow; we clear locally anyway
        }
      }
    },
    onSettled: () => {
      clearAuth();
      qc.clear();
    },
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (profile: Profile) => api.updateProfile(profile),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.me }),
  });
}

export function useUpdateAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => api.updateAccount(name),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.me }),
  });
}

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.deleteAccount(),
    onSettled: () => {
      clearAuth();
      qc.clear();
    },
  });
}
