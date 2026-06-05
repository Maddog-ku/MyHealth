import { Flame } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useCalorieBudget } from "@/hooks/useCalorieBudget";
import type { MacroBudget } from "@/types/api";

const MACRO_LABEL: Record<MacroBudget["name"], string> = {
  protein: "蛋白質",
  carb: "碳水",
  fat: "脂肪",
};
const MACRO_BAR: Record<MacroBudget["name"], string> = {
  protein: "bg-rose-400",
  carb: "bg-amber-400",
  fat: "bg-sky-400",
};

const R = 52;
const CIRC = 2 * Math.PI * R;

export function CalorieBudgetRing() {
  const { data, isLoading } = useCalorieBudget();

  return (
    <Card className="border border-slate-100/80 dark:border-slate-900/60 bg-white/70 dark:bg-slate-950/40 backdrop-blur-xl shadow-xl shadow-slate-100/50 dark:shadow-none rounded-3xl overflow-hidden card-hover-effect">
      <CardHeader className="pb-2">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Flame className="size-4.5 text-orange-500" />
          今日熱量預算
        </CardTitle>
        <CardDescription className="text-xs">目標加上運動消耗，扣掉已攝取的剩餘額度</CardDescription>
      </CardHeader>

      <CardContent className="pt-2">
        {isLoading || !data ? (
          <Skeleton className="h-40 w-full rounded-2xl" />
        ) : (
          <div className="flex flex-col sm:flex-row items-center gap-6">
            {/* Ring */}
            <div className="relative shrink-0">
              <svg width="132" height="132" viewBox="0 0 132 132" className="-rotate-90">
                <circle cx="66" cy="66" r={R} fill="none" strokeWidth="12" className="stroke-slate-100 dark:stroke-slate-800" />
                <circle
                  cx="66"
                  cy="66"
                  r={R}
                  fill="none"
                  strokeWidth="12"
                  strokeLinecap="round"
                  className={data.over ? "stroke-rose-500" : "stroke-emerald-500"}
                  strokeDasharray={CIRC}
                  strokeDashoffset={CIRC * (1 - Math.min(100, Math.max(0, data.consumedPct)) / 100)}
                  style={{ transition: "stroke-dashoffset 0.5s ease" }}
                />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
                <span className={`text-2xl font-extrabold ${data.over ? "text-rose-500" : "text-slate-800 dark:text-slate-100"}`}>
                  {data.over ? `超 ${Math.abs(data.remainingKcal)}` : data.remainingKcal}
                </span>
                <span className="text-[10px] text-muted-foreground">{data.over ? "kcal 超標" : "kcal 可吃"}</span>
              </div>
            </div>

            {/* Breakdown + macros */}
            <div className="flex-1 w-full space-y-3">
              <div className="grid grid-cols-3 gap-2 text-center">
                <Stat label="已攝取" value={data.intakeKcal} />
                <Stat label="運動補回" value={`+${data.burnKcal}`} />
                <Stat label="預算" value={data.budgetKcal} />
              </div>
              <div className="space-y-2">
                {data.macros.map((m) => (
                  <MacroRow key={m.name} macro={m} />
                ))}
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-2xl bg-slate-50/60 dark:bg-slate-900/20 border border-slate-100 dark:border-slate-900/50 p-2">
      <p className="text-[9px] font-semibold text-muted-foreground uppercase tracking-wide">{label}</p>
      <p className="text-sm font-extrabold text-slate-800 dark:text-slate-100">{value}</p>
    </div>
  );
}

function MacroRow({ macro: m }: { macro: MacroBudget }) {
  const pct = Math.min(100, Math.max(0, m.pct));
  const over = m.pct > 100;
  return (
    <div>
      <div className="flex items-center justify-between text-[11px] mb-0.5">
        <span className="font-semibold text-slate-600 dark:text-slate-300">{MACRO_LABEL[m.name]}</span>
        <span className={over ? "text-amber-600 dark:text-amber-400 font-semibold" : "text-muted-foreground"}>
          {m.consumedG} / {m.targetG} g
        </span>
      </div>
      <div className="h-1.5 w-full rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
        <div className={`h-full rounded-full ${over ? "bg-amber-500" : MACRO_BAR[m.name]}`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
