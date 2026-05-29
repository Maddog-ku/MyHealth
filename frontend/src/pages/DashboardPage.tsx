import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Activity, Apple, Dumbbell, Flame, Scale } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useDailyStats } from "@/hooks/useDailyStats";
import { useAiStatus } from "@/hooks/useAiStatus";
import { api } from "@/api/client";
import { daysAgoLocalISO, todayLocalISO } from "@/lib/date";

export function DashboardPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const sevenDaysAgo = useMemo(() => daysAgoLocalISO(6), []);
  const stats = useDailyStats(today);
  const ai = useAiStatus();

  const range = useQuery({
    queryKey: ["stats", "range", sevenDaysAgo, today],
    queryFn: () => api.rangeStats(sevenDaysAgo, today),
    retry: false,
  });

  return (
    <section className="grid gap-6 animate-fade-in">
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Metric
          icon={<Apple className="size-4" />}
          label="今日攝取"
          value={stats.isLoading ? null : `${stats.data?.intakeKcal ?? 0}`}
          unit="kcal"
        />
        <Metric
          icon={<Flame className="size-4" />}
          label="預估消耗"
          value={stats.isLoading ? null : `${stats.data?.burnKcal ?? 0}`}
          unit="kcal"
        />
        <Metric
          icon={<Activity className="size-4" />}
          label="淨熱量"
          value={stats.isLoading ? null : `${stats.data?.netKcal ?? 0}`}
          unit="kcal"
          accent={stats.data ? (stats.data.netKcal > 0 ? "destructive" : "success") : undefined}
        />
        <Metric
          icon={<Scale className="size-4" />}
          label="體重"
          value={stats.isLoading ? null : stats.data?.weightKg ? `${stats.data.weightKg}` : "—"}
          unit="kg"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>近 7 天體重趨勢</CardTitle>
                <CardDescription>來自每日紀錄與量測歷史</CardDescription>
              </div>
              <Badge variant="muted">估算</Badge>
            </div>
          </CardHeader>
          <CardContent>
            {range.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : range.data && range.data.series.some((p) => p.weightKg != null) ? (
              <div className="h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={range.data.series} margin={{ left: 0, right: 12, top: 8, bottom: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                    <XAxis dataKey="date" stroke="hsl(var(--muted-foreground))" fontSize={11} />
                    <YAxis stroke="hsl(var(--muted-foreground))" fontSize={11} domain={["dataMin-1", "dataMax+1"]} />
                    <Tooltip
                      contentStyle={{
                        background: "hsl(var(--popover))",
                        borderColor: "hsl(var(--border))",
                        color: "hsl(var(--popover-foreground))",
                      }}
                    />
                    <Line type="monotone" dataKey="weightKg" stroke="hsl(var(--primary))" strokeWidth={2} dot />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <EmptyState>還沒有體重紀錄，到「設定」更新身體數據即可累積趨勢。</EmptyState>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Dumbbell className="size-4 text-muted-foreground" />
              AI 狀態
            </CardTitle>
            <CardDescription>本地推論引擎</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {ai.isLoading ? (
              <Skeleton className="h-20 w-full" />
            ) : ai.data ? (
              <>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Provider</span>
                  <Badge variant="secondary">{ai.data.provider}</Badge>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">文字模型</span>
                  <span className="font-mono text-xs">{ai.data.textModel}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">視覺模型</span>
                  <span className="font-mono text-xs">{ai.data.visionModel}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">載入狀態</span>
                  <Badge variant={ai.data.loaded ? "success" : "muted"}>{ai.data.loaded ? "已載入" : "閒置"}</Badge>
                </div>
                <p className="pt-2 text-xs text-muted-foreground">閒置 {ai.data.idleTimeoutSec}s 後自動釋放</p>
              </>
            ) : (
              <EmptyState>AI 服務未連線</EmptyState>
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
}: {
  icon: React.ReactNode;
  label: string;
  value: string | null;
  unit: string;
  accent?: "destructive" | "success";
}) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-2 p-5">
        <div className="flex items-center gap-1.5 text-xs uppercase tracking-wide text-muted-foreground">
          {icon}
          {label}
        </div>
        {value === null ? (
          <Skeleton className="h-8 w-20" />
        ) : (
          <div className="flex items-baseline gap-1.5">
            <span
              className={
                accent === "destructive"
                  ? "text-2xl font-bold text-destructive"
                  : accent === "success"
                    ? "text-2xl font-bold text-success"
                    : "text-2xl font-bold"
              }
            >
              {value}
            </span>
            <span className="text-xs text-muted-foreground">{unit}</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function EmptyState({ children }: { children: React.ReactNode }) {
  return <div className="grid h-32 place-items-center rounded-md bg-muted/30 text-sm text-muted-foreground">{children}</div>;
}
