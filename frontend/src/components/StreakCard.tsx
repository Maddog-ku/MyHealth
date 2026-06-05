import { Flame, Trophy } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useStreak } from "@/hooks/useStreak";
import type { Achievement, StreakInfo } from "@/types/api";

export function StreakCard() {
  const { data, isLoading } = useStreak();

  const newly = data?.achievements?.filter((a) => data.newlyUnlocked?.includes(a.code)) ?? [];

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-2">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Flame className="size-4.5 text-orange-500" />
          連續記錄與成就
        </CardTitle>
        <CardDescription className="text-xs">每天記錄一點,累積你的健康連勝 🔥</CardDescription>
      </CardHeader>

      <CardContent className="space-y-4 pt-2">
        {isLoading || !data ? (
          <Skeleton className="h-28 w-full rounded-2xl" />
        ) : (
          <>
            {/* Streaks */}
            <div className="grid grid-cols-3 gap-2.5">
              <StreakStat label="總記錄" info={data.overallStreak} highlight />
              <StreakStat label="飲食" info={data.mealStreak} />
              <StreakStat label="運動" info={data.workoutStreak} />
            </div>

            {/* Celebrate anything just unlocked */}
            {newly.length > 0 && (
              <div className="rounded-2xl bg-gradient-to-tr from-amber-400/15 to-orange-400/10 border border-amber-400/30 p-3 text-sm">
                <p className="font-semibold text-amber-700 dark:text-amber-300 flex items-center gap-1.5">
                  <Trophy className="size-4" /> 恭喜解鎖新成就!
                </p>
                <p className="mt-1 text-slate-600 dark:text-slate-300">
                  {newly.map((a) => `${a.emoji} ${a.title}`).join("、")}
                </p>
              </div>
            )}

            {/* Badge wall */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
              {data.achievements.map((a) => (
                <BadgeTile key={a.code} achievement={a} />
              ))}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function StreakStat({ label, info, highlight }: { label: string; info: StreakInfo; highlight?: boolean }) {
  return (
    <div
      className={`rounded-2xl border p-3 ${
        highlight
          ? "bg-orange-500/5 border-orange-500/20"
          : "bg-slate-50/60 dark:bg-slate-900/20 border-slate-100 dark:border-slate-900/50"
      }`}
    >
      <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wide">{label}</p>
      <p className="mt-1 flex items-baseline gap-1">
        <span className={`text-2xl font-extrabold ${highlight ? "text-orange-600 dark:text-orange-400" : "text-slate-800 dark:text-slate-100"}`}>
          {info.current}
        </span>
        <span className="text-[9px] font-bold text-muted-foreground">天</span>
      </p>
      <p className="text-[9px] text-muted-foreground">最長 {info.longest} 天</p>
    </div>
  );
}

function BadgeTile({ achievement: a }: { achievement: Achievement }) {
  const pct = a.threshold > 0 ? Math.min(100, Math.round((a.progress / a.threshold) * 100)) : 0;
  return (
    <div
      className={`rounded-2xl border p-3 flex flex-col items-center text-center gap-1 ${
        a.unlocked
          ? "bg-violet-500/5 border-violet-500/20"
          : "bg-slate-50/40 dark:bg-slate-900/20 border-dashed border-slate-200 dark:border-slate-800"
      }`}
      title={a.description}
    >
      <span className={`text-2xl ${a.unlocked ? "" : "grayscale opacity-40"}`}>{a.emoji}</span>
      <span className={`text-[11px] font-semibold leading-tight ${a.unlocked ? "text-slate-700 dark:text-slate-200" : "text-muted-foreground"}`}>
        {a.title}
      </span>
      {a.unlocked ? (
        <span className="text-[9px] text-violet-500 font-bold">已解鎖</span>
      ) : (
        <div className="w-full mt-0.5">
          <div className="h-1 w-full rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
            <div className="h-full rounded-full bg-violet-400/70" style={{ width: `${pct}%` }} />
          </div>
          <span className="text-[9px] text-muted-foreground">
            {a.progress}/{a.threshold}
          </span>
        </div>
      )}
    </div>
  );
}
