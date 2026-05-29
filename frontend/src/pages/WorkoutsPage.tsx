import { useMemo, useState } from "react";
import { CheckCircle2, Dumbbell, Plus, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useCompleteWorkout, useGenerateWorkout, useWorkouts } from "@/hooks/useWorkouts";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";
import type { ExerciseItem } from "@/types/api";

const CATEGORIES: { value: string; label: string }[] = [
  { value: "abs", label: "腹肌" },
  { value: "legs", label: "腿部" },
  { value: "chest", label: "胸" },
  { value: "back", label: "背" },
  { value: "arms", label: "手臂" },
  { value: "glutes", label: "臀" },
  { value: "cardio", label: "有氧" },
  { value: "full_body", label: "全身" },
];

const INTENSITIES = [
  { value: "low", label: "輕鬆" },
  { value: "medium", label: "中等" },
  { value: "high", label: "強度" },
];

const DURATIONS = [15, 30, 45, 60];

export function WorkoutsPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const [category, setCategory] = useState("abs");
  const [duration, setDuration] = useState(30);
  const [intensity, setIntensity] = useState("medium");

  const workouts = useWorkouts(today);
  const generate = useGenerateWorkout(today);
  const complete = useCompleteWorkout(today);

  return (
    <section className="grid gap-6 animate-fade-in">
      <Card>
        <CardHeader>
          <CardTitle>產生今日菜單</CardTitle>
          <CardDescription>AI 會依分類、時長與強度推薦動作組合</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-[1fr_1fr_1fr_auto]">
          <div className="grid gap-2">
            <label className="text-xs text-muted-foreground">分類</label>
            <Select value={category} onValueChange={setCategory}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {CATEGORIES.map((c) => (
                  <SelectItem key={c.value} value={c.value}>{c.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <label className="text-xs text-muted-foreground">時長（分鐘）</label>
            <Select value={String(duration)} onValueChange={(v) => setDuration(Number(v))}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {DURATIONS.map((d) => (
                  <SelectItem key={d} value={String(d)}>{d} 分鐘</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <label className="text-xs text-muted-foreground">強度</label>
            <Select value={intensity} onValueChange={setIntensity}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {INTENSITIES.map((i) => (
                  <SelectItem key={i.value} value={i.value}>{i.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button
            className="md:self-end"
            disabled={generate.isPending}
            onClick={() => generate.mutate({ date: today, category, durationMin: duration, intensity })}
          >
            <Sparkles className="size-4" />
            {generate.isPending ? "產生中…" : "產生菜單"}
          </Button>
        </CardContent>
        {generate.error instanceof ApiError && generate.error.status === 503 && (
          <CardContent>
            <Alert variant="destructive">
              <AlertTitle>AI 暫時不可用</AlertTitle>
              <AlertDescription>請稍後再試，或手動建立今日菜單。</AlertDescription>
            </Alert>
          </CardContent>
        )}
      </Card>

      {workouts.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : workouts.data && workouts.data.data.length > 0 ? (
        workouts.data.data.map((plan) => (
          <Card key={plan.id}>
            <CardHeader className="flex flex-row items-start justify-between gap-4">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Dumbbell className="size-4 text-primary" />
                  {CATEGORIES.find((c) => c.value === plan.category)?.label ?? plan.category}
                </CardTitle>
                <CardDescription>
                  共 {plan.items.length} 個動作 · 預估 {plan.totalKcal} kcal · {new Date(plan.createdAt).toLocaleTimeString("zh-TW", { hour: "2-digit", minute: "2-digit" })}
                </CardDescription>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="muted">估算</Badge>
                <Button
                  size="sm"
                  variant={plan.done ? "secondary" : "default"}
                  disabled={plan.done || complete.isPending}
                  onClick={() => complete.mutate(plan.id)}
                >
                  <CheckCircle2 className="size-4" />
                  {plan.done ? "已完成" : "完成"}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              <ul className="divide-y divide-border rounded-md border">
                {plan.items.map((item) => (
                  <ExerciseRow key={item.name} item={item} />
                ))}
              </ul>
            </CardContent>
          </Card>
        ))
      ) : (
        <Card>
          <CardContent className="grid place-items-center gap-3 p-10 text-center">
            <Plus className="size-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">今天還沒有菜單，選好分類後按上方「產生菜單」。</p>
          </CardContent>
        </Card>
      )}
    </section>
  );
}

function ExerciseRow({ item }: { item: ExerciseItem }) {
  return (
    <li className="grid grid-cols-[1fr_auto] gap-2 px-4 py-3 text-sm md:grid-cols-[2fr_1fr_1fr_1fr]">
      <div>
        <p className="font-medium">{item.name}</p>
        {item.note && <p className="text-xs text-muted-foreground">{item.note}</p>}
      </div>
      <p className="text-muted-foreground">{item.sets} 組 × {item.reps}</p>
      <p className="hidden text-muted-foreground md:block">休息 {item.restSec}s</p>
      <p className="text-muted-foreground">{item.kcal} kcal</p>
    </li>
  );
}
