import { useState, type ReactNode } from "react";
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { BarChart3, Dumbbell, Flame, CalendarCheck, Activity } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { useWorkoutVolume } from "@/hooks/useWorkoutVolume";
import type { CategoryVolume } from "@/types/api";

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

const WEEK_OPTIONS = [
  { value: 4, label: "近 4 週" },
  { value: 8, label: "近 8 週" },
  { value: 12, label: "近 12 週" },
];

function categoryLabel(code: string): string {
  return CATEGORY_LABELS[code] ?? code;
}

function mmdd(iso: string): string {
  const parts = iso.split("-");
  return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : iso;
}

export function WorkoutVolumeCard() {
  const [weeks, setWeeks] = useState(4);
  const volume = useWorkoutVolume(weeks);
  const data = volume.data;
  // Be defensive about shape: a partial/unexpected payload degrades to the empty state.
  const categories = data?.byCategory ?? [];
  const series = data?.series ?? [];
  const hasData = (data?.totalSessions ?? 0) > 0 && series.length > 0;
  const maxSessions = Math.max(1, ...categories.map((c) => c.sessions));

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="flex flex-row items-start justify-between gap-3 pb-3">
        <div>
          <CardTitle className="text-lg font-bold flex items-center gap-2">
            <BarChart3 className="size-4.5 text-amber-500" />
            訓練量分析
          </CardTitle>
          <CardDescription className="text-xs">完成的訓練統計 · 各部位頻率 · 每週趨勢</CardDescription>
        </div>
        <Select value={String(weeks)} onValueChange={(v) => setWeeks(Number(v))}>
          <SelectTrigger className="h-9 w-28 shrink-0 rounded-xl border-slate-200/80 bg-white/50 dark:border-slate-800 dark:bg-slate-900/50 text-xs px-3">
            <SelectValue />
          </SelectTrigger>
          <SelectContent className="rounded-2xl border-slate-200/80 dark:border-slate-800">
            {WEEK_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={String(o.value)} className="rounded-xl text-xs">{o.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </CardHeader>

      <CardContent className="px-6 pb-6 space-y-5">
        {volume.isLoading ? (
          <Skeleton className="h-64 w-full rounded-2xl" />
        ) : hasData ? (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              <Stat icon={<Dumbbell className="size-4 text-emerald-500" />} label="總訓練次數" value={`${data!.totalSessions}`} unit="次" />
              <Stat icon={<CalendarCheck className="size-4 text-indigo-500" />} label="活躍天數" value={`${data!.activeDays}`} unit="天" />
              <Stat icon={<Activity className="size-4 text-sky-500" />} label="平均每週" value={`${data!.avgSessionsPerWeek}`} unit="次/週" />
              <Stat icon={<Flame className="size-4 text-rose-500" />} label="總消耗" value={`${data!.totalKcal}`} unit="kcal" />
            </div>

            {/* series / category lists below use the null-safe locals */}

            <div>
              <p className="mb-2 px-1 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">每週訓練次數</p>
              <div className="h-44">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={series} margin={{ left: -18, right: 8, top: 8, bottom: 4 }}>
                    <defs>
                      <linearGradient id="volumeFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.25} />
                        <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0.0} />
                      </linearGradient>
                    </defs>
                    <XAxis
                      dataKey="weekStart"
                      stroke="hsl(var(--muted-foreground))"
                      fontSize={10}
                      tickLine={false}
                      axisLine={false}
                      dy={8}
                      tickFormatter={mmdd}
                    />
                    <YAxis
                      stroke="hsl(var(--muted-foreground))"
                      fontSize={10}
                      tickLine={false}
                      axisLine={false}
                      allowDecimals={false}
                      width={32}
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
                      labelFormatter={(v) => `${mmdd(String(v))} 那週`}
                      formatter={(value: number) => [`${value} 次`, "訓練"]}
                    />
                    <Area
                      type="monotone"
                      dataKey="sessions"
                      name="訓練"
                      stroke="hsl(var(--primary))"
                      strokeWidth={3}
                      fillOpacity={1}
                      fill="url(#volumeFill)"
                      activeDot={{ r: 5, strokeWidth: 0, fill: "hsl(var(--primary))" }}
                      dot={{ r: 2.5, strokeWidth: 1.5, fill: "hsl(var(--card))", stroke: "hsl(var(--primary))" }}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div>
              <p className="mb-2 px-1 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">各部位訓練頻率</p>
              <ul className="space-y-2">
                {categories.map((c) => (
                  <CategoryBar key={c.category} item={c} maxSessions={maxSessions} />
                ))}
              </ul>
            </div>
          </>
        ) : (
          <div className="rounded-2xl border border-dashed border-slate-200 dark:border-slate-800 bg-white/40 dark:bg-slate-950/10 py-10 text-center">
            <div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-full bg-slate-100 dark:bg-slate-900 text-slate-400">
              <BarChart3 className="size-5" />
            </div>
            <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">這段期間還沒有完成的訓練</p>
            <p className="mt-1 text-xs text-muted-foreground max-w-xs mx-auto leading-normal">完成幾次訓練打卡後，這裡會統計你的訓練量、各部位頻率與每週趨勢。</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Stat({ icon, label, value, unit }: { icon: ReactNode; label: string; value: string; unit: string }) {
  return (
    <div className="rounded-2xl border border-slate-100 dark:border-slate-900/60 bg-slate-50/40 dark:bg-slate-900/10 p-3 flex flex-col items-center text-center">
      <span className="text-[10px] text-muted-foreground font-semibold">{label}</span>
      <div className="mt-1 flex items-center gap-1">
        {icon}
        <span className="font-extrabold text-slate-800 dark:text-slate-100">{value}</span>
      </div>
      <span className="text-[9px] text-muted-foreground mt-0.5">{unit}</span>
    </div>
  );
}

function CategoryBar({ item, maxSessions }: { item: CategoryVolume; maxSessions: number }) {
  const pct = Math.round((item.sessions / maxSessions) * 100);
  return (
    <li className="flex items-center gap-3">
      <span className="w-16 shrink-0 text-xs font-semibold text-slate-600 dark:text-slate-300">{categoryLabel(item.category)}</span>
      <div className="relative h-6 flex-1 overflow-hidden rounded-lg bg-slate-100/70 dark:bg-slate-900/40">
        <div
          className="absolute inset-y-0 left-0 rounded-lg bg-gradient-to-r from-amber-500/80 to-orange-400/80"
          style={{ width: `${Math.max(8, pct)}%` }}
        />
        <span className="absolute inset-y-0 left-2 flex items-center text-[10px] font-bold text-slate-700 dark:text-slate-100">
          {item.sessions} 次
        </span>
      </div>
      <span className="w-24 shrink-0 text-right text-[10px] text-muted-foreground tabular-nums">
        {item.sets} 組 · {item.kcal} kcal
      </span>
    </li>
  );
}
