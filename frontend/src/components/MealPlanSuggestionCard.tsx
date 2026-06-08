import { Lightbulb } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { mealSlotLabel } from "@/lib/mealSlots";
import type { HealthPlan } from "@/types/api";

export function MealPlanSuggestionCard({
  plan,
  loading,
  selectedSlot,
}: {
  plan: HealthPlan | null;
  loading: boolean;
  selectedSlot: string;
}) {
  if (loading) {
    return <Skeleton className="h-28 w-full rounded-3xl" />;
  }
  if (!plan) {
    return null;
  }

  const protein = plan.nutrition.macros.find((macro) => macro.name === "protein");
  const proteinGap = protein ? Math.max(0, protein.targetG - protein.consumedG) : 0;
  const remaining = plan.nutrition.remainingKcal;
  const targetKcal = plan.nutrition.over ? 250 : Math.max(250, Math.min(700, remaining));
  const proteinTarget = Math.max(20, Math.min(45, proteinGap || Math.round((targetKcal * 0.25) / 4)));

  const headline = plan.nutrition.over
    ? "今日熱量已超過預算"
    : protein && protein.pct < 70
      ? "下一餐優先補蛋白質"
      : remaining < 400
        ? "下一餐保持輕量"
        : "下一餐維持均衡";
  const detail = plan.nutrition.over
    ? "選擇低油、高纖、足量蛋白的組合，避免再堆高熱量。"
    : protein && protein.pct < 70
      ? `目前蛋白質約達成 ${protein.pct}%，建議下一餐抓 ${proteinTarget}g 蛋白質。`
      : remaining < 400
        ? "今日可用熱量不多，下一餐以蔬菜、湯品與瘦蛋白為主。"
        : `今日還有約 ${remaining} kcal，可安排一份主食、一份蛋白質與蔬菜。`;

  return (
    <Card className="border border-emerald-500/10 bg-emerald-500/5 dark:bg-emerald-950/10 backdrop-blur-xl shadow-xl shadow-emerald-100/40 dark:shadow-none rounded-3xl overflow-hidden">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg font-bold flex items-center gap-2">
          <Lightbulb className="size-4.5 text-emerald-500" />
          下一餐建議
        </CardTitle>
        <CardDescription className="text-xs">
          依今日健康計畫與目前選擇的{mealSlotLabel(selectedSlot)}時段調整
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 px-6 pb-6 md:grid-cols-[1fr_auto] md:items-center">
        <div>
          <p className="text-sm font-extrabold text-slate-800 dark:text-slate-100">{headline}</p>
          <p className="mt-1 text-xs leading-relaxed text-slate-600 dark:text-slate-300">{detail}</p>
        </div>
        <div className="grid grid-cols-2 gap-2 text-center sm:flex">
          <SuggestionPill label="建議熱量" value={`${targetKcal}`} unit="kcal" warn={plan.nutrition.over} />
          <SuggestionPill label="蛋白質" value={`${proteinTarget}`} unit="g" />
        </div>
      </CardContent>
    </Card>
  );
}

function SuggestionPill({ label, value, unit, warn }: { label: string; value: string; unit: string; warn?: boolean }) {
  return (
    <div className={`rounded-2xl border px-4 py-3 min-w-28 ${
      warn
        ? "border-amber-500/20 bg-amber-500/10"
        : "border-emerald-500/15 bg-white/60 dark:bg-slate-950/30"
    }`}>
      <p className="text-[10px] font-semibold text-muted-foreground">{label}</p>
      <p className="mt-0.5 flex items-baseline justify-center gap-1">
        <span className={`text-xl font-extrabold ${warn ? "text-amber-600 dark:text-amber-400" : "text-emerald-600 dark:text-emerald-400"}`}>{value}</span>
        <span className="text-[9px] font-bold text-muted-foreground">{unit}</span>
      </p>
    </div>
  );
}
