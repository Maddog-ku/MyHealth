import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";
import type { FoodItem } from "@/types/api";

export function useMeals(date: string) {
  return useQuery({ queryKey: qk.meals(date), queryFn: () => api.meals(date) });
}

export function useRecentMeals(date: string) {
  return useQuery({ queryKey: qk.recentMeals(date), queryFn: () => api.recentMeals(date, 5) });
}

export function useFavoriteMeals() {
  return useQuery({ queryKey: qk.favoriteMeals, queryFn: () => api.favoriteMeals() });
}

export function useFoodSearch(query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: qk.foods(q),
    queryFn: () => api.foods(q, 8),
    enabled: q.length > 0,
  });
}

export function useCreateMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (form: FormData) => api.createMeal(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
      qc.invalidateQueries({ queryKey: qk.healthPlan(date) });
      qc.invalidateQueries({ queryKey: qk.calorieBudget(date) });
    },
  });
}

export function usePreviewMeal() {
  return useMutation({
    mutationFn: (form: FormData) => api.previewMeal(form),
  });
}

export function useConfirmMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (form: FormData) => api.confirmMeal(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
      qc.invalidateQueries({ queryKey: qk.healthPlan(date) });
      qc.invalidateQueries({ queryKey: qk.calorieBudget(date) });
    },
  });
}

export function useCopyMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, slot }: { id: number; slot?: string }) => api.copyMeal(id, { date, slot }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.recentMeals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
      qc.invalidateQueries({ queryKey: qk.healthPlan(date) });
      qc.invalidateQueries({ queryKey: qk.calorieBudget(date) });
    },
  });
}

export function useCopyFavoriteMeal(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, slot }: { id: number; slot?: string }) => api.copyFavoriteMeal(id, { date, slot }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.meals(date) });
      qc.invalidateQueries({ queryKey: qk.dailyStats(date) });
      qc.invalidateQueries({ queryKey: qk.healthPlan(date) });
      qc.invalidateQueries({ queryKey: qk.calorieBudget(date) });
    },
  });
}

export function useFavoriteMeal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }: { id: number; name?: string }) => api.favoriteMeal(id, name),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.favoriteMeals });
    },
  });
}

export function useDeleteFavoriteMeal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.deleteFavoriteMeal(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.favoriteMeals });
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
      qc.invalidateQueries({ queryKey: qk.healthPlan(date) });
      qc.invalidateQueries({ queryKey: qk.calorieBudget(date) });
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
      qc.invalidateQueries({ queryKey: qk.healthPlan(date) });
      qc.invalidateQueries({ queryKey: qk.calorieBudget(date) });
    },
  });
}
