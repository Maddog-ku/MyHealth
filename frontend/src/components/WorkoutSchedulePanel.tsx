import { useMemo, useState } from "react";
import { CalendarRange, Sparkles, Trash2, Plus, Bed, Check } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  useApplyScheduleDay,
  useDeleteWorkoutSchedule,
  useGenerateWorkoutSchedule,
  useWorkoutSchedules,
} from "@/hooks/useWorkoutSchedule";
import { ApiError } from "@/api/client";
import { mondayOfWeekLocalISO } from "@/lib/date";
import type { ScheduleDay, WorkoutSchedule } from "@/types/api";

const CATEGORY_LABELS: Record<string, string> = {
  abs: "腹肌核心",
  waist: "腰腹側線",
  legs: "腿部肌群",
  chest: "胸部塑造",
  back: "背部強化",
  arms: "手臂雕塑",
  glutes: "臀部緊實",
  cardio: "高效有氧",
  full_body: "全身燃脂",
};

const INTENSITIES = [
  { value: "low", label: "輕鬆舒適" },
  { value: "medium", label: "中等訓練" },
  { value: "high", label: "高強挑戰" },
];

const WEEKDAY_LABELS = ["一", "二", "三", "四", "五", "六", "日"];

function categoryLabel(code: string | null): string {
  return code ? CATEGORY_LABELS[code] ?? code : "";
}

/** Add `n` days to an ISO `YYYY-MM-DD` string in local time, returning ISO. */
function addDaysISO(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(y, m - 1, d + n);
  const yy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

export function WorkoutSchedulePanel() {
  const [daysPerWeek, setDaysPerWeek] = useState(3);
  const [weeks, setWeeks] = useState(4);
  const [intensity, setIntensity] = useState("medium");

  const schedules = useWorkoutSchedules();
  const generate = useGenerateWorkoutSchedule();
  const remove = useDeleteWorkoutSchedule();

  // Show the most recently generated schedule.
  const current = schedules.data?.data?.[0] ?? null;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <CalendarRange className="size-4.5 text-indigo-500" />
          週期課表規劃
        </CardTitle>
        <CardDescription className="text-xs">
          讓 AI 依你的目標排出一週訓練分配（split），可重複數週；點任一訓練日即可一鍵生成當天的詳細菜單。
        </CardDescription>
      </CardHeader>
      <CardContent className="px-6 pb-6 space-y-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto] items-end">
          <div className="grid gap-1.5">
            <label className="text-xs font-semibold text-slate-500 px-1">每週訓練天數</label>
            <Select value={String(daysPerWeek)} onValueChange={(v) => setDaysPerWeek(Number(v))} disabled={generate.isPending}>
              <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                {[2, 3, 4, 5, 6].map((d) => (
                  <SelectItem key={d} value={String(d)} className="rounded-xl">{d} 天 / 週</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-1.5">
            <label className="text-xs font-semibold text-slate-500 px-1">規劃週數</label>
            <Select value={String(weeks)} onValueChange={(v) => setWeeks(Number(v))} disabled={generate.isPending}>
              <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                {[1, 2, 3, 4].map((w) => (
                  <SelectItem key={w} value={String(w)} className="rounded-xl">{w === 4 ? "4 週（約一個月）" : `${w} 週`}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-1.5">
            <label className="text-xs font-semibold text-slate-500 px-1">訓練強度</label>
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
            onClick={() =>
              generate.mutate({ startDate: mondayOfWeekLocalISO(0), daysPerWeek, weeks, intensity })
            }
            className="rounded-2xl py-5 px-6 bg-gradient-to-r from-indigo-600 to-violet-500 hover:from-indigo-500 hover:to-violet-400 text-white font-semibold shadow-md shadow-indigo-500/10 hover:shadow-lg transition-all-smooth gap-1.5 w-full lg:w-auto"
          >
            {generate.isPending ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                規劃中...
              </>
            ) : (
              <>
                <Sparkles className="size-4" />
                {current ? "重新規劃" : "產生週期課表"}
              </>
            )}
          </Button>
        </div>

        {generate.error instanceof ApiError && (
          <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
            <AlertTitle className="text-xs font-bold">
              {generate.error.status === 429 ? "規劃太頻繁" : "課表規劃失敗"}
            </AlertTitle>
            <AlertDescription className="text-[11px] opacity-90">
              {generate.error.status === 429 ? "稍等一下再試一次。" : "請稍後再試，或先用上方的單日菜單。"}
            </AlertDescription>
          </Alert>
        )}

        {schedules.isLoading ? (
          <Skeleton className="h-40 w-full rounded-2xl" />
        ) : current ? (
          <ScheduleView schedule={current} onDelete={() => remove.mutate(current.id)} deleting={remove.isPending} />
        ) : !generate.isPending ? (
          <div className="rounded-2xl border border-dashed border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-slate-950/10 py-8 text-center">
            <p className="text-xs text-muted-foreground max-w-sm mx-auto leading-normal">
              還沒有週期課表。設定每週天數與週數，讓 AI 幫你把不同部位分散到一週裡，安排合理的休息日。
            </p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ScheduleView({
  schedule,
  onDelete,
  deleting,
}: {
  schedule: WorkoutSchedule;
  onDelete: () => void;
  deleting: boolean;
}) {
  const [week, setWeek] = useState(0);
  const apply = useApplyScheduleDay();
  const [applied, setApplied] = useState<Set<number>>(new Set());

  // Order days Monday..Sunday regardless of how the backend returned them.
  const days = useMemo(
    () => [...schedule.days].sort((a, b) => a.weekday - b.weekday),
    [schedule.days],
  );
  const intensityLabel = INTENSITIES.find((i) => i.value === schedule.intensity)?.label ?? schedule.intensity;

  const applyDay = (day: ScheduleDay) => {
    const date = addDaysISO(schedule.startDate, week * 7 + (day.weekday - 1));
    apply.mutate(
      { id: schedule.id, date, weekday: day.weekday },
      { onSuccess: () => setApplied((prev) => new Set(prev).add(day.weekday)) },
    );
  };

  // Re-applying a different week should clear the "added" ticks from the previous one.
  const onWeekChange = (v: string) => {
    setWeek(Number(v));
    setApplied(new Set());
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge className="rounded-full text-[10px] bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-500/15 px-2.5 py-0.5">
            目標 · {schedule.goal}
          </Badge>
          <Badge variant="muted" className="rounded-full text-[10px] bg-slate-500/10 text-slate-600 dark:text-slate-400 px-2.5 py-0.5">
            {schedule.daysPerWeek} 天/週 · {intensityLabel}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          {schedule.weeks > 1 && (
            <Select value={String(week)} onValueChange={onWeekChange}>
              <SelectTrigger className="h-9 rounded-xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 text-xs px-3">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                {Array.from({ length: schedule.weeks }, (_, i) => (
                  <SelectItem key={i} value={String(i)} className="rounded-xl text-xs">第 {i + 1} 週</SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <Button
            variant="ghost"
            size="sm"
            disabled={deleting}
            onClick={onDelete}
            className="rounded-xl text-xs font-semibold gap-1.5 text-slate-400 hover:text-rose-500"
          >
            <Trash2 className="size-3.5" /> 刪除
          </Button>
        </div>
      </div>

      {apply.error instanceof ApiError && (
        <Alert variant="destructive" className="rounded-2xl border-rose-500/20 bg-rose-500/5 text-rose-600 dark:text-rose-400">
          <AlertDescription className="text-[11px] opacity-90">
            {apply.error.status === 503 ? "AI 引擎忙碌中，請稍後再加入。" : "加入失敗，請稍後再試。"}
          </AlertDescription>
        </Alert>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2.5">
        {days.map((day) => {
          const date = addDaysISO(schedule.startDate, week * 7 + (day.weekday - 1));
          const isApplied = applied.has(day.weekday);
          const pending = apply.isPending && apply.variables?.weekday === day.weekday;
          return (
            <div
              key={day.weekday}
              className={`flex flex-col rounded-2xl border p-3 min-h-[8rem] ${
                day.rest
                  ? "border-slate-100 dark:border-slate-900/60 bg-slate-50/40 dark:bg-slate-900/10"
                  : "border-indigo-100 dark:border-indigo-950/40 bg-indigo-50/30 dark:bg-indigo-950/10"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-slate-500 dark:text-slate-400">週{WEEKDAY_LABELS[day.weekday - 1]}</span>
                <span className="text-[9px] text-muted-foreground">{date.slice(5)}</span>
              </div>

              {day.rest ? (
                <div className="flex flex-1 flex-col items-center justify-center gap-1 text-slate-400">
                  <Bed className="size-4" />
                  <span className="text-[10px] font-medium">休息日</span>
                </div>
              ) : (
                <div className="flex flex-1 flex-col">
                  <p className="mt-1.5 text-sm font-extrabold text-slate-700 dark:text-slate-200">{categoryLabel(day.category)}</p>
                  <p className="text-[10px] text-muted-foreground mt-0.5">{day.focus}</p>
                  <p className="text-[10px] text-indigo-500 dark:text-indigo-400 font-semibold mt-0.5">約 {day.durationMin} 分鐘</p>
                  <Button
                    size="sm"
                    disabled={pending || isApplied}
                    onClick={() => applyDay(day)}
                    className={`mt-auto rounded-xl text-[11px] font-semibold gap-1 py-1.5 ${
                      isApplied
                        ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10"
                        : "bg-indigo-600 hover:bg-indigo-500 text-white"
                    }`}
                  >
                    {pending ? (
                      <span className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />
                    ) : isApplied ? (
                      <><Check className="size-3.5" /> 已加入</>
                    ) : (
                      <><Plus className="size-3.5" /> 加入當天</>
                    )}
                  </Button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
