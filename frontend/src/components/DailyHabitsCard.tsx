import { Check, Droplets, Moon, Salad, StretchHorizontal } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useDailyHabits, useToggleHabit } from "@/hooks/useHabits";
import type { HabitItem, HabitType } from "@/types/api";

const ICONS: Record<HabitType, React.ReactNode> = {
  WATER: <Droplets className="size-4" />,
  STRETCH: <StretchHorizontal className="size-4" />,
  PROTEIN: <Salad className="size-4" />,
  SLEEP: <Moon className="size-4" />,
};

export function DailyHabitsCard({ date }: { date: string }) {
  const habits = useDailyHabits(date);
  const toggle = useToggleHabit(date);
  const pct = habits.data ? Math.round((habits.data.completed / habits.data.total) * 100) : 0;

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-4">
          <div>
            <CardTitle className="text-lg font-bold flex items-center gap-2">
              <Check className="size-4.5 text-emerald-500" />
              今日習慣
            </CardTitle>
            <CardDescription className="text-xs">維持水分、活動、營養與恢復的基本節奏</CardDescription>
          </div>
          <div className="rounded-2xl bg-emerald-500/10 px-3 py-2 text-right text-emerald-600 dark:text-emerald-400">
            <div className="text-lg font-extrabold leading-none">{pct}%</div>
            <div className="mt-0.5 text-[9px] font-bold uppercase tracking-wider">完成</div>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-3 px-6 pb-6">
        {habits.isLoading ? (
          <div className="grid gap-2 sm:grid-cols-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-20 rounded-2xl" />
            ))}
          </div>
        ) : habits.data ? (
          <>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-900">
              <div className="h-full rounded-full bg-emerald-500 transition-all duration-300" style={{ width: `${pct}%` }} />
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              {habits.data.items.map((item) => (
                <HabitToggle
                  key={item.type}
                  item={item}
                  disabled={toggle.isPending}
                  onToggle={() => toggle.mutate({ type: item.type, completed: !item.completed })}
                />
              ))}
            </div>
          </>
        ) : (
          <div className="rounded-2xl border border-dashed border-slate-200 dark:border-slate-800 p-4 text-center text-xs text-muted-foreground">
            無法載入今日習慣。
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function HabitToggle({ item, disabled, onToggle }: { item: HabitItem; disabled: boolean; onToggle: () => void }) {
  return (
    <Button
      type="button"
      variant="outline"
      disabled={disabled}
      onClick={onToggle}
      aria-pressed={item.completed}
      className={`h-auto justify-start gap-3 rounded-2xl border p-3 text-left transition-all ${
        item.completed
          ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 hover:bg-emerald-500/15 dark:text-emerald-300"
          : "border-slate-100 bg-white/45 text-slate-700 hover:border-slate-200 hover:bg-slate-50 dark:border-slate-900/60 dark:bg-slate-950/20 dark:text-slate-300 dark:hover:bg-slate-900/40"
      }`}
    >
      <span
        className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${
          item.completed ? "bg-emerald-500 text-white" : "bg-slate-100 text-muted-foreground dark:bg-slate-900"
        }`}
      >
        {item.completed ? <Check className="size-4" /> : ICONS[item.type]}
      </span>
      <span className="min-w-0">
        <span className="block text-xs font-extrabold">{item.title}</span>
        <span className="mt-0.5 block whitespace-normal text-[10px] leading-snug opacity-75">{item.description}</span>
      </span>
    </Button>
  );
}
