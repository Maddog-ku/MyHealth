import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, getAccessToken } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { ChatMessage } from "@/types/api";

export function useChatHistory() {
  return useQuery({
    queryKey: qk.chat,
    queryFn: api.chatHistory,
    enabled: Boolean(getAccessToken()),
    staleTime: 60_000,
  });
}

export function useSendChat() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (message: string) => api.sendChat(message),
    // Optimistically show the user's message immediately; reconcile on success.
    onMutate: async (message: string) => {
      await qc.cancelQueries({ queryKey: qk.chat });
      const previous = qc.getQueryData<ChatMessage[]>(qk.chat) ?? [];
      const optimistic: ChatMessage = {
        id: -Date.now(),
        role: "user",
        content: message,
        createdAt: new Date().toISOString(),
      };
      qc.setQueryData<ChatMessage[]>(qk.chat, [...previous, optimistic]);
      return { previous };
    },
    onError: (_err, _message, context) => {
      if (context) qc.setQueryData(qk.chat, context.previous);
    },
    onSuccess: ({ userMessage, reply, mealLogged, workoutLogged }) => {
      qc.setQueryData<ChatMessage[]>(qk.chat, (current) => {
        // Drop the optimistic temp (negative id) and append the persisted pair.
        const committed = (current ?? []).filter((m) => m.id >= 0);
        return [...committed, userMessage, reply];
      });
      // The assistant just recorded a meal — refresh 飲食追蹤 and dashboard totals.
      if (mealLogged) {
        qc.invalidateQueries({ queryKey: ["meals"] });
        qc.invalidateQueries({ queryKey: ["stats", "daily"] });
      }
      // ...or generated a workout plan — refresh 運動菜單 and dashboard totals.
      if (workoutLogged) {
        qc.invalidateQueries({ queryKey: ["workouts"] });
        qc.invalidateQueries({ queryKey: ["stats", "daily"] });
      }
    },
  });
}

export function useClearChat() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.clearChat(),
    onSuccess: () => qc.setQueryData(qk.chat, []),
  });
}
