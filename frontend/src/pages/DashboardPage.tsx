import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Activity, Apple, Dumbbell, Flame, Pencil, Scale, Sparkles, TrendingUp, Cpu } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { WeightCheckInDialog } from "@/components/WeightCheckInDialog";
import { QuickWeightDialog } from "@/components/QuickWeightDialog";
import { useDailyStats } from "@/hooks/useDailyStats";
import { useAiStatus } from "@/hooks/useAiStatus";
import { useMe } from "@/hooks/useAuth";
import { api } from "@/api/client";
import { daysAgoLocalISO, todayLocalISO } from "@/lib/date";

export function DashboardPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const sevenDaysAgo = useMemo(() => daysAgoLocalISO(6), []);
  const stats = useDailyStats(today);
  const ai = useAiStatus();
  const { data: user } = useMe();

  // Daily body check-in: auto-prompt once per calendar day so the weight trend
  // stays current. Dismiss (save or skip) is remembered in localStorage per date.
  const checkInKey = `weightCheckIn:${today}`;
  const [checkInOpen, setCheckInOpen] = useState(false);
  useEffect(() => {
    if (!user?.profile) return;
    if (!localStorage.getItem(checkInKey)) setCheckInOpen(true);
  }, [user, checkInKey]);

  function resolveCheckIn() {
    localStorage.setItem(checkInKey, "done");
    setCheckInOpen(false);
  }

  // Quick weight edit, opened by tapping the "目前體重" metric.
  const [quickWeightOpen, setQuickWeightOpen] = useState(false);

  const range = useQuery({
    queryKey: ["stats", "range", sevenDaysAgo, today],
    queryFn: () => api.rangeStats(sevenDaysAgo, today),
    retry: false,
  });

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {user?.profile && (
        <>
          <WeightCheckInDialog open={checkInOpen} onClose={resolveCheckIn} profile={user.profile} />
          <QuickWeightDialog open={quickWeightOpen} onClose={() => setQuickWeightOpen(false)} profile={user.profile} />
        </>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Metric
          icon={<Apple className="size-5 text-emerald-500" />}
          label="今日攝取"
          value={stats.isLoading ? null : `${stats.data?.intakeKcal ?? 0}`}
          unit="kcal"
          color="emerald"
        />
        <Metric
          icon={<Flame className="size-5 text-amber-500 animate-pulse" />}
          label="運動消耗"
          value={stats.isLoading ? null : `${stats.data?.burnKcal ?? 0}`}
          unit="kcal"
          color="amber"
        />
        <Metric
          icon={<Activity className="size-5 text-teal-500" />}
          label="淨熱量收支"
          value={stats.isLoading ? null : `${stats.data?.netKcal ?? 0}`}
          unit="kcal"
          accent={stats.data ? (stats.data.netKcal > 0 ? "destructive" : "success") : undefined}
          color="teal"
        />
        <Metric
          icon={<Scale className="size-5 text-indigo-500" />}
          label="目前體重"
          value={stats.isLoading ? null : stats.data?.weightKg ? `${stats.data.weightKg}` : "—"}
          unit="kg"
          color="indigo"
          onClick={user?.profile ? () => setQuickWeightOpen(true) : undefined}
        />
      </div>

      {/* Main Content Layout Grid */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Trend Area Chart (Col span 2) */}
        <Card className="lg:col-span-2 border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
          <CardHeader className="flex flex-row items-center justify-between gap-4 pb-2">
            <div>
              <CardTitle className="text-md font-bold flex items-center gap-2">
                <TrendingUp className="size-4.5 text-emerald-500" />
                近七日體重與測量趨勢
              </CardTitle>
              <CardDescription className="text-xs">整合每日身體數據與量測變動軌跡</CardDescription>
            </div>
            <Badge variant="secondary" className="rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/10 text-[10px] py-0.5 font-bold uppercase tracking-wider">
              系統估算
            </Badge>
          </CardHeader>
          <CardContent className="pt-2">
            {range.isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-44 w-full rounded-2xl" />
              </div>
            ) : range.data && range.data.series.some((p) => p.weightKg != null) ? (
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
                      dataKey="weightKg"
                      name="體重 (kg)"
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
              <EmptyState>目前尚無足夠的體重數據。請至「系統設定」更新體重，數據將同步於此呈現。</EmptyState>
            )}
          </CardContent>
        </Card>

        {/* AI Engine Status Card */}
        <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect flex flex-col justify-between">
          <div>
            <CardHeader className="pb-3">
              <CardTitle className="text-md font-bold flex items-center gap-2">
                <Cpu className="size-4.5 text-emerald-500" />
                AI 核心智能引擎
              </CardTitle>
              <CardDescription className="text-xs">管理本機推論引擎與深度分析狀態</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {ai.isLoading ? (
                <div className="space-y-3">
                  <Skeleton className="h-4 w-full rounded-md" />
                  <Skeleton className="h-4 w-[80%] rounded-md" />
                  <Skeleton className="h-4 w-[60%] rounded-md" />
                </div>
              ) : ai.data ? (
                <div className="space-y-3.5 pt-1">
                  {/* Status Indicator */}
                  <div className="flex items-center justify-between p-3 rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-slate-100 dark:border-slate-900/50">
                    <span className="text-xs text-muted-foreground font-medium">引擎狀態</span>
                    <div className="flex items-center gap-2">
                      <span className="relative flex h-2 w-2">
                        <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${ai.data.loaded ? 'bg-emerald-400' : 'bg-amber-400'}`}></span>
                        <span className={`relative inline-flex rounded-full h-2 w-2 ${ai.data.loaded ? 'bg-emerald-500' : 'bg-amber-500'}`}></span>
                      </span>
                      <Badge
                        variant="secondary"
                        className={`rounded-xl text-[10px] font-bold px-2 py-0.5 border ${
                          ai.data.loaded
                            ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/10"
                            : "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/10"
                        }`}
                      >
                        {ai.data.loaded ? "運作中 / 已載入" : "閒置等待中"}
                      </Badge>
                    </div>
                  </div>

                  {/* Metadata Grid */}
                  <div className="grid gap-2.5 text-xs">
                    <div className="flex items-center justify-between py-1 border-b border-slate-50 dark:border-slate-900/30">
                      <span className="text-muted-foreground">推理主機</span>
                      <span className="font-semibold">{ai.data.provider}</span>
                    </div>
                    <div className="flex items-center justify-between py-1 border-b border-slate-50 dark:border-slate-900/30">
                      <span className="text-muted-foreground">主語言模型</span>
                      <span className="font-mono text-[10px] bg-slate-100 dark:bg-slate-900 px-2 py-0.5 rounded-md font-semibold truncate max-w-[150px]" title={ai.data.textModel}>
                        {ai.data.textModel}
                      </span>
                    </div>
                    <div className="flex items-center justify-between py-1 border-b border-slate-50 dark:border-slate-900/30">
                      <span className="text-muted-foreground">視覺辨識模型</span>
                      <span className="font-mono text-[10px] bg-slate-100 dark:bg-slate-900 px-2 py-0.5 rounded-md font-semibold truncate max-w-[150px]" title={ai.data.visionModel}>
                        {ai.data.visionModel}
                      </span>
                    </div>
                  </div>
                </div>
              ) : (
                <EmptyState>智慧引擎離線，無法取得即時分析服務。</EmptyState>
              )}
            </CardContent>
          </div>

          <CardContent className="pt-2">
            {ai.data && (
              <div className="p-3 rounded-2xl bg-amber-500/5 border border-amber-500/10 text-[10px] text-amber-700 dark:text-amber-300 flex items-start gap-2 leading-relaxed">
                <Sparkles className="size-4 shrink-0 text-amber-500 mt-0.5" />
                <span>
                  本機推論優化啟動中：當閒置超過 <strong>{ai.data.idleTimeoutSec}秒</strong>，將自動釋放顯存 (VRAM) 以降低功耗並釋放記憶體。
                </span>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  );
}

function Metric({
  icon,
  label,
  value,
  unit,
  accent,
  color,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  value: string | null;
  unit: string;
  accent?: "destructive" | "success";
  color: "emerald" | "amber" | "teal" | "indigo";
  onClick?: () => void;
}) {
  const colorMap = {
    emerald: "border-l-emerald-500 bg-emerald-500/5",
    amber: "border-l-amber-500 bg-amber-500/5",
    teal: "border-l-teal-500 bg-teal-500/5",
    indigo: "border-l-indigo-500 bg-indigo-500/5",
  };

  const clickable = Boolean(onClick);

  return (
    <Card
      {...(clickable
        ? {
            role: "button",
            tabIndex: 0,
            "aria-label": `${label}，點擊以快速更新`,
            onClick,
            onKeyDown: (e: React.KeyboardEvent) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onClick?.();
              }
            },
          }
        : {})}
      className={`border border-slate-100/80 dark:border-slate-900/40 border-l-4 ${colorMap[color]} bg-white/60 dark:bg-slate-950/20 backdrop-blur-xl shadow-md shadow-slate-100/50 dark:shadow-none rounded-2xl overflow-hidden card-hover-effect${
        clickable
          ? " cursor-pointer transition-all hover:border-indigo-300 dark:hover:border-indigo-500/40 hover:shadow-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/60"
          : ""
      }`}
    >
      <CardContent className="flex flex-col gap-2 p-4 md:p-5">
        <div className="flex items-center justify-between text-xs font-semibold text-muted-foreground uppercase tracking-wide">
          <span className="flex items-center gap-1.5">
            {label}
            {clickable && <Pencil className="size-3 text-indigo-400" />}
          </span>
          <div className="p-1.5 rounded-xl bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-900 shadow-sm">
            {icon}
          </div>
        </div>

        {value === null ? (
          <Skeleton className="h-8 w-20 rounded-lg mt-1" />
        ) : (
          <div className="flex items-baseline gap-1.5 mt-1">
            <span
              className={
                accent === "destructive"
                  ? "text-2xl font-extrabold text-rose-500 text-glow"
                  : accent === "success"
                    ? "text-2xl font-extrabold text-emerald-600 dark:text-emerald-400 text-glow"
                    : "text-2xl font-extrabold text-slate-800 dark:text-slate-100"
              }
            >
              {value}
            </span>
            <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">{unit}</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function EmptyState({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid h-36 place-items-center rounded-2xl bg-slate-50/50 dark:bg-slate-900/20 border border-dashed border-slate-200 dark:border-slate-800 text-xs text-muted-foreground text-center p-6 leading-relaxed select-none">
      <div className="flex flex-col items-center gap-2 max-w-xs">
        <Sparkles className="size-5 text-emerald-500/60 animate-bounce" />
        {children}
      </div>
    </div>
  );
}
