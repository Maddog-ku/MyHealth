import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import { qk } from "@/lib/queryClient";

export function useWorkoutVolume(weeks: number) {
  return useQuery({
    queryKey: qk.workoutVolume(weeks),
    queryFn: () => api.workoutVolume(weeks),
  });
}
