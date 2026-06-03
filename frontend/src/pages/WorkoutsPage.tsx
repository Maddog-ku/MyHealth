import { useMemo, useState } from "react";
import { Dumbbell, Play, Sparkles, Flame, Clock, Timer, Heart, CheckCircle2, Check, SlidersHorizontal, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { AiGenerationPanel } from "@/components/AiGenerationPanel";
import { WorkoutPlayer, workSeconds } from "@/components/WorkoutPlayer";
import { useCancelWorkoutSelection, useCompleteWorkout, useGenerateWorkout, useWorkouts } from "@/hooks/useWorkouts";
import { ApiError } from "@/api/client";
import { todayLocalISO } from "@/lib/date";
import type { ExerciseItem, WorkoutPlan } from "@/types/api";

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

function categoryLabel(value: string): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value;
}

export function WorkoutsPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const [category, setCategory] = useState("abs");
  const [duration, setDuration] = useState(30);
  const [intensity, setIntensity] = useState("medium");
  // The plan currently being performed in the timed player (null = none open).
  const [activePlan, setActivePlan] = useState<WorkoutPlan | null>(null);

  const workouts = useWorkouts(today);
  const generate = useGenerateWorkout(today);
  const complete = useCompleteWorkout(today);
  const cancel = useCancelWorkoutSelection(today);

  // Management mode: tick individual exercises to cancel, and/or whole plan cards
  // to delete — then cancel the lot (single or batch) from one action bar.
  const [manageMode, setManageMode] = useState(false);
  const [selectedPlans, setSelectedPlans] = useState<Set<number>>(new Set());
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  // Step 2 of cancellation: a confirmation dialog must be confirmed before anything is removed.
  const [confirming, setConfirming] = useState(false);

  const itemKey = (planId: number, idx: number) => `${planId}:${idx}`;
  const togglePlan = (id: number) =>
    setSelectedPlans((prev) => toggle(prev, id));
  const toggleItem = (planId: number, idx: number) =>
    setSelectedItems((prev) => toggle(prev, itemKey(planId, idx)));
  const clearSelection = () => {
    setSelectedPlans(new Set());
    setSelectedItems(new Set());
  };
  const exitManage = () => {
    setManageMode(false);
    setConfirming(false);
    clearSelection();
  };

  // Items belonging to a plan that's being deleted outright don't count separately.
  const liveItemKeys = [...selectedItems].filter((k) => !selectedPlans.has(Number(k.split(":")[0])));
  const selectedItemCount = liveItemKeys.length;
  const selectedPlanCount = selectedPlans.size;

  // Resolve the ticked keys to display names for the confirmation dialog.
  const plansById = new Map((workouts.data?.data ?? []).map((p) => [p.id, p]));
  const selectedItemDetails = liveItemKeys.map((k) => {
    const [pid, idx] = k.split(":").map(Number);
    const p = plansById.get(pid);
    return { name: p?.items[idx]?.name ?? "動作", planLabel: categoryLabel(p?.category ?? "") };
  });
  const selectedPlanDetails = [...selectedPlans].map((id) => {
    const p = plansById.get(id);
    return { id, label: categoryLabel(p?.category ?? ""), count: p?.items.length ?? 0 };
  });

  const runCancel = () => {
    const itemsByPlan: Record<number, number[]> = {};
    liveItemKeys.forEach((k) => {
      const [pid, idx] = k.split(":").map(Number);
      (itemsByPlan[pid] ??= []).push(idx);
    });
    cancel.mutate(
      { itemsByPlan, deletePlanIds: [...selectedPlans] },
      { onSuccess: exitManage },
    );
  };

  const hasPlans = !!workouts.data && workouts.data.data.length > 0;

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {/* Generate Routine Card */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden accent-glow">
        <CardHeader className="pb-3">
          <CardTitle className="text-md font-bold flex items-center gap-2">
            <Sparkles className="size-4.5 text-emerald-500" />
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
        <div className="flex items-center justify-between px-1">
          <h2 className="text-sm font-bold text-slate-500 dark:text-slate-400 tracking-wider uppercase">今日訓練計畫</h2>
          {hasPlans && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => (manageMode ? exitManage() : setManageMode(true))}
              className="rounded-xl text-xs font-semibold gap-1.5 text-slate-500 dark:text-slate-400 hover:text-emerald-600"
            >
              {manageMode ? <>完成</> : <><SlidersHorizontal className="size-3.5" /> 管理</>}
            </Button>
          )}
        </div>

        {generate.isPending && <AiGenerationPanel kind="workout" />}

        {workouts.isLoading ? (
          <Skeleton className="h-44 w-full rounded-3xl" />
        ) : workouts.data && workouts.data.data.length > 0 ? (
          workouts.data.data.map((plan) => (
            <Card key={plan.id} className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
              <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-50 dark:border-slate-900/30">
                <div className="flex items-center gap-3">
                  {manageMode && (
                    <CancelBox
                      checked={selectedPlans.has(plan.id)}
                      onToggle={() => togglePlan(plan.id)}
                      label={`選取整份菜單：${categoryLabel(plan.category)}`}
                    />
                  )}
                  <div className="flex size-10 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                    <Dumbbell className="size-5" />
                  </div>
                  <div>
                    <CardTitle className="text-sm font-bold flex items-center gap-2">
                      {categoryLabel(plan.category)}
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
                  {manageMode ? (
                    selectedPlans.has(plan.id) && (
                      <Badge className="rounded-2xl px-3 py-1.5 text-[11px] font-semibold gap-1 bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/15">
                        <Trash2 className="size-3.5" /> 整份待刪除
                      </Badge>
                    )
                  ) : plan.done ? (
                    <Badge className="rounded-2xl px-3 py-2 text-xs font-semibold gap-1.5 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/15">
                      <CheckCircle2 className="size-4" />
                      已完成 · 消耗 {plan.burnedKcal ?? plan.totalKcal} kcal
                    </Badge>
                  ) : (
                    <Button
                      size="sm"
                      disabled={complete.isPending || plan.items.length === 0}
                      onClick={() => setActivePlan(plan)}
                      className="rounded-2xl px-4 py-4 text-xs font-semibold gap-1.5 shadow-sm bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-500/10"
                    >
                      <Play className="size-4" />
                      開始訓練
                    </Button>
                  )}
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
                    <span className="text-[10px] text-muted-foreground font-semibold">預估總時長</span>
                    <div className="flex items-center gap-1 mt-1">
                      <Timer className="size-4 text-sky-500" />
                      <span className="font-extrabold text-slate-800 dark:text-slate-100">{estimateMinutes(plan.items)} 分</span>
                    </div>
                  </div>
                  <div className="p-4 flex flex-col items-center">
                    <span className="text-[10px] text-muted-foreground font-semibold">訓練總組數</span>
                    <div className="flex items-center gap-1 mt-1">
                      <Heart className="size-4 text-rose-400" />
                      <span className="font-extrabold text-slate-800 dark:text-slate-100">{totalSets(plan.items)} 組</span>
                    </div>
                  </div>
                </div>

                {manageMode ? (
                  !plan.done && (
                    <p className="px-6 pt-3 text-[11px] text-muted-foreground">
                      勾選要取消的動作，或勾選左上角刪除整份菜單；選好後按下方「取消所選」。
                    </p>
                  )
                ) : (
                  !plan.done && (
                    <p className="px-6 pt-3 text-[11px] text-muted-foreground">
                      點「開始訓練」進入計時引導，逐組完成每個動作；未做滿設定秒數的動作不會計入消耗。
                    </p>
                  )
                )}
                <ul className="divide-y divide-slate-50 dark:divide-slate-900/60 bg-white/40 dark:bg-slate-950/10">
                  {plan.items.map((item, idx) => {
                    const selectable = manageMode && !plan.done && !selectedPlans.has(plan.id);
                    return (
                      <ExerciseRow
                        key={`${item.name}-${idx}`}
                        item={item}
                        selectable={selectable}
                        selected={selectable && selectedItems.has(itemKey(plan.id, idx))}
                        onToggle={() => toggleItem(plan.id, idx)}
                      />
                    );
                  })}
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

      {manageMode && selectedItemCount + selectedPlanCount > 0 && (
        <div className="fixed inset-x-0 bottom-24 md:bottom-6 z-50 flex justify-center px-4">
          <div className="flex items-center gap-3 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white/95 dark:bg-slate-950/95 backdrop-blur px-4 py-3 shadow-2xl">
            <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">
              已選 {selectedItemCount} 個動作
              {selectedPlanCount > 0 && ` · ${selectedPlanCount} 份菜單`}
            </span>
            <Button
              size="sm"
              variant="ghost"
              onClick={clearSelection}
              className="rounded-xl text-xs font-semibold text-muted-foreground"
            >
              清除
            </Button>
            <Button
              size="sm"
              onClick={() => setConfirming(true)}
              className="rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold gap-1.5"
            >
              <Trash2 className="size-4" /> 取消所選
            </Button>
          </div>
        </div>
      )}

      {confirming && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="確認取消運動"
          className="fixed inset-0 z-50 grid place-items-center bg-slate-950/60 backdrop-blur-sm p-4 animate-fade-in"
        >
          <div className="w-full max-w-md rounded-3xl border border-slate-100/80 dark:border-slate-800 bg-white dark:bg-slate-950 p-6 shadow-2xl space-y-4">
            <div className="flex items-center gap-3">
              <div className="grid size-11 shrink-0 place-items-center rounded-2xl bg-rose-500/10 text-rose-500">
                <Trash2 className="size-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">確認取消這些運動？</h2>
                <p className="text-xs text-muted-foreground mt-0.5">請再次確認以下勾選項目，確認後即無法復原。</p>
              </div>
            </div>

            <div className="max-h-64 overflow-auto space-y-3">
              {selectedItemDetails.length > 0 && (
                <div>
                  <p className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                    取消動作（{selectedItemDetails.length}）
                  </p>
                  <ul className="space-y-1">
                    {selectedItemDetails.map((d, i) => (
                      <li key={i} className="flex items-center justify-between gap-2 rounded-xl bg-rose-50/60 dark:bg-rose-950/15 px-3 py-2 text-xs">
                        <span className="font-semibold text-rose-700 dark:text-rose-300">{d.name}</span>
                        <span className="text-[10px] text-muted-foreground">{d.planLabel}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {selectedPlanDetails.length > 0 && (
                <div>
                  <p className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                    刪除整份菜單（{selectedPlanDetails.length}）
                  </p>
                  <ul className="space-y-1">
                    {selectedPlanDetails.map((d) => (
                      <li key={d.id} className="flex items-center justify-between gap-2 rounded-xl bg-rose-50/60 dark:bg-rose-950/15 px-3 py-2 text-xs">
                        <span className="font-semibold text-rose-700 dark:text-rose-300">{d.label}</span>
                        <span className="text-[10px] text-muted-foreground">{d.count} 個動作</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            <div className="grid grid-cols-2 gap-2">
              <Button
                variant="secondary"
                onClick={() => setConfirming(false)}
                className="rounded-2xl py-5 font-semibold"
              >
                返回
              </Button>
              <Button
                disabled={cancel.isPending}
                onClick={runCancel}
                className="rounded-2xl py-5 font-semibold gap-1.5 bg-rose-600 hover:bg-rose-500 text-white"
              >
                <Trash2 className="size-4" /> {cancel.isPending ? "取消中…" : "確認取消"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {activePlan && (
        <WorkoutPlayer
          plan={activePlan}
          categoryLabel={categoryLabel(activePlan.category)}
          onClose={() => setActivePlan(null)}
          onComplete={(actualKcal) => {
            complete.mutate({ id: activePlan.id, actualKcal });
            setActivePlan(null);
          }}
        />
      )}
    </section>
  );
}

/** Toggle a value's membership in a Set, returning a new Set (immutable update). */
function toggle<T>(set: Set<T>, value: T): Set<T> {
  const next = new Set(set);
  next.has(value) ? next.delete(value) : next.add(value);
  return next;
}

function CancelBox({ checked, onToggle, label }: { checked: boolean; onToggle: () => void; label: string }) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={label}
      onClick={onToggle}
      className={`flex size-6 shrink-0 items-center justify-center rounded-md border-2 transition-colors ${
        checked
          ? "bg-rose-500 border-rose-500 text-white"
          : "border-slate-300 dark:border-slate-700 text-transparent hover:border-rose-400"
      }`}
    >
      <Check className="size-4" />
    </button>
  );
}

function totalSets(items: ExerciseItem[]): number {
  return items.reduce((sum, i) => sum + i.sets, 0);
}

/** Rough wall-clock estimate: every set's work seconds + the rests between them. */
function estimateMinutes(items: ExerciseItem[]): number {
  const seconds = items.reduce((sum, item) => {
    const work = workSeconds(item) * item.sets;
    const rest = item.restSec * Math.max(0, item.sets - 1);
    return sum + work + rest;
  }, 0);
  return Math.max(1, Math.round(seconds / 60));
}

function ExerciseRow({
  item,
  selectable = false,
  selected = false,
  onToggle,
}: {
  item: ExerciseItem;
  selectable?: boolean;
  selected?: boolean;
  onToggle?: () => void;
}) {
  return (
    <li
      role={selectable ? "button" : undefined}
      aria-pressed={selectable ? selected : undefined}
      aria-label={selectable ? `${item.name}${selected ? "，已選取取消" : "，點擊選取取消"}` : undefined}
      tabIndex={selectable ? 0 : undefined}
      onClick={selectable ? onToggle : undefined}
      onKeyDown={
        selectable
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onToggle?.();
              }
            }
          : undefined
      }
      className={`grid grid-cols-[1fr_auto] gap-3 px-6 py-4 text-xs items-center md:grid-cols-[2fr_1.2fr_1fr_1fr] transition-colors ${
        selectable ? "cursor-pointer" : ""
      } ${selected ? "bg-rose-50/60 dark:bg-rose-950/15" : selectable ? "hover:bg-slate-50 dark:hover:bg-slate-900/10" : ""}`}
    >
      <div className="flex items-center gap-3">
        {selectable ? (
          <span
            aria-hidden
            className={`flex size-6 shrink-0 items-center justify-center rounded-md border-2 transition-colors ${
              selected ? "bg-rose-500 border-rose-500 text-white" : "border-slate-300 dark:border-slate-700 text-transparent"
            }`}
          >
            <Check className="size-3.5" />
          </span>
        ) : (
          <span aria-hidden className="flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-500">
            <Dumbbell className="size-3.5" />
          </span>
        )}
        <div>
          <p
            className={`font-extrabold text-sm ${
              selected ? "text-rose-600 dark:text-rose-400 line-through decoration-rose-500/40" : "text-slate-700 dark:text-slate-200"
            }`}
          >
            {item.name}
          </p>
          {item.note && <p className="text-[10px] text-muted-foreground mt-0.5 italic">{item.note}</p>}
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Badge variant="outline" className="rounded-xl px-2 py-0.5 bg-slate-50 dark:bg-slate-900 border-slate-100 dark:border-slate-800 font-bold text-slate-600 dark:text-slate-400 text-[10px]">
          {item.sets} 組 × {item.reps}
        </Badge>
      </div>
      <div className="hidden md:flex items-center gap-1 text-muted-foreground font-semibold">
        <Timer className="size-3.5 text-emerald-500" />
        <span>每組 {workSeconds(item)}s</span>
      </div>
      <div className="flex items-center gap-3 justify-end">
        <span className="hidden md:flex items-center gap-1 text-muted-foreground font-semibold">
          <Clock className="size-3.5 text-amber-500" />
          休息 {item.restSec}s
        </span>
        <span className="flex items-center gap-1 font-extrabold text-rose-500">
          <Flame className="size-3.5" />
          {item.kcal}
        </span>
      </div>
    </li>
  );
}
