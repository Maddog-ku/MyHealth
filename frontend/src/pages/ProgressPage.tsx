import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { TrendingUp } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { WeightGoalCard } from "@/components/WeightGoalCard";
import { WorkoutGoalCard } from "@/components/WorkoutGoalCard";
import { StreakCard } from "@/components/StreakCard";
import { WeeklyReportCard } from "@/components/WeeklyReportCard";
import { api } from "@/api/client";
import { daysAgoLocalISO, todayLocalISO } from "@/lib/date";

const TREND_METRICS = [
  { key: "weightKg", label: "體重", unit: "kg" },
  { key: "bodyFatPct", label: "體脂率", unit: "%" },
  { key: "muscleMassKg", label: "肌肉量", unit: "kg" },
  { key: "waistCm", label: "腰圍", unit: "cm" },
  { key: "bodyWaterPct", label: "體水分率", unit: "%" },
] as const;
type MetricKey = (typeof TREND_METRICS)[number]["key"];

/**
 * Progress / review hub: body-metric trends, weight & training goals, achievements and the
 * weekly AI report. Separated from the dashboard so "today" stays focused on the present.
 */
export function ProgressPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const [metric, setMetric] = useState<MetricKey>("weightKg");
  const selectedMetric = TREND_METRICS.find((m) => m.key === metric)!;
  const [rangeDays, setRangeDays] = useState<number>(30);
  const rangeFrom = useMemo(() => daysAgoLocalISO(rangeDays - 1), [rangeDays]);

  const range = useQuery({
    queryKey: ["stats", "range", rangeFrom, today],
    queryFn: () => api.rangeStats(rangeFrom, today),
    retry: false,
  });

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {/* Body-metric trend */}
      <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
        <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-2">
          <div>
            <CardTitle className="text-lg font-bold flex items-center gap-2">
              <TrendingUp className="size-4.5 text-emerald-500" />
              近 {rangeDays} 日身體量測趨勢
            </CardTitle>
            <CardDescription className="text-xs">選擇指標與範圍查看變化軌跡</CardDescription>
          </div>
          <div className="flex flex-col items-stretch gap-1.5 sm:items-end">
            <div className="flex flex-wrap gap-1.5">
              {TREND_METRICS.map((m) => (
                <button
                  key={m.key}
                  type="button"
                  onClick={() => setMetric(m.key)}
                  className={`rounded-full px-2.5 py-1 text-[10px] font-bold border transition-colors ${
                    metric === m.key
                      ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20"
                      : "bg-slate-50 dark:bg-slate-900/40 text-muted-foreground border-slate-100 dark:border-slate-800 hover:text-foreground"
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>
            <div className="flex gap-1.5">
              {[7, 30, 90].map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => setRangeDays(d)}
                  className={`rounded-full px-2.5 py-1 text-[10px] font-bold border transition-colors ${
                    rangeDays === d
                      ? "bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border-indigo-500/20"
                      : "bg-slate-50 dark:bg-slate-900/40 text-muted-foreground border-slate-100 dark:border-slate-800 hover:text-foreground"
                  }`}
                >
                  {d} 天
                </button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-2">
          {range.isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-44 w-full rounded-2xl" />
            </div>
          ) : range.data && range.data.series.some((p) => p[metric] != null) ? (
            <div className="h-60 mt-2 pr-2">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={range.data.series} margin={{ left: -10, right: 10, top: 10, bottom: 5 }}>
                  <defs>
                    <linearGradient id="colorWeight" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.25} />
                      <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0.0} />
                    </linearGradient>
                  </defs>
                  <XAxis
                    dataKey="date"
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    dy={10}
                    tickFormatter={(v) => {
                      const parts = v.split("-");
                      return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : v;
                    }}
                  />
                  <YAxis
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                    tickLine={false}
                    axisLine={false}
                    domain={["dataMin - 1", "dataMax + 1"]}
                    dx={-5}
                  />
                  <Tooltip
                    contentStyle={{
                      background: "rgba(255, 255, 255, 0.85)",
                      backdropFilter: "blur(12px)",
                      border: "1px solid rgba(255, 255, 255, 0.4)",
                      borderRadius: "16px",
                      boxShadow: "0 10px 35px -10px rgba(0, 0, 0, 0.08)",
                      color: "hsl(var(--foreground))",
                      fontSize: "11px",
                    }}
                    itemStyle={{ color: "hsl(var(--primary))", fontWeight: "bold" }}
                    labelStyle={{ color: "hsl(var(--muted-foreground))", fontWeight: "normal" }}
                  />
                  <Area
                    type="monotone"
                    dataKey={metric}
                    name={`${selectedMetric.label} (${selectedMetric.unit})`}
                    connectNulls
                    stroke="hsl(var(--primary))"
                    strokeWidth={3}
                    fillOpacity={1}
                    fill="url(#colorWeight)"
                    activeDot={{ r: 6, strokeWidth: 0, fill: "hsl(var(--primary))" }}
                    dot={{ r: 3, strokeWidth: 1.5, fill: "hsl(var(--card))", stroke: "hsl(var(--primary))" }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <EmptyState>目前尚無「{selectedMetric.label}」的量測數據。請至「生理指標」更新身體數據，趨勢將同步於此呈現。</EmptyState>
          )}
        </CardContent>
      </Card>

      {/* Goals */}
      <div className="grid gap-6 lg:grid-cols-2">
        <WeightGoalCard />
        <WorkoutGoalCard />
      </div>

      {/* Achievements & streaks */}
      <StreakCard />

      {/* Weekly AI health report */}
      <WeeklyReportCard />
    </section>
  );
}

function EmptyState({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid h-36 place-items-center rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-dashed border-slate-200 dark:border-slate-800 text-xs text-muted-foreground text-center p-6 leading-relaxed select-none">
      {children}
    </div>
  );
}
