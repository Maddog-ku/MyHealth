import { useEffect, useState, type ReactNode } from "react";
import { Target, Pencil, Trash2, Flag, CalendarClock } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/api/client";
import { useWeightGoal, useSetWeightGoal, useDeleteWeightGoal } from "@/hooks/useWeightGoal";
import { useMe } from "@/hooks/useAuth";
import type { WeightGoalProgress } from "@/types/api";

function fmtDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("zh-TW", { month: "long", day: "numeric" }) : "—";
}

export function WeightGoalCard() {
  const { data, isLoading } = useWeightGoal();
  const [editing, setEditing] = useState(false);
  const progress = data?.progress ?? null;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <div>
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <Target className="size-4.5 text-rose-500" />
            體重目標
          </CardTitle>
          <CardDescription className="text-xs">設定目標，追蹤進度與預估達標時間</CardDescription>
        </div>
        {progress && (
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" className="rounded-full size-8 text-muted-foreground" onClick={() => setEditing(true)} title="編輯目標">
              <Pencil className="size-4" />
            </Button>
          </div>
        )}
      </CardHeader>

      <CardContent className="pt-2">
        {isLoading ? (
          <Skeleton className="h-28 w-full rounded-2xl" />
        ) : progress ? (
          <GoalProgress progress={progress} />
        ) : (
          <div className="grid place-items-center rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-dashed border-slate-200 dark:border-slate-800 p-6 text-center">
            <Flag className="size-5 text-rose-500/60 mb-2" />
            <p className="text-xs text-muted-foreground mb-3">設定一個目標體重，讓系統依趨勢預估你的達標日。</p>
            <Button onClick={() => setEditing(true)} className="rounded-2xl bg-gradient-to-tr from-rose-500 to-orange-500 text-white">
              設定體重目標
            </Button>
          </div>
        )}
      </CardContent>

      <WeightGoalDialog open={editing} onClose={() => setEditing(false)} current={progress} />
    </Card>
  );
}

function GoalProgress({ progress: p }: { progress: WeightGoalProgress }) {
  const pct = Math.max(0, Math.min(100, p.progressPct));
  const barColor = p.achieved
    ? "bg-emerald-500"
    : p.onTrack === false
      ? "bg-amber-500"
      : "bg-gradient-to-r from-rose-500 to-orange-500";

  const status = p.achieved
    ? { label: "已達標 🎉", cls: "text-emerald-600 dark:text-emerald-400 bg-emerald-500/10" }
    : p.onTrack === true
      ? { label: "進度順利", cls: "text-emerald-600 dark:text-emerald-400 bg-emerald-500/10" }
      : p.onTrack === false
        ? { label: "需加把勁", cls: "text-amber-600 dark:text-amber-400 bg-amber-500/10" }
        : null;

  return (
    <div className="space-y-3">
      <div className="flex items-end justify-between">
        <div>
          <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">目前 → 目標</p>
          <p className="text-2xl font-extrabold text-slate-800 dark:text-slate-100">
            {p.currentWeightKg} <span className="text-base text-muted-foreground">→ {p.targetWeightKg} kg</span>
          </p>
        </div>
        {status && <span className={`text-[11px] font-bold px-2.5 py-1 rounded-full ${status.cls}`}>{status.label}</span>}
      </div>

      {/* progress bar */}
      <div>
        <div className="h-2.5 w-full rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
          <div className={`h-full rounded-full transition-all ${barColor}`} style={{ width: `${pct}%` }} />
        </div>
        <p className="mt-1 text-[10px] text-muted-foreground text-right">{pct}% 完成</p>
      </div>

      <div className="grid grid-cols-3 gap-2.5">
        <Metric label="還差" value={`${Math.abs(p.remainingKg).toFixed(1)}`} unit="kg" />
        <Metric label="每週速率" value={p.ratePerWeekKg != null ? `${p.ratePerWeekKg > 0 ? "+" : ""}${p.ratePerWeekKg}` : "—"} unit="kg/週" />
        <Metric label="預估達標" value={fmtDate(p.projectedDate)} icon={<CalendarClock className="size-3" />} />
      </div>

      {p.targetDate && (
        <p className="text-[10px] text-muted-foreground">
          目標日：{fmtDate(p.targetDate)}
          {p.projectedDate && !p.achieved && (p.onTrack ? "（可望提前達成）" : "（依目前速度恐延後）")}
        </p>
      )}
    </div>
  );
}

function Metric({ label, value, unit, icon }: { label: string; value: string; unit?: string; icon?: ReactNode }) {
  return (
    <div className="rounded-2xl bg-slate-50/60 dark:bg-slate-900/20 border border-slate-100 dark:border-slate-900/50 p-2.5">
      <p className="text-[9px] font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-1">{icon}{label}</p>
      <p className="mt-0.5 flex items-baseline gap-1">
        <span className="text-sm font-extrabold text-slate-800 dark:text-slate-100">{value}</span>
        {unit && <span className="text-[9px] font-bold text-muted-foreground">{unit}</span>}
      </p>
    </div>
  );
}

function WeightGoalDialog({
  open,
  onClose,
  current,
}: {
  open: boolean;
  onClose: () => void;
  current: WeightGoalProgress | null;
}) {
  const { data: me } = useMe();
  const setGoal = useSetWeightGoal();
  const removeGoal = useDeleteWeightGoal();
  const [target, setTarget] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setTarget(current ? String(current.targetWeightKg) : me?.profile?.weightKg ? String(me.profile.weightKg) : "");
      setTargetDate(current?.targetDate ?? "");
      setError(null);
    }
  }, [open, current, me?.profile?.weightKg]);

  const pending = setGoal.isPending || removeGoal.isPending;

  async function save() {
    const w = Number(target);
    if (!Number.isFinite(w) || w < 20 || w > 400) {
      setError("目標體重請介於 20 ~ 400 kg。");
      return;
    }
    try {
      await setGoal.mutateAsync({ targetWeightKg: w, targetDate: targetDate || null });
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "儲存失敗，請稍後再試。");
    }
  }

  async function remove() {
    try {
      await removeGoal.mutateAsync();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "刪除失敗，請稍後再試。");
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <div className="flex size-11 items-center justify-center rounded-2xl bg-gradient-to-tr from-rose-500/15 to-orange-500/10 text-rose-600 dark:text-rose-400 mb-1">
            <Target className="size-5" />
          </div>
          <DialogTitle>{current ? "編輯體重目標" : "設定體重目標"}</DialogTitle>
          <DialogDescription>目標達成的進度與預估日期會依你的體重紀錄即時計算。</DialogDescription>
        </DialogHeader>

        <div className="grid gap-3 pt-1">
          <div className="grid gap-1.5">
            <Label htmlFor="goal-target" className="text-xs font-semibold text-slate-500">目標體重 (kg)</Label>
            <Input
              id="goal-target"
              type="number"
              min={20}
              max={400}
              step="0.1"
              autoFocus
              inputMode="decimal"
              value={target}
              disabled={pending}
              onChange={(e) => { setTarget(e.target.value); setError(null); }}
              className="rounded-2xl py-5 text-center text-lg font-bold focus-visible:ring-rose-500"
            />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="goal-date" className="text-xs font-semibold text-slate-500">目標日期（選填）</Label>
            <Input
              id="goal-date"
              type="date"
              value={targetDate}
              disabled={pending}
              onChange={(e) => setTargetDate(e.target.value)}
              className="rounded-2xl"
            />
          </div>
        </div>

        {error && <p className="text-[11px] font-medium text-rose-600 dark:text-rose-400 px-1">{error}</p>}

        <DialogFooter className="pt-1 sm:justify-between">
          {current ? (
            <Button type="button" variant="ghost" onClick={remove} disabled={pending} className="rounded-2xl text-sm text-rose-500 hover:text-rose-600 hover:bg-rose-500/5 gap-1.5">
              <Trash2 className="size-4" /> 移除
            </Button>
          ) : (
            <span />
          )}
          <Button type="button" onClick={save} disabled={pending} className="rounded-2xl text-sm bg-gradient-to-r from-rose-600 to-orange-500 text-white font-semibold">
            {setGoal.isPending ? "儲存中..." : "儲存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
