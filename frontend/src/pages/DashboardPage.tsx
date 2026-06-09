import { useEffect, useMemo, useState } from "react";
import { Activity, Apple, Flame, Pencil, Scale } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { WeightCheckInDialog } from "@/components/WeightCheckInDialog";
import { QuickWeightDialog } from "@/components/QuickWeightDialog";
import { CalorieBudgetRing } from "@/components/CalorieBudgetRing";
import { DailyHabitsCard } from "@/components/DailyHabitsCard";
import { HealthPlanCard } from "@/components/HealthPlanCard";
import { useDailyStats } from "@/hooks/useDailyStats";
import { useMe } from "@/hooks/useAuth";
import { todayLocalISO } from "@/lib/date";

/**
 * "Today" at a glance: the day's calorie balance, the health-plan summary with next-step
 * actions, and today's habits. Longer-term trends, goals, achievements and the weekly report
 * live on the Progress page so this stays focused on the present.
 */
export function DashboardPage() {
  const today = useMemo(() => todayLocalISO(), []);
  const stats = useDailyStats(today);
  const { data: user } = useMe();

  // Daily body check-in: auto-prompt once per calendar day so the weight trend stays current.
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

  return (
    <section className="grid gap-6 animate-fade-in pb-10">
      {user?.profile && (
        <>
          <WeightCheckInDialog open={checkInOpen} onClose={resolveCheckIn} profile={user.profile} />
          <QuickWeightDialog open={quickWeightOpen} onClose={() => setQuickWeightOpen(false)} profile={user.profile} />
        </>
      )}

      {/* Today's metrics */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <Metric
          icon={<Apple className="size-5 text-emerald-500" />}
          label="今日攝取"
          value={stats.isLoading ? null : `${stats.data?.intakeKcal ?? 0}`}
          unit="kcal"
          color="emerald"
        />
        <Metric
          icon={<Flame className="size-5 text-amber-500" />}
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

      {/* Today's calorie budget ring */}
      <CalorieBudgetRing />

      {/* Health plan summary + next-step actions */}
      <HealthPlanCard date={today} />

      {/* Today's habit checklist */}
      <DailyHabitsCard date={today} />
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
