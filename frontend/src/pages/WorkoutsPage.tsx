import { useMemo, useState } from "react";
import { CheckCircle2, Dumbbell, Plus, Sparkles, Flame, Clock, Trophy, Heart } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { AiGenerationPanel } from "@/components/AiGenerationPanel";
import { useCompleteWorkout, useGenerateWorkout, useWorkouts } from "@/hooks/useWorkouts";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";
import type { ExerciseItem } from "@/types/api";

const CATEGORIES: { value: string; label: string }[] = [
  { value: "abs", label: "腹肌核心" },
  { value: "waist", label: "腰腹側線" },
  { value: "legs", label: "腿部肌群" },
  { value: "chest", label: "胸部塑造" },
  { value: "back", label: "背部強化" },
  { value: "arms", label: "手臂雕塑" },
  { value: "glutes", label: "臀部緊實" },
  { value: "cardio", label: "高效有氧" },
  { value: "full_body", label: "全身燃脂" },
];

const INTENSITIES = [
  { value: "low", label: "輕鬆舒適" },
  { value: "medium", label: "中等訓練" },
  { value: "high", label: "高強挑戰" },
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
    <section className="grid gap-6 animate-fade-in pb-10">
      {/* Generate Routine Card */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden accent-glow">
        <CardHeader className="pb-3">
          <CardTitle className="text-md font-bold flex items-center gap-2">
            <Sparkles className="size-4.5 text-emerald-500 animate-pulse" />
            AI 智慧菜單規劃
          </CardTitle>
          <CardDescription className="text-xs">選擇您今日想鍛鍊的部位、時間與心肺強度，AI 將量身訂製最適動作組合</CardDescription>
        </CardHeader>
        <CardContent className="px-6 pb-6">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[1.5fr_1fr_1fr_auto] items-end">
            <div className="grid gap-1.5">
              <label className="text-xs font-semibold text-slate-500 px-1">鍛鍊目標分類</label>
              <Select value={category} onValueChange={setCategory} disabled={generate.isPending}>
                <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  {CATEGORIES.map((c) => (
                    <SelectItem key={c.value} value={c.value} className="rounded-xl">{c.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            
            <div className="grid gap-1.5">
              <label className="text-xs font-semibold text-slate-500 px-1">訓練預估時長</label>
              <Select value={String(duration)} onValueChange={(v) => setDuration(Number(v))} disabled={generate.isPending}>
                <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  {DURATIONS.map((d) => (
                    <SelectItem key={d} value={String(d)} className="rounded-xl">{d} 分鐘</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            
            <div className="grid gap-1.5">
              <label className="text-xs font-semibold text-slate-500 px-1">目標阻力強度</label>
              <Select value={intensity} onValueChange={setIntensity} disabled={generate.isPending}>
                <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  {INTENSITIES.map((i) => (
                    <SelectItem key={i.value} value={i.value} className="rounded-xl">{i.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button
              disabled={generate.isPending}
              onClick={() => generate.mutate({ date: today, category, durationMin: duration, intensity })}
              className="rounded-2xl py-5 px-6 bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white font-semibold shadow-md shadow-emerald-500/10 hover:shadow-lg transition-all-smooth gap-1.5 w-full sm:w-auto"
            >
              {generate.isPending ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  菜單生成中...
                </>
              ) : (
                <>
                  <Sparkles className="size-4" />
                  產生訓練菜單
                </>
              )}
            </Button>
          </div>

          {generate.error instanceof ApiError && generate.error.status === 503 && (
            <div className="mt-4">
              <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
                <AlertTitle className="text-xs font-bold">AI 推理引擎忙碌中</AlertTitle>
                <AlertDescription className="text-[11px] opacity-90">請稍後再試一次，或是手動安排合適的伸展活動。</AlertDescription>
              </Alert>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Routine Display List */}
      <div className="space-y-4">
        <h2 className="text-sm font-bold text-slate-500 dark:text-slate-400 px-1 tracking-wider uppercase">今日訓練計畫</h2>

        {generate.isPending && <AiGenerationPanel kind="workout" />}
        
        {workouts.isLoading ? (
          <Skeleton className="h-44 w-full rounded-3xl" />
        ) : workouts.data && workouts.data.data.length > 0 ? (
          workouts.data.data.map((plan) => (
            <Card key={plan.id} className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
              <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-50 dark:border-slate-900/30">
                <div className="flex items-center gap-3">
                  <div className="flex size-10 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                    <Dumbbell className="size-5" />
                  </div>
                  <div>
                    <CardTitle className="text-sm font-bold flex items-center gap-2">
                      {CATEGORIES.find((c) => c.value === plan.category)?.label ?? plan.category}
                    </CardTitle>
                    <CardDescription className="text-[10px] mt-0.5">
                      規劃於 {new Date(plan.createdAt).toLocaleTimeString("zh-TW", { hour: "2-digit", minute: "2-digit" })}
                    </CardDescription>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="muted" className="rounded-full text-[9px] bg-slate-500/10 text-slate-600 dark:text-slate-400 px-2 py-0.5 border border-slate-500/5">
                    AI 智慧生成
                  </Badge>
                  <Button
                    size="sm"
                    variant={plan.done ? "secondary" : "default"}
                    disabled={plan.done || complete.isPending}
                    onClick={() => complete.mutate(plan.id)}
                    className={`rounded-2xl px-4 py-4 text-xs font-semibold gap-1.5 shadow-sm transition-all duration-300 ${
                      plan.done
                        ? "bg-slate-100 dark:bg-slate-900 text-muted-foreground border border-slate-200 dark:border-slate-800"
                        : "bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-500/10"
                    }`}
                  >
                    <CheckCircle2 className="size-4" />
                    {plan.done ? "已打卡完成" : "完成本次訓練"}
                  </Button>
                </div>
              </CardHeader>
              
              <CardContent className="p-0">
                {/* Dashboard inside routine card for quick metrics */}
                <div className="grid grid-cols-2 sm:grid-cols-4 divide-x divide-y sm:divide-y-0 divide-slate-50 dark:divide-slate-900 bg-slate-50/30 dark:bg-slate-900/10 border-b border-slate-50 dark:border-slate-900/30 text-center text-xs">
                  <div className="p-4 flex flex-col items-center">
                    <span className="text-[10px] text-muted-foreground font-semibold">預估卡路里</span>
                    <div className="flex items-center gap-1 mt-1">
                      <Flame className="size-4 text-rose-500" />
                      <span className="font-extrabold text-slate-800 dark:text-slate-100">{plan.totalKcal} kcal</span>
                    </div>
                  </div>
                  <div className="p-4 flex flex-col items-center">
                    <span className="text-[10px] text-muted-foreground font-semibold">動作總計</span>
                    <div className="flex items-center gap-1 mt-1">
                      <Dumbbell className="size-4 text-emerald-500" />
                      <span className="font-extrabold text-slate-800 dark:text-slate-100">{plan.items.length} 個</span>
                    </div>
                  </div>
                  <div className="p-4 flex flex-col items-center">
                    <span className="text-[10px] text-muted-foreground font-semibold">平均間隔休息</span>
                    <div className="flex items-center gap-1 mt-1">
                      <Clock className="size-4 text-amber-500" />
                      <span className="font-extrabold text-slate-800 dark:text-slate-100">{avgRestSec(plan.items)} 秒</span>
                    </div>
                  </div>
                  <div className="p-4 flex flex-col items-center">
                    <span className="text-[10px] text-muted-foreground font-semibold">訓練總組數</span>
                    <div className="flex items-center gap-1 mt-1">
                      <Heart className="size-4 text-sky-500" />
                      <span className="font-extrabold text-slate-800 dark:text-slate-100">{totalSets(plan.items)} 組</span>
                    </div>
                  </div>
                </div>

                {/* Exercises list - Board format */}
                <ul className="divide-y divide-slate-50 dark:divide-slate-900/60 bg-white/40 dark:bg-slate-950/10">
                  {plan.items.map((item, idx) => (
                    <ExerciseRow key={`${item.name}-${idx}`} item={item} />
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))
        ) : !generate.isPending ? (
          <Card className="border border-dashed border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-slate-950/10 rounded-3xl overflow-hidden py-12 text-center">
            <CardContent className="flex flex-col items-center gap-3">
              <div className="flex size-14 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-900 text-slate-400">
                <Dumbbell className="size-6" />
              </div>
              <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">今天尚未產生任何運動菜單</p>
              <p className="text-xs text-muted-foreground max-w-xs leading-normal">點擊上方「產生訓練菜單」，讓 AI 智慧引擎依據您的性別、身高體重，規畫專屬動作組合吧！</p>
            </CardContent>
          </Card>
        ) : null}
      </div>
    </section>
  );
}

function avgRestSec(items: ExerciseItem[]): number {
  if (items.length === 0) return 0;
  return Math.round(items.reduce((sum, i) => sum + i.restSec, 0) / items.length);
}

function totalSets(items: ExerciseItem[]): number {
  return items.reduce((sum, i) => sum + i.sets, 0);
}

function ExerciseRow({ item }: { item: ExerciseItem }) {
  return (
    <li className="grid grid-cols-[1fr_auto] gap-3 px-6 py-4 text-xs items-center hover:bg-slate-50 dark:hover:bg-slate-900/10 transition-colors duration-200 md:grid-cols-[2fr_1.2fr_1fr_1fr]">
      <div>
        <p className="font-extrabold text-slate-700 dark:text-slate-200 text-sm">{item.name}</p>
        {item.note && <p className="text-[10px] text-muted-foreground mt-0.5 italic">{item.note}</p>}
      </div>
      <div className="flex items-center gap-2">
        <Badge variant="outline" className="rounded-xl px-2 py-0.5 bg-slate-50 dark:bg-slate-900 border-slate-100 dark:border-slate-800 font-bold text-slate-600 dark:text-slate-400 text-[10px]">
          {item.sets} 組 × {item.reps}
        </Badge>
      </div>
      <div className="hidden md:flex items-center gap-1 text-muted-foreground font-semibold">
        <Clock className="size-3.5 text-amber-500" />
        <span>休息 {item.restSec}s</span>
      </div>
      <div className="flex items-center gap-1 font-extrabold text-rose-500 text-right justify-end">
        <Flame className="size-3.5" />
        <span>{item.kcal} kcal</span>
      </div>
    </li>
  );
}
