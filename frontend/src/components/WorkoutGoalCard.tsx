import { useState } from "react";
import { Target, Trophy, Pencil, Check } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useDeleteWorkoutGoal, useSetWorkoutGoal, useWorkoutGoal } from "@/hooks/useWorkoutGoal";

const TARGET_OPTIONS = [1, 2, 3, 4, 5, 6, 7];

export function WorkoutGoalCard() {
  const goal = useWorkoutGoal();
  const save = useSetWorkoutGoal();
  const remove = useDeleteWorkoutGoal();
  const progress = goal.data?.progress ?? null;

  const [editing, setEditing] = useState(false);
  const [target, setTarget] = useState(3);

  function startEditing() {
    setTarget(progress?.targetSessionsPerWeek ?? 3);
    setEditing(true);
  }

  function submit() {
    save.mutate(target, { onSuccess: () => setEditing(false) });
  }

  const showForm = editing || (!progress && !goal.isLoading);

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="flex flex-row items-start justify-between gap-3 pb-3">
        <div>
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Target className="size-4.5 text-rose-500" />
            每週訓練目標
          </CardTitle>
          <CardDescription className="text-xs">設定每週想完成的訓練次數，追蹤本週進度</CardDescription>
        </div>
        {progress && !showForm && (
          <Button
            variant="ghost"
            size="sm"
            onClick={startEditing}
            className="rounded-xl text-xs font-semibold gap-1.5 text-slate-400 hover:text-rose-500"
          >
            <Pencil className="size-3.5" /> 調整
          </Button>
        )}
      </CardHeader>
      <CardContent className="px-6 pb-6">
        {goal.isLoading ? (
          <Skeleton className="h-24 w-full rounded-2xl" />
        ) : showForm ? (
          <div className="flex flex-wrap items-end gap-3">
            <div className="grid gap-1.5">
              <label className="text-xs font-semibold text-slate-500 px-1">每週目標次數</label>
              <Select value={String(target)} onValueChange={(v) => setTarget(Number(v))} disabled={save.isPending}>
                <SelectTrigger className="w-40 rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                  {TARGET_OPTIONS.map((n) => (
                    <SelectItem key={n} value={String(n)} className="rounded-xl">{n} 次 / 週</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button
              onClick={submit}
              disabled={save.isPending}
              className="rounded-2xl py-5 px-6 bg-gradient-to-r from-rose-500 to-pink-500 hover:from-rose-400 hover:to-pink-400 text-white font-semibold gap-1.5"
            >
              <Check className="size-4" />
              {progress ? "更新目標" : "設定目標"}
            </Button>
            {progress && (
              <Button
                variant="ghost"
                onClick={() => remove.mutate(undefined, { onSuccess: () => setEditing(false) })}
                disabled={remove.isPending}
                className="rounded-2xl py-5 text-xs font-semibold text-muted-foreground hover:text-rose-500"
              >
                清除目標
              </Button>
            )}
            {editing && (
              <Button variant="ghost" onClick={() => setEditing(false)} className="rounded-2xl py-5 text-xs text-muted-foreground">
                取消
              </Button>
            )}
          </div>
        ) : progress ? (
          <div className="space-y-3">
            <div className="flex items-end justify-between">
              <div className="flex items-baseline gap-1.5">
                <span className="text-3xl font-extrabold text-slate-800 dark:text-slate-100 tabular-nums">{progress.completedThisWeek}</span>
                <span className="text-sm text-muted-foreground font-semibold">/ {progress.targetSessionsPerWeek} 次</span>
              </div>
              {progress.achieved ? (
                <Badge className="rounded-full text-[10px] gap-1 bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/15 px-2.5 py-1">
                  <Trophy className="size-3.5" /> 本週達標
                </Badge>
              ) : (
                <span className="text-xs font-semibold text-rose-500">還差 {progress.remaining} 次</span>
              )}
            </div>
            <div className="h-3 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-900/50">
              <div
                className={`h-full rounded-full transition-all ${
                  progress.achieved
                    ? "bg-gradient-to-r from-amber-500 to-orange-400"
                    : "bg-gradient-to-r from-rose-500 to-pink-400"
                }`}
                style={{ width: `${Math.max(4, progress.progressPct)}%` }}
              />
            </div>
            <p className="text-[10px] text-muted-foreground">本週（{progress.weekStart.slice(5)} 起）已完成 {progress.completedThisWeek} 次訓練</p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
