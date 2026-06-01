import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { FoodItem } from "@/types/api";

export function useMeals(date: string) {
  return useQuery({ queryKey: qk.meals(date), queryFn: () => api.meals(date) });
}

export function useCreateMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (form: FormData) => api.createMeal(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}

export function useUpdateMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, items, aiSuggestion }: { id: number; items: FoodItem[]; aiSuggestion: string | null }) =>
      api.updateMeal(id, { items, aiSuggestion }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}

export function useDeleteMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteMeal(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
    },
  });
}
