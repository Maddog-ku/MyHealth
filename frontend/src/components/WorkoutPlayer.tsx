import { useEffect, useMemo, useState } from "react";
import { Check, Flame, Pause, Play, SkipForward, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ExerciseAnimation } from "@/components/ExerciseAnimation";
import { cn } from "@/lib/utils";
import type { ExerciseItem, WorkoutPlan } from "@/types/api";

/**
 * Per-set work seconds the user must actually finish. The AI provides
 * `durationSec`, but legacy plans (saved before that field existed) store 0,
 * so we fall back to estimating from the reps string — mirroring the backend's
 * LocalAiProvider.deriveDurationSec so old and new plans behave identically.
 */
export function workSeconds(item: ExerciseItem): number {
  if (item.durationSec >= 10 && item.durationSec <= 600) return item.durationSec;
  const reps = item.reps ?? "";
  const match = reps.match(/\d+/);
  if (!match) return 40;
  const n = parseInt(match[0], 10);
  let secs = /[sS秒]/.test(reps) ? n : n * 3;
  if (/每側|每邊|左右/.test(reps)) secs *= 2;
  return Math.max(10, Math.min(600, secs));
}

type Phase =
  | { type: "work"; exerciseIndex: number; set: number; seconds: number }
  | { type: "rest"; exerciseIndex: number; seconds: number };

function buildPhases(items: ExerciseItem[]): Phase[] {
  const phases: Phase[] = [];
  items.forEach((item, exerciseIndex) => {
    const work = workSeconds(item);
    for (let set = 1; set <= item.sets; set++) {
      phases.push({ type: "work", exerciseIndex, set, seconds: work });
      const lastSetOfLastExercise = exerciseIndex === items.length - 1 && set === item.sets;
      if (!lastSetOfLastExercise) {
        phases.push({ type: "rest", exerciseIndex, seconds: item.restSec });
      }
    }
  });
  return phases;
}

function mmss(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export function WorkoutPlayer({
  plan,
  categoryLabel,
  onClose,
  onComplete,
}: {
  plan: WorkoutPlan;
  categoryLabel: string;
  onClose: () => void;
  onComplete: (actualKcal: number) => void;
}) {
  const items = plan.items;
  const phases = useMemo(() => buildPhases(items), [items]);

  const [index, setIndex] = useState(0);
  const [remaining, setRemaining] = useState(() => phases[0]?.seconds ?? 0);
  const [running, setRunning] = useState(true);
  const [finished, setFinished] = useState(phases.length === 0);
  // How many work-sets of each exercise were fully timed out (skips don't count).
  const [completedSets, setCompletedSets] = useState<Record<number, number>>({});

  const current = phases[index];

  // One-second ticker. When the phase elapses naturally, a work set earns credit;
  // pausing (running=false) clears the timeout so the countdown freezes.
  useEffect(() => {
    if (finished || !running || !current) return;
    if (remaining > 0) {
      const timer = setTimeout(() => setRemaining((r) => r - 1), 1000);
      return () => clearTimeout(timer);
    }
    // remaining === 0 → phase finished on its own
    if (current.type === "work") {
      setCompletedSets((prev) => ({
        ...prev,
        [current.exerciseIndex]: (prev[current.exerciseIndex] ?? 0) + 1,
      }));
    }
    goTo(index + 1);
  }, [remaining, running, finished, index, current]);

  function goTo(next: number) {
    if (next >= phases.length) {
      setFinished(true);
      return;
    }
    setIndex(next);
    setRemaining(phases[next].seconds);
  }

  // Skipping abandons the current phase with no credit, so a skipped work set
  // leaves its exercise incomplete — exactly the "didn't finish the seconds" rule.
  function skip() {
    goTo(index + 1);
  }

  const completedExercises = useMemo(
    () => items.filter((item, i) => (completedSets[i] ?? 0) >= item.sets),
    [items, completedSets],
  );
  const earnedKcal = completedExercises.reduce((sum, item) => sum + item.kcal, 0);

  const totalWorkSets = items.reduce((sum, item) => sum + item.sets, 0);
  const doneWorkSets = Object.values(completedSets).reduce((sum, n) => sum + n, 0);

  if (finished) {
    return (
      <Overlay onClose={onClose}>
        <div className="text-center space-y-5 py-2">
          <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-500">
            <Check className="size-8" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">訓練結束</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              完成 {completedExercises.length}/{items.length} 個動作 · 計時 {doneWorkSets}/{totalWorkSets} 組
            </p>
          </div>

          <ul className="space-y-1.5 text-left max-h-52 overflow-auto px-1">
            {items.map((item, i) => {
              const done = (completedSets[i] ?? 0) >= item.sets;
              return (
                <li
                  key={`${item.name}-${i}`}
                  className={cn(
                    "flex items-center justify-between gap-3 rounded-xl px-3 py-2 text-xs",
                    done
                      ? "bg-emerald-50/70 dark:bg-emerald-950/20 text-emerald-700 dark:text-emerald-300"
                      : "bg-slate-50 dark:bg-slate-900/30 text-muted-foreground",
                  )}
                >
                  <span className="font-semibold">{item.name}</span>
                  <span className="font-bold">
                    {done ? `+${item.kcal} kcal` : `${completedSets[i] ?? 0}/${item.sets} 組 · 未完成`}
                  </span>
                </li>
              );
            })}
          </ul>

          <div className="flex items-center justify-center gap-1.5 text-rose-500 font-extrabold">
            <Flame className="size-5" />
            <span className="text-xl">{earnedKcal}</span>
            <span className="text-sm">kcal</span>
          </div>

          {earnedKcal > 0 ? (
            <Button
              onClick={() => onComplete(earnedKcal)}
              className="w-full rounded-2xl py-5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold gap-1.5"
            >
              <Check className="size-4" /> 完成並記錄
            </Button>
          ) : (
            <Button
              onClick={onClose}
              variant="secondary"
              className="w-full rounded-2xl py-5 font-semibold"
            >
              關閉（未完成任何動作，不計入消耗）
            </Button>
          )}
        </div>
      </Overlay>
    );
  }

  if (!current) {
    return null;
  }

  const activeItem = items[current.exerciseIndex];
  const isWork = current.type === "work";
  const phaseSeconds = current.seconds || 1;
  const progressPct = ((phaseSeconds - remaining) / phaseSeconds) * 100;

  return (
    <Overlay onClose={onClose}>
      <div className="space-y-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{categoryLabel}</p>
            <p className="text-xs text-muted-foreground mt-0.5">
              動作 {current.exerciseIndex + 1}/{items.length} · 已完成 {completedExercises.length} 個
            </p>
          </div>
          <span
            className={cn(
              "rounded-full px-3 py-1 text-[11px] font-bold",
              isWork
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-sky-500/10 text-sky-600 dark:text-sky-400",
            )}
          >
            {isWork ? `第 ${current.set}/${activeItem.sets} 組` : "組間休息"}
          </span>
        </div>

        <h2 className="text-xl font-extrabold text-slate-800 dark:text-slate-100">
          {isWork ? activeItem.name : "休息一下，準備下一組"}
        </h2>

        {/* Animated guidance figure */}
        <div
          className={cn(
            "mx-auto grid h-44 w-44 place-items-center rounded-3xl border",
            isWork
              ? "border-emerald-500/15 bg-emerald-500/5 text-emerald-500"
              : "border-sky-500/15 bg-sky-500/5 text-sky-500",
          )}
        >
          <ExerciseAnimation
            name={activeItem.name}
            category={plan.category}
            phase={isWork && running ? "work" : "rest"}
            className="h-36 w-36"
          />
        </div>

        {isWork && activeItem.note && (
          <p className="text-center text-xs text-muted-foreground italic">{activeItem.note}</p>
        )}

        {/* Countdown */}
        <div className="text-center">
          <div className="text-5xl font-black tabular-nums text-slate-800 dark:text-slate-100">{mmss(remaining)}</div>
          <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
            <div
              className={cn("h-full rounded-full transition-all duration-1000 ease-linear", isWork ? "bg-emerald-500" : "bg-sky-500")}
              style={{ width: `${progressPct}%` }}
            />
          </div>
          {!running && <p className="mt-2 text-[11px] font-semibold text-amber-500">已暫停</p>}
        </div>

        {/* Controls */}
        <div className="grid grid-cols-[1fr_auto] gap-2">
          <Button
            onClick={() => setRunning((r) => !r)}
            className={cn(
              "rounded-2xl py-5 font-semibold gap-1.5 text-white",
              running ? "bg-amber-500 hover:bg-amber-400" : "bg-emerald-600 hover:bg-emerald-500",
            )}
          >
            {running ? <><Pause className="size-4" /> 暫停</> : <><Play className="size-4" /> 繼續</>}
          </Button>
          <Button
            onClick={skip}
            variant="secondary"
            className="rounded-2xl py-5 px-4 font-semibold gap-1.5"
            title={isWork ? "跳過此組（不計入完成）" : "跳過休息"}
          >
            <SkipForward className="size-4" /> {isWork ? "跳過" : "略過休息"}
          </Button>
        </div>
        <Button
          onClick={() => setFinished(true)}
          variant="ghost"
          className="w-full rounded-2xl py-3 text-xs font-semibold text-muted-foreground hover:text-rose-500"
        >
          結束訓練
        </Button>
      </div>
    </Overlay>
  );
}

function Overlay({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/60 backdrop-blur-sm p-4 animate-fade-in">
      <div className="relative w-full max-w-md rounded-3xl border border-slate-100/80 dark:border-slate-800 bg-white dark:bg-slate-950 p-6 shadow-2xl">
        <button
          onClick={onClose}
          aria-label="關閉"
          className="absolute right-4 top-4 grid size-8 place-items-center rounded-full text-muted-foreground hover:bg-slate-100 dark:hover:bg-slate-900"
        >
          <X className="size-4" />
        </button>
        {children}
      </div>
    </div>
  );
}
