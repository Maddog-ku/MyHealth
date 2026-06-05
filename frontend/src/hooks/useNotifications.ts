import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { NotificationFeed } from "@/types/api";

/** Notification feed (achievement unlocks + live reminders) with unread count. */
export function useNotifications() {
  return useQuery({
    queryKey: qk.notifications,
    queryFn: () => api.notifications(),
    enabled: Boolean(getAccessToken()),
    // Reminders only change a few times a day; refetch on a relaxed interval.
    staleTime: 60_000,
    refetchInterval: 5 * 60_000,
  });
}

/** Mark everything read; the server returns the refreshed feed (unreadCount 0). */
export function useMarkNotificationsRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.markNotificationsRead(),
    onSuccess: (feed: NotificationFeed) => qc.setQueryData(qk.notifications, feed),
  });
}
