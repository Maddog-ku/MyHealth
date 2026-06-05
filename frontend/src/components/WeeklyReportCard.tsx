import { useMemo, useState } from "react";
import { ChevronLeft, ChevronRight, FileText, RefreshCw, Sparkles, Wand2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useWeeklyReport, useGenerateWeeklyReport } from "@/hooks/useWeeklyReport";
import { useMe } from "@/hooks/useAuth";
import { mondayOfWeekLocalISO } from "@/lib/date";
import type { Goal } from "@/types/api";

/**
 * Colour the week's weight change by the user's goal: losing weight is "good" for
 * fat_loss but "off-track" for muscle_gain, and vice-versa. `maintain` stays neutral
 * since either direction is a small drift, not clearly good or bad.
 */
function weightAccent(delta: number, goal?: Goal): "success" | "warn" | undefined {
  if (delta === 0 || goal === "maintain" || goal == null) return undefined;
  if (goal === "muscle_gain") return delta > 0 ? "success" : "warn";
  return delta < 0 ? "success" : "warn"; // fat_loss
}

export function WeeklyReportCard() {
  // 0 = this week, -1 = last week. Capped so we don't wander far back.
  const [weekOffset, setWeekOffset] = useState(0);
  const weekStart = useMemo(() => mondayOfWeekLocalISO(weekOffset), [weekOffset]);

  const { data: me } = useMe();
  const report = useWeeklyReport(weekStart);
  const generate = useGenerateWeeklyReport();
  const generating = generate.isPending;

  const summary = report.data?.summary;
  const narrative = report.data?.narrative ?? null;

  const weekLabel = weekOffset === 0 ? "本週" : weekOffset === -1 ? "上週" : `${-weekOffset} 週前`;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-2">
        <div>
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <FileText className="size-4.5 text-violet-500" />
            {weekLabel} AI 健康報告
          </CardTitle>
          <CardDescription className="text-xs">
            {report.data ? `${report.data.weekStart} ~ ${report.data.weekEnd}` : "整合一週飲食、運動與體重的智慧回顧"}
          </CardDescription>
        </div>
        <div className="flex items-center gap-1.5">
          <Button
            variant="ghost"
            size="icon"
            className="rounded-full size-8 text-muted-foreground hover:text-foreground"
            onClick={() => setWeekOffset((w) => Math.max(-8, w - 1))}
            disabled={weekOffset <= -8}
            title="上一週"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="rounded-full size-8 text-muted-foreground hover:text-foreground"
            onClick={() => setWeekOffset((w) => Math.min(0, w + 1))}
            disabled={weekOffset >= 0}
            title="下一週"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </CardHeader>

      <CardContent className="space-y-4 pt-2">
        {report.isLoading ? (
          <Skeleton className="h-24 w-full rounded-2xl" />
        ) : (
          <>
            {/* Live key numbers */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              <Stat label="平均攝取" value={summary ? `${summary.avgIntakeKcal}` : "—"} unit="kcal/日" />
              <Stat label="運動消耗" value={summary ? `${summary.totalBurnKcal}` : "—"} unit="kcal" />
              <Stat label="完成訓練" value={summary ? `${summary.workoutsDone}` : "—"} unit="次" />
              <Stat
                label="體重變化"
                value={summary?.weightDelta != null ? `${summary.weightDelta > 0 ? "+" : ""}${summary.weightDelta}` : "—"}
                unit="kg"
                accent={summary?.weightDelta != null ? weightAccent(summary.weightDelta, me?.profile?.goal) : undefined}
              />
            </div>

            {/* AI narrative */}
            {narrative ? (
              <div className="rounded-2xl bg-violet-500/5 border border-violet-500/10 p-4 text-sm leading-relaxed text-slate-700 dark:text-slate-200 whitespace-pre-wrap">
                {narrative}
                {report.data?.generatedAt && (
                  <p className="mt-2 text-[10px] text-muted-foreground">
                    生成於 {new Date(report.data.generatedAt).toLocaleString("zh-TW")}
                  </p>
                )}
              </div>
            ) : (
              <div className="grid place-items-center rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-dashed border-slate-200 dark:border-slate-800 p-6 text-center">
                <div className="flex flex-col items-center gap-2 max-w-xs">
                  <Sparkles className="size-5 text-violet-500/60" />
                  <p className="text-xs text-muted-foreground leading-relaxed">
                    讓本機 AI 根據上方數字，為你寫一份{weekLabel}的健康回顧與建議。
                  </p>
                </div>
              </div>
            )}

            {generate.isError && (
              <p className="text-[11px] text-rose-500 dark:text-rose-400">
                產生報告失敗，請稍後再試（本機 AI 可能未啟動）。
              </p>
            )}

            <Button
              onClick={() => generate.mutate(weekStart)}
              disabled={generating}
              className="w-full rounded-2xl bg-gradient-to-tr from-violet-500 to-indigo-500 hover:opacity-90 text-white"
            >
              {generating ? (
                <RefreshCw className="size-4 mr-1.5 animate-spin" />
              ) : narrative ? (
                <RefreshCw className="size-4 mr-1.5" />
              ) : (
                <Wand2 className="size-4 mr-1.5" />
              )}
              {generating ? "AI 生成中…" : narrative ? "重新生成" : `產生${weekLabel}報告`}
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function Stat({
  label,
  value,
  unit,
  accent,
}: {
  label: string;
  value: string;
  unit: string;
  accent?: "success" | "warn";
}) {
  const valueClass =
    accent === "success"
      ? "text-emerald-600 dark:text-emerald-400"
      : accent === "warn"
        ? "text-amber-600 dark:text-amber-400"
        : "text-slate-800 dark:text-slate-100";
  return (
    <div className="rounded-2xl bg-slate-50/60 dark:bg-slate-900/20 border border-slate-100 dark:border-slate-900/50 p-3">
      <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">{label}</p>
      <p className="mt-1 flex items-baseline gap-1">
        <span className={`text-lg font-extrabold ${valueClass}`}>{value}</span>
        <span className="text-[9px] font-bold text-muted-foreground">{unit}</span>
      </p>
    </div>
  );
}
