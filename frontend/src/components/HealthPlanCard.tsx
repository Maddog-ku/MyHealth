import { ArrowRight, Dumbbell, Flag, Flame, Salad, Scale } from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useHealthPlan } from "@/hooks/useHealthPlan";

export function HealthPlanCard({ date }: { date: string }) {
  const plan = useHealthPlan(date);
  const data = plan.data;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="text-lg font-bold flex items-center gap-2">
              <Flag className="size-4.5 text-emerald-500" />
              今日健康計畫
            </CardTitle>
            <CardDescription className="text-xs">
              {data ? `${data.primaryGoal} · ${data.date}` : "整合目標、飲食、訓練與連續紀錄"}
            </CardDescription>
          </div>
          {data && (
            <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/5 px-3 py-2 text-right">
              <p className="text-[10px] font-semibold text-muted-foreground">準備度</p>
              <p className="text-2xl font-extrabold text-emerald-600 dark:text-emerald-400">{data.readinessScore}</p>
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {plan.isLoading || !data ? (
          <div className="space-y-3">
            <Skeleton className="h-20 w-full rounded-2xl" />
            <Skeleton className="h-16 w-full rounded-2xl" />
          </div>
        ) : (
          <>
            <div className="grid gap-2.5 sm:grid-cols-4">
              <PlanStat
                icon={<Salad className="size-4 text-emerald-500" />}
                label="剩餘熱量"
                value={`${data.nutrition.remainingKcal}`}
                unit="kcal"
                accent={data.nutrition.over ? "warn" : "good"}
              />
              <PlanStat
                icon={<Dumbbell className="size-4 text-indigo-500" />}
                label="本週訓練"
                value={data.workout.configured ? `${data.workout.completedThisWeek ?? 0}/${data.workout.targetSessionsPerWeek ?? 0}` : "未設定"}
                unit={data.workout.configured ? "次" : ""}
              />
              <PlanStat
                icon={<Scale className="size-4 text-sky-500" />}
                label="體重目標"
                value={data.weight.configured ? `${data.weight.progressPct}` : "未設定"}
                unit={data.weight.configured ? "%" : ""}
              />
              <PlanStat
                icon={<Flame className="size-4 text-orange-500" />}
                label="連續紀錄"
                value={`${data.streak.current}`}
                unit="天"
                accent={data.streak.current > 0 ? "good" : undefined}
              />
            </div>

            {data.nextActions.length > 0 && (
              <div className="grid gap-2">
                {data.nextActions.map((action) => (
                  <Link
                    key={`${action.type}-${action.href}`}
                    to={action.href}
                    className="group flex items-center justify-between gap-3 rounded-2xl border border-slate-100 bg-slate-50/60 px-3 py-2.5 text-sm transition-colors hover:border-emerald-500/30 hover:bg-emerald-500/5 dark:border-slate-900 dark:bg-slate-900/20"
                  >
                    <span className="min-w-0">
                      <span className="block font-semibold text-slate-800 dark:text-slate-100">{action.title}</span>
                      <span className="block truncate text-xs text-muted-foreground">{action.detail}</span>
                    </span>
                    <ArrowRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-emerald-500" />
                  </Link>
                ))}
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function PlanStat({
  icon,
  label,
  value,
  unit,
  accent,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  unit: string;
  accent?: "good" | "warn";
}) {
  return (
    <div className="rounded-2xl border border-slate-100 bg-slate-50/60 p-3 dark:border-slate-900 dark:bg-slate-900/20">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[10px] font-semibold text-muted-foreground">{label}</p>
        {icon}
      </div>
      <p className="mt-2 flex items-baseline gap-1">
        <span
          className={`text-xl font-extrabold ${
            accent === "good"
              ? "text-emerald-600 dark:text-emerald-400"
              : accent === "warn"
                ? "text-rose-500"
                : "text-slate-800 dark:text-slate-100"
          }`}
        >
          {value}
        </span>
        {unit && <span className="text-[9px] font-bold text-muted-foreground">{unit}</span>}
      </p>
    </div>
  );
}
