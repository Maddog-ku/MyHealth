import { useEffect, useState } from "react";
import { Check, Settings2 } from "lucide-react";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useHealthPlanSettings, useUpdateHealthPlanSettings } from "@/hooks/useHealthPlan";
import type { Goal } from "@/types/api";

const GOAL_LABEL: Record<Goal, string> = {
  fat_loss: "減脂",
  muscle_gain: "增肌",
  maintain: "維持健康",
};

export function HealthPlanSettingsCard() {
  const settings = useHealthPlanSettings();
  const update = useUpdateHealthPlanSettings();
  const [primaryGoal, setPrimaryGoal] = useState<Goal>("maintain");
  const [weightEnabled, setWeightEnabled] = useState(false);
  const [targetWeight, setTargetWeight] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [workoutEnabled, setWorkoutEnabled] = useState(false);
  const [targetSessions, setTargetSessions] = useState("3");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!settings.data) return;
    setPrimaryGoal(settings.data.primaryGoal);
    setWeightEnabled(Boolean(settings.data.weightGoal));
    setTargetWeight(settings.data.weightGoal?.targetWeightKg != null
      ? String(settings.data.weightGoal.targetWeightKg)
      : settings.data.currentWeightKg != null ? String(settings.data.currentWeightKg) : "");
    setTargetDate(settings.data.weightGoal?.targetDate ?? "");
    setWorkoutEnabled(Boolean(settings.data.workoutGoal));
    setTargetSessions(String(settings.data.workoutGoal?.targetSessionsPerWeek ?? 3));
  }, [settings.data]);

  async function save() {
    setMessage(null);
    const parsedWeight = Number(targetWeight);
    const parsedSessions = Number(targetSessions);

    if (weightEnabled && (!Number.isFinite(parsedWeight) || parsedWeight < 20 || parsedWeight > 400)) {
      setMessage("目標體重請介於 20 ~ 400 kg。");
      return;
    }
    if (workoutEnabled && (!Number.isInteger(parsedSessions) || parsedSessions < 1 || parsedSessions > 14)) {
      setMessage("每週訓練次數請介於 1 ~ 14。");
      return;
    }

    try {
      await update.mutateAsync({
        primaryGoal,
        weightGoal: {
          enabled: weightEnabled,
          targetWeightKg: weightEnabled ? parsedWeight : null,
          targetDate: weightEnabled && targetDate ? targetDate : null,
        },
        workoutGoal: {
          enabled: workoutEnabled,
          targetSessionsPerWeek: workoutEnabled ? parsedSessions : null,
        },
      });
      setMessage("健康計畫已更新。");
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "儲存失敗，請稍後再試。");
    }
  }

  const pending = update.isPending;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Settings2 className="size-4.5 text-sky-500" />
          健康計畫設定
        </CardTitle>
        <CardDescription className="text-xs">一次同步主目標、體重目標與每週訓練目標</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {settings.isLoading || !settings.data ? (
          <Skeleton className="h-36 w-full rounded-2xl" />
        ) : (
          <>
            <div className="grid gap-3 md:grid-cols-3">
              <div className="grid gap-1.5">
                <Label className="text-xs font-semibold text-slate-500">主目標</Label>
                <Select value={primaryGoal} onValueChange={(value) => setPrimaryGoal(value as Goal)} disabled={pending}>
                  <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                    {Object.entries(GOAL_LABEL).map(([value, label]) => (
                      <SelectItem key={value} value={value} className="rounded-xl">{label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2 rounded-2xl border border-slate-100 bg-slate-50/60 p-3 dark:border-slate-900 dark:bg-slate-900/20">
                <label className="flex items-center gap-2 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  <input
                    type="checkbox"
                    checked={weightEnabled}
                    disabled={pending}
                    onChange={(event) => setWeightEnabled(event.target.checked)}
                    className="size-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
                  />
                  體重目標
                </label>
                <div className="grid grid-cols-[1fr_auto] gap-2">
                  <Input
                    type="number"
                    min={20}
                    max={400}
                    step="0.1"
                    inputMode="decimal"
                    value={targetWeight}
                    disabled={pending || !weightEnabled}
                    onChange={(event) => setTargetWeight(event.target.value)}
                    className="rounded-2xl"
                  />
                  <span className="self-center text-[10px] font-bold text-muted-foreground">kg</span>
                </div>
                <Input
                  type="date"
                  value={targetDate}
                  disabled={pending || !weightEnabled}
                  onChange={(event) => setTargetDate(event.target.value)}
                  className="rounded-2xl"
                />
              </div>

              <div className="grid gap-2 rounded-2xl border border-slate-100 bg-slate-50/60 p-3 dark:border-slate-900 dark:bg-slate-900/20">
                <label className="flex items-center gap-2 text-xs font-semibold text-slate-600 dark:text-slate-300">
                  <input
                    type="checkbox"
                    checked={workoutEnabled}
                    disabled={pending}
                    onChange={(event) => setWorkoutEnabled(event.target.checked)}
                    className="size-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
                  />
                  每週訓練目標
                </label>
                <Select value={targetSessions} onValueChange={setTargetSessions} disabled={pending || !workoutEnabled}>
                  <SelectTrigger className="rounded-2xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 py-5">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
                    {[1, 2, 3, 4, 5, 6, 7].map((value) => (
                      <SelectItem key={value} value={String(value)} className="rounded-xl">{value} 次 / 週</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className={`text-xs ${message?.includes("失敗") || message?.includes("請介於") ? "text-rose-500" : "text-muted-foreground"}`}>
                {message ?? `目前主目標：${GOAL_LABEL[settings.data.primaryGoal]}`}
              </p>
              <Button
                onClick={save}
                disabled={pending}
                className="rounded-2xl bg-gradient-to-r from-sky-500 to-emerald-500 px-5 font-semibold text-white hover:from-sky-400 hover:to-emerald-400"
              >
                <Check className="size-4" />
                儲存計畫
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
